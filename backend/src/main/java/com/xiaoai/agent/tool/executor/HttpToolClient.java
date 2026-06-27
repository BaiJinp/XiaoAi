package com.xiaoai.agent.tool.executor;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

interface HttpToolClient {

    HttpToolResult postJson(URI uri, String payloadJson, Map<String, String> headers, Duration timeout);
}
