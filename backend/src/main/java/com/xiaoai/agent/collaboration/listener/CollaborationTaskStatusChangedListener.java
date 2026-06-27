package com.xiaoai.agent.collaboration.listener;

import com.xiaoai.agent.collaboration.service.AgentThreadService;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.task.event.TaskStatusChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CollaborationTaskStatusChangedListener {

    private final AgentThreadService agentThreadService;

    public CollaborationTaskStatusChangedListener(AgentThreadService agentThreadService) {
        this.agentThreadService = agentThreadService;
    }

    @EventListener
    @Transactional(rollbackFor = Exception.class)
public void onTaskStatusChanged(TaskStatusChangedEvent event) {
        if (event == null || event.getTaskId() == null) {
            return;
        }
        UserContext previousContext = UserContextHolder.get();
        bindEventContext(event);
        try {
            agentThreadService.syncThreadByTaskId(event.getTaskId());
        } finally {
            restoreContext(previousContext);
        }
    }

    private void bindEventContext(TaskStatusChangedEvent event) {
        if (event.getTenantId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Missing tenant context");
        }
        UserContextHolder.set(UserContext.builder()
                .tenantId(event.getTenantId())
                .userId(event.getUserId())
                .build());
    }

    private void restoreContext(UserContext previousContext) {
        if (previousContext == null) {
            UserContextHolder.clear();
            return;
        }
        UserContextHolder.set(previousContext);
    }
}
