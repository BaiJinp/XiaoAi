package com.xiaoai.agent.agent.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.agent.model.AgentDraftResponse;
import com.xiaoai.agent.agent.model.AgentVersionResponse;
import com.xiaoai.agent.agent.model.CreateAgentDraftCommand;
import com.xiaoai.agent.agent.model.CreateMainAgentCommand;
import com.xiaoai.agent.agent.model.MainAgentPreviewResponse;
import com.xiaoai.agent.agent.model.MainAgentSetupResponse;
import com.xiaoai.agent.agent.model.MainAgentTrialRunCommand;
import com.xiaoai.agent.agent.model.MainAgentTrialRunResponse;
import com.xiaoai.agent.agent.model.UpdateAgentConfigCommand;
import com.xiaoai.agent.agent.service.AgentService;
import com.xiaoai.agent.agent.service.AgentVersionService;
import com.xiaoai.agent.agent.service.MainAgentTemplateService;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.exception.BusinessException;
import com.xiaoai.agent.task.entity.Task;
import com.xiaoai.agent.task.entity.TaskArtifact;
import com.xiaoai.agent.task.entity.TaskEvent;
import com.xiaoai.agent.task.model.CreateTaskCommand;
import com.xiaoai.agent.task.model.StartTaskCommand;
import com.xiaoai.agent.task.model.TaskCreateResponse;
import com.xiaoai.agent.task.model.TaskRunResponse;
import com.xiaoai.agent.task.service.TaskArtifactService;
import com.xiaoai.agent.task.service.TaskService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MainAgentTemplateServiceImpl implements MainAgentTemplateService {

    private final AgentService agentService;
    private final AgentVersionService agentVersionService;
    private final TaskService taskService;
    private final TaskArtifactService taskArtifactService;
    private final ObjectMapper objectMapper;

    public MainAgentTemplateServiceImpl(AgentService agentService,
                                        AgentVersionService agentVersionService,
                                        TaskService taskService,
                                        TaskArtifactService taskArtifactService,
                                        ObjectMapper objectMapper) {
        this.agentService = agentService;
        this.agentVersionService = agentVersionService;
        this.taskService = taskService;
        this.taskArtifactService = taskArtifactService;
        this.objectMapper = objectMapper;
    }

    @Override
public MainAgentPreviewResponse preview() {
        return MainAgentPreviewResponse.builder()
                .agentName("项目助理主 Agent")
                .agentType("project_assistant")
                .description("面向项目团队的主 Agent，负责理解任务、检索项目知识、编排模型和工具，并在高风险动作前触发审批。")
                .rolePrompt("你是企业项目助理主 Agent，负责把用户自然语言目标转为可执行计划，并在权限边界内协调知识、模型和工具完成任务。")
                .responsibilityText("识别任务意图；检索项目知识；生成周报、风险分析和会议纪要；调用低风险工具；高风险动作进入审批和断点恢复。")
                .boundaryText("不得绕过审批执行高风险动作；不得访问未授权知识库；不直接执行外部系统写操作，除非策略允许或审批通过。")
                .capabilities(List.of(
                        "项目知识问答",
                        "项目周报生成",
                        "风险分析",
                        "会议纪要整理",
                        "工具调用审批",
                        "审批通过后断点续跑"
                ))
                .sampleTasks(List.of(
                        "生成本周项目周报，包含进展、风险和下周计划",
                        "基于会议记录整理行动项和负责人",
                        "分析当前项目延期风险，并给出建议动作",
                        "创建项目任务前先判断是否需要审批"
                ))
                .governanceRules(List.of(
                        "低风险动作自动执行",
                        "高风险工具调用必须审批",
                        "审批通过后从 checkpoint 恢复",
                        "所有运行事件、工具调用和模型调用留痕"
                ))
                .build();
    }

    @Override
public MainAgentSetupResponse createDraft(CreateMainAgentCommand command) {
        MainAgentPreviewResponse preview = preview();
        CreateAgentDraftCommand draftCommand = new CreateAgentDraftCommand();
        draftCommand.setAgentName(preview.getAgentName());
        draftCommand.setDescription(preview.getDescription());
        draftCommand.setOwnerUserId(command.getOwnerUserId());
        AgentDraftResponse draft = agentService.createDraft(draftCommand);

        UpdateAgentConfigCommand configCommand = new UpdateAgentConfigCommand();
        configCommand.setRolePrompt(preview.getRolePrompt());
        configCommand.setResponsibilityText(preview.getResponsibilityText());
        configCommand.setBoundaryText(preview.getBoundaryText());
        configCommand.setConfigJson("{\"runtimeType\":\"java-in-process\",\"orchestrationMode\":\"main_agent\",\"assistantTaskTypes\":[\"weekly_report\",\"risk_analysis\",\"meeting_minutes\"]}");
        configCommand.setKnowledgeScopeJson("[{\"scopeType\":\"project_knowledge_base\",\"mode\":\"retrieve\"}]");
        configCommand.setToolScopeJson("[{\"toolCode\":\"builtin.echo\",\"riskLevel\":\"low\"},{\"toolCode\":\"project.task.create\",\"riskLevel\":\"high\",\"approvalRequired\":true}]");
        configCommand.setPermissionPolicyJson("{\"lowRisk\":\"allow\",\"highRisk\":\"approve\",\"checkpoint\":\"required\"}");
        configCommand.setBudgetPolicyJson("{\"dailyTokenLimit\":200000,\"taskTokenLimit\":20000}");
        AgentVersionResponse version = agentVersionService.createVersion(draft.getAgentId(), configCommand);

        return MainAgentSetupResponse.builder()
                .agentId(draft.getAgentId())
                .agentCode(draft.getAgentCode())
                .agentVersionId(version.getAgentVersionId())
                .versionNo(version.getVersionNo())
                .status(version.getVersionStatus())
                .preview(preview)
                .build();
    }

    @Override
public MainAgentTrialRunResponse trialRun(MainAgentTrialRunCommand command) {
        String assistantTaskType = StringUtils.hasText(command.getAssistantTaskType())
                ? command.getAssistantTaskType()
                : "weekly_report";
        CreateTaskCommand taskCommand = new CreateTaskCommand();
        taskCommand.setAgentId(command.getAgentId());
        taskCommand.setAgentVersionId(command.getAgentVersionId());
        taskCommand.setUserId(command.getUserId());
        taskCommand.setChannelType("main_agent_console");
        taskCommand.setTitle("主 Agent 试运行 - " + assistantTaskType);
        taskCommand.setInputText(buildTrialRunInput(command, assistantTaskType));
        TaskCreateResponse task = taskService.createTask(taskCommand);

        StartTaskCommand startCommand = new StartTaskCommand();
        startCommand.setRuntimeType("java-in-process");
        TaskRunResponse run = taskService.startTask(task.getTaskId(), startCommand);
        Task savedTask = taskService.getById(task.getTaskId());
        List<TaskEvent> events = taskService.listTaskEvents(task.getTaskId());
        List<TaskArtifact> artifacts = taskArtifactService.listByTaskId(task.getTaskId());

        return MainAgentTrialRunResponse.builder()
                .taskId(task.getTaskId())
                .taskCode(task.getTaskCode())
                .runId(run.getRunId())
                .runCode(run.getRunCode())
                .status(run.getStatus())
                .taskStatus(savedTask == null ? null : savedTask.getStatus())
                .runtimeType(run.getRuntimeType())
                .resultSummary(savedTask == null ? null : savedTask.getResultSummary())
                .events(events)
                .artifacts(artifacts)
                .build();
    }

    private String buildTrialRunInput(MainAgentTrialRunCommand command, String assistantTaskType) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("assistantTaskType", assistantTaskType);
        if (StringUtils.hasText(command.getPrompt())) {
            input.put("prompt", command.getPrompt());
        }
        if (StringUtils.hasText(command.getQuery())) {
            input.put("query", command.getQuery());
        }
        try {
            return objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Build main agent trial run input failed");
        }
    }
}
