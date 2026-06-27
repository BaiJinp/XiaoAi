package com.xiaoai.agent.collaboration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.collaboration.entity.CollaborationTemplate;
import com.xiaoai.agent.collaboration.mapper.CollaborationTemplateMapper;
import com.xiaoai.agent.collaboration.model.CollaborationTemplateResponse;
import com.xiaoai.agent.collaboration.service.CollaborationTemplateService;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class CollaborationTemplateServiceImpl extends ServiceImpl<CollaborationTemplateMapper, CollaborationTemplate> implements CollaborationTemplateService {

    @Override
public CollaborationTemplate getTemplate(Long templateId) {
        Long tenantId = UserContextHolder.requireTenantId();
        CollaborationTemplate template = getBaseMapper().selectOne(new LambdaQueryWrapper<CollaborationTemplate>()
                .eq(CollaborationTemplate::getTenantId, tenantId)
                .eq(CollaborationTemplate::getId, templateId)
                .last("limit 1"));
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Collaboration template not found");
        }
        return template;
    }

    @Override
public List<CollaborationTemplateResponse> listActiveTemplates(String domainCode) {
        Long tenantId = UserContextHolder.requireTenantId();
        LambdaQueryWrapper<CollaborationTemplate> query = new LambdaQueryWrapper<CollaborationTemplate>()
                .eq(CollaborationTemplate::getTenantId, tenantId)
                .eq(CollaborationTemplate::getStatus, "active");
        if (StringUtils.hasText(domainCode)) {
            query.eq(CollaborationTemplate::getDomainCode, domainCode);
        }
        return getBaseMapper().selectList(query).stream()
                .map(CollaborationTemplateResponse::from)
                .toList();
    }
}
