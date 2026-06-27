package com.xiaoai.agent.collaboration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoai.agent.collaboration.entity.AgentHandoff;
import com.xiaoai.agent.collaboration.entity.AgentThread;
import com.xiaoai.agent.collaboration.mapper.AgentHandoffMapper;
import com.xiaoai.agent.collaboration.mapper.AgentThreadMapper;
import com.xiaoai.agent.collaboration.model.AgentThreadResponse;
import com.xiaoai.agent.collaboration.model.CreateAgentThreadCommand;
import com.xiaoai.agent.collaboration.service.AgentRoleService;
import com.xiaoai.agent.collaboration.service.AgentThreadService;
import com.xiaoai.agent.collaboration.service.CollaborationSessionService;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.task.entity.Task;
import com.xiaoai.agent.task.entity.TaskArtifact;
import com.xiaoai.agent.task.model.CreateTaskCommand;
import com.xiaoai.agent.task.model.StartTaskCommand;
import com.xiaoai.agent.task.model.TaskCreateResponse;
import com.xiaoai.agent.task.model.TaskRunResponse;
import com.xiaoai.agent.task.service.TaskArtifactService;
import com.xiaoai.agent.task.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AgentThreadServiceImpl extends ServiceImpl<AgentThreadMapper, AgentThread> implements AgentThreadService {

    private final CollaborationSessionService collaborationSessionService;
    private final AgentRoleService agentRoleService;
    private final TaskService taskService;
    private final TaskArtifactService taskArtifactService;
    private final AgentHandoffMapper agentHandoffMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public AgentThreadServiceImpl(CollaborationSessionService collaborationSessionService,
                                  AgentRoleService agentRoleService,
                                  TaskService taskService,
                                  TaskArtifactService taskArtifactService,
                                  AgentHandoffMapper agentHandoffMapper) {
        this.collaborationSessionService = collaborationSessionService;
        this.agentRoleService = agentRoleService;
        this.taskService = taskService;
        this.taskArtifactService = taskArtifactService;
        this.agentHandoffMapper = agentHandoffMapper;
    }

    public AgentThreadServiceImpl(CollaborationSessionService collaborationSessionService,
                                  AgentRoleService agentRoleService,
                                  TaskService taskService,
                                  TaskArtifactService taskArtifactService) {
        this(collaborationSessionService, agentRoleService, taskService, taskArtifactService, null);
    }

    public AgentThreadServiceImpl(CollaborationSessionService collaborationSessionService,
                                  AgentRoleService agentRoleService,
                                  TaskService taskService) {
        this.collaborationSessionService = collaborationSessionService;
        this.agentRoleService = agentRoleService;
        this.taskService = taskService;
        this.taskArtifactService = null;
        this.agentHandoffMapper = null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
public AgentThreadResponse createThread(CreateAgentThreadCommand command) {
        Long tenantId = UserContextHolder.requireTenantId();
        Long userId = UserContextHolder.requireUserId();
        collaborationSessionService.getSession(command.getSessionId());
        if (command.getRoleId() != null) {
            agentRoleService.getRole(command.getRoleId());
        }
        if (command.getParentThreadId() != null) {
            AgentThread parent = getThread(command.getParentThreadId());
            if (parent.getParentThreadId() != null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent thread depth exceeds MVP limit 1");
            }
        }
        Long taskId = command.isCreateTask() ? createBoundTask(command, userId).getTaskId() : null;
        AgentThread thread = new AgentThread();
        thread.setTenantId(tenantId);
        thread.setSessionId(command.getSessionId());
        thread.setParentThreadId(command.getParentThreadId());
        thread.setTaskId(taskId);
        thread.setAgentId(command.getAgentId());
        thread.setAgentVersionId(command.getAgentVersionId());
        thread.setRoleId(command.getRoleId());
        thread.setThreadCode("TH" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        thread.setThreadName(command.getThreadName());
        thread.setStatus("pending");
        thread.setInputArtifactId(command.getInputArtifactId());
        thread.setContextJson(buildThreadContext(command));
        save(thread);
        return AgentThreadResponse.builder()
                .threadId(thread.getId())
                .threadCode(thread.getThreadCode())
                .taskId(thread.getTaskId())
                .status(thread.getStatus())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
public TaskRunResponse startThreadTask(Long sessionId, Long threadId, StartTaskCommand command) {
        collaborationSessionService.getSession(sessionId);
        AgentThread thread = getThread(threadId);
        if (!sessionId.equals(thread.getSessionId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent thread not found");
        }
        if (thread.getTaskId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent thread has no bound task");
        }
        ensureAcceptedInputHandoff(sessionId, thread);
        ensureInputArtifactVersion(thread);
        thread.setStatus("running");
        updateById(thread);

        TaskRunResponse response = taskService.startTask(thread.getTaskId(), command == null ? new StartTaskCommand() : command);
        Task task = taskService.getTask(thread.getTaskId());
        thread.setStatus(toThreadStatus(task == null ? response.getStatus() : task.getStatus()));
        syncOutputArtifact(thread);
        updateById(thread);
        return response;
    }

    @Override
public List<AgentThread> listThreads(Long sessionId) {
        Long tenantId = UserContextHolder.requireTenantId();
        return getBaseMapper().selectList(new LambdaQueryWrapper<AgentThread>()
                .eq(AgentThread::getTenantId, tenantId)
                .eq(AgentThread::getSessionId, sessionId)
                .orderByAsc(AgentThread::getCreatedAt));
    }

    @Override
public AgentThread getThread(Long threadId) {
        Long tenantId = UserContextHolder.requireTenantId();
        AgentThread thread = getBaseMapper().selectOne(new LambdaQueryWrapper<AgentThread>()
                .eq(AgentThread::getTenantId, tenantId)
                .eq(AgentThread::getId, threadId)
                .last("limit 1"));
        if (thread == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent thread not found");
        }
        return thread;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
public void syncThreadByTaskId(Long taskId) {
        if (taskId == null) {
            return;
        }
        Long tenantId = UserContextHolder.requireTenantId();
        AgentThread thread = getBaseMapper().selectOne(new LambdaQueryWrapper<AgentThread>()
                .eq(AgentThread::getTenantId, tenantId)
                .eq(AgentThread::getTaskId, taskId)
                .last("limit 1"));
        if (thread == null) {
            return;
        }
        Task task = taskService.getTask(taskId);
        if (task == null) {
            return;
        }
        thread.setStatus(toThreadStatus(task.getStatus()));
        syncOutputArtifact(thread);
        updateById(thread);
    }

    private TaskCreateResponse createBoundTask(CreateAgentThreadCommand command, Long userId) {
        CreateTaskCommand taskCommand = new CreateTaskCommand();
        taskCommand.setAgentId(command.getAgentId());
        taskCommand.setAgentVersionId(command.getAgentVersionId());
        taskCommand.setUserId(userId);
        taskCommand.setChannelType("collaboration");
        taskCommand.setTitle(command.getThreadName());
        taskCommand.setInputText(buildCollaborationTaskInput(command));
        return taskService.createTask(taskCommand);
    }

    private String buildCollaborationTaskInput(CreateAgentThreadCommand command) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("inputText", command.getInputText() == null ? "" : command.getInputText());
        if (command.getStageCode() != null && !command.getStageCode().isBlank()) {
            root.put("assistantTaskType", "agile_" + command.getStageCode());
            root.put("prompt", command.getInputText() == null ? command.getStageCode() : command.getInputText());
        }
        JsonNode toolCalls = readToolCalls(command.getToolCallsJson());
        if (toolCalls != null) {
            root.set("toolCalls", toolCalls);
        }
        ObjectNode context = root.putObject("collaborationContext");
        context.put("sessionId", command.getSessionId());
        if (command.getParentThreadId() != null) {
            context.put("parentThreadId", command.getParentThreadId());
        }
        if (command.getAgentId() != null) {
            context.put("agentId", command.getAgentId());
        }
        if (command.getAgentVersionId() != null) {
            context.put("agentVersionId", command.getAgentVersionId());
        }
        if (command.getRoleId() != null) {
            context.put("roleId", command.getRoleId());
        }
        if (command.getInputArtifactId() != null) {
            context.put("inputArtifactId", command.getInputArtifactId());
        }
        if (command.getInputArtifactIds() != null && !command.getInputArtifactIds().isEmpty()) {
            ArrayNode inputArtifactIds = context.putArray("inputArtifactIds");
            command.getInputArtifactIds().forEach(inputArtifactIds::add);
        }
        if (command.getOutputArtifactTypes() != null && !command.getOutputArtifactTypes().isEmpty()) {
            ArrayNode outputArtifactTypes = context.putArray("outputArtifactTypes");
            command.getOutputArtifactTypes().forEach(outputArtifactTypes::add);
            ArrayNode rootOutputArtifactTypes = root.putArray("outputArtifactTypes");
            command.getOutputArtifactTypes().forEach(rootOutputArtifactTypes::add);
        }
        if (command.getInputArtifactVersion() != null) {
            context.put("inputArtifactVersion", command.getInputArtifactVersion());
        }
        if (command.getStageCode() != null) {
            context.put("stageCode", command.getStageCode());
        }
        if (command.getThreadName() != null) {
            context.put("threadName", command.getThreadName());
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Build collaboration task input failed");
        }
    }

    private String buildThreadContext(CreateAgentThreadCommand command) {
        ObjectNode context = objectMapper.createObjectNode();
        if (command.isRequireAcceptedInputHandoff()) {
            context.put("requireAcceptedInputHandoff", true);
        }
        if (command.getInputArtifactVersion() != null) {
            context.put("inputArtifactVersion", command.getInputArtifactVersion());
        }
        if (command.getInputArtifactIds() != null && !command.getInputArtifactIds().isEmpty()) {
            ArrayNode inputArtifactIds = context.putArray("inputArtifactIds");
            command.getInputArtifactIds().forEach(inputArtifactIds::add);
        }
        if (command.getStageCode() != null) {
            context.put("stageCode", command.getStageCode());
        }
        if (command.getThreadName() != null) {
            context.put("threadName", command.getThreadName());
        }
        return context.toString();
    }

    private JsonNode readToolCalls(String toolCallsJson) {
        if (toolCallsJson == null || toolCallsJson.isBlank()) {
            return null;
        }
        try {
            JsonNode toolCalls = objectMapper.readTree(toolCallsJson);
            if (!toolCalls.isArray()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Collaboration stage toolCalls must be an array");
            }
            return toolCalls;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Collaboration stage toolCalls is invalid JSON");
        }
    }

    private String toThreadStatus(String taskStatus) {
        if ("completed".equals(taskStatus)) {
            return "completed";
        }
        if ("suspended".equals(taskStatus)) {
            return "suspended";
        }
        if ("cancelled".equals(taskStatus) || "failed".equals(taskStatus)) {
            return taskStatus;
        }
        return "running";
    }

    private void syncOutputArtifact(AgentThread thread) {
        if (taskArtifactService == null || thread.getTaskId() == null) {
            return;
        }
        List<TaskArtifact> artifacts = taskArtifactService.listByTaskId(thread.getTaskId());
        if (!artifacts.isEmpty()) {
            artifacts.stream()
                    .filter(artifact -> artifact.getId() != null)
                    .max((left, right) -> left.getId().compareTo(right.getId()))
                    .ifPresent(artifact -> thread.setOutputArtifactId(artifact.getId()));
        }
    }

    private void ensureAcceptedInputHandoff(Long sessionId, AgentThread thread) {
        if (agentHandoffMapper == null || !requiresAcceptedInputHandoff(thread)) {
            return;
        }
        List<Long> inputArtifactIds = inputArtifactIds(thread);
        if (inputArtifactIds.isEmpty()) {
            return;
        }
        for (Long inputArtifactId : inputArtifactIds) {
            ensureAcceptedInputHandoff(sessionId, thread.getId(), inputArtifactId);
        }
    }

    private void ensureAcceptedInputHandoff(Long sessionId, Long threadId, Long inputArtifactId) {
        AgentHandoff handoff = agentHandoffMapper.selectOne(new LambdaQueryWrapper<AgentHandoff>()
                .eq(AgentHandoff::getTenantId, UserContextHolder.requireTenantId())
                .eq(AgentHandoff::getSessionId, sessionId)
                .eq(AgentHandoff::getToThreadId, threadId)
                .eq(AgentHandoff::getArtifactId, inputArtifactId)
                .eq(AgentHandoff::getHandoffType, "artifact")
                .ne(AgentHandoff::getStatus, "accepted")
                .last("limit 1"));
        if (handoff != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent thread input artifact handoff must be accepted before start");
        }
        AgentHandoff accepted = agentHandoffMapper.selectOne(new LambdaQueryWrapper<AgentHandoff>()
                .eq(AgentHandoff::getTenantId, UserContextHolder.requireTenantId())
                .eq(AgentHandoff::getSessionId, sessionId)
                .eq(AgentHandoff::getToThreadId, threadId)
                .eq(AgentHandoff::getArtifactId, inputArtifactId)
                .eq(AgentHandoff::getHandoffType, "artifact")
                .eq(AgentHandoff::getStatus, "accepted")
                .last("limit 1"));
        if (accepted == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent thread input artifact handoff must be accepted before start");
        }
    }

    private List<Long> inputArtifactIds(AgentThread thread) {
        if (thread.getContextJson() != null && !thread.getContextJson().isBlank()) {
            try {
                JsonNode ids = objectMapper.readTree(thread.getContextJson()).path("inputArtifactIds");
                if (ids.isArray() && !ids.isEmpty()) {
                    java.util.ArrayList<Long> values = new java.util.ArrayList<>();
                    ids.forEach(id -> {
                        if (id.isIntegralNumber()) {
                            values.add(id.asLong());
                        }
                    });
                    return values;
                }
            } catch (Exception ignored) {
                // Fall back to the legacy single input artifact column.
            }
        }
        return thread.getInputArtifactId() == null ? List.of() : List.of(thread.getInputArtifactId());
    }

    private boolean requiresAcceptedInputHandoff(AgentThread thread) {
        if (thread.getContextJson() == null || thread.getContextJson().isBlank()) {
            return false;
        }
        try {
            return objectMapper.readTree(thread.getContextJson()).path("requireAcceptedInputHandoff").asBoolean(false);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void ensureInputArtifactVersion(AgentThread thread) {
        Long expectedVersion = requiredInputArtifactVersion(thread);
        if (expectedVersion == null || taskArtifactService == null || thread.getInputArtifactId() == null) {
            return;
        }
        TaskArtifact artifact = taskArtifactService.getArtifact(thread.getInputArtifactId());
        Long actualVersion = artifactVersion(artifact);
        if (!expectedVersion.equals(actualVersion)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent thread input artifact version mismatch");
        }
    }

    private Long requiredInputArtifactVersion(AgentThread thread) {
        if (thread.getContextJson() == null || thread.getContextJson().isBlank()) {
            return null;
        }
        try {
            JsonNode version = objectMapper.readTree(thread.getContextJson()).path("inputArtifactVersion");
            return version.isIntegralNumber() ? version.asLong() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long artifactVersion(TaskArtifact artifact) {
        if (artifact == null || artifact.getMetadataJson() == null || artifact.getMetadataJson().isBlank()) {
            return null;
        }
        try {
            JsonNode version = objectMapper.readTree(artifact.getMetadataJson()).path("artifactVersion");
            return version.isIntegralNumber() ? version.asLong() : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
