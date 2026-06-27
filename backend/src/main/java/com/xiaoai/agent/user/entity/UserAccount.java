package com.xiaoai.agent.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoai.agent.common.entity.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("user_account")
public class UserAccount extends TenantEntity {

    private String userCode;

    private String username;

    private String displayName;

    private String email;

    private String mobile;

    private String status;

    private String roleCodes;
}
