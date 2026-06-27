package com.xiaoai.agent.tool.executor;

import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.exception.BusinessException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

class DefaultHttpToolClient implements HttpToolClient {

    private final HttpClient httpClient;

    DefaultHttpToolClient() {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    DefaultHttpToolClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
public HttpToolResult postJson(URI uri, String payloadJson, Map<String, String> headers, Duration timeout) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payloadJson));
            headers.forEach(builder::header);
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new HttpToolResult(response.statusCode(), response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "HTTP tool execution interrupted");
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "HTTP tool execution failed: " + exception.getMessage());
        }
    }
}
