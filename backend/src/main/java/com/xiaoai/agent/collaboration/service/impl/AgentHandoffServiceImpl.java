package com.xiaoai.agent.collaboration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoai.agent.collaboration.entity.AgentHandoff;
import com.xiaoai.agent.collaboration.entity.AgentThread;
import com.xiaoai.agent.collaboration.entity.ArtifactType;
import com.xiaoai.agent.collaboration.mapper.AgentHandoffMapper;
import com.xiaoai.agent.collaboration.model.CreateAgentHandoffCommand;
import com.xiaoai.agent.collaboration.service.AgentHandoffService;
import com.xiaoai.agent.collaboration.service.AgentThreadService;
import com.xiaoai.agent.collaboration.service.ArtifactTypeService;
import com.xiaoai.agent.collaboration.service.CollaborationSessionService;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.task.entity.TaskArtifact;
import com.xiaoai.agent.task.service.TaskArtifactService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentHandoffServiceImpl extends ServiceImpl<AgentHandoffMapper, AgentHandoff> implements AgentHandoffService {

    private final CollaborationSessionService collaborationSessionService;
    private final AgentThreadService agentThreadService;
    private final TaskArtifactService taskArtifactService;
    private final ArtifactTypeService artifactTypeService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentHandoffServiceImpl(CollaborationSessionService collaborationSessionService,
                                   AgentThreadService agentThreadService,
                                   TaskArtifactService taskArtifactService,
                                   ArtifactTypeService artifactTypeService) {
        this.collaborationSessionService = collaborationSessionService;
        this.agentThreadService = agentThreadService;
        this.taskArtifactService = taskArtifactService;
        this.artifactTypeService = artifactTypeService;
    }

    @Override
public AgentHandoff createHandoff(CreateAgentHandoffCommand command) {
        Long tenantId = UserContextHolder.requireTenantId();
        UserContextHolder.requireUserId();
        collaborationSessionService.getSession(command.getSessionId());
        if (command.getArtifactId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent handoff requires artifact");
        }
        TaskArtifact artifact = taskArtifactService.getArtifact(command.getArtifactId());
        ensureArtifactTypeExists(tenantId, artifact.getArtifactType());
        AgentThread fromThread = ensureThreadInSession(command.getSessionId(), command.getFromThreadId());
        ensureArtifactBelongsToFromThread(artifact, fromThread);
        AgentThread toThread = ensureThreadInSession(command.getSessionId(), command.getToThreadId());
        AgentHandoff handoff = new AgentHandoff();
        handoff.setTenantId(tenantId);
        handoff.setSessionId(command.getSessionId());
        handoff.setFromThreadId(command.getFromThreadId());
        handoff.setToThreadId(command.getToThreadId());
        handoff.setArtifactId(command.getArtifactId());
        handoff.setHandoffType(command.getHandoffType() == null || command.getHandoffType().isBlank() ? "artifact" : command.getHandoffType());
        handoff.setStatus("pending");
        handoff.setMessageText(command.getMessageText());
        handoff.setMetadataJson(buildHandoffMetadata(artifact, fromThread, toThread));
        save(handoff);
        return handoff;
    }

    @Override
public List<AgentHandoff> listHandoffs(Long sessionId) {
        Long tenantId = UserContextHolder.requireTenantId();
        return getBaseMapper().selectList(new LambdaQueryWrapper<AgentHandoff>()
                .eq(AgentHandoff::getTenantId, tenantId)
                .eq(AgentHandoff::getSessionId, sessionId)
                .orderByDesc(AgentHandoff::getCreatedAt));
    }

    @Override
public boolean hasUnacceptedArtifactHandoff(Long sessionId, Long toThreadId, Long artifactId) {
        if (sessionId == null || toThreadId == null || artifactId == null) {
            return false;
        }
        Long tenantId = UserContextHolder.requireTenantId();
        AgentHandoff handoff = getBaseMapper().selectOne(new LambdaQueryWrapper<AgentHandoff>()
                .eq(AgentHandoff::getTenantId, tenantId)
                .eq(AgentHandoff::getSessionId, sessionId)
                .eq(AgentHandoff::getToThreadId, toThreadId)
                .eq(AgentHandoff::getArtifactId, artifactId)
                .eq(AgentHandoff::getHandoffType, "artifact")
                .ne(AgentHandoff::getStatus, "accepted")
                .last("limit 1"));
        return handoff != null;
    }

    @Override
public void acceptHandoff(Long handoffId) {
        AgentHandoff handoff = getHandoff(handoffId);
        handoff.setStatus("accepted");
        updateById(handoff);
    }

    @Override
public void rejectHandoff(Long handoffId) {
        AgentHandoff handoff = getHandoff(handoffId);
        handoff.setStatus("rejected");
        updateById(handoff);
    }

    @Override
public AgentHandoff getHandoff(Long handoffId) {
        Long tenantId = UserContextHolder.requireTenantId();
        AgentHandoff handoff = getBaseMapper().selectOne(new LambdaQueryWrapper<AgentHandoff>()
                .eq(AgentHandoff::getTenantId, tenantId)
                .eq(AgentHandoff::getId, handoffId)
                .last("limit 1"));
        if (handoff == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent handoff not found");
        }
        return handoff;
    }

    private AgentThread ensureThreadInSession(Long sessionId, Long threadId) {
        if (threadId == null) {
            return null;
        }
        AgentThread thread = agentThreadService.getThread(threadId);
        if (!sessionId.equals(thread.getSessionId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent handoff threads must belong to same session");
        }
        return thread;
    }

    private void ensureArtifactBelongsToFromThread(TaskArtifact artifact, AgentThread fromThread) {
        if (fromThread == null || fromThread.getTaskId() == null || artifact == null || artifact.getTaskId() == null) {
            return;
        }
        if (!fromThread.getTaskId().equals(artifact.getTaskId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent handoff artifact must belong to from thread task");
        }
    }

    private void ensureArtifactTypeExists(Long tenantId, String artifactType) {
        if (artifactTypeService.list(new LambdaQueryWrapper<ArtifactType>()
                .eq(ArtifactType::getTenantId, tenantId)
                .eq(ArtifactType::getTypeCode, artifactType)
                .last("limit 1")).isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Artifact type not found");
        }
    }

    private String buildHandoffMetadata(TaskArtifact artifact, AgentThread fromThread, AgentThread toThread) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("artifactId", artifact.getId());
        metadata.put("artifactType", artifact.getArtifactType());
        metadata.put("artifactName", artifact.getArtifactName());
        if (artifact.getTaskId() != null) {
            metadata.put("artifactTaskId", artifact.getTaskId());
        }
        if (artifact.getRunId() != null) {
            metadata.put("artifactRunId", artifact.getRunId());
        }
        putThreadMetadata(metadata, "from", fromThread);
        putThreadMetadata(metadata, "to", toThread);
        if (artifact.getMetadataJson() != null && !artifact.getMetadataJson().isBlank()) {
            try {
                metadata.set("artifactMetadata", objectMapper.readTree(artifact.getMetadataJson()));
            } catch (Exception ignored) {
                metadata.put("artifactMetadataRaw", artifact.getMetadataJson());
            }
        }
        return metadata.toString();
    }

    private void putThreadMetadata(ObjectNode metadata, String prefix, AgentThread thread) {
        if (thread == null) {
            return;
        }
        metadata.put(prefix + "ThreadId", thread.getId());
        if (thread.getTaskId() != null) {
            metadata.put(prefix + "TaskId", thread.getTaskId());
        }
        if (thread.getAgentId() != null) {
            metadata.put(prefix + "AgentId", thread.getAgentId());
        }
        if (thread.getAgentVersionId() != null) {
            metadata.put(prefix + "AgentVersionId", thread.getAgentVersionId());
        }
        if (thread.getThreadName() != null && !thread.getThreadName().isBlank()) {
            metadata.put(prefix + "ThreadName", thread.getThreadName());
        }
    }
}
