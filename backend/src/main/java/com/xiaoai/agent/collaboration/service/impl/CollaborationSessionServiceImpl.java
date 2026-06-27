package com.xiaoai.agent.collaboration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaoai.agent.collaboration.entity.CollaborationSession;
import com.xiaoai.agent.collaboration.mapper.CollaborationSessionMapper;
import com.xiaoai.agent.collaboration.model.CollaborationSessionPageQuery;
import com.xiaoai.agent.collaboration.model.CollaborationSessionResponse;
import com.xiaoai.agent.collaboration.model.CreateCollaborationSessionCommand;
import com.xiaoai.agent.collaboration.service.CollaborationSessionService;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.api.PageResponse;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class CollaborationSessionServiceImpl extends ServiceImpl<CollaborationSessionMapper, CollaborationSession> implements CollaborationSessionService {

    @Override
public CollaborationSessionResponse createSession(CreateCollaborationSessionCommand command) {
        Long tenantId = UserContextHolder.requireTenantId();
        UserContextHolder.requireUserId();
        CollaborationSession session = new CollaborationSession();
        session.setTenantId(tenantId);
        session.setSessionCode("CS" + OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        session.setTemplateId(command.getTemplateId());
        session.setRootTaskId(command.getRootTaskId());
        session.setStrategyType(command.getStrategyType());
        session.setGoalText(command.getGoalText());
        session.setStatus("planning");
        session.setContextJson(defaultJson(command.getContextJson()));
        save(session);
        return CollaborationSessionResponse.builder()
                .sessionId(session.getId())
                .sessionCode(session.getSessionCode())
                .status(session.getStatus())
                .build();
    }

    @Override
public CollaborationSession getSession(Long sessionId) {
        Long tenantId = UserContextHolder.requireTenantId();
        CollaborationSession session = getBaseMapper().selectOne(new LambdaQueryWrapper<CollaborationSession>()
                .eq(CollaborationSession::getTenantId, tenantId)
                .eq(CollaborationSession::getId, sessionId)
                .last("limit 1"));
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Collaboration session not found");
        }
        return session;
    }

    @Override
public PageResponse<CollaborationSession> pageSessions(CollaborationSessionPageQuery query) {
        Long tenantId = UserContextHolder.requireTenantId();
        LambdaQueryWrapper<CollaborationSession> wrapper = new LambdaQueryWrapper<CollaborationSession>()
                .eq(CollaborationSession::getTenantId, tenantId)
                .eq(query.getTemplateId() != null, CollaborationSession::getTemplateId, query.getTemplateId())
                .eq(query.getStrategyType() != null && !query.getStrategyType().isBlank(), CollaborationSession::getStrategyType, query.getStrategyType())
                .eq(query.getStatus() != null && !query.getStatus().isBlank(), CollaborationSession::getStatus, query.getStatus())
                .orderByDesc(CollaborationSession::getCreatedAt);
        Page<CollaborationSession> page = page(new Page<>(query.normalizedPageNo(), query.normalizedPageSize()), wrapper);
        return PageResponse.<CollaborationSession>builder()
                .pageNo(page.getCurrent())
                .pageSize(page.getSize())
                .total(page.getTotal())
                .records(page.getRecords())
                .build();
    }

    private String defaultJson(String value) {
        return value == null || value.isBlank() ? "{}" : value;
    }
}
