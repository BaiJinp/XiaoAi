package com.xiaoai.agent.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Getter
@Setter
@TableName("user_identity_mapping")
public class UserIdentityMapping extends TenantEntity {
    private Long userId;

    private String channelType;

    private String channelUserId;

    private String channelUserName;

    private String bindStatus;

    private OffsetDateTime bindTime;
}
