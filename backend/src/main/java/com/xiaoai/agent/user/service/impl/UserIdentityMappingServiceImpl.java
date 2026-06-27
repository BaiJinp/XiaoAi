package com.xiaoai.agent.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.user.entity.UserIdentityMapping;
import com.xiaoai.agent.user.mapper.UserIdentityMappingMapper;
import com.xiaoai.agent.user.service.UserIdentityMappingService;
import org.springframework.stereotype.Service;

@Service
public class UserIdentityMappingServiceImpl extends ServiceImpl<UserIdentityMappingMapper, UserIdentityMapping> implements UserIdentityMappingService {
}
