package com.xiaoai.agent.agent.service.impl;

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
import com.xiaoai.agent.task.entity.Task;
import com.xiaoai.agent.task.entity.TaskArtifact;
import com.xiaoai.agent.task.entity.TaskEvent;
import com.xiaoai.agent.task.model.CreateTaskCommand;
import com.xiaoai.agent.task.model.StartTaskCommand;
import com.xiaoai.agent.task.model.TaskCreateResponse;
import com.xiaoai.agent.task.model.TaskRunResponse;
import com.xiaoai.agent.task.service.TaskArtifactService;
import com.xiaoai.agent.task.service.TaskService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MainAgentTemplateServiceImplTest {

    private final AgentService agentService = mock(AgentService.class);
    private final AgentVersionService agentVersionService = mock(AgentVersionService.class);
    private final TaskService taskService = mock(TaskService.class);
    private final TaskArtifactService taskArtifactService = mock(TaskArtifactService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MainAgentTemplateServiceImpl service =
            new MainAgentTemplateServiceImpl(agentService, agentVersionService, taskService, taskArtifactService, objectMapper);

    @Test
    void previewShouldReturnProjectAssistantMainAgentTemplate() {
        MainAgentPreviewResponse response = service.preview();

        assertThat(response.getAgentName()).isEqualTo("项目助理主 Agent");
        assertThat(response.getAgentType()).isEqualTo("project_assistant");
        assertThat(response.getCapabilities()).contains("项目周报生成", "工具调用审批");
        assertThat(response.getSampleTasks()).contains("创建项目任务前先判断是否需要审批");
        assertThat(response.getGovernanceRules()).contains("审批通过后从 checkpoint 恢复");
    }

    @Test
    void createDraftShouldCreateAgentDraftAndVersionFromTemplate() {
        when(agentService.createDraft(any(CreateAgentDraftCommand.class))).thenReturn(AgentDraftResponse.builder()
                .agentId(11L)
                .agentCode("agent-main-project")
                .status("draft")
                .build());
        when(agentVersionService.createVersion(any(), any(UpdateAgentConfigCommand.class)))
                .thenReturn(AgentVersionResponse.builder()
                        .agentVersionId(22L)
                        .versionNo("v1")
                        .versionStatus("draft")
                        .build());
        CreateMainAgentCommand command = new CreateMainAgentCommand();
        command.setOwnerUserId(200L);

        MainAgentSetupResponse response = service.createDraft(command);

        ArgumentCaptor<CreateAgentDraftCommand> draftCaptor = ArgumentCaptor.forClass(CreateAgentDraftCommand.class);
        verify(agentService).createDraft(draftCaptor.capture());
        assertThat(draftCaptor.getValue().getAgentName()).isEqualTo("项目助理主 Agent");
        assertThat(draftCaptor.getValue().getOwnerUserId()).isEqualTo(200L);

        ArgumentCaptor<UpdateAgentConfigCommand> versionCaptor = ArgumentCaptor.forClass(UpdateAgentConfigCommand.class);
        verify(agentVersionService).createVersion(eq(11L), versionCaptor.capture());
        assertThat(versionCaptor.getValue().getConfigJson()).contains("\"orchestrationMode\":\"main_agent\"");
        assertThat(versionCaptor.getValue().getToolScopeJson()).contains("\"approvalRequired\":true");
        assertThat(versionCaptor.getValue().getPermissionPolicyJson()).contains("\"checkpoint\":\"required\"");

        assertThat(response.getAgentId()).isEqualTo(11L);
        assertThat(response.getAgentVersionId()).isEqualTo(22L);
        assertThat(response.getPreview().getAgentType()).isEqualTo("project_assistant");
    }

    @Test
    void trialRunShouldCreateTaskStartRuntimeAndReturnEvents() {
        when(taskService.createTask(any(CreateTaskCommand.class))).thenReturn(TaskCreateResponse.builder()
                .taskId(101L)
                .taskCode("TASK-001")
                .status("pending")
                .build());
        when(taskService.startTask(eq(101L), any(StartTaskCommand.class))).thenReturn(TaskRunResponse.builder()
                .taskId(101L)
                .runId(202L)
                .runCode("RUN-001")
                .status("completed")
                .runtimeType("java-in-process")
                .build());
        TaskEvent event = new TaskEvent();
        event.setEventType("ASSISTANT_TASK_COMPLETED");
        Task savedTask = new Task();
        savedTask.setStatus("completed");
        savedTask.setResultSummary("{\"content\":\"done\"}");
        TaskArtifact artifact = new TaskArtifact();
        artifact.setArtifactName("任务结果");
        artifact.setContentText("done");
        when(taskService.getById(101L)).thenReturn(savedTask);
        when(taskService.listTaskEvents(101L)).thenReturn(List.of(event));
        when(taskArtifactService.listByTaskId(101L)).thenReturn(List.of(artifact));
        MainAgentTrialRunCommand command = new MainAgentTrialRunCommand();
        command.setAgentId(11L);
        command.setAgentVersionId(22L);
        command.setUserId(200L);
        command.setAssistantTaskType("risk_analysis");
        command.setPrompt("分析延期风险");

        MainAgentTrialRunResponse response = service.trialRun(command);

        ArgumentCaptor<CreateTaskCommand> taskCaptor = ArgumentCaptor.forClass(CreateTaskCommand.class);
        verify(taskService).createTask(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getAgentId()).isEqualTo(11L);
        assertThat(taskCaptor.getValue().getAgentVersionId()).isEqualTo(22L);
        assertThat(taskCaptor.getValue().getUserId()).isEqualTo(200L);
        assertThat(taskCaptor.getValue().getChannelType()).isEqualTo("main_agent_console");
        assertThat(taskCaptor.getValue().getInputText()).contains("\"assistantTaskType\":\"risk_analysis\"");
        assertThat(taskCaptor.getValue().getInputText()).contains("\"prompt\":\"分析延期风险\"");

        ArgumentCaptor<StartTaskCommand> startCaptor = ArgumentCaptor.forClass(StartTaskCommand.class);
        verify(taskService).startTask(eq(101L), startCaptor.capture());
        assertThat(startCaptor.getValue().getRuntimeType()).isEqualTo("java-in-process");
        assertThat(response.getTaskId()).isEqualTo(101L);
        assertThat(response.getRunId()).isEqualTo(202L);
        assertThat(response.getTaskStatus()).isEqualTo("completed");
        assertThat(response.getResultSummary()).isEqualTo("{\"content\":\"done\"}");
        assertThat(response.getEvents()).hasSize(1);
        assertThat(response.getArtifacts()).hasSize(1);
    }
}
