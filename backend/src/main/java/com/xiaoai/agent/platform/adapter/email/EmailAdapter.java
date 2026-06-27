package com.xiaoai.agent.platform.adapter.email;

import com.xiaoai.agent.platform.PlatformAdapter;
import com.xiaoai.agent.platform.PlatformMessage;
import com.xiaoai.agent.platform.PlatformMessageHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.mail.BodyPart;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.search.FlagTerm;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 邮件平台适配器
 * <p>
 * 使用SMTP发送邮件，使用IMAP接收邮件
 * </p>
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-27
 */
@Slf4j
@Component
public class EmailAdapter implements PlatformAdapter {

    public static final String PLATFORM_TYPE = "email";

    private final EmailConfig config;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private PlatformMessageHandler messageHandler;

    /**
     * 缓存的用户信息（邮箱地址 -> 用户信息）
     */
    private final Map<String, String> userInfoCache = new ConcurrentHashMap<>();

    /**
     * 定时任务执行器用于轮询IMAP
     */
    private ScheduledExecutorService pollingExecutor;

    /**
     * IMAP连接会话工厂
     */
    private Session imapSession;
    private Session smtpSession;

    public EmailAdapter(EmailConfig config) {
        this.config = config;
    }

    @Override
    public String getPlatformType() {
        return PLATFORM_TYPE;
    }

    @Override
    public void start() throws Exception {
        if (running.get()) {
            log.warn("EmailAdapter is already running");
            return;
        }

        if (!config.isEnabled()) {
            log.warn("EmailAdapter is disabled in configuration");
            return;
        }

        try {
            // 初始化邮件会话
            initializeSessions();

            // 测试SMTP连接
            testSmtpConnection();

            // 启动IMAP轮询
            startImapPolling();

            running.set(true);
            log.info("EmailAdapter started successfully, from: {}", config.getFrom());

            if (messageHandler != null) {
                messageHandler.handleStatusChange(PLATFORM_TYPE, true);
            }
        } catch (Exception e) {
            log.error("Failed to start EmailAdapter", e);
            throw e;
        }
    }

    @Override
    public void stop() throws Exception {
        if (!running.get()) {
            return;
        }

        running.set(false);

        // 停止轮询
        if (pollingExecutor != null) {
            pollingExecutor.shutdown();
            try {
                if (!pollingExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    pollingExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                pollingExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        userInfoCache.clear();

        log.info("EmailAdapter stopped");

        if (messageHandler != null) {
            messageHandler.handleStatusChange(PLATFORM_TYPE, false);
        }
    }

    @Override
    public boolean sendMessage(String chatId, String message) {
        if (!validateState()) {
            return false;
        }

        return sendEmail(chatId, message, false);
    }

    @Override
    public boolean sendMarkdownMessage(String chatId, String message) {
        if (!validateState()) {
            return false;
        }

        // 邮件发送HTML格式，将markdown作为HTML内容
        return sendEmail(chatId, message, true);
    }

    @Override
    public boolean replyMessage(String chatId, String replyToMessageId, String message) {
        if (!validateState()) {
            return false;
        }

        // 邮件回复：在主题前添加"Re: "，并引用原文
        return sendEmail(chatId, message, false, true);
    }

    @Override
    public void setMessageHandler(PlatformMessageHandler handler) {
        this.messageHandler = handler;
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public String getUserInfo(String userId) {
        if (!validateState()) {
            return null;
        }

        if (userInfoCache.containsKey(userId)) {
            return userInfoCache.get(userId);
        }

        // 邮件场景下，userId就是邮箱地址，构造简单的用户信息
        String userInfo = "{\"email\": \"" + userId + "\"}";
        userInfoCache.put(userId, userInfo);
        return userInfo;
    }

    @Override
    public String getChatInfo(String chatId) {
        // 邮件场景下，chatId即为邮箱地址
        return getUserInfo(chatId);
    }

    /**
     * 发送邮件
     *
     * @param to      收件人
     * @param content 内容
     * @param isHtml  是否为HTML格式
     * @return 是否成功
     */
    private boolean sendEmail(String to, String content, boolean isHtml) {
        return sendEmail(to, content, isHtml, false);
    }

    /**
     * 发送邮件
     *
     * @param to           收件人
     * @param content      内容
     * @param isHtml       是否为HTML格式
     * @param isReply      是否为回复
     * @return 是否成功
     */
    private boolean sendEmail(String to, String content, boolean isHtml, boolean isReply) {
        try {
            MimeMessage mimeMessage = new MimeMessage(smtpSession);

            // 设置发件人
            mimeMessage.setFrom(new InternetAddress(config.getFrom()));

            // 设置收件人
            mimeMessage.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));

            // 设置主题（如果是回复，添加Re:前缀）
            String subject = isReply ? "Re: " + content.substring(0, Math.min(50, content.length())) : "Agent Response";
            mimeMessage.setSubject(subject, "UTF-8");

            // 设置内容
            if (isHtml) {
                // 将markdown简单转换为HTML（实际项目中可使用专门的库）
                String htmlContent = convertMarkdownToHtml(content);
                mimeMessage.setContent(htmlContent, "text/html; charset=utf-8");
            } else {
                mimeMessage.setText(content, "UTF-8");
            }

            // 设置时间戳
            mimeMessage.setSentDate(java.util.Date.from(OffsetDateTime.now().toInstant()));

            // 发送邮件
            Transport.send(mimeMessage);

            log.debug("Email sent successfully to: {}", to);
            return true;
        } catch (Exception e) {
            log.error("Error sending email to: {}", to, e);
            handleError(e);
            return false;
        }
    }

    /**
     * 初始化邮件会话
     */
    private void initializeSessions() {
        // SMTP会话配置
        Properties smtpProps = new Properties();
        smtpProps.put("mail.smtp.host", config.getSmtpHost());
        smtpProps.put("mail.smtp.port", config.getSmtpPort());
        smtpProps.put("mail.smtp.auth", "true");
        if (config.isUseSsl()) {
            smtpProps.put("mail.smtp.ssl.enable", "true");
        }
        smtpSession = Session.getInstance(smtpProps);

        // IMAP会话配置
        Properties imapProps = new Properties();
        imapProps.put("mail.imap.host", config.getImapHost());
        imapProps.put("mail.imap.port", config.getImapPort());
        imapProps.put("mail.imap.ssl.enable", "true");
        imapSession = Session.getInstance(imapProps);
    }

    /**
     * 测试SMTP连接
     */
    private void testSmtpConnection() throws MessagingException {
        Transport transport = smtpSession.getTransport("smtp");
        try {
            transport.connect(config.getSmtpHost(), config.getUsername(), config.getPassword());
            log.debug("SMTP connection test successful");
        } finally {
            transport.close();
        }
    }

    /**
     * 启动IMAP轮询
     */
    private void startImapPolling() {
        pollingExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "email-poller");
            t.setDaemon(true);
            return t;
        });

        pollingExecutor.scheduleAtFixedRate(
                this::pollInbox,
                0,
                config.getPollIntervalMs(),
                TimeUnit.MILLISECONDS
        );

        log.debug("IMAP polling started with interval: {}ms", config.getPollIntervalMs());
    }

    /**
     * 轮询收件箱
     */
    private void pollInbox() {
        Store store = null;
        Folder inbox = null;

        try {
            store = imapSession.getStore("imap");
            store.connect(config.getImapHost(), config.getUsername(), config.getPassword());

            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);

            // 获取未读消息
            FlagTerm unseenFlag = new FlagTerm(new Flags(Flags.Flag.SEEN), false);
            Message[] messages = inbox.search(unseenFlag);

            for (Message message : messages) {
                try {
                    processMessage(message);
                    // 标记为已读
                    message.setFlag(Flags.Flag.SEEN, true);
                } catch (Exception e) {
                    log.error("Error processing email message", e);
                }
            }

        } catch (Exception e) {
            log.error("Error polling inbox", e);
            handleError(e);
        } finally {
            try {
                if (inbox != null && inbox.isOpen()) {
                    inbox.close(false);
                }
                if (store != null && store.isConnected()) {
                    store.close();
                }
            } catch (MessagingException e) {
                log.warn("Error closing IMAP resources", e);
            }
        }
    }

    /**
     * 处理单条消息
     */
    private void processMessage(Message message) throws MessagingException, IOException {
        String from = InternetAddress.toString(message.getFrom())[0];
        String subject = message.getSubject();
        String content = extractMessageContent(message);
        String messageId = message.getMessageID();

        // 构建PlatformMessage
        PlatformMessage platformMessage = PlatformMessage.builder()
                .messageId(messageId)
                .platformType(PLATFORM_TYPE)
                .platformUserId(from)
                .platformChatId(from)
                .content(content)
                .messageType("text")
                .isGroup(false)
                .timestamp(OffsetDateTime.ofInstant(message.getSentDate().toInstant(), OffsetDateTime.now().getOffset()))
                .build();

        if (messageHandler != null) {
            messageHandler.handleMessage(platformMessage);
        }
    }

    /**
     * 提取消息内容
     */
    private String extractMessageContent(Message message) throws MessagingException, IOException {
        Object content = message.getContent();

        if (content instanceof String) {
            return (String) content;
        } else if (content instanceof Multipart) {
            Multipart multipart = (Multipart) content;
            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                String contentType = bodyPart.getContentType().toLowerCase();

                if (contentType.contains("text/plain") || contentType.contains("text/html")) {
                    sb.append(bodyPart.getContent().toString());
                    sb.append("\n");
                }
            }

            return sb.toString().trim();
        }

        return content != null ? content.toString() : "";
    }

    /**
     * 简单将Markdown转换为HTML
     * <p>
     * 这是一个简化的实现，实际项目应使用专门的库如 flexmark
     * </p>
     */
    private String convertMarkdownToHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "<html><body></body></html>";
        }

        StringBuilder html = new StringBuilder("<html><body>");

        // 简单转换
        String[] lines = markdown.split("\n");
        for (String line : lines) {
            line = line.trim();

            if (line.startsWith("# ")) {
                html.append("<h1>").append(line.substring(2)).append("</h1>");
            } else if (line.startsWith("## ")) {
                html.append("<h2>").append(line.substring(3)).append("</h2>");
            } else if (line.startsWith("### ")) {
                html.append("<h3>").append(line.substring(4)).append("</h3>");
            } else if (line.startsWith("- ") || line.startsWith("* ")) {
                html.append("<li>").append(line.substring(2)).append("</li>");
            } else if (line.startsWith("> ")) {
                html.append("<blockquote>").append(line.substring(2)).append("</blockquote>");
            } else if (line.matches("\\*\\*.+\\*\\*")) {
                html.append("<strong>").append(line.replaceAll("\\*\\*", "")).append("</strong>");
            } else if (line.matches("\\*.+\\*")) {
                html.append("<em>").append(line.replaceAll("\\*", "")).append("</em>");
            } else if (!line.isEmpty()) {
                html.append("<p>").append(line).append("</p>");
            }
        }

        html.append("</body></html>");
        return html.toString();
    }

    /**
     * 验证适配器状态
     */
    private boolean validateState() {
        if (!running.get()) {
            log.warn("EmailAdapter is not running");
            return false;
        }
        if (!config.isEnabled()) {
            log.warn("EmailAdapter is disabled");
            return false;
        }
        return true;
    }

    /**
     * 统一错误处理
     */
    private void handleError(Exception e) {
        if (messageHandler != null) {
            messageHandler.handleError(PLATFORM_TYPE, e);
        }
    }
}
