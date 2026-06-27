package com.xiaoai.agent.platform.adapter.feishu;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 飞书/Lark配置
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-27
 */
@Data
@Component
@ConfigurationProperties(prefix = "platform.feishu")
public class FeishuConfig {

    /**
     * 应用ID
     */
    private String appId;

    /**
     * 应用Secret
     */
    private String appSecret;

    /**
     * 验证Token（用于接收事件回调）
     */
    private String verificationToken;

    /**
     * 加密Key（用于消息加解密）
     */
    private String encryptKey;

    /**
     * 是否启用
     */
    private boolean enabled = false;

    /**
     * 飞书API基础URL
     */
    private static final String API_BASE_URL = "https://open.feishu.cn";

    public String getApiBaseUrl() {
        return API_BASE_URL;
    }

    /**
     * 获取tenant_access_token URL
     */
    public String getTenantAccessTokenUrl() {
        return API_BASE_URL + "/open-apis/auth/v3/tenant_access_token/internal";
    }

    /**
     * 发送消息URL
     */
    public String getSendMessageUrl() {
        return API_BASE_URL + "/open-apis/im/v1/messages";
    }
}
