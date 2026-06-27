package com.xiaoai.agent.tool.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.tool.executor.browser.BrowserActionResult;
import com.xiaoai.agent.tool.executor.browser.BrowserSession;
import com.xiaoai.agent.tool.executor.browser.BrowserSessionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.*;

/**
 * 浏览器工具执行器
 * <p>
 * 受控浏览器工具，支持网页导航、内容提取、截图、表单填写、元素点击等操作。
 * 遵循 controlled.browser. 前缀命名规范，复用 HTTP 工具的安全策略（私网限制、URL 白名单）。
 * </p>
 *
 * 支持的 toolCode：
 * - controlled.browser.navigate：导航到指定 URL 并返回页面文本
 * - controlled.browser.screenshot：页面截图（返回 base64）
 * - controlled.browser.extract_table：提取页面表格数据
 * - controlled.browser.click：点击指定元素
 * - controlled.browser.fill_form：填写表单字段
 */
@Component
public class BrowserToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(BrowserToolExecutor.class);
    private static final String TOOL_CODE_PREFIX = "controlled.browser.";
    private static final int MAX_OUTPUT_LENGTH = 4000;

    private final Set<String> allowedHosts;
    private final BrowserSessionPool sessionPool;
    private final ObjectMapper objectMapper;

    @Autowired
    public BrowserToolExecutor(BrowserSessionPool sessionPool, ObjectMapper objectMapper) {
        this.sessionPool = sessionPool;
        this.objectMapper = objectMapper;
        this.allowedHosts = Set.of();
    }

    /**
     * 执行浏览器工具调用
     */
    public String execute(String toolCode, String payloadJson, long timeoutMs) {
        if (!toolCode.startsWith(TOOL_CODE_PREFIX)) {
            return errorResult("Invalid tool code: must start with " + TOOL_CODE_PREFIX);
        }

        String action = toolCode.substring(TOOL_CODE_PREFIX.length());
        log.info("Browser tool executing: action={}, timeoutMs={}", action, timeoutMs);

        try {
            JsonNode payload = objectMapper.readTree(payloadJson != null ? payloadJson : "{}");

            switch (action) {
                case "navigate":
                    return executeNavigate(payload, timeoutMs);
                case "screenshot":
                    return executeScreenshot(payload, timeoutMs);
                case "extract_table":
                    return executeExtractTable(payload, timeoutMs);
                case "click":
                    return executeClick(payload, timeoutMs);
                case "fill_form":
                    return executeFillForm(payload, timeoutMs);
                default:
                    return errorResult("Unknown browser action: " + action);
            }
        } catch (Exception e) {
            log.error("Browser tool execution failed: action={}", action, e);
            return errorResult("Browser tool execution failed: " + e.getMessage());
        }
    }

    private String executeNavigate(JsonNode payload, long timeoutMs) throws Exception {
        String url = payload.has("url") ? payload.get("url").asText() : null;
        if (url == null || url.isBlank()) {
            return errorResult("Missing required parameter: url");
        }
        validateUrl(url);

        BrowserSession session = sessionPool.acquire();
        if (session == null) {
            return errorResult("Browser session pool exhausted, please retry later");
        }
        try {
            BrowserActionResult result = session.navigate(url);
            if (result.isSuccess()) {
                String content = result.getContent();
                if (content != null && content.length() > MAX_OUTPUT_LENGTH) {
                    content = content.substring(0, MAX_OUTPUT_LENGTH) + "... [truncated]";
                }
                return successResult("navigate", content, result.getElapsedMs());
            } else {
                return errorResult("Navigation failed: " + result.getErrorMessage());
            }
        } finally {
            sessionPool.release(session);
        }
    }

    private String executeScreenshot(JsonNode payload, long timeoutMs) throws Exception {
        BrowserSession session = sessionPool.acquire();
        if (session == null) return errorResult("Browser session pool exhausted");
        try {
            BrowserActionResult result = session.takeScreenshot();
            if (result.isSuccess() && result.getContentBytes() != null) {
                String base64 = Base64.getEncoder().encodeToString(result.getContentBytes());
                return successResult("screenshot", "data:image/png;base64," + base64, result.getElapsedMs());
            } else {
                return errorResult("Screenshot failed: " + result.getErrorMessage());
            }
        } finally {
            sessionPool.release(session);
        }
    }

    private String executeExtractTable(JsonNode payload, long timeoutMs) throws Exception {
        BrowserSession session = sessionPool.acquire();
        if (session == null) return errorResult("Browser session pool exhausted");
        try {
            BrowserActionResult result = session.getTableContent();
            if (result.isSuccess()) {
                String content = result.getContent();
                if (content != null && content.length() > MAX_OUTPUT_LENGTH) {
                    content = content.substring(0, MAX_OUTPUT_LENGTH) + "... [truncated]";
                }
                return successResult("extract_table", content, result.getElapsedMs());
            } else {
                return errorResult("Table extraction failed: " + result.getErrorMessage());
            }
        } finally {
            sessionPool.release(session);
        }
    }

    private String executeClick(JsonNode payload, long timeoutMs) throws Exception {
        String selector = payload.has("selector") ? payload.get("selector").asText() : null;
        if (selector == null || selector.isBlank()) return errorResult("Missing required parameter: selector");

        BrowserSession session = sessionPool.acquire();
        if (session == null) return errorResult("Browser session pool exhausted");
        try {
            BrowserActionResult result = session.clickElement(selector);
            if (result.isSuccess()) {
                return successResult("click", "Element clicked: " + selector, result.getElapsedMs());
            } else {
                return errorResult("Click failed: " + result.getErrorMessage());
            }
        } finally {
            sessionPool.release(session);
        }
    }

    private String executeFillForm(JsonNode payload, long timeoutMs) throws Exception {
        String selector = payload.has("selector") ? payload.get("selector").asText() : null;
        String value = payload.has("value") ? payload.get("value").asText() : null;
        if (selector == null || selector.isBlank()) return errorResult("Missing required parameter: selector");
        if (value == null) return errorResult("Missing required parameter: value");

        BrowserSession session = sessionPool.acquire();
        if (session == null) return errorResult("Browser session pool exhausted");
        try {
            BrowserActionResult result = session.fillForm(selector, value);
            if (result.isSuccess()) {
                return successResult("fill_form", "Form filled: " + selector, result.getElapsedMs());
            } else {
                return errorResult("Fill form failed: " + result.getErrorMessage());
            }
        } finally {
            sessionPool.release(session);
        }
    }

    // ========== 安全检查 ==========

    private void validateUrl(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException("Only http/https URLs are allowed");
        }
        try {
            String host = new java.net.URI(url).getHost();
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("Invalid URL: cannot extract host");
            }
            if (isPrivateAddress(host)) {
                throw new IllegalArgumentException("Access to private network addresses is not allowed: " + host);
            }
            if (!allowedHosts.isEmpty() && !allowedHosts.contains(host)) {
                throw new IllegalArgumentException("Host not in allowed list: " + host);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("URL validation failed: " + e.getMessage());
        }
    }

    private boolean isPrivateAddress(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            return address.isLoopbackAddress() || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress() || address.isAnyLocalAddress();
        } catch (Exception e) {
            return true;
        }
    }

    // ========== 结果构建 ==========

    private String successResult(String action, String content, long elapsedMs) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("status", "success");
            map.put("action", action);
            map.put("content", content);
            map.put("elapsedMs", elapsedMs);
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{\"status\":\"success\",\"action\":\"" + action + "\"}";
        }
    }

    private String errorResult(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("status", "failed", "error", message));
        } catch (Exception e) {
            return "{\"status\":\"failed\",\"error\":\"" + message.replace("\"", "\\\"") + "\"}";
        }
    }
}
