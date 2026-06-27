package com.xiaoai.agent.platform.adapter.dingtalk;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 钉钉配置
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-27
 */
@Data
@Component
@ConfigurationProperties(prefix = "platform.dingtalk")
public class DingtalkConfig {

    /**
     * 应用AppKey
     */
    private String appKey;

    /**
     * 应用AppSecret
     */
    private String appSecret;

    /**
     * 机器人RobotCode
     */
    private String robotCode;

    /**
     * Webhook Secret（用于签名验证）
     */
    private String webhookSecret;

    /**
     * 是否启用
     */
    private boolean enabled = false;

    /**
     * 钉钉API基础URL
     */
    private static final String API_BASE_URL = "https://oapi.dingtalk.com";

    public String getApiBaseUrl() {
        return API_BASE_URL;
    }

    /**
     * 获取access_token URL
     */
    public String getAccessTokenUrl() {
        return API_BASE_URL + "/gettoken?appkey=" + appKey + "&appsecret=" + appSecret;
    }

    /**
     * 发送消息URL模板
     */
    public String getSendMessageUrlTemplate() {
        return API_BASE_URL + "/robot/send?access_token=";
    }
}
