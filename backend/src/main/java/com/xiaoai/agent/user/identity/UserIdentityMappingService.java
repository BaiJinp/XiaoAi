package com.xiaoai.agent.user.identity;

import com.baomidou.mybatisplus.extension.service.IService;

public interface UserIdentityMappingService extends IService<UserIdentityMapping> {

    /**
     * 根据渠道用户ID获取平台用户ID
     */
    Long getPlatformUserId(Long tenantId, String channelType, String channelUserId);

    /**
     * 创建或更新身份映射
     */
    UserIdentityMapping createOrUpdateMapping(Long tenantId, Long platformUserId,
                                               String channelType, String channelUserId,
                                               String channelUserName);

    /**
     * 验证身份映射
     */
    boolean verifyMapping(Long tenantId, String channelType, String channelUserId);

    /**
     * 根据渠道信息查询用户（如果不存在则创建匿名身份）
     */
    Long resolveUserIdentity(Long tenantId, String channelType, String channelUserId,
                             String channelUserName);
}
