package com.xiaoai.agent.tool.executor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class DefaultCliProcessRunner implements CliProcessRunner {

    @Override
public CliProcessResult run(List<String> command, Path workingDirectory, Duration timeout, int maxOutputChars)
            throws IOException, InterruptedException, TimeoutException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (workingDirectory != null) {
            processBuilder.directory(workingDirectory.toFile());
        }
        Process process = processBuilder.start();
        ExecutorService streamReaders = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<String> stdout = CompletableFuture.supplyAsync(
                    () -> readLimitedUnchecked(process.getInputStream(), maxOutputChars), streamReaders);
            CompletableFuture<String> stderr = CompletableFuture.supplyAsync(
                    () -> readLimitedUnchecked(process.getErrorStream(), maxOutputChars), streamReaders);
            boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new TimeoutException("CLI tool timed out");
            }
            return new CliProcessResult(process.exitValue(), getFuture(stdout), getFuture(stderr));
        } finally {
            streamReaders.shutdownNow();
        }
    }

    private String readLimitedUnchecked(InputStream input, int maxOutputChars) {
        try {
            return readLimited(input, maxOutputChars);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String readLimited(InputStream input, int maxOutputChars) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[512];
        int remaining = Math.max(0, maxOutputChars);
        int read;
        boolean truncated = false;
        while ((read = input.read(buffer)) != -1) {
            if (remaining > 0) {
                int writable = Math.min(read, remaining);
                output.write(buffer, 0, writable);
                remaining -= writable;
                if (read > writable) {
                    truncated = true;
                }
            } else {
                truncated = true;
            }
        }
        if (truncated) {
            output.write("\n...truncated".getBytes(StandardCharsets.UTF_8));
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private String getFuture(CompletableFuture<String> future) throws IOException, InterruptedException {
        try {
            return future.get();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalStateException && cause.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IOException(cause);
        }
    }
}
