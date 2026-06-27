package com.xiaoai.agent.user.identity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户身份映射实体
 * 支持跨渠道的用户身份映射（飞书、钉钉、Web等）
 */
@Getter
@Setter
@TableName("user_identity_mapping")
public class UserIdentityMapping extends TenantEntity {

    /**
     * 平台用户ID
     */
    private Long platformUserId;

    /**
     * 渠道类型（feishu、dingtalk、wechat、web、api）
     */
    private String channelType;

    /**
     * 渠道用户ID（飞书用户ID、钉钉用户ID等）
     */
    private String channelUserId;

    /**
     * 渠道用户名（显示名称）
     */
    private String channelUserName;

    /**
     * 身份验证状态（verified、pending、failed）
     */
    private String verificationStatus;

    /**
     * 最后验证时间
     */
    private java.time.OffsetDateTime lastVerifiedAt;

    /**
     * 状态（active、inactive）
     */
    private String status;
}
