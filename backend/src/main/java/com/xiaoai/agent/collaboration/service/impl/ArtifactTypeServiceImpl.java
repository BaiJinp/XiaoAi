package com.xiaoai.agent.collaboration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.collaboration.entity.ArtifactType;
import com.xiaoai.agent.collaboration.mapper.ArtifactTypeMapper;
import com.xiaoai.agent.collaboration.service.ArtifactTypeService;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import org.springframework.stereotype.Service;

@Service
public class ArtifactTypeServiceImpl extends ServiceImpl<ArtifactTypeMapper, ArtifactType> implements ArtifactTypeService {

    @Override
public ArtifactType getArtifactType(Long artifactTypeId) {
        Long tenantId = UserContextHolder.requireTenantId();
        ArtifactType artifactType = getBaseMapper().selectOne(new LambdaQueryWrapper<ArtifactType>()
                .eq(ArtifactType::getTenantId, tenantId)
                .eq(ArtifactType::getId, artifactTypeId)
                .last("limit 1"));
        if (artifactType == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Artifact type not found");
        }
        return artifactType;
    }
}
