package com.xiaoai.agent.collaboration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.collaboration.entity.CollaborationPlan;
import com.xiaoai.agent.collaboration.mapper.CollaborationPlanMapper;
import com.xiaoai.agent.collaboration.model.CollaborationPlanValidationResult;
import com.xiaoai.agent.collaboration.model.SubmitCollaborationPlanCommand;
import com.xiaoai.agent.collaboration.service.CollaborationPlanService;
import com.xiaoai.agent.collaboration.service.CollaborationPlanValidator;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CollaborationPlanServiceImpl extends ServiceImpl<CollaborationPlanMapper, CollaborationPlan> implements CollaborationPlanService {

    private final CollaborationPlanValidator collaborationPlanValidator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CollaborationPlanServiceImpl(CollaborationPlanValidator collaborationPlanValidator) {
        this.collaborationPlanValidator = collaborationPlanValidator;
    }

    @Override
public CollaborationPlan submitPlan(SubmitCollaborationPlanCommand command) {
        Long tenantId = UserContextHolder.requireTenantId();
        UserContextHolder.requireUserId();
        CollaborationPlanValidationResult validationResult = collaborationPlanValidator.validate(command.getPlanJson());
        CollaborationPlan plan = new CollaborationPlan();
        plan.setTenantId(tenantId);
        plan.setSessionId(command.getSessionId());
        plan.setGeneratedByThreadId(command.getGeneratedByThreadId());
        plan.setPlanStatus("submitted");
        plan.setValidationStatus(validationResult.isPassed() ? "passed" : "failed");
        plan.setPlanJson(command.getPlanJson());
        plan.setValidationResultJson(toJson(validationResult));
        save(plan);
        return plan;
    }

    @Override
public List<CollaborationPlan> listPlans(Long sessionId) {
        Long tenantId = UserContextHolder.requireTenantId();
        return getBaseMapper().selectList(new LambdaQueryWrapper<CollaborationPlan>()
                .eq(CollaborationPlan::getTenantId, tenantId)
                .eq(CollaborationPlan::getSessionId, sessionId)
                .orderByDesc(CollaborationPlan::getCreatedAt));
    }

    @Override
public CollaborationPlan getPlan(Long planId) {
        Long tenantId = UserContextHolder.requireTenantId();
        CollaborationPlan plan = getBaseMapper().selectOne(new LambdaQueryWrapper<CollaborationPlan>()
                .eq(CollaborationPlan::getTenantId, tenantId)
                .eq(CollaborationPlan::getId, planId)
                .last("limit 1"));
        if (plan == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Collaboration plan not found");
        }
        return plan;
    }

    private String toJson(CollaborationPlanValidationResult validationResult) {
        try {
            return objectMapper.writeValueAsString(validationResult);
        } catch (Exception exception) {
            return "{\"passed\":false,\"errors\":[\"validation result serialization failed\"]}";
        }
    }
}
