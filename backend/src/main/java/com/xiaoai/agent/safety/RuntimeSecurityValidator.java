package com.xiaoai.agent.safety;

import com.xiaoai.agent.runtime.model.RuntimeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 运行时安全校验器
 * 在 Agent 执行过程中进行实时安全检查
 */
@Component
public class RuntimeSecurityValidator {

    private static final Logger log = LoggerFactory.getLogger(RuntimeSecurityValidator.class);

    private final PromptSafetyValidator promptSafetyValidator;

    @Autowired
    public RuntimeSecurityValidator(PromptSafetyValidator promptSafetyValidator) {
        this.promptSafetyValidator = promptSafetyValidator;
    }

    /**
     * 运行时安全校验结果
     */
    public static class RuntimeSecurityResult {
        private final boolean safe;
        private final String warning;
        private final List<String> detectedRisks;
        private final String action; // "continue", "warn", "block"

        public RuntimeSecurityResult(boolean safe, String warning, List<String> detectedRisks, String action) {
            this.safe = safe;
            this.warning = warning;
            this.detectedRisks = detectedRisks;
            this.action = action;
        }
public boolean isSafe() { return safe; }
public String getWarning() { return warning; }
public List<String> getDetectedRisks() { return detectedRisks; }
public String getAction() { return action; }
    }

    /**
     * 校验工具调用参数
     */
    public RuntimeSecurityResult validateToolCallParameters(Long toolId, String toolCode,
                                                             String callPayloadJson) {
        List<String> risks = new ArrayList<>();

        // 检查是否包含敏感信息
        if (containsSensitiveData(callPayloadJson)) {
            risks.add("工具调用参数可能包含敏感信息");
        }

        // 检查是否有异常大的参数
        if (callPayloadJson != null && callPayloadJson.length() > 10000) {
            risks.add("工具调用参数过大（>10KB）");
        }

        if (!risks.isEmpty()) {
            String warning = "工具调用安全检查发现潜在风险: " + String.join(", ", risks);
            log.warn("Tool call security check: toolId={}, toolCode={}, risks={}",
                    toolId, toolCode, risks);
            return new RuntimeSecurityResult(false, warning, risks, "warn");
        }

        return new RuntimeSecurityResult(true, null, List.of(), "continue");
    }

    /**
     * 校验模型输出
     */
    public RuntimeSecurityResult validateModelOutput(String modelOutput) {
        List<String> risks = new ArrayList<>();

        // 检查是否泄露了系统信息
        if (leaksSystemInfo(modelOutput)) {
            risks.add("模型输出可能泄露系统信息");
        }

        // 检查是否包含不当内容
        if (containsInappropriateContent(modelOutput)) {
            risks.add("模型输出包含不当内容");
        }

        if (!risks.isEmpty()) {
            String warning = "模型输出安全检查发现潜在风险: " + String.join(", ", risks);
            log.warn("Model output security check: risks={}", risks);
            return new RuntimeSecurityResult(false, warning, risks, "warn");
        }

        return new RuntimeSecurityResult(true, null, List.of(), "continue");
    }

    /**
     * 校验用户输入（增强版）
     */
    public RuntimeSecurityResult validateUserInput(String userInput, Long userId, Long taskId) {
        // 1. 使用 PromptSafetyValidator 进行基础检查
        PromptSafetyValidator.ValidationResult promptResult = promptSafetyValidator.validate(userInput);

        if (promptResult.isInjection()) {
            log.warn("Prompt injection detected: userId={}, taskId={}, patterns={}",
                    userId, taskId, promptResult.getDetectedPatterns());
            return new RuntimeSecurityResult(
                    false,
                    promptResult.getWarningMessage(),
                    promptResult.getDetectedPatterns(),
                    "block"
            );
        }

        // 2. 额外检查
        List<String> risks = new ArrayList<>();

        // 检查是否包含恶意代码片段
        if (containsMaliciousCode(userInput)) {
            risks.add("输入可能包含恶意代码");
        }

        // 检查是否有异常长的输入
        if (userInput != null && userInput.length() > 50000) {
            risks.add("输入过长（>50KB）");
        }

        if (!risks.isEmpty()) {
            String warning = "用户输入安全检查发现潜在风险: " + String.join(", ", risks);
            log.warn("User input security check: userId={}, taskId={}, risks={}",
                    userId, taskId, risks);
            return new RuntimeSecurityResult(false, warning, risks, "warn");
        }

        return new RuntimeSecurityResult(true, null, List.of(), "continue");
    }

    /**
     * 记录安全事件
     */
    public RuntimeEvent createSecurityEvent(Long tenantId, Long userId, Long taskId, Long runId,
                                            String eventType, RuntimeSecurityResult result) {
        return RuntimeEvent.builder()
                .tenantId(tenantId)
                .userId(userId)
                .taskId(taskId)
                .runId(runId)
                .eventType(eventType)
                .eventSummary("Security check: " + (result.isSafe() ? "passed" : "failed"))
                .payloadJson(buildSecurityEventPayload(result))
                .occurredAt(java.time.OffsetDateTime.now())
                .build();
    }

    /**
     * 检查是否包含敏感数据
     */
    private boolean containsSensitiveData(String text) {
        if (text == null) return false;

        String lower = text.toLowerCase();
        return lower.contains("password") ||
               lower.contains("secret") ||
               lower.contains("apikey") ||
               lower.contains("api_key") ||
               lower.contains("token");
    }

    /**
     * 检查是否泄露系统信息
     */
    private boolean leaksSystemInfo(String text) {
        if (text == null) return false;

        String lower = text.toLowerCase();
        return lower.contains("system prompt") ||
               lower.contains("内部指令") ||
               lower.contains("配置信息") ||
               lower.contains("数据库密码");
    }

    /**
     * 检查是否包含不当内容
     */
    private boolean containsInappropriateContent(String text) {
        if (text == null) return false;

        // 这里可以添加更多的内容过滤规则
        String lower = text.toLowerCase();
        return lower.contains("暴力") ||
               lower.contains("色情") ||
               lower.contains("赌博");
    }

    /**
     * 检查是否包含恶意代码
     */
    private boolean containsMaliciousCode(String text) {
        if (text == null) return false;

        // 检查常见的恶意代码模式
        return text.contains("<script>") ||
               text.contains("eval(") ||
               text.contains("exec(") ||
               text.contains("rm -rf");
    }

    /**
     * 构建安全事件 payload
     */
    private String buildSecurityEventPayload(RuntimeSecurityResult result) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"safe\":").append(result.isSafe());
        if (result.getWarning() != null) {
            json.append(",\"warning\":\"").append(escapeJson(result.getWarning())).append("\"");
        }
        if (result.getDetectedRisks() != null && !result.getDetectedRisks().isEmpty()) {
            json.append(",\"risks\":[");
            for (int i = 0; i < result.getDetectedRisks().size(); i++) {
                if (i > 0) json.append(",");
                json.append("\"").append(escapeJson(result.getDetectedRisks().get(i))).append("\"");
            }
            json.append("]");
        }
        json.append(",\"action\":\"").append(result.getAction()).append("\"");
        json.append("}");
        return json.toString();
    }

    /**
     * JSON 转义
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}
