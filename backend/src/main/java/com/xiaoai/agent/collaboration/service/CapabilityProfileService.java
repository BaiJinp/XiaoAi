package com.xiaoai.agent.collaboration.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaoai.agent.collaboration.entity.CapabilityProfile;

public interface CapabilityProfileService extends IService<CapabilityProfile> {

    CapabilityProfile getProfile(Long profileId);
}
