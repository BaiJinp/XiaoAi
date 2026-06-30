package com.xiaoai.agent.user.identity;

import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service("identityUserIdentityMappingServiceImpl")
public class UserIdentityMappingServiceImpl implements UserIdentityMappingService {

    private final Map<String, UserIdentityMapping> mappings = new ConcurrentHashMap<>();

    @Override
    public Long getPlatformUserId(Long tenantId, String channelType, String channelUserId) {
        UserIdentityMapping mapping = mappings.get(key(tenantId, channelType, channelUserId));
        return mapping == null ? null : mapping.getPlatformUserId();
    }

    @Override
    public UserIdentityMapping createOrUpdateMapping(Long tenantId, Long platformUserId,
                                                     String channelType, String channelUserId,
                                                     String channelUserName) {
        UserIdentityMapping mapping = mappings.computeIfAbsent(key(tenantId, channelType, channelUserId), ignored -> {
            UserIdentityMapping created = new UserIdentityMapping();
            created.setTenantId(tenantId);
            created.setChannelType(channelType);
            created.setChannelUserId(channelUserId);
            created.setCreatedAt(OffsetDateTime.now());
            return created;
        });
        mapping.setPlatformUserId(platformUserId);
        mapping.setChannelUserName(channelUserName);
        mapping.setVerificationStatus("verified");
        mapping.setLastVerifiedAt(OffsetDateTime.now());
        mapping.setStatus("active");
        mapping.setUpdatedAt(OffsetDateTime.now());
        return mapping;
    }

    @Override
    public boolean verifyMapping(Long tenantId, String channelType, String channelUserId) {
        return getPlatformUserId(tenantId, channelType, channelUserId) != null;
    }

    @Override
    public Long resolveUserIdentity(Long tenantId, String channelType, String channelUserId,
                                    String channelUserName) {
        return getPlatformUserId(tenantId, channelType, channelUserId);
    }

    private String key(Long tenantId, String channelType, String channelUserId) {
        return tenantId + ":" + channelType + ":" + channelUserId;
    }
}
