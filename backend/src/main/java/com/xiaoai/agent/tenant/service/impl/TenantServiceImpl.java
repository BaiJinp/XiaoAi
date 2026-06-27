package com.xiaoai.agent.tenant.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.tenant.entity.Tenant;
import com.xiaoai.agent.tenant.mapper.TenantMapper;
import com.xiaoai.agent.tenant.service.TenantService;
import org.springframework.stereotype.Service;

@Service
public class TenantServiceImpl extends ServiceImpl<TenantMapper, Tenant> implements TenantService {
}
