package com.xiaoai.agent.platform.adapter.email;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 邮件平台配置
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-27
 */
@Data
@Component
@ConfigurationProperties(prefix = "platform.email")
public class EmailConfig {

    /**
     * SMTP服务器地址
     */
    private String smtpHost;

    /**
     * SMTP服务器端口
     */
    private Integer smtpPort = 587;

    /**
     * SMTP用户名
     */
    private String username;

    /**
     * SMTP密码
     */
    private String password;

    /**
     * IMAP服务器地址
     */
    private String imapHost;

    /**
     * 发件人地址
     */
    private String from;

    /**
     * 是否启用
     */
    private boolean enabled = false;

    /**
     * 是否使用SSL
     */
    private boolean useSsl = true;

    /**
     * IMAP端口
     */
    private Integer imapPort = 993;

    /**
     * 轮询间隔（毫秒）
     */
    private Long pollIntervalMs = 5000L;
}
