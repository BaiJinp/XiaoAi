package com.xiaoai.agent.tool.executor;

import java.util.List;
import java.util.regex.Pattern;

final class ToolOutputRedactor {

    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
            Pattern.compile("(?i)(\"?(?:token|password|secret|apiKey|api_key)\"?\\s*[:=]\\s*\")([^\"\\s]+)(\")"),
            Pattern.compile("(?i)((?:token|password|secret|apiKey|api_key)\\s*[:=]\\s*)([^\\s,;]+)()"),
            Pattern.compile("(?i)(Bearer\\s+)([A-Za-z0-9._~+\\-/]+=*)()")
    );

    private ToolOutputRedactor() {
    }

    static String redact(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String redacted = value;
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            redacted = pattern.matcher(redacted).replaceAll("$1***$3");
        }
        return redacted;
    }
}
