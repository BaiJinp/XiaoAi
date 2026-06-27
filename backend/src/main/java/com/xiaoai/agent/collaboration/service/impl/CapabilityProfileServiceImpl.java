package com.xiaoai.agent.collaboration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.collaboration.entity.CapabilityProfile;
import com.xiaoai.agent.collaboration.mapper.CapabilityProfileMapper;
import com.xiaoai.agent.collaboration.service.CapabilityProfileService;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import org.springframework.stereotype.Service;

@Service
public class CapabilityProfileServiceImpl extends ServiceImpl<CapabilityProfileMapper, CapabilityProfile> implements CapabilityProfileService {

    @Override
public CapabilityProfile getProfile(Long profileId) {
        Long tenantId = UserContextHolder.requireTenantId();
        CapabilityProfile profile = getBaseMapper().selectOne(new LambdaQueryWrapper<CapabilityProfile>()
                .eq(CapabilityProfile::getTenantId, tenantId)
                .eq(CapabilityProfile::getId, profileId)
                .last("limit 1"));
        if (profile == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Capability profile not found");
        }
        return profile;
    }
}
