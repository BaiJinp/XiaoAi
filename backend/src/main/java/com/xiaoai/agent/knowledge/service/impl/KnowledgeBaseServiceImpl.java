package com.xiaoai.agent.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.knowledge.entity.KnowledgeBase;
import com.xiaoai.agent.knowledge.mapper.KnowledgeBaseMapper;
import com.xiaoai.agent.knowledge.service.KnowledgeBaseService;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase> implements KnowledgeBaseService {

    @Override
public KnowledgeBase getKnowledgeBase(Long knowledgeBaseId) {
        Long tenantId = UserContextHolder.requireTenantId();
        KnowledgeBase knowledgeBase = getBaseMapper().selectOne(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getTenantId, tenantId)
                .eq(KnowledgeBase::getId, knowledgeBaseId)
                .last("limit 1"));
        if (knowledgeBase == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Knowledge base not found");
        }
        return knowledgeBase;
    }
}
