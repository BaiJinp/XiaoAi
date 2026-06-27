package com.xiaoai.agent.platform.adapter.webhook;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Webhook平台配置
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-27
 */
@Data
@Component
@ConfigurationProperties(prefix = "platform.webhook")
public class WebhookConfig {

    /**
     * 是否启用
     */
    private boolean enabled = false;

    /**
     * Webhook密钥（用于签名验证）
     */
    private String secret;

    /**
     * 连接超时（秒）
     */
    private Integer connectTimeout = 10;

    /**
     * 读取超时（秒）
     */
    private Integer readTimeout = 30;
}
