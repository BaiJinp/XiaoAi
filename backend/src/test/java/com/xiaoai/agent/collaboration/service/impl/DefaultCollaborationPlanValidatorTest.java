package com.xiaoai.agent.collaboration.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xiaoai.agent.agent.entity.AgentVersion;
import com.xiaoai.agent.agent.mapper.AgentVersionMapper;
import com.xiaoai.agent.collaboration.entity.AgentRole;
import com.xiaoai.agent.collaboration.entity.ArtifactType;
import com.xiaoai.agent.collaboration.mapper.AgentRoleMapper;
import com.xiaoai.agent.collaboration.mapper.ArtifactTypeMapper;
import com.xiaoai.agent.collaboration.model.CollaborationPlanValidationResult;
import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.test.TestReflectionUtils;
import com.xiaoai.agent.tool.entity.ToolConfig;
import com.xiaoai.agent.tool.mapper.ToolConfigMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultCollaborationPlanValidatorTest {

    private final AgentRoleMapper agentRoleMapper = mock(AgentRoleMapper.class);
    private final ArtifactTypeMapper artifactTypeMapper = mock(ArtifactTypeMapper.class);
    private final AgentVersionMapper agentVersionMapper = mock(AgentVersionMapper.class);
    private final ToolConfigMapper toolConfigMapper = mock(ToolConfigMapper.class);
    private final DefaultCollaborationPlanValidator validator = new DefaultCollaborationPlanValidator(
            agentRoleMapper,
            artifactTypeMapper,
            agentVersionMapper,
            toolConfigMapper,
            6
    );

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder().tenantId(100L).userId(200L).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void validateShouldPassGenericPlanWhenRolesAndArtifactTypesExist() {
        when(agentRoleMapper.selectOne(any(Wrapper.class))).thenReturn(new AgentRole());
        when(artifactTypeMapper.selectOne(any(Wrapper.class))).thenReturn(new ArtifactType());
        String planJson = """
                {
                  "goal": "完成一次通用协作交付",
                  "maxDepth": 1,
                  "maxThreads": 3,
                  "stages": [
                    {
                      "stageCode": "analysis",
                      "roleCode": "generic_analyst",
                      "inputArtifactTypes": ["source_document"],
                      "outputArtifactTypes": ["analysis_report"],
                      "requiresGate": "analysis_confirmed"
                    }
                  ]
                }
                """;

        CollaborationPlanValidationResult result = validator.validate(planJson);

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void validateShouldRejectPlanWithMissingRoleAndTooManyThreads() {
        when(agentRoleMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(artifactTypeMapper.selectOne(any(Wrapper.class))).thenReturn(new ArtifactType());
        String planJson = """
                {
                  "goal": "完成一次通用协作交付",
                  "maxDepth": 1,
                  "maxThreads": 9,
                  "stages": [
                    {
                      "stageCode": "analysis",
                      "roleCode": "missing_role",
                      "inputArtifactTypes": ["source_document"],
                      "outputArtifactTypes": ["analysis_report"]
                    }
                  ]
                }
                """;

        CollaborationPlanValidationResult result = validator.validate(planJson);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getErrors()).contains("maxThreads exceeds limit 6", "Role not found: missing_role");
    }

    @Test
    void validateShouldRejectAutoStartWithoutCreateTaskAndAgentId() {
        String planJson = """
                {
                  "goal": "auto start validation",
                  "maxDepth": 1,
                  "maxThreads": 1,
                  "stages": [
                    {
                      "stageCode": "analysis",
                      "autoStartTask": true
                    }
                  ]
                }
                """;

        CollaborationPlanValidationResult result = validator.validate(planJson);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getErrors()).contains("autoStartTask requires createTask", "autoStartTask requires agentId");
    }

    @Test
    void validateShouldAcceptStrictHandoffPolicyAndRejectInvalidValues() {
        String validPlanJson = """
                {
                  "goal": "strict handoff validation",
                  "handoffPolicy": {
                    "mode": "strict",
                    "requireAcceptedBeforeConsume": true
                  },
                  "stages": [
                    {
                      "stageCode": "analysis",
                      "agentId": 12,
                      "agentVersionId": 13,
                      "createTask": true,
                      "autoStartTask": true,
                      "waitForHandoffAcceptance": true
                    }
                  ]
                }
                """;

        CollaborationPlanValidationResult validResult = validator.validate(validPlanJson);

        assertThat(validResult.isPassed()).isTrue();

        String invalidPlanJson = """
                {
                  "goal": "strict handoff validation",
                  "handoffPolicy": {
                    "mode": "locked",
                    "requireAcceptedBeforeConsume": "yes"
                  },
                  "stages": [
                    {
                      "stageCode": "analysis",
                      "agentId": 12,
                      "agentVersionId": 13,
                      "createTask": true,
                      "autoStartTask": true,
                      "waitForHandoffAcceptance": "yes"
                    }
                  ]
                }
                """;

        CollaborationPlanValidationResult invalidResult = validator.validate(invalidPlanJson);

        assertThat(invalidResult.isPassed()).isFalse();
        assertThat(invalidResult.getErrors()).contains(
                "handoffPolicy.mode is invalid",
                "handoffPolicy.requireAcceptedBeforeConsume must be boolean",
                "waitForHandoffAcceptance must be boolean"
        );
    }

    @Test
    void validateShouldRejectCreateTaskWithoutAgentId() {
        String planJson = """
                {
                  "goal": "task binding validation",
                  "maxDepth": 1,
                  "maxThreads": 1,
                  "stages": [
                    {
                      "stageCode": "analysis",
                      "createTask": true
                    }
                  ]
                }
                """;

        CollaborationPlanValidationResult result = validator.validate(planJson);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getErrors()).contains("createTask requires agentId");
    }

    @Test
    void validateShouldRejectInvalidInputArtifactVersion() {
        String planJson = """
                {
                  "goal": "artifact version validation",
                  "maxDepth": 1,
                  "maxThreads": 1,
                  "stages": [
                    {
                      "stageCode": "analysis",
                      "agentId": 12,
                      "agentVersionId": 13,
                      "createTask": true,
                      "inputArtifactVersion": 0
                    }
                  ]
                }
                """;

        CollaborationPlanValidationResult result = validator.validate(planJson);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getErrors()).contains("inputArtifactVersion must be a positive integer");
    }

    @Test
    void validateShouldRejectMissingReworkStageCode() {
        String planJson = """
                {
                  "goal": "rework validation",
                  "maxDepth": 1,
                  "maxThreads": 1,
                  "stages": [
                    {
                      "stageCode": "requirement_review",
                      "requiresGate": "requirement_confirmed",
                      "reworkStageCode": "requirement_rework"
                    }
                  ]
                }
                """;

        CollaborationPlanValidationResult result = validator.validate(planJson);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getErrors()).contains("reworkStageCode not found: requirement_rework");
    }

    @Test
    void validateShouldRejectInvalidStageToolCalls() {
        String planJson = """
                {
                  "goal": "tool chain validation",
                  "maxDepth": 1,
                  "maxThreads": 1,
                  "stages": [
                    {
                      "stageCode": "business_sync",
                      "agentId": 12,
                      "createTask": true,
                      "toolCalls": [
                        {"toolCode": "controlled.http.project-query"},
                        {"toolId": 22, "callPayloadJson": "bad"}
                      ]
                    }
                  ]
                }
                """;

        CollaborationPlanValidationResult result = validator.validate(planJson);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getErrors()).contains(
                "stage toolCalls requires agentVersionId",
                "stage toolCalls[0] missing toolId",
                "stage toolCalls[1] callPayloadJson must be an object"
        );
    }

    @Test
    void validateShouldRejectToolCallsWithoutCreateTask() {
        when(toolConfigMapper.selectOne(any(Wrapper.class))).thenReturn(tool(22L, "controlled.http.project-query"));
        String planJson = """
                {
                  "goal": "tool chain validation",
                  "maxDepth": 1,
                  "maxThreads": 1,
                  "stages": [
                    {
                      "stageCode": "business_sync",
                      "agentId": 12,
                      "toolCalls": [
                        {"toolId": 22, "toolCode": "controlled.http.project-query", "callPayloadJson": {"query": "scope"}}
                      ]
                    }
                  ]
                }
                """;

        CollaborationPlanValidationResult result = validator.validate(planJson);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getErrors()).contains("stage toolCalls requires createTask");
    }

    @Test
    void validateShouldRejectToolCallsWithoutAgentVersionId() {
        when(toolConfigMapper.selectOne(any(Wrapper.class))).thenReturn(tool(22L, "controlled.http.project-query"));
        String planJson = """
                {
                  "goal": "tool chain validation",
                  "maxDepth": 1,
                  "maxThreads": 1,
                  "stages": [
                    {
                      "stageCode": "business_sync",
                      "agentId": 12,
                      "createTask": true,
                      "toolCalls": [
                        {"toolId": 22, "toolCode": "controlled.http.project-query", "callPayloadJson": {"query": "scope"}}
                      ]
                    }
                  ]
                }
                """;

        CollaborationPlanValidationResult result = validator.validate(planJson);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getErrors()).contains("stage toolCalls requires agentVersionId");
    }

    @Test
    void validateShouldPassWhenStageToolCallIsInAgentVersionScope() {
        when(agentVersionMapper.selectOne(any(Wrapper.class))).thenReturn(agentVersion("[{\"toolId\":22,\"toolCode\":\"controlled.http.project-query\"}]"));
        when(toolConfigMapper.selectOne(any(Wrapper.class))).thenReturn(tool(22L, "controlled.http.project-query"));
        String planJson = """
                {
                  "goal": "business tool chain",
                  "maxDepth": 1,
                  "maxThreads": 1,
                  "stages": [
                    {
                      "stageCode": "business_query",
                      "agentId": 12,
                      "agentVersionId": 13,
                      "createTask": true,
                      "toolCalls": [
                        {"toolId": 22, "toolCode": "controlled.http.project-query", "callPayloadJson": {"query": "scope"}}
                      ]
                    }
                  ]
                }
                """;

        CollaborationPlanValidationResult result = validator.validate(planJson);

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void validateShouldRejectStageToolCallOutsideAgentVersionScope() {
        when(agentVersionMapper.selectOne(any(Wrapper.class))).thenReturn(agentVersion("[{\"toolId\":99,\"toolCode\":\"controlled.cli.other\"}]"));
        when(toolConfigMapper.selectOne(any(Wrapper.class))).thenReturn(tool(22L, "controlled.http.project-query"));
        String planJson = """
                {
                  "goal": "business tool chain",
                  "maxDepth": 1,
                  "maxThreads": 1,
                  "stages": [
                    {
                      "stageCode": "business_query",
                      "agentId": 12,
                      "agentVersionId": 13,
                      "createTask": true,
                      "toolCalls": [
                        {"toolId": 22, "toolCode": "controlled.http.project-query", "callPayloadJson": {"query": "scope"}}
                      ]
                    }
                  ]
                }
                """;

        CollaborationPlanValidationResult result = validator.validate(planJson);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getErrors()).contains("stage toolCalls[0] tool not in agent version scope: controlled.http.project-query");
    }

    @Test
    void validateShouldRejectAgentVersionFromDifferentStageAgent() {
        when(agentVersionMapper.selectOne(any(Wrapper.class))).thenReturn(agentVersion("[{\"toolId\":22}]"));
        String planJson = """
                {
                  "goal": "business tool chain",
                  "maxDepth": 1,
                  "maxThreads": 1,
                  "stages": [
                    {
                      "stageCode": "business_query",
                      "agentId": 99,
                      "agentVersionId": 13,
                      "createTask": true,
                      "toolCalls": [
                        {"toolId": 22, "callPayloadJson": {"query": "scope"}}
                      ]
                    }
                  ]
                }
                """;

        CollaborationPlanValidationResult result = validator.validate(planJson);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getErrors()).contains("Agent version does not belong to stage agent: 13");
    }

    @Test
    void validateShouldRejectMissingRegisteredToolCall() {
        when(toolConfigMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(agentVersionMapper.selectOne(any(Wrapper.class))).thenReturn(agentVersion("[{\"toolId\":22,\"toolCode\":\"controlled.http.project-query\"}]"));
        String planJson = toolCallPlanJson("""
                {"toolId": 22, "toolCode": "controlled.http.project-query", "callPayloadJson": {"query": "scope"}}
                """);

        CollaborationPlanValidationResult result = validator.validate(planJson);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getErrors()).contains("Tool not found: 22");
    }

    @Test
    void validateShouldRejectInactiveRegisteredToolCall() {
        when(toolConfigMapper.selectOne(any(Wrapper.class))).thenReturn(tool(22L, "controlled.http.project-query", "inactive"));
        when(agentVersionMapper.selectOne(any(Wrapper.class))).thenReturn(agentVersion("[{\"toolId\":22,\"toolCode\":\"controlled.http.project-query\"}]"));
        String planJson = toolCallPlanJson("""
                {"toolId": 22, "toolCode": "controlled.http.project-query", "callPayloadJson": {"query": "scope"}}
                """);

        CollaborationPlanValidationResult result = validator.validate(planJson);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getErrors()).contains("stage toolCalls[0] tool is not active: controlled.http.project-query");
    }

    @Test
    void validateShouldRejectToolCodeMismatch() {
        when(toolConfigMapper.selectOne(any(Wrapper.class))).thenReturn(tool(22L, "controlled.http.project-query"));
        when(agentVersionMapper.selectOne(any(Wrapper.class))).thenReturn(agentVersion("[{\"toolId\":22,\"toolCode\":\"controlled.http.project-query\"}]"));
        String planJson = toolCallPlanJson("""
                {"toolId": 22, "toolCode": "controlled.http.other", "callPayloadJson": {"query": "scope"}}
                """);

        CollaborationPlanValidationResult result = validator.validate(planJson);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getErrors()).contains("stage toolCalls[0] toolCode mismatch: controlled.http.other");
    }

    private AgentVersion agentVersion(String toolScopeJson) {
        AgentVersion version = new AgentVersion();
        version.setId(13L);
        version.setTenantId(100L);
        version.setAgentId(12L);
        version.setToolScopeJson(toolScopeJson);
        return version;
    }

    private ToolConfig tool(Long toolId, String toolCode) {
        return tool(toolId, toolCode, "active");
    }

    private ToolConfig tool(Long toolId, String toolCode, String status) {
        ToolConfig tool = new ToolConfig();
        tool.setId(toolId);
        tool.setTenantId(100L);
        tool.setToolCode(toolCode);
        tool.setStatus(status);
        return tool;
    }

    private String toolCallPlanJson(String toolCallJson) {
        return """
                {
                  "goal": "business tool chain",
                  "maxDepth": 1,
                  "maxThreads": 1,
                  "stages": [
                    {
                      "stageCode": "business_query",
                      "agentId": 12,
                      "agentVersionId": 13,
                      "createTask": true,
                      "toolCalls": [
                        %s
                      ]
                    }
                  ]
                }
                """.formatted(toolCallJson);
    }
}
