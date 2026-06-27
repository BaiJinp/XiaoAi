package com.xiaoai.agent.safety;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Prompt 注入防护验证器
 * 检测常见的 Prompt 注入攻击模式
 */
@Component
public class PromptSafetyValidator {

    /**
     * 注入检测结果
     */
    public static class ValidationResult {
        private final boolean isInjection;
        private final String warningMessage;
        private final List<String> detectedPatterns;

        public ValidationResult(boolean isInjection, String warningMessage, List<String> detectedPatterns) {
            this.isInjection = isInjection;
            this.warningMessage = warningMessage;
            this.detectedPatterns = detectedPatterns;
        }
public boolean isInjection() {
            return isInjection;
        }
public String getWarningMessage() {
            return warningMessage;
        }
public List<String> getDetectedPatterns() {
            return detectedPatterns;
        }
    }

    // 常见的注入模式
    private static final List<InjectionPattern> INJECTION_PATTERNS = Arrays.asList(
            // 忽略指令类
            new InjectionPattern(
                    Pattern.compile("(忽略|ignore|forget|disregard)\\s*(所有|之前的|前面的|previous|all|prior)\\s*(指令|instructions|prompts|rules)", Pattern.CASE_INSENSITIVE),
                    "尝试忽略系统指令",
                    "ignore_instructions"
            ),
            new InjectionPattern(
                    Pattern.compile("(ignore|forget)\\s+(all|everything)\\s+(above|before|previous)", Pattern.CASE_INSENSITIVE),
                    "Attempt to ignore previous instructions",
                    "ignore_instructions_en"
            ),

            // 角色扮演类
            new InjectionPattern(
                    Pattern.compile("(你现在是|you are now|act as|pretend to be|成为)\\s*(一个|a|an)?\\s*(系统|system|admin|root|开发者|developer)", Pattern.CASE_INSENSITIVE),
                    "尝试角色扮演系统角色",
                    "role_play_system"
            ),
            new InjectionPattern(
                    Pattern.compile("(以|as|in)\\s*(系统|system|debug|admin)\\s*(模式|mode|调试)", Pattern.CASE_INSENSITIVE),
                    "尝试进入系统模式",
                    "system_mode"
            ),

            // 权限提升类
            new InjectionPattern(
                    Pattern.compile("(给我|show me|give me|reveal|输出|print)\\s*(所有|all|完整|full)\\s*(权限|access|prompt|指令|instructions|system prompt)", Pattern.CASE_INSENSITIVE),
                    "尝试获取系统权限或提示词",
                    "privilege_escalation"
            ),
            new InjectionPattern(
                    Pattern.compile("(绕过|bypass|override|disable)\\s*(限制|restriction|limit|safety|安全)", Pattern.CASE_INSENSITIVE),
                    "尝试绕过安全限制",
                    "bypass_restriction"
            ),

            // 系统调试类
            new InjectionPattern(
                    Pattern.compile("(调试|debug|test|测试)\\s*(模式|mode)", Pattern.CASE_INSENSITIVE),
                    "尝试进入调试模式",
                    "debug_mode"
            ),
            new InjectionPattern(
                    Pattern.compile("(print|输出|显示)\\s*(完整|full|complete)\\s*(system|系统)\\s*(prompt|指令)", Pattern.CASE_INSENSITIVE),
                    "尝试获取完整系统提示词",
                    "extract_system_prompt"
            ),

            // 数据泄露类
            new InjectionPattern(
                    Pattern.compile("(告诉|tell|show|给我)\\s*(我|me)\\s*(所有|all)\\s*(用户|user)\\s*(数据|data|信息|info)", Pattern.CASE_INSENSITIVE),
                    "尝试获取其他用户数据",
                    "data_leakage"
            ),

            // 指令覆盖类
            new InjectionPattern(
                    Pattern.compile("(你的|your)\\s*(新|new)\\s*(指令|instructions|rules)\\s*(是|are)", Pattern.CASE_INSENSITIVE),
                    "尝试覆盖系统指令",
                    "override_instructions"
            )
    );

    /**
     * 验证输入是否包含注入攻击
     *
     * @param userInput 用户输入
     * @return 验证结果
     */
    public ValidationResult validate(String userInput) {
        if (!StringUtils.hasText(userInput)) {
            return new ValidationResult(false, null, List.of());
        }

        List<String> detectedPatterns = new java.util.ArrayList<>();

        for (InjectionPattern pattern : INJECTION_PATTERNS) {
            if (pattern.pattern.matcher(userInput).find()) {
                detectedPatterns.add(pattern.patternName);
            }
        }

        if (detectedPatterns.isEmpty()) {
            return new ValidationResult(false, null, List.of());
        }

        String warning = buildWarningMessage(detectedPatterns);
        return new ValidationResult(true, warning, detectedPatterns);
    }

    /**
     * 构建警告消息
     */
    private String buildWarningMessage(List<String> detectedPatterns) {
        StringBuilder warning = new StringBuilder();
        warning.append("检测到潜在的安全风险输入，包含以下模式：\n");

        for (String pattern : detectedPatterns) {
            warning.append("- ").append(getPatternDescription(pattern)).append("\n");
        }

        warning.append("\n已拒绝执行该请求。如果您有合法需求，请重新表述。");
        return warning.toString();
    }

    /**
     * 获取模式描述
     */
    private String getPatternDescription(String patternName) {
        for (InjectionPattern pattern : INJECTION_PATTERNS) {
            if (pattern.patternName.equals(patternName)) {
                return pattern.description;
            }
        }
        return patternName;
    }

    /**
     * 注入模式定义
     */
    private static class InjectionPattern {
        final Pattern pattern;
        final String description;
        final String patternName;

        InjectionPattern(Pattern pattern, String description, String patternName) {
            this.pattern = pattern;
            this.description = description;
            this.patternName = patternName;
        }
    }
}
