package com.xiaoai.agent.tool.executor;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

interface CliProcessRunner {

    CliProcessResult run(List<String> command, Path workingDirectory, Duration timeout, int maxOutputChars)
            throws IOException, InterruptedException, TimeoutException;
}

record CliProcessResult(int exitCode, String stdout, String stderr) {
}
