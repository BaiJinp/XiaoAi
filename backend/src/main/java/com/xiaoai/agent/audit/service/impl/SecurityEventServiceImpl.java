package com.xiaoai.agent.audit.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.audit.entity.SecurityEvent;
import com.xiaoai.agent.audit.mapper.SecurityEventMapper;
import com.xiaoai.agent.audit.service.SecurityEventService;
import org.springframework.stereotype.Service;

@Service
public class SecurityEventServiceImpl extends ServiceImpl<SecurityEventMapper, SecurityEvent> implements SecurityEventService {
}
