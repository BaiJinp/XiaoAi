package com.xiaoai.agent.user.identity;

import com.baomidou.mybatisplus.extension.service.IService;

public interface UserIdentityMappingService {

    /**
     * 鏍规嵁娓犻亾鐢ㄦ埛ID鑾峰彇骞冲彴鐢ㄦ埛ID
     */
    Long getPlatformUserId(Long tenantId, String channelType, String channelUserId);

    /**
     * 鍒涘缓鎴栨洿鏂拌韩浠芥槧灏?     */
    UserIdentityMapping createOrUpdateMapping(Long tenantId, Long platformUserId,
                                               String channelType, String channelUserId,
                                               String channelUserName);

    /**
     * 楠岃瘉韬唤鏄犲皠
     */
    boolean verifyMapping(Long tenantId, String channelType, String channelUserId);

    /**
     * 鏍规嵁娓犻亾淇℃伅鏌ヨ鐢ㄦ埛锛堝鏋滀笉瀛樺湪鍒欏垱寤哄尶鍚嶈韩浠斤級
     */
    Long resolveUserIdentity(Long tenantId, String channelType, String channelUserId,
                             String channelUserName);
}
