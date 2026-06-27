package com.xiaoai.agent.collaboration.integration;

import com.xiaoai.agent.collaboration.controller.QualityGateController;
import com.xiaoai.agent.collaboration.entity.AgentThread;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.collaboration.entity.CollaborationPlan;
import com.xiaoai.agent.collaboration.entity.CollaborationSession;
import com.xiaoai.agent.collaboration.entity.QualityGate;
import com.xiaoai.agent.collaboration.model.UpdateQualityGateCommand;
import com.xiaoai.agent.collaboration.model.CreateAgentHandoffCommand;
import com.xiaoai.agent.collaboration.mapper.AgentThreadMapper;
import com.xiaoai.agent.collaboration.mapper.CollaborationPlanMapper;
import com.xiaoai.agent.collaboration.mapper.CollaborationSessionMapper;
import com.xiaoai.agent.collaboration.mapper.QualityGateMapper;
import com.xiaoai.agent.collaboration.model.CollaborationPlanValidationResult;
import com.xiaoai.agent.collaboration.model.CollaborationSessionResponse;
import com.xiaoai.agent.collaboration.model.CreateCollaborationSessionCommand;
import com.xiaoai.agent.collaboration.model.SubmitCollaborationPlanCommand;
import com.xiaoai.agent.collaboration.service.AgentHandoffService;
import com.xiaoai.agent.collaboration.service.AgentRoleService;
import com.xiaoai.agent.collaboration.service.CollaborationPlanValidator;
import com.xiaoai.agent.collaboration.service.impl.AgentThreadServiceImpl;
import com.xiaoai.agent.collaboration.service.impl.CollaborationGateAdvanceServiceImpl;
import com.xiaoai.agent.collaboration.service.impl.CollaborationPlanServiceImpl;
import com.xiaoai.agent.collaboration.service.impl.CollaborationSessionServiceImpl;
import com.xiaoai.agent.collaboration.service.impl.QualityGateServiceImpl;
import com.xiaoai.agent.collaboration.strategy.CollaborationStrategyRegistry;
import com.xiaoai.agent.collaboration.strategy.CollaborationStrategyContext;
import com.xiaoai.agent.collaboration.strategy.CollaborationStrategyResult;
import com.xiaoai.agent.collaboration.strategy.impl.OrchestratedTeamStrategy;
import com.xiaoai.agent.common.api.ApiResponse;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.task.service.TaskService;
import com.xiaoai.agent.task.entity.Task;
import com.xiaoai.agent.task.entity.TaskArtifact;
import com.xiaoai.agent.task.model.CreateTaskCommand;
import com.xiaoai.agent.task.model.TaskCreateResponse;
import com.xiaoai.agent.task.model.TaskRunResponse;
import com.xiaoai.agent.task.service.TaskArtifactService;
import com.xiaoai.agent.test.TestReflectionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollaborationSessionFlowTest {

    private final CollaborationSessionMapper sessionMapper = mock(CollaborationSessionMapper.class);
    private final CollaborationPlanMapper planMapper = mock(CollaborationPlanMapper.class);
    private final AgentThreadMapper threadMapper = mock(AgentThreadMapper.class);
    private final QualityGateMapper gateMapper = mock(QualityGateMapper.class);
    private final CollaborationPlanValidator validator = mock(CollaborationPlanValidator.class);
    private final AgentRoleService agentRoleService = mock(AgentRoleService.class);
    private final AgentHandoffService handoffService = mock(AgentHandoffService.class);
    private final TaskService taskService = mock(TaskService.class);
    private final TaskArtifactService taskArtifactService = mock(TaskArtifactService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CollaborationSessionServiceImpl sessionService = new CollaborationSessionServiceImpl();
    private final CollaborationPlanServiceImpl planService = new CollaborationPlanServiceImpl(validator);
    private final AgentThreadServiceImpl threadService = new AgentThreadServiceImpl(sessionService, agentRoleService, taskService);
    private final QualityGateServiceImpl gateService = new QualityGateServiceImpl();
    private final OrchestratedTeamStrategy strategy = new OrchestratedTeamStrategy(planService, threadService, handoffService, gateService);

    @BeforeEach
    void setUp() {
        TestReflectionUtils.injectBaseMapper(sessionService, sessionMapper);
        TestReflectionUtils.injectBaseMapper(planService, planMapper);
        TestReflectionUtils.injectBaseMapper(threadService, threadMapper);
        TestReflectionUtils.injectBaseMapper(gateService, gateMapper);
        gateService.setQualitySnapshotDependencies(threadMapper, taskArtifactService);
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void genericProjectManagementSessionShouldSubmitPlanAndStartStrategyWithoutSoftwareSpecificTypes() {
        when(sessionMapper.insert(any(CollaborationSession.class))).thenAnswer(invocation -> {
            CollaborationSession session = invocation.getArgument(0);
            session.setId(10L);
            return 1;
        });
        when(validator.validate(any())).thenReturn(CollaborationPlanValidationResult.passed());
        when(planMapper.insert(any(CollaborationPlan.class))).thenAnswer(invocation -> {
            CollaborationPlan plan = invocation.getArgument(0);
            plan.setId(20L);
            return 1;
        });
        when(planMapper.selectOne(any())).thenAnswer(invocation -> {
            CollaborationPlan plan = new CollaborationPlan();
            plan.setId(20L);
            plan.setTenantId(100L);
            plan.setSessionId(10L);
            plan.setValidationStatus("passed");
            plan.setPlanJson(projectManagementPlanJson());
            return plan;
        });
        when(sessionMapper.selectOne(any())).thenAnswer(invocation -> {
            CollaborationSession session = new CollaborationSession();
            session.setId(10L);
            session.setTenantId(100L);
            session.setStrategyType("orchestrated_team");
            session.setStatus("planning");
            return session;
        });

        CreateCollaborationSessionCommand sessionCommand = new CreateCollaborationSessionCommand();
        sessionCommand.setStrategyType("orchestrated_team");
        sessionCommand.setGoalText("完成项目管理风险评审协作");
        sessionCommand.setContextJson("{\"domain\":\"project_management\"}");
        CollaborationSessionResponse session = sessionService.createSession(sessionCommand);

        SubmitCollaborationPlanCommand planCommand = new SubmitCollaborationPlanCommand();
        planCommand.setSessionId(session.getSessionId());
        planCommand.setPlanJson(projectManagementPlanJson());
        CollaborationPlan plan = planService.submitPlan(planCommand);

        CollaborationStrategyResult result = strategy.start(CollaborationStrategyContext.builder()
                .sessionId(session.getSessionId())
                .planId(plan.getId())
                .build());

        ArgumentCaptor<CollaborationSession> sessionCaptor = ArgumentCaptor.forClass(CollaborationSession.class);
        verify(sessionMapper).insert(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getContextJson()).contains("project_management");
        assertThat(sessionCaptor.getValue().getGoalText()).contains("项目管理风险评审");

        ArgumentCaptor<CollaborationPlan> planCaptor = ArgumentCaptor.forClass(CollaborationPlan.class);
        verify(planMapper).insert(planCaptor.capture());
        assertThat(planCaptor.getValue().getValidationStatus()).isEqualTo("passed");
        assertThat(planCaptor.getValue().getPlanJson()).contains("risk_intake", "stakeholder_review");
        assertThat(planCaptor.getValue().getPlanJson()).doesNotContain("software_product_manager");
        assertThat(planCaptor.getValue().getPlanJson()).doesNotContain("backend_developer");

        verify(threadMapper).insert(any(AgentThread.class));
        verify(gateMapper).insert(any(QualityGate.class));
        assertThat(result.getStatus()).isEqualTo("started");
        assertThat(result.getCreatedThreadCount()).isEqualTo(1);
        assertThat(result.getCreatedGateCount()).isEqualTo(1);
    }

    @Test
    void orchestratedTeamShouldAutoStartStagesAndCreateArtifactHandoffAfterGate() {
        List<AgentThread> threads = new ArrayList<>();
        when(planMapper.selectOne(any())).thenAnswer(invocation -> {
            CollaborationPlan plan = new CollaborationPlan();
            plan.setId(20L);
            plan.setTenantId(100L);
            plan.setSessionId(10L);
            plan.setValidationStatus("passed");
            plan.setPlanJson(autoStartPlanJson());
            return plan;
        });
        when(sessionMapper.selectOne(any())).thenAnswer(invocation -> {
            CollaborationSession session = new CollaborationSession();
            session.setId(10L);
            session.setTenantId(100L);
            session.setStrategyType("orchestrated_team");
            session.setStatus("running");
            session.setContextJson("{\"activePlanId\":20}");
            return session;
        });
        when(threadMapper.insert(any(AgentThread.class))).thenAnswer(invocation -> {
            AgentThread thread = invocation.getArgument(0);
            thread.setId(100L + threads.size());
            threads.add(thread);
            return 1;
        });
        when(threadMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(threads));
        when(threadMapper.selectOne(any())).thenAnswer(invocation -> threads.get(threads.size() - 1));
        when(threadMapper.updateById(any(AgentThread.class))).thenAnswer(invocation -> 1);
        when(gateMapper.insert(any(QualityGate.class))).thenAnswer(invocation -> {
            QualityGate gate = invocation.getArgument(0);
            gate.setId(300L);
            return 1;
        });
        List<CreateTaskCommand> taskCommands = new ArrayList<>();
        final long[] taskSequence = {500L};
        when(taskService.createTask(any(CreateTaskCommand.class))).thenAnswer(invocation -> {
            CreateTaskCommand command = invocation.getArgument(0);
            taskCommands.add(command);
            return TaskCreateResponse.builder()
                    .taskId(taskSequence[0]++)
                    .taskCode("TASK-" + command.getAgentId())
                    .status("pending")
                    .build();
        });
        when(taskService.startTask(any(Long.class), any())).thenAnswer(invocation -> TaskRunResponse.builder()
                .taskId(invocation.getArgument(0))
                .runId(900L)
                .status("completed")
                .runtimeType("java-in-process")
                .build());
        when(taskService.getTask(any(Long.class))).thenAnswer(invocation -> {
            Task task = new Task();
            task.setId(invocation.getArgument(0));
            task.setStatus("completed");
            return task;
        });
        when(handoffService.listHandoffs(10L)).thenReturn(List.of());

        CollaborationStrategyResult started = strategy.start(CollaborationStrategyContext.builder()
                .sessionId(10L)
                .planId(20L)
                .build());
        threads.get(0).setOutputArtifactId(700L);

        CollaborationStrategyResult continued = strategy.continueAfterGate(CollaborationStrategyContext.builder()
                .sessionId(10L)
                .planId(20L)
                .gateCode("prd_confirmed")
                .build());

        assertThat(started.getCurrentStageCode()).isEqualTo("prd");
        assertThat(continued.getCurrentStageCode()).isEqualTo("dev");
        assertThat(threads).hasSize(2);
        assertThat(threads.get(0).getThreadName()).isEqualTo("Product PRD");
        assertThat(threads.get(0).getTaskId()).isEqualTo(500L);
        assertThat(threads.get(0).getStatus()).isEqualTo("completed");
        assertThat(threads.get(1).getThreadName()).isEqualTo("Development");
        assertThat(threads.get(1).getTaskId()).isEqualTo(501L);
        assertThat(threads.get(1).getInputArtifactId()).isEqualTo(700L);
        assertThat(threads.get(1).getStatus()).isEqualTo("completed");
        assertThat(taskCommands).hasSize(2);
        assertThat(taskCommands.get(0).getInputText()).contains("\"toolCalls\"", "\"toolId\":22", "controlled.http.project-query");
        assertThat(taskCommands.get(1).getInputText()).contains("\"toolCalls\"", "\"toolId\":23", "controlled.cli.project-update");
        assertThat(taskCommands.get(1).getInputText()).contains("\"inputArtifactId\":700");
        verify(taskService, org.mockito.Mockito.times(2)).startTask(any(Long.class), any());
        ArgumentCaptor<CreateAgentHandoffCommand> handoffCaptor = ArgumentCaptor.forClass(CreateAgentHandoffCommand.class);
        verify(handoffService).createHandoff(handoffCaptor.capture());
        assertThat(handoffCaptor.getValue().getFromThreadId()).isEqualTo(100L);
        assertThat(handoffCaptor.getValue().getToThreadId()).isEqualTo(101L);
        assertThat(handoffCaptor.getValue().getArtifactId()).isEqualTo(700L);
    }

    @Test
    void softwareAgileTeamShouldAdvanceRequirementThroughDeliveryStages() throws Exception {
        List<AgentThread> threads = new ArrayList<>();
        List<CreateTaskCommand> taskCommands = new ArrayList<>();
        when(planMapper.selectOne(any())).thenAnswer(invocation -> {
            CollaborationPlan plan = new CollaborationPlan();
            plan.setId(20L);
            plan.setTenantId(100L);
            plan.setSessionId(10L);
            plan.setValidationStatus("passed");
            plan.setPlanJson(softwareAgileDeliveryPlanJson());
            return plan;
        });
        when(sessionMapper.selectOne(any())).thenAnswer(invocation -> {
            CollaborationSession session = new CollaborationSession();
            session.setId(10L);
            session.setTenantId(100L);
            session.setStrategyType("orchestrated_team");
            session.setStatus("running");
            session.setContextJson("{\"activePlanId\":20}");
            return session;
        });
        when(threadMapper.insert(any(AgentThread.class))).thenAnswer(invocation -> {
            AgentThread thread = invocation.getArgument(0);
            thread.setId(100L + threads.size());
            threads.add(thread);
            return 1;
        });
        when(threadMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(threads));
        when(threadMapper.selectOne(any())).thenAnswer(invocation -> threads.get(threads.size() - 1));
        when(threadMapper.updateById(any(AgentThread.class))).thenAnswer(invocation -> 1);
        when(gateMapper.insert(any(QualityGate.class))).thenAnswer(invocation -> {
            QualityGate gate = invocation.getArgument(0);
            gate.setId(300L);
            return 1;
        });
        final long[] taskSequence = {500L};
        when(taskService.createTask(any(CreateTaskCommand.class))).thenAnswer(invocation -> {
            CreateTaskCommand command = invocation.getArgument(0);
            taskCommands.add(command);
            return TaskCreateResponse.builder()
                    .taskId(taskSequence[0]++)
                    .taskCode("TASK-" + command.getAgentId())
                    .status("pending")
                    .build();
        });
        when(taskService.startTask(any(Long.class), any())).thenAnswer(invocation -> TaskRunResponse.builder()
                .taskId(invocation.getArgument(0))
                .runId(900L)
                .status("completed")
                .runtimeType("java-in-process")
                .build());
        when(taskService.getTask(any(Long.class))).thenAnswer(invocation -> {
            Task task = new Task();
            task.setId(invocation.getArgument(0));
            task.setStatus("completed");
            return task;
        });
        when(handoffService.listHandoffs(10L)).thenReturn(List.of());

        CollaborationStrategyResult started = strategy.start(CollaborationStrategyContext.builder()
                .sessionId(10L)
                .planId(20L)
                .build());
        assertThat(started.getCurrentStageCode()).isEqualTo("requirement_analysis");
        threads.get(0).setOutputArtifactId(700L);

        CollaborationStrategyResult design = passGateAndPublishArtifact("requirement_confirmed", threads, 701L);
        CollaborationStrategyResult backend = passGateAndPublishArtifact("design_confirmed", threads, 702L);
        threads.get(threads.size() - 2).setOutputArtifactId(703L);
        CollaborationStrategyResult testing = passGateAndPublishArtifact("implementation_done", threads, 704L);
        CollaborationStrategyResult delivery = passGateAndPublishArtifact("tests_passed", threads, 705L);

        assertThat(design.getCurrentStageCode()).isEqualTo("technical_design");
        assertThat(backend.getCurrentStageCode()).isEqualTo("backend_implementation");
        assertThat(backend.getCreatedThreadCount()).isEqualTo(2);
        assertThat(testing.getCurrentStageCode()).isEqualTo("testing");
        assertThat(delivery.getCurrentStageCode()).isEqualTo("review_and_delivery");
        assertThat(threads).extracting(AgentThread::getThreadName).containsExactly(
                "需求分析",
                "技术方案",
                "后端实现",
                "前端实现",
                "测试验证",
                "审查交付");
        assertThat(threads).extracting(AgentThread::getStatus).containsOnly("completed");
        assertThat(threads).extracting(AgentThread::getInputArtifactId).containsExactly(
                null,
                700L,
                701L,
                701L,
                702L,
                704L);
        assertThat(taskCommands).hasSize(6);
        assertThat(taskCommands).extracting(CreateTaskCommand::getAgentId).containsExactly(101L, 102L, 103L, 104L, 105L, 106L);
        assertThat(taskCommands).extracting(CreateTaskCommand::getAgentVersionId).containsExactly(1001L, 1002L, 1003L, 1004L, 1005L, 1006L);
        JsonNode testingInput = objectMapper.readTree(taskCommands.get(4).getInputText());
        assertThat(testingInput.path("collaborationContext").path("inputArtifactId").asLong()).isEqualTo(702L);
        assertThat(testingInput.path("collaborationContext").path("inputArtifactIds")).extracting(JsonNode::asLong)
                .containsExactlyInAnyOrder(702L, 703L);
        assertThat(taskCommands.get(5).getInputText()).contains("\"stageCode\":\"review_and_delivery\"", "\"inputArtifactId\":704");
        verify(taskService, org.mockito.Mockito.times(6)).startTask(any(Long.class), any());
        ArgumentCaptor<CreateAgentHandoffCommand> handoffCaptor = ArgumentCaptor.forClass(CreateAgentHandoffCommand.class);
        verify(handoffService, org.mockito.Mockito.times(9)).createHandoff(handoffCaptor.capture());
        assertThat(handoffCaptor.getAllValues()).extracting(CreateAgentHandoffCommand::getArtifactId)
                .contains(700L, 701L, 702L, 703L, 704L);
    }

    @Test
    void qualityGateControllerShouldDriveAgileTeamMvpToCompletedSession() {
        List<AgentThread> threads = new ArrayList<>();
        List<QualityGate> gates = new ArrayList<>();
        List<CreateTaskCommand> taskCommands = new ArrayList<>();
        long[] currentGateLookup = {0L};
        CollaborationSession session = new CollaborationSession();
        session.setId(10L);
        session.setTenantId(100L);
        session.setStrategyType("orchestrated_team");
        session.setStatus("running");
        session.setContextJson("{\"activePlanId\":20}");
        CollaborationStrategyRegistry registry = new CollaborationStrategyRegistry(List.of(strategy));
        CollaborationGateAdvanceServiceImpl gateAdvanceService = new CollaborationGateAdvanceServiceImpl(
                sessionService,
                planService,
                threadService,
                registry
        );
        QualityGateController controller = new QualityGateController(gateService, gateAdvanceService);

        when(planMapper.selectOne(any())).thenAnswer(invocation -> {
            CollaborationPlan plan = new CollaborationPlan();
            plan.setId(20L);
            plan.setTenantId(100L);
            plan.setSessionId(10L);
            plan.setValidationStatus("passed");
            plan.setPlanJson(softwareAgileDeliveryPlanJson());
            return plan;
        });
        when(sessionMapper.selectOne(any())).thenReturn(session);
        when(sessionMapper.updateById(any(CollaborationSession.class))).thenReturn(1);
        when(threadMapper.insert(any(AgentThread.class))).thenAnswer(invocation -> {
            AgentThread thread = invocation.getArgument(0);
            thread.setId(100L + threads.size());
            threads.add(thread);
            return 1;
        });
        when(threadMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(threads));
        when(threadMapper.selectOne(any())).thenAnswer(invocation -> threads.get(threads.size() - 1));
        when(threadMapper.updateById(any(AgentThread.class))).thenReturn(1);
        when(gateMapper.insert(any(QualityGate.class))).thenAnswer(invocation -> {
            QualityGate gate = invocation.getArgument(0);
            gate.setId(300L + gates.size());
            gates.add(gate);
            return 1;
        });
        when(gateMapper.selectList(any())).thenReturn(List.of());
        when(gateMapper.selectOne(any())).thenAnswer(invocation -> gates.stream()
                .filter(gate -> gate.getId().equals(currentGateLookup[0]))
                .findFirst()
                .orElse(null));
        when(gateMapper.updateById(any(QualityGate.class))).thenReturn(1);
        final long[] taskSequence = {500L};
        when(taskService.createTask(any(CreateTaskCommand.class))).thenAnswer(invocation -> {
            CreateTaskCommand command = invocation.getArgument(0);
            taskCommands.add(command);
            return TaskCreateResponse.builder()
                    .taskId(taskSequence[0]++)
                    .taskCode("TASK-" + command.getAgentId())
                    .status("pending")
                    .build();
        });
        when(taskService.startTask(any(Long.class), any())).thenAnswer(invocation -> TaskRunResponse.builder()
                .taskId(invocation.getArgument(0))
                .runId(900L)
                .status("completed")
                .runtimeType("java-in-process")
                .build());
        when(taskService.getTask(any(Long.class))).thenAnswer(invocation -> {
            Task task = new Task();
            task.setId(invocation.getArgument(0));
            task.setStatus("completed");
            return task;
        });
        when(taskArtifactService.getArtifact(any(Long.class))).thenAnswer(invocation -> artifact(invocation.getArgument(0)));
        when(handoffService.listHandoffs(10L)).thenReturn(List.of());

        CollaborationStrategyResult started = strategy.start(CollaborationStrategyContext.builder()
                .sessionId(10L)
                .planId(20L)
                .build());
        publishStageArtifact(threads, "requirement_analysis", 700L);

        passGate(controller, gates, currentGateLookup, "requirement_confirmed");
        assertThat(threads).extracting(thread -> readStageCode(thread.getContextJson()))
                .contains("technical_design");
        publishStageArtifact(threads, "technical_design", 701L);
        passGate(controller, gates, currentGateLookup, "design_confirmed");
        publishStageArtifact(threads, "backend_implementation", 702L);
        publishStageArtifact(threads, "frontend_implementation", 703L);
        passGate(controller, gates, currentGateLookup, "implementation_done");
        publishStageArtifact(threads, "testing", 704L);
        passGate(controller, gates, currentGateLookup, "tests_passed");
        publishStageArtifact(threads, "review_and_delivery", 705L);
        passGate(controller, gates, currentGateLookup, "delivery_confirmed");

        assertThat(started.getCurrentStageCode()).isEqualTo("requirement_analysis");
        assertThat(session.getStatus()).isEqualTo("completed");
        assertThat(session.getCurrentStageCode()).isEqualTo("review_and_delivery");
        assertThat(gates).extracting(QualityGate::getStatus).containsOnly("passed");
        assertThat(threads).hasSize(6);
        assertThat(threads).extracting(AgentThread::getStatus).containsOnly("completed");
        assertThat(taskCommands).hasSize(6);
    }

    private String projectManagementPlanJson() {
        return """
                {
                  "goal": "完成项目管理风险评审协作",
                  "maxDepth": 1,
                  "maxThreads": 3,
                  "stages": [
                    {
                      "stageCode": "risk_intake",
                      "stageName": "风险材料整理",
                      "agentId": 71,
                      "roleCode": "project_risk_analyst",
                      "outputArtifactTypes": ["risk_register"],
                      "requiresGate": "risk_review_confirmed",
                      "gateName": "风险评审确认",
                      "gateType": "manual_confirmation"
                    },
                    {
                      "stageCode": "stakeholder_review",
                      "stageName": "干系人复核",
                      "agentId": 72,
                      "roleCode": "project_stakeholder_reviewer",
                      "inputArtifactTypes": ["risk_register"],
                      "outputArtifactTypes": ["decision_summary"]
                    }
                  ]
                }
                """;
    }

    private String autoStartPlanJson() {
        return """
                {
                  "goal": "deliver requirement through agile team",
                  "stages": [
                    {
                      "stageCode": "prd",
                      "stageName": "Product PRD",
                      "agentId": 71,
                      "agentVersionId": 171,
                      "createTask": true,
                      "autoStartTask": true,
                      "inputText": "write PRD",
                      "toolCalls": [
                        {
                          "toolId": 22,
                          "toolCode": "controlled.http.project-query",
                          "toolType": "http",
                          "callPayloadJson": {"query": "requirement"}
                        }
                      ],
                      "requiresGate": "prd_confirmed",
                      "gateName": "PRD Confirmed"
                    },
                    {
                      "stageCode": "dev",
                      "stageName": "Development",
                      "agentId": 72,
                      "agentVersionId": 172,
                      "createTask": true,
                      "autoStartTask": true,
                      "toolCalls": [
                        {
                          "toolId": 23,
                          "toolCode": "controlled.cli.project-update",
                          "toolType": "cli",
                          "callPayloadJson": {"id": 1, "status": "done"}
                        }
                      ]
                    }
                  ]
                }
                """;
    }

    private CollaborationStrategyResult passGateAndPublishArtifact(String gateCode, List<AgentThread> threads, Long artifactId) {
        CollaborationStrategyResult result = strategy.continueAfterGate(CollaborationStrategyContext.builder()
                .sessionId(10L)
                .planId(20L)
                .gateCode(gateCode)
                .build());
        if (!threads.isEmpty()) {
            threads.get(threads.size() - 1).setOutputArtifactId(artifactId);
        }
        return result;
    }

    private void passGate(QualityGateController controller,
                          List<QualityGate> gates,
                          long[] currentGateLookup,
                          String gateCode) {
        QualityGate gate = gates.stream()
                .filter(item -> gateCode.equals(item.getGateCode()))
                .findFirst()
                .orElseThrow();
        currentGateLookup[0] = gate.getId();
        ApiResponse<QualityGate> response = controller.passGate(10L, gate.getId(), new UpdateQualityGateCommand());
        assertThat(response.getData().getStatus()).isEqualTo("passed");
    }

    private void publishStageArtifact(List<AgentThread> threads, String stageCode, Long artifactId) {
        threads.stream()
                .filter(thread -> stageCode.equals(readStageCode(thread.getContextJson())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing stage " + stageCode + ", existing stages: "
                        + threads.stream().map(thread -> readStageCode(thread.getContextJson())).toList()))
                .setOutputArtifactId(artifactId);
    }

    private String readStageCode(String contextJson) {
        try {
            return objectMapper.readTree(contextJson).path("stageCode").asText(null);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private TaskArtifact artifact(Long artifactId) {
        TaskArtifact artifact = new TaskArtifact();
        artifact.setId(artifactId);
        artifact.setArtifactType("mvp_artifact");
        artifact.setArtifactName("Artifact #" + artifactId);
        artifact.setContentText("content for artifact #" + artifactId);
        artifact.setMetadataJson("""
                {"artifactVersion":1,"producerAgentId":1,"producerAgentVersionId":1,"collaborationSessionId":10,"stageCode":"mvp"}
                """);
        return artifact;
    }

    private String softwareAgileDeliveryPlanJson() {
        return """
                {
                  "goal": "deliver requirement through agile software team",
                  "maxDepth": 1,
                  "maxThreads": 6,
                  "stages": [
                    {
                      "stageCode": "requirement_analysis",
                      "stageName": "需求分析",
                      "roleCode": "software_product_manager",
                      "agentId": 101,
                      "agentVersionId": 1001,
                      "createTask": true,
                      "autoStartTask": true,
                      "inputText": "基于用户需求输出 PRD、用户故事和验收标准。",
                      "inputArtifactTypes": ["requirement_document"],
                      "outputArtifactTypes": ["requirement_analysis"],
                      "requiresGate": "requirement_confirmed",
                      "gateName": "需求确认",
                      "gateType": "manual_confirmation"
                    },
                    {
                      "stageCode": "technical_design",
                      "stageName": "技术方案",
                      "roleCode": "software_architect",
                      "agentId": 102,
                      "agentVersionId": 1002,
                      "createTask": true,
                      "autoStartTask": true,
                      "inputText": "基于已确认需求输出技术方案、接口契约和关键风险。",
                      "inputArtifactTypes": ["requirement_analysis"],
                      "outputArtifactTypes": ["technical_design", "api_contract"],
                      "requiresGate": "design_confirmed",
                      "gateName": "方案确认",
                      "gateType": "manual_confirmation"
                    },
                    {
                      "stageCode": "backend_implementation",
                      "stageName": "后端实现",
                      "roleCode": "software_backend_developer",
                      "agentId": 103,
                      "agentVersionId": 1003,
                      "createTask": true,
                      "autoStartTask": true,
                      "inputText": "基于技术方案完成后端实现并输出实现摘要。",
                      "inputArtifactTypes": ["technical_design", "api_contract"],
                      "outputArtifactTypes": ["implementation_summary"],
                      "requiresGate": "implementation_done",
                      "gateName": "实现完成",
                      "gateType": "artifact_check"
                    },
                    {
                      "stageCode": "frontend_implementation",
                      "stageName": "前端实现",
                      "roleCode": "software_frontend_developer",
                      "agentId": 104,
                      "agentVersionId": 1004,
                      "createTask": true,
                      "autoStartTask": true,
                      "inputText": "基于技术方案完成前端实现并输出实现摘要。",
                      "inputArtifactTypes": ["technical_design", "api_contract"],
                      "outputArtifactTypes": ["implementation_summary"],
                      "requiresGate": "implementation_done",
                      "gateName": "实现完成",
                      "gateType": "artifact_check"
                    },
                    {
                      "stageCode": "testing",
                      "stageName": "测试验证",
                      "roleCode": "software_tester",
                      "agentId": 105,
                      "agentVersionId": 1005,
                      "createTask": true,
                      "autoStartTask": true,
                      "inputText": "基于验收标准和实现摘要输出测试计划、执行结果和风险。",
                      "inputArtifactTypes": ["requirement_analysis", "implementation_summary"],
                      "outputArtifactTypes": ["test_report"],
                      "requiresGate": "tests_passed",
                      "gateName": "测试通过",
                      "gateType": "test_result"
                    },
                    {
                      "stageCode": "review_and_delivery",
                      "stageName": "审查交付",
                      "roleCode": "software_reviewer",
                      "agentId": 106,
                      "agentVersionId": 1006,
                      "createTask": true,
                      "autoStartTask": true,
                      "inputText": "基于技术方案、实现摘要和测试报告输出审查结论与交付总结。",
                      "inputArtifactTypes": ["technical_design", "implementation_summary", "test_report"],
                      "outputArtifactTypes": ["review_report", "delivery_summary"],
                      "requiresGate": "delivery_confirmed",
                      "gateName": "交付确认",
                      "gateType": "manual_confirmation"
                    }
                  ]
                }
                """;
    }
}
