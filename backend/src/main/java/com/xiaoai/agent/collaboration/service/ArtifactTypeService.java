package com.xiaoai.agent.collaboration.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.collaboration.entity.ArtifactType;

public interface ArtifactTypeService extends IService<ArtifactType> {

    ArtifactType getArtifactType(Long artifactTypeId);
}
