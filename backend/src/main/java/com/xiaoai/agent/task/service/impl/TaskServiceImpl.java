package com.xiaoai.agent.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoai.agent.agent.entity.AgentVersion;
import com.xiaoai.agent.agent.service.AgentVersionService;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.api.PageResponse;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.runtime.gateway.RuntimeGateway;
import com.xiaoai.agent.runtime.model.RunCancelCommand;
import com.xiaoai.agent.runtime.model.RunApprovalResultCommand;
import com.xiaoai.agent.runtime.model.RunResumeCommand;
import com.xiaoai.agent.runtime.model.RunStartCommand;
import com.xiaoai.agent.runtime.model.RunStartResult;
import com.xiaoai.agent.runtime.model.RuntimeEvent;
import com.xiaoai.agent.task.entity.Task;
import com.xiaoai.agent.task.entity.TaskArtifact;
import com.xiaoai.agent.task.entity.TaskEvent;
import com.xiaoai.agent.task.entity.TaskRun;
import com.xiaoai.agent.task.event.TaskStatusChangedEvent;
import com.xiaoai.agent.task.mapper.TaskMapper;
import com.xiaoai.agent.task.model.CancelTaskCommand;
import com.xiaoai.agent.task.model.CreateTaskCommand;
import com.xiaoai.agent.task.model.StartTaskCommand;
import com.xiaoai.agent.task.model.SuspendForApprovalCommand;
import com.xiaoai.agent.task.model.TaskCreateResponse;
import com.xiaoai.agent.task.model.TaskPageQuery;
import com.xiaoai.agent.task.model.TaskRunResponse;
import com.xiaoai.agent.task.model.ResumeTaskCommand;
import com.xiaoai.agent.task.service.TaskService;
import com.xiaoai.agent.task.service.TaskArtifactService;
import com.xiaoai.agent.task.service.TaskEventRecordService;
import com.xiaoai.agent.task.service.TaskEventService;
import com.xiaoai.agent.task.service.TaskRunService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 任务服务实现类
 * <p>
 * 实现任务的创建、启动、取消、挂起、恢复等全生命周期管理。
 * 核心流程：
 * <ul>
 *   <li>createTask：创建pending状态的任务，分配唯一taskCode</li>
 *   <li>startTask：创建TaskRun并通过RuntimeGateway启动Agent循环执行，
 *       执行后自动检测是否需要挂起审批或标记完成</li>
 *   <li>suspendForApproval：将任务和当前Run同时标记为suspended，记录审批事件</li>
 *   <li>resumeTask：提交审批结果后恢复执行，并继续检测后续事件</li>
 *   <li>cancelTask：取消任务并终止当前Run，记录取消原因</li>
 * </ul>
 * 所有写操作都在事务中执行，保证Task与TaskRun状态一致性。
 * </p>
 *
 * @author Agent-xiaoAI Team
 * @date 2026-06-27
 */
@Service
public class TaskServiceImpl extends ServiceImpl<TaskMapper, Task> implements TaskService {

    private final TaskRunService taskRunService;
    private final TaskEventService taskEventService;
    private final TaskEventRecordService taskEventRecordService;
    private final TaskArtifactService taskArtifactService;
    private final RuntimeGateway runtimeGateway;
    private final AgentVersionService agentVersionService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 最简构造函数（用于测试场景，AgentVersionService和EventPublisher为null）
     */
    public TaskServiceImpl(TaskRunService taskRunService,
                           TaskEventService taskEventService,
                           TaskEventRecordService taskEventRecordService,
                           TaskArtifactService taskArtifactService,
                           RuntimeGateway runtimeGateway) {
        this(taskRunService, taskEventService, taskEventRecordService, taskArtifactService, runtimeGateway, null, null);
    }

    /**
     * 中间构造函数（无EventPublisher，用于测试场景）
     */
    public TaskServiceImpl(TaskRunService taskRunService,
                           TaskEventService taskEventService,
                           TaskEventRecordService taskEventRecordService,
                           TaskArtifactService taskArtifactService,
                           RuntimeGateway runtimeGateway,
                           AgentVersionService agentVersionService) {
        this(taskRunService, taskEventService, taskEventRecordService, taskArtifactService, runtimeGateway, agentVersionService, null);
    }

    /**
     * 完整构造函数，注入所有依赖服务
     *
     * @param taskRunService         TaskRun管理服务
     * @param taskEventService       TaskEvent查询服务
     * @param taskEventRecordService TaskEvent写入服务
     * @param taskArtifactService    TaskArtifact管理服务
     * @param runtimeGateway         Agent运行时网关，负责启动/取消/恢复Run
     * @param agentVersionService    Agent版本服务，用于获取运行时快照配置
     * @param eventPublisher         Spring事件发布器，用于发布TaskStatusChangedEvent
     */
    @Autowired
    public TaskServiceImpl(TaskRunService taskRunService,
                           TaskEventService taskEventService,
                           TaskEventRecordService taskEventRecordService,
                           TaskArtifactService taskArtifactService,
                           RuntimeGateway runtimeGateway,
                           AgentVersionService agentVersionService,
                           ApplicationEventPublisher eventPublisher) {
        this.taskRunService = taskRunService;
        this.taskEventService = taskEventService;
        this.taskEventRecordService = taskEventRecordService;
        this.taskArtifactService = taskArtifactService;
        this.runtimeGateway = runtimeGateway;
        this.agentVersionService = agentVersionService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 分页查询任务列表
     * <p>
     * 在当前租户上下文中按agentId、userId、status过滤，按创建时间倒序排列。
     * 使用query的normalizedPageNo/normalizedPageSize规范化分页参数。
     * </p>
     *
     * @param query 查询条件，包含agentId、userId、status等可选筛选项
     * @return 分页结果，包含任务列表和总数
     */
    @Override
public PageResponse<Task> pageTasks(TaskPageQuery query) {
        Long tenantId = UserContextHolder.requireTenantId();
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<Task>()
                .eq(Task::getTenantId, tenantId)
                .eq(query.getAgentId() != null, Task::getAgentId, query.getAgentId())
                .eq(query.getUserId() != null, Task::getUserId, query.getUserId())
                .eq(StringUtils.hasText(query.getStatus()), Task::getStatus, query.getStatus())
                .orderByDesc(Task::getCreatedAt);
        Page<Task> page = page(new Page<>(query.normalizedPageNo(), query.normalizedPageSize()), wrapper);
        return PageResponse.<Task>builder()
                .pageNo(page.getCurrent())
                .pageSize(page.getSize())
                .total(page.getTotal())
                .records(page.getRecords())
                .build();
    }

    /**
     * 根据ID获取任务（在当前租户上下文中查询，不存在则抛出NOT_FOUND异常）
     *
     * @param taskId 任务ID
     * @return Task实体
     */
    @Override
public Task getTask(Long taskId) {
        return getTenantTask(taskId);
    }

    /**
     * 创建任务
     * <p>
     * 在当前租户上下文中创建pending状态的任务。
     * 生成格式为"TASK-" + 16位UUID的唯一taskCode。
     * 默认taskType为chat_task，channelType未指定时为web_chat。
     * </p>
     *
     * @param command 创建命令，包含agentId、userId、inputText等必要信息
     * @return 包含taskId、taskCode和status的响应对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
public TaskCreateResponse createTask(CreateTaskCommand command) {
        Long tenantId = UserContextHolder.requireTenantId();
        UserContextHolder.requireUserId();
        Task task = new Task();
        task.setTenantId(tenantId);
        task.setTaskCode("TASK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        task.setAgentId(command.getAgentId());
        task.setAgentVersionId(command.getAgentVersionId());
        task.setUserId(command.getUserId());
        task.setChannelType(command.getChannelType() == null ? "web_chat" : command.getChannelType());
        task.setTitle(command.getTitle());
        task.setInputText(command.getInputText());
        task.setTaskType("chat_task");
        task.setComplexity("unknown");
        task.setStatus("pending");
        save(task);
        return TaskCreateResponse.builder()
                .taskId(task.getId())
                .taskCode(task.getTaskCode())
                .status(task.getStatus())
                .build();
    }

    /**
     * 启动任务
     * <p>
     * 执行流程：
     * <ol>
     *   <li>创建TaskRun记录（runCode格式：RUN-16位UUID），状态为running</li>
     *   <li>从AgentVersion获取runtimeSnapshotJson（策略配置快照）</li>
     *   <li>将task.currentRunId和task.status更新为running</li>
     *   <li>通过RuntimeGateway.startRun启动Agent循环执行</li>
     *   <li>记录RUN_STARTED事件，并收集后续运行时事件</li>
     *   <li>自动检测TOOL_BLOCKED事件并挂起任务等待审批</li>
     *   <li>若运行直接成功（无阻塞/失败事件），标记任务为completed并创建TaskArtifact</li>
     * </ol>
     * </p>
     *
     * @param taskId  待启动的任务ID（必须是pending状态）
     * @param command 启动命令，包含runtimeType（默认java-in-process）
     * @return 包含taskId、runId、runCode和runtimeType的响应
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
public TaskRunResponse startTask(Long taskId, StartTaskCommand command) {
        UserContextHolder.requireUserId();
        Task task = getTenantTask(taskId);
        TaskRun run = new TaskRun();
        run.setTenantId(task.getTenantId());
        run.setRunCode("RUN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        run.setTaskId(task.getId());
        run.setAgentId(task.getAgentId());
        run.setAgentVersionId(task.getAgentVersionId());
        run.setRuntimeType(command.getRuntimeType() == null ? "java-in-process" : command.getRuntimeType());
        run.setStatus("running");
        run.setStartTime(OffsetDateTime.now());
        run.setTraceId(UserContextHolder.get() == null ? null : UserContextHolder.get().getTraceId());
        run.setSnapshotJson(resolveRuntimeSnapshotJson(task.getAgentVersionId()));
        run.setUsageJson("{}");
        taskRunService.save(run);

        task.setCurrentRunId(run.getId());
        task.setStatus("running");
        updateById(task);
        publishTaskStatusChanged(task, run, "start");

        RunStartResult result = runtimeGateway.startRun(RunStartCommand.builder()
                .tenantId(task.getTenantId())
                .userId(task.getUserId())
                .agentId(task.getAgentId())
                .agentVersionId(task.getAgentVersionId())
                .taskId(task.getId())
                .runId(run.getId())
                .channelType(task.getChannelType())
                .inputText(task.getInputText())
                .traceId(run.getTraceId())
                .runtimeSnapshotJson(run.getSnapshotJson())
                .build());

        taskEventRecordService.record(RuntimeEvent.builder()
                .tenantId(task.getTenantId())
                .userId(task.getUserId())
                .agentId(task.getAgentId())
                .taskId(task.getId())
                .runId(run.getId())
                .traceId(run.getTraceId())
                .eventType("RUN_STARTED")
                .eventSummary("Runtime run started")
                .payloadJson("{\"runtimeType\":\"" + result.getRuntimeType() + "\"}")
                .occurredAt(OffsetDateTime.now())
                .build());
        List<RuntimeEvent> runtimeEvents = runtimeGateway.listEvents(run.getId());
        runtimeEvents.forEach(taskEventRecordService::record);
        suspendTaskIfRuntimeBlocked(task, run, runtimeEvents);
        completeTaskIfRuntimeSucceeded(task, run, runtimeEvents);

        return TaskRunResponse.builder()
                .taskId(task.getId())
                .runId(run.getId())
                .runCode(run.getRunCode())
                .status(run.getStatus())
                .runtimeType(result.getRuntimeType())
                .build();
    }

    /**
     * 检测运行时事件列表中是否包含TOOL_BLOCKED事件，若有则自动挂起任务等待审批。
     * 从事件payload中提取approvalRequestId，用于后续resumeTask时提交审批结果。
     *
     * @param task          当前任务
     * @param run           当前TaskRun
     * @param runtimeEvents 运行时产生的事件列表
     */
    private void suspendTaskIfRuntimeBlocked(Task task, TaskRun run, List<RuntimeEvent> runtimeEvents) {
        runtimeEvents.stream()
                .filter(event -> "TOOL_BLOCKED".equals(event.getEventType()))
                .findFirst()
                .flatMap(this::extractApprovalRequestId)
                .ifPresent(approvalRequestId -> {
                    SuspendForApprovalCommand command = new SuspendForApprovalCommand();
                    command.setApprovalRequestId(approvalRequestId);
                    command.setReason("Tool call requires approval");
                    suspendForApproval(task.getId(), command);
                });
    }

    /**
     * 从运行时事件的payloadJson中解析approvalRequestId字段值。
     * 通过字符串定位方式提取（不依赖JSON解析库），若字段不存在或格式异常则返回empty。
     *
     * @param event 运行时事件，payloadJson格式为JSON字符串
     * @return 审批请求ID，若不存在则返回Optional.empty()
     */
    private java.util.Optional<Long> extractApprovalRequestId(RuntimeEvent event) {
        String payloadJson = event.getPayloadJson();
        if (!StringUtils.hasText(payloadJson)) {
            return java.util.Optional.empty();
        }
        int keyIndex = payloadJson.indexOf("\"approvalRequestId\":");
        if (keyIndex < 0) {
            return java.util.Optional.empty();
        }
        int valueStart = keyIndex + "\"approvalRequestId\":".length();
        int valueEnd = valueStart;
        while (valueEnd < payloadJson.length() && Character.isDigit(payloadJson.charAt(valueEnd))) {
            valueEnd++;
        }
        if (valueEnd == valueStart) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(Long.parseLong(payloadJson.substring(valueStart, valueEnd)));
    }

    /**
     * 检测运行时是否直接成功完成（无阻塞/失败事件），若是则：
     * <ol>
     *   <li>将task状态更新为completed，记录resultSummary（取最后一条ASSISTANT_ARTIFACT的contentText）</li>
     *   <li>将run状态更新为completed，记录endTime</li>
     *   <li>创建TaskArtifact记录，保存任务的最终产出物（类型、名称、内容、元数据）</li>
     *   <li>记录RUN_COMPLETED事件，发布TaskStatusChangedEvent</li>
     * </ol>
     *
     * @param task          当前任务
     * @param run           当前TaskRun
     * @param runtimeEvents 运行时产生的事件列表
     */
    private void completeTaskIfRuntimeSucceeded(Task task, TaskRun run, List<RuntimeEvent> runtimeEvents) {
        if (runtimeEvents.stream().anyMatch(this::isBlockedOrFailedEvent)) {
            return;
        }
        if (runtimeEvents.stream().noneMatch(this::isSuccessfulTerminalEvent)) {
            return;
        }
        String resultSummary = runtimeEvents.stream()
                .filter(event -> "ASSISTANT_ARTIFACT".equals(event.getEventType()))
                .map(this::extractArtifactContent)
                .filter(StringUtils::hasText)
                .reduce((first, second) -> second)
                .orElse("{\"status\":\"completed\"}");
        task.setStatus("completed");
        task.setResultSummary(resultSummary);
        updateById(task);

        run.setStatus("completed");
        run.setEndTime(OffsetDateTime.now());
        taskRunService.updateById(run);

        TaskArtifact artifact = new TaskArtifact();
        artifact.setTenantId(task.getTenantId());
        artifact.setTaskId(task.getId());
        artifact.setRunId(run.getId());
        RuntimeEvent artifactEvent = runtimeEvents.stream()
                .filter(event -> "ASSISTANT_ARTIFACT".equals(event.getEventType()))
                .reduce((first, second) -> second)
                .orElse(null);
        artifact.setArtifactType(extractPayloadString(artifactEvent, "artifactType", "markdown"));
        artifact.setArtifactName(extractPayloadString(artifactEvent, "artifactName", "任务结果"));
        artifact.setContentText(resultSummary);
        artifact.setMetadataJson(buildArtifactMetadata(task, artifactEvent));
        taskArtifactService.save(artifact);
        publishTaskStatusChanged(task, run, "runtime_completed");

        taskEventRecordService.record(RuntimeEvent.builder()
                .tenantId(task.getTenantId())
                .userId(task.getUserId())
                .agentId(task.getAgentId())
                .taskId(task.getId())
                .runId(run.getId())
                .traceId(run.getTraceId())
                .eventType("RUN_COMPLETED")
                .eventSummary("Runtime run completed")
                .payloadJson("{\"artifactType\":\"" + safeJson(artifact.getArtifactType()) + "\"}")
                .occurredAt(OffsetDateTime.now())
                .build());
    }

    /**
     * 判断事件是否为成功终止事件（ASSISTANT_TASK_COMPLETED、ASSISTANT_ARTIFACT、MODEL_RESULT、TOOL_RESULT之一）
     */
    private boolean isSuccessfulTerminalEvent(RuntimeEvent event) {
        return "ASSISTANT_TASK_COMPLETED".equals(event.getEventType())
                || "ASSISTANT_ARTIFACT".equals(event.getEventType())
                || "MODEL_RESULT".equals(event.getEventType())
                || "TOOL_RESULT".equals(event.getEventType());
    }

    /**
     * 判断事件是否为阻塞或失败事件（TOOL_BLOCKED、TOOL_FAILED、ASSISTANT_TASK_SUSPENDED之一）
     */
    private boolean isBlockedOrFailedEvent(RuntimeEvent event) {
        return "TOOL_BLOCKED".equals(event.getEventType())
                || "TOOL_FAILED".equals(event.getEventType())
                || "ASSISTANT_TASK_SUSPENDED".equals(event.getEventType());
    }

    /**
     * 取消任务
     * <p>
     * 将task状态设置为cancelled，同时将当前TaskRun状态设置为cancelled并记录endTime。
     * 通过RuntimeGateway.cancelRun终止正在执行的Agent循环，并记录RUN_CANCELLED事件（含取消原因）。
     * </p>
     *
     * @param taskId  任务ID
     * @param command 取消命令，包含operatorUserId和取消原因reason
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
public void cancelTask(Long taskId, CancelTaskCommand command) {
        UserContextHolder.requireUserId();
        Task task = getTenantTask(taskId);
        task.setStatus("cancelled");
        updateById(task);
        if (task.getCurrentRunId() != null) {
            TaskRun run = taskRunService.getById(task.getCurrentRunId());
            run.setStatus("cancelled");
            run.setEndTime(OffsetDateTime.now());
            taskRunService.updateById(run);
            publishTaskStatusChanged(task, run, "cancel");
            runtimeGateway.cancelRun(RunCancelCommand.builder()
                    .tenantId(task.getTenantId())
                    .taskId(task.getId())
                    .runId(run.getId())
                    .operatorUserId(command.getOperatorUserId())
                    .reason(command.getReason())
                    .build());
            taskEventRecordService.record(RuntimeEvent.builder()
                    .tenantId(task.getTenantId())
                    .userId(task.getUserId())
                    .agentId(task.getAgentId())
                    .taskId(task.getId())
                    .runId(run.getId())
                    .traceId(run.getTraceId())
                    .eventType("RUN_CANCELLED")
                    .eventSummary("Runtime run cancelled")
                    .payloadJson("{\"reason\":\"" + safeJson(command.getReason()) + "\"}")
                    .occurredAt(OffsetDateTime.now())
                    .build());
        }
    }

    /**
     * 挂起任务等待审批
     * <p>
     * 当工具调用需要人工审批时调用此方法。
     * 将task和当前TaskRun的状态均设置为suspended，并在run中记录suspendReason。
     * 依次记录APPROVAL_REQUIRED和RUN_SUSPENDED两个事件，并发布TaskStatusChangedEvent通知。
     * 若task没有currentRunId，则静默返回。
     * </p>
     *
     * @param taskId  任务ID
     * @param command 挂起命令，包含approvalRequestId和挂起原因
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
public void suspendForApproval(Long taskId, SuspendForApprovalCommand command) {
        UserContextHolder.requireUserId();
        Task task = getTenantTask(taskId);
        if (task.getCurrentRunId() == null) {
            return;
        }
        TaskRun run = taskRunService.getById(task.getCurrentRunId());
        if (run == null) {
            return;
        }
        task.setStatus("suspended");
        updateById(task);
        run.setStatus("suspended");
        run.setSuspendReason(command.getReason());
        taskRunService.updateById(run);
        publishTaskStatusChanged(task, run, "approval_required");

        taskEventRecordService.record(RuntimeEvent.builder()
                .tenantId(task.getTenantId())
                .userId(task.getUserId())
                .agentId(task.getAgentId())
                .taskId(task.getId())
                .runId(run.getId())
                .traceId(run.getTraceId())
                .eventType("APPROVAL_REQUIRED")
                .eventSummary("Approval required")
                .payloadJson("{\"approvalRequestId\":" + command.getApprovalRequestId() + "}")
                .occurredAt(OffsetDateTime.now())
                .build());
        taskEventRecordService.record(RuntimeEvent.builder()
                .tenantId(task.getTenantId())
                .userId(task.getUserId())
                .agentId(task.getAgentId())
                .taskId(task.getId())
                .runId(run.getId())
                .traceId(run.getTraceId())
                .eventType("RUN_SUSPENDED")
                .eventSummary("Runtime run suspended")
                .payloadJson("{\"reason\":\"" + safeJson(command.getReason()) + "\"}")
                .occurredAt(OffsetDateTime.now())
                .build());
    }

    /**
     * 恢复被挂起的任务
     * <p>
     * 执行流程：
     * <ol>
     *   <li>将task和TaskRun状态恢复为running，清空run的suspendReason</li>
     *   <li>通过RuntimeGateway.submitApprovalResult提交审批通过结果</li>
     *   <li>通过RuntimeGateway.resumeRun恢复Agent循环执行</li>
     *   <li>记录RUN_RESUMED事件，并收集恢复后新产生的运行时事件</li>
     *   <li>若恢复后运行直接完成，调用completeTaskIfRuntimeSucceeded处理</li>
     * </ol>
     * </p>
     *
     * @param taskId  任务ID
     * @param command 恢复命令，包含approvalRequestId和可选的resumePayloadJson
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
public void resumeTask(Long taskId, ResumeTaskCommand command) {
        UserContextHolder.requireUserId();
        Task task = getTenantTask(taskId);
        if (task.getCurrentRunId() == null) {
            return;
        }
        TaskRun run = taskRunService.getById(task.getCurrentRunId());
        task.setStatus("running");
        updateById(task);
        run.setStatus("running");
        run.setSuspendReason(null);
        taskRunService.updateById(run);
        publishTaskStatusChanged(task, run, "resume");

        runtimeGateway.submitApprovalResult(RunApprovalResultCommand.builder()
                .tenantId(task.getTenantId())
                .taskId(task.getId())
                .runId(run.getId())
                .approvalRequestId(command.getApprovalRequestId())
                .approvalStatus("approved")
                .build());
        int existingEventCount = runtimeGateway.listEvents(run.getId()).size();
        runtimeGateway.resumeRun(RunResumeCommand.builder()
                .tenantId(task.getTenantId())
                .taskId(task.getId())
                .runId(run.getId())
                .resumePayloadJson(command.getResumePayloadJson())
                .build());
        taskEventRecordService.record(RuntimeEvent.builder()
                .tenantId(task.getTenantId())
                .userId(task.getUserId())
                .agentId(task.getAgentId())
                .taskId(task.getId())
                .runId(run.getId())
                .traceId(run.getTraceId())
                .eventType("RUN_RESUMED")
                .eventSummary("Runtime run resumed")
                .payloadJson(command.getResumePayloadJson() == null ? "{}" : command.getResumePayloadJson())
                .occurredAt(OffsetDateTime.now())
                .build());
        List<RuntimeEvent> resumedEvents = runtimeGateway.listEvents(run.getId()).stream()
                .skip(existingEventCount)
                .toList();
        resumedEvents.forEach(taskEventRecordService::record);
        completeTaskIfRuntimeSucceeded(task, run, resumedEvents);
    }

    /**
     * 发布TaskStatusChangedEvent事件到Spring事件总线，供其他模块（如SSE推送）监听。
     * eventPublisher为null时（测试场景）静默跳过。
     *
     * @param task   当前任务
     * @param run    当前TaskRun（可为null，取task.currentRunId）
     * @param source 事件来源标识，如"start"、"cancel"、"approval_required"、"resume"等
     */
    private void publishTaskStatusChanged(Task task, TaskRun run, String source) {
        if (eventPublisher == null || task == null) {
            return;
        }
        eventPublisher.publishEvent(TaskStatusChangedEvent.builder()
                .tenantId(task.getTenantId())
                .userId(task.getUserId())
                .taskId(task.getId())
                .runId(run == null ? task.getCurrentRunId() : run.getId())
                .status(task.getStatus())
                .source(source)
                .build());
    }

    /**
     * 获取指定任务的所有事件（委托给listTaskEventsAfter，lastEventId为null）
     *
     * @param taskId 任务ID
     * @return 按ID升序排列的事件列表
     */
    @Override
public List<TaskEvent> listTaskEvents(Long taskId) {
        return listTaskEventsAfter(taskId, null);
    }

    /**
     * 获取指定任务中lastEventId之后产生的事件（用于增量拉取/SSE长轮询）。
     * 在当前租户上下文中查询，按ID升序排列。
     *
     * @param taskId      任务ID
     * @param lastEventId 上次已知的最大事件ID，为null或0时返回全部事件
     * @return 新增的事件列表
     */
    @Override
public List<TaskEvent> listTaskEventsAfter(Long taskId, Long lastEventId) {
        Long tenantId = UserContextHolder.requireTenantId();
        LambdaQueryWrapper<TaskEvent> queryWrapper = new LambdaQueryWrapper<TaskEvent>()
                .eq(TaskEvent::getTenantId, tenantId)
                .eq(TaskEvent::getTaskId, taskId);
        if (lastEventId != null && lastEventId > 0) {
            queryWrapper.gt(TaskEvent::getId, lastEventId);
        }
        return taskEventService.list(queryWrapper.orderByAsc(TaskEvent::getId));
    }

    /**
     * 在当前租户上下文中查询任务，不存在时抛出NOT_FOUND异常。
     * 所有task操作统一通过此方法获取，保证租户隔离。
     *
     * @param taskId 任务ID
     * @return Task实体
     * @throws BusinessException 如果任务不存在
     */
    private Task getTenantTask(Long taskId) {
        Long tenantId = UserContextHolder.requireTenantId();
        Task task = getBaseMapper().selectOne(new LambdaQueryWrapper<Task>()
                .eq(Task::getTenantId, tenantId)
                .eq(Task::getId, taskId)
                .last("limit 1"));
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Task not found");
        }
        return task;
    }

    /**
     * 从AgentVersion中获取运行时快照JSON（包含模型、工具、上下文等策略配置）。
     * agentVersionId为null或agentVersionService不可用时返回空JSON对象"{}"。
     *
     * @param agentVersionId Agent版本ID
     * @return 运行时快照JSON字符串
     */
    private String resolveRuntimeSnapshotJson(Long agentVersionId) {
        if (agentVersionId == null || agentVersionService == null) {
            return "{}";
        }
        AgentVersion version = agentVersionService.getVersion(agentVersionId);
        if (version == null || !StringUtils.hasText(version.getRuntimeSnapshotJson())) {
            return "{}";
        }
        return version.getRuntimeSnapshotJson();
    }

    /**
     * 将字符串中的反斜杠和双引号进行JSON转义，用于安全拼接JSON字符串值。
     *
     * @param value 原始字符串，null时返回空串
     * @return 转义后的字符串
     */
    private String safeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 从运行时事件的payload中提取contentText字段值（作为任务最终结果摘要）
     *
     * @param event 运行时事件
     * @return contentText字段值，不存在时返回null
     */
    private String extractArtifactContent(RuntimeEvent event) {
        return extractPayloadString(event, "contentText", null);
    }

    /**
     * 从运行时事件的payloadJson中提取指定字段的字符串值。
     * 通过逐字符解析JSON字符串值（支持转义字符），不依赖JSON库，避免引入额外依赖。
     *
     * @param event        运行时事件
     * @param fieldName    字段名
     * @param defaultValue 字段不存在时的默认值
     * @return 字段字符串值，或defaultValue
     */
    private String extractPayloadString(RuntimeEvent event, String fieldName, String defaultValue) {
        if (event == null || !StringUtils.hasText(event.getPayloadJson())) {
            return defaultValue;
        }
        String key = "\"" + fieldName + "\":\"";
        int keyIndex = event.getPayloadJson().indexOf(key);
        if (keyIndex < 0) {
            return defaultValue;
        }
        int valueStart = keyIndex + key.length();
        StringBuilder value = new StringBuilder();
        boolean escaping = false;
        for (int index = valueStart; index < event.getPayloadJson().length(); index++) {
            char current = event.getPayloadJson().charAt(index);
            if (escaping) {
                value.append(unescapeJsonChar(current));
                escaping = false;
                continue;
            }
            if (current == '\\') {
                escaping = true;
                continue;
            }
            if (current == '"') {
                return value.toString();
            }
            value.append(current);
        }
        return defaultValue;
    }

    /**
     * 从运行时事件的payloadJson中提取指定字段的JSON对象或数组值。
     * 通过深度计数（depth）追踪嵌套括号，正确处理字符串内的括号，提取完整的JSON子结构。
     *
     * @param event        运行时事件
     * @param fieldName    字段名
     * @param defaultValue 字段不存在时的默认值
     * @return JSON对象/数组的字符串表示，或defaultValue
     */
    private String extractPayloadObject(RuntimeEvent event, String fieldName, String defaultValue) {
        if (event == null || !StringUtils.hasText(event.getPayloadJson())) {
            return defaultValue;
        }
        String key = "\"" + fieldName + "\":";
        int keyIndex = event.getPayloadJson().indexOf(key);
        if (keyIndex < 0) {
            return defaultValue;
        }
        int valueStart = keyIndex + key.length();
        int depth = 0;
        boolean inString = false;
        boolean escaping = false;
        for (int index = valueStart; index < event.getPayloadJson().length(); index++) {
            char current = event.getPayloadJson().charAt(index);
            if (escaping) {
                escaping = false;
                continue;
            }
            if (current == '\\') {
                escaping = true;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == '{' || current == '[') {
                depth++;
            } else if (current == '}' || current == ']') {
                depth--;
                if (depth == 0) {
                    return event.getPayloadJson().substring(valueStart, index + 1);
                }
            }
        }
        return defaultValue;
    }

    /**
     * 构建TaskArtifact的metadataJson。
     * <p>
     * 从事件payload中读取原始metadata对象，合并任务的collaborationContext（若inputText为JSON且包含该字段），
     * 并补充以下字段（仅在不存在时写入）：
     * <ul>
     *   <li>collaborationSessionId - 协作会话ID</li>
     *   <li>stageCode - 阶段编码</li>
     *   <li>artifactVersion - 固定为1</li>
     *   <li>producerAgentId / producerAgentVersionId - 生产该产物的Agent信息</li>
     * </ul>
     * 序列化失败时返回默认值"{"source":"runtime"}"。
     * </p>
     *
     * @param task          当前任务（用于获取agentId、agentVersionId和inputText）
     * @param artifactEvent ASSISTANT_ARTIFACT类型的运行时事件，可为null
     * @return metadata JSON字符串
     */
    private String buildArtifactMetadata(Task task, RuntimeEvent artifactEvent) {
        ObjectNode metadata = readObjectNode(extractPayloadObject(artifactEvent, "metadata", "{\"source\":\"runtime\"}"));
        JsonNode collaborationContext = readCollaborationContext(task.getInputText());
        if (collaborationContext != null) {
            metadata.set("collaborationContext", collaborationContext);
            putLongIfAbsent(metadata, "collaborationSessionId", readNullableLong(collaborationContext, "sessionId"));
            putStringIfAbsent(metadata, "stageCode", readNullableText(collaborationContext, "stageCode"));
        }
        putLongIfAbsent(metadata, "artifactVersion", 1L);
        putLongIfAbsent(metadata, "producerAgentId", task.getAgentId());
        if (task.getAgentVersionId() != null && !metadata.has("agentVersionId")) {
            metadata.put("agentVersionId", task.getAgentVersionId());
        }
        putLongIfAbsent(metadata, "producerAgentVersionId", task.getAgentVersionId());
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception exception) {
            return "{\"source\":\"runtime\"}";
        }
    }

    /**
     * 向metadata节点写入Long值（仅当字段不存在时写入，避免覆盖原始值）
     */
    private void putLongIfAbsent(ObjectNode metadata, String fieldName, Long value) {
        if (value != null && !metadata.has(fieldName)) {
            metadata.put(fieldName, value);
        }
    }

    /**
     * 向metadata节点写入字符串值（仅当字段不存在且value非空时写入）
     */
    private void putStringIfAbsent(ObjectNode metadata, String fieldName, String value) {
        if (StringUtils.hasText(value) && !metadata.has(fieldName)) {
            metadata.put(fieldName, value);
        }
    }

    /**
     * 从JsonNode中安全读取Long字段，字段不存在或为null时返回null
     */
    private Long readNullableLong(JsonNode node, String fieldName) {
        return node != null && node.hasNonNull(fieldName) ? node.get(fieldName).asLong() : null;
    }

    /**
     * 从JsonNode中安全读取字符串字段，字段不存在或为null时返回null
     */
    private String readNullableText(JsonNode node, String fieldName) {
        return node != null && node.hasNonNull(fieldName) ? node.get(fieldName).asText() : null;
    }

    /**
     * 将JSON字符串解析为ObjectNode。
     * 字符串为空或解析失败时，返回包含"source":"runtime"的默认空对象节点。
     *
     * @param json JSON字符串
     * @return ObjectNode
     */
    private ObjectNode readObjectNode(String json) {
        if (!StringUtils.hasText(json)) {
            return objectMapper.createObjectNode().put("source", "runtime");
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node != null && node.isObject()) {
                return (ObjectNode) node;
            }
        } catch (Exception ignored) {
        }
        return objectMapper.createObjectNode().put("source", "runtime");
    }

    /**
     * 将inputText解析为JSON并提取其中的collaborationContext对象节点。
     * 用于识别任务是否由子代理协作产生（inputText中携带协作上下文）。
     * inputText不是合法JSON或不含collaborationContext时返回null。
     *
     * @param inputText 任务的输入文本（可能为JSON格式）
     * @return collaborationContext节点，或null
     */
    private JsonNode readCollaborationContext(String inputText) {
        if (!StringUtils.hasText(inputText)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(inputText);
            JsonNode context = root.path("collaborationContext");
            return context.isObject() ? context : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 将JSON转义字符（n/r/t）还原为对应的控制字符，其他字符原样返回。
     * 用于extractPayloadString中的字符串值逐字符解析。
     *
     * @param value 转义后的字符（如'n'、'r'、't'）
     * @return 还原后的字符（'\n'、'\r'、'\t'），其他字符不变
     */
    private char unescapeJsonChar(char value) {
        if (value == 'n') {
            return '\n';
        }
        if (value == 'r') {
            return '\r';
        }
        if (value == 't') {
            return '\t';
        }
        return value;
    }
}
