package com.xiaoai.agent.audit.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.audit.entity.AuditLog;
import com.xiaoai.agent.audit.mapper.AuditLogMapper;
import com.xiaoai.agent.audit.service.AuditLogService;
import org.springframework.stereotype.Service;

@Service
public class AuditLogServiceImpl extends ServiceImpl<AuditLogMapper, AuditLog> implements AuditLogService {
}
