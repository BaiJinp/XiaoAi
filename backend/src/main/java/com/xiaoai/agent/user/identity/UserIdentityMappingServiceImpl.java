package com.xiaoai.agent.user.identity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;

@Service
public class UserIdentityMappingServiceImpl extends ServiceImpl<UserIdentityMappingMapper, UserIdentityMapping>
        implements UserIdentityMappingService {

    private static final Logger log = LoggerFactory.getLogger(UserIdentityMappingServiceImpl.class);

    @Override
public Long getPlatformUserId(Long tenantId, String channelType, String channelUserId) {
        if (!StringUtils.hasText(channelType) || !StringUtils.hasText(channelUserId)) {
            return null;
        }

        UserIdentityMapping mapping = getOne(new LambdaQueryWrapper<UserIdentityMapping>()
                .eq(UserIdentityMapping::getTenantId, tenantId)
                .eq(UserIdentityMapping::getChannelType, channelType)
                .eq(UserIdentityMapping::getChannelUserId, channelUserId)
                .eq(UserIdentityMapping::getStatus, "active")
                .eq(UserIdentityMapping::getVerificationStatus, "verified"));

        return mapping != null ? mapping.getPlatformUserId() : null;
    }

    @Override
public UserIdentityMapping createOrUpdateMapping(Long tenantId, Long platformUserId,
                                                      String channelType, String channelUserId,
                                                      String channelUserName) {
        // 查找现有映射
        UserIdentityMapping existing = getOne(new LambdaQueryWrapper<UserIdentityMapping>()
                .eq(UserIdentityMapping::getTenantId, tenantId)
                .eq(UserIdentityMapping::getChannelType, channelType)
                .eq(UserIdentityMapping::getChannelUserId, channelUserId));

        if (existing != null) {
            // 更新现有映射
            existing.setPlatformUserId(platformUserId);
            existing.setChannelUserName(channelUserName);
            existing.setVerificationStatus("verified");
            existing.setLastVerifiedAt(OffsetDateTime.now());
            existing.setUpdatedAt(OffsetDateTime.now());
            updateById(existing);
            log.info("Updated user identity mapping: channelType={}, channelUserId={}, platformUserId={}",
                    channelType, channelUserId, platformUserId);
            return existing;
        } else {
            // 创建新映射
            UserIdentityMapping mapping = new UserIdentityMapping();
            mapping.setTenantId(tenantId);
            mapping.setPlatformUserId(platformUserId);
            mapping.setChannelType(channelType);
            mapping.setChannelUserId(channelUserId);
            mapping.setChannelUserName(channelUserName);
            mapping.setVerificationStatus("verified");
            mapping.setLastVerifiedAt(OffsetDateTime.now());
            mapping.setStatus("active");
            mapping.setCreatedAt(OffsetDateTime.now());
            mapping.setUpdatedAt(OffsetDateTime.now());
            save(mapping);
            log.info("Created user identity mapping: channelType={}, channelUserId={}, platformUserId={}",
                    channelType, channelUserId, platformUserId);
            return mapping;
        }
    }

    @Override
public boolean verifyMapping(Long tenantId, String channelType, String channelUserId) {
        UserIdentityMapping mapping = getOne(new LambdaQueryWrapper<UserIdentityMapping>()
                .eq(UserIdentityMapping::getTenantId, tenantId)
                .eq(UserIdentityMapping::getChannelType, channelType)
                .eq(UserIdentityMapping::getChannelUserId, channelUserId));

        if (mapping != null) {
            mapping.setVerificationStatus("verified");
            mapping.setLastVerifiedAt(OffsetDateTime.now());
            mapping.setUpdatedAt(OffsetDateTime.now());
            updateById(mapping);
            return true;
        }

        return false;
    }

    @Override
public Long resolveUserIdentity(Long tenantId, String channelType, String channelUserId,
                                    String channelUserName) {
        // 1. 尝试查找已有的身份映射
        Long platformUserId = getPlatformUserId(tenantId, channelType, channelUserId);

        if (platformUserId != null) {
            log.debug("Found existing identity mapping: channelType={}, channelUserId={}, platformUserId={}",
                    channelType, channelUserId, platformUserId);
            return platformUserId;
        }

        // 2. 如果没有找到映射，根据渠道类型决定处理方式
        if ("web".equals(channelType) || "api".equals(channelType)) {
            // Web/API 渠道：应该通过认证系统获取用户ID
            log.warn("Web/API channel requires authentication: channelType={}, channelUserId={}",
                    channelType, channelUserId);
            return null;
        }

        // 3. 企业聊天渠道：创建匿名用户身份（需要后续验证）
        // 这里简化处理，实际应该调用用户服务创建匿名用户
        log.info("Creating anonymous user identity for channel: channelType={}, channelUserId={}",
                channelType, channelUserId);

        // TODO: 调用 UserAccountService 创建匿名用户
        // 这里返回 null，表示需要进一步处理
        return null;
    }
}
