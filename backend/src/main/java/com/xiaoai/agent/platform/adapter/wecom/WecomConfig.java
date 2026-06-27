package com.xiaoai.agent.platform.adapter.wecom;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 企业微信配置
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-27
 */
@Data
@Component
@ConfigurationProperties(prefix = "platform.wecom")
public class WecomConfig {

    /**
     * 企业ID
     */
    private String corpid;

    /**
     * 应用ID
     */
    private Integer agentId;

    /**
     * 应用Secret
     */
    private String secret;

    /**
     * Token（用于接收消息回调）
     */
    private String token;

    /**
     * EncodingAESKey（用于消息加解密）
     */
    private String aesKey;

    /**
     * 是否启用
     */
    private boolean enabled = false;

    /**
     * 企业微信API基础URL
     */
    private static final String API_BASE_URL = "https://qyapi.weixin.qq.com";

    public String getApiBaseUrl() {
        return API_BASE_URL;
    }

    /**
     * 获取access_token URL
     */
    public String getAccessTokenUrl() {
        return API_BASE_URL + "/cgi-bin/gettoken?corpid=" + corpid + "&corpsecret=" + secret;
    }

    /**
     * 发送消息URL
     */
    public String getSendMessageUrl() {
        return API_BASE_URL + "/cgi-bin/message/send?access_token=";
    }
}
