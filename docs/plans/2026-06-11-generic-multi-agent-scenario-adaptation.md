# 通用多 Agent 场景适配 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 补齐通用多 Agent 协作运行时能力，并以“需求文档到开发交付”作为一个可配置场景模板验证平台能力，避免把平台定制成软件研发 Agent 平台。

**Architecture:** 在现有 Agent、Task、Runtime、Artifact、Policy、Approval、Tool、Knowledge 基础上新增通用 Collaboration Runtime。核心抽象是 CollaborationSession、AgentThread、CollaborationPlan、AgentHandoff、QualityGate、CollaborationTemplate、ArtifactType、CapabilityProfile；软件研发全链路只作为 template/seed 数据和演示流程，不写死在底层模型。

**Tech Stack:** Java 17 + Spring Boot 3.x + MyBatis-Plus 3.5.x + PostgreSQL + React + TypeScript + Vite + Ant Design + TanStack Query + Zustand + SSE。

---

## 0. 设计边界与当前缺口

### 0.1 必须保持的边界

本计划只为打通“需求文档到开发交付”场景补齐通用架构能力，不能把平台写成研发专用系统。

底层模型必须保持通用：

- `AgentRole`：通用角色定义，不写死产品经理、前端、后端、测试。
- `CapabilityProfile`：通用能力画像，描述可用工具、知识、交付物、协作能力和风险边界。
- `CollaborationStrategy`：通用协作策略，编排器只是其中一种实现。
- `CollaborationTemplate`：通用场景模板，软件研发全链路只是一个模板实例。
- `ArtifactType`：通用交付物类型，研发、项目管理、销售、财务、人事等场景共用。
- `QualityGate`：通用质量门禁，需求确认、方案确认、测试通过只是研发场景下的门禁实例。

### 0.2 当前已有能力

当前代码和 SQL 已具备以下底座：

- `agent` / `agent_version`：Agent 与版本配置。
- `agent_tool_binding` / `agent_knowledge_binding`：Agent 工具与知识范围。
- `task` / `task_run` / `task_step` / `task_event` / `task_artifact`：任务、运行、步骤、事件和产物账本。
- `runtime_gateway` / `JavaInProcessRuntimeGateway`：Runtime 执行抽象与当前 Java 内置 Runtime。
- `policy_rule` / `approval_request` / `approval_record`：策略和审批。
- `tool_config` / `tool_call_log`：工具配置与执行日志。
- `knowledge_base` / `knowledge_document` / `knowledge_chunk`：知识入库、切块、检索。
- `model_provider` / `model_config` / `model_call_log`：模型网关与模型调用日志。

### 0.3 为适配该场景仍缺的通用能力

为打通“需求文档到开发交付”这个场景，架构缺口不是研发角色本身，而是以下通用能力：

1. **通用角色定义层**：缺 `AgentRole` / `CapabilityProfile`，导致角色还只能靠 `agent_type` 或 prompt 表达。
2. **通用协作会话层**：缺 `CollaborationSession`，无法表达一次多 Agent 协作任务。
3. **Agent 独立线程层**：缺 `AgentThread`，无法隔离每个角色 Agent 的执行上下文、输入和输出。
4. **自编排计划层**：缺 `CollaborationPlan`，无法支持 Agent 先生成计划、平台校验后执行。
5. **结构化交接层**：缺 `AgentHandoff` / `HandoffArtifact`，Agent 间交接仍只能靠自然语言或任务事件。
6. **通用质量门禁层**：缺独立 `QualityGate`，现有审批偏风险/权限，不等同于流程质量门禁。
7. **通用场景模板层**：缺 `CollaborationTemplate`，无法把“软件研发全链路”作为模板实例配置。
8. **Artifact 类型注册层**：现有 `task_artifact.artifact_type` 是字符串，缺 schema、renderer、validator 管理。
9. **协作策略执行层**：缺 `CollaborationStrategy` 接口和默认策略实现。
10. **前端协作可视化层**：当前 Workbench 展示单任务运行，缺协作会话、Agent 线程、交接物、门禁视图。

---

## 1. 推荐开发阶段

### 阶段 1：通用协作元模型

目标：先补平台通用抽象，不实现研发定制逻辑。

交付：

- `agent_role`
- `capability_profile`
- `artifact_type`
- `collaboration_template`
- `collaboration_session`
- `agent_thread`
- `collaboration_plan`
- `agent_handoff`
- `quality_gate`

验收：

- SQL schema 能表达任意领域的协作模板。
- 不能出现写死研发角色的枚举或字段。
- 软件研发只以 seed/template JSON 存在。

### 阶段 2：通用协作服务与策略接口

目标：让平台能创建协作会话、校验计划、创建 AgentThread、记录交接和门禁。

交付：

- `CollaborationSessionService`
- `AgentThreadService`
- `CollaborationPlanService`
- `AgentHandoffService`
- `QualityGateService`
- `CollaborationStrategy`
- `OrchestratedTeamStrategy`
- `SelfOrchestratedStrategy` 骨架

验收：

- 可通过 API 创建一个协作会话。
- 可提交一份 `CollaborationPlan` 并完成平台级校验。
- 可根据 plan 创建多个 `AgentThread`。
- 可记录 Agent 之间的交接物和门禁状态。

### 阶段 3：软件研发全链路作为场景模板

目标：用通用模板机制适配“需求文档到开发交付”场景。

交付：

- `software_requirement_to_delivery` template seed。
- role 实例：`software_product_manager`、`software_architect`、`software_backend_developer`、`software_frontend_developer`、`software_tester`、`software_reviewer`。
- artifact type 实例：`requirement_analysis`、`technical_design`、`api_contract`、`implementation_summary`、`test_report`、`review_report`、`delivery_summary`。
- quality gate 实例：`requirement_confirmed`、`design_confirmed`、`implementation_done`、`tests_passed`、`review_passed`、`delivery_confirmed`。

验收：

- 研发角色都是数据配置，不是代码枚举。
- 替换 template 后同一套服务可支持项目管理、销售、财务等其它场景。

### 阶段 4：场景最小运行闭环

目标：不先做真实代码修改沙箱，先跑通协作链路和交付物闭环。

MVP 链路：

```text
需求文档输入
  ↓
创建 CollaborationSession
  ↓
生成或选择 CollaborationPlan
  ↓
PM Thread 输出 requirement_analysis
  ↓
Architect Thread 输出 technical_design / api_contract
  ↓
Backend/Frontend/Test/Review Thread 输出对应交付物草案
  ↓
QualityGate 检查
  ↓
Delivery summary
```

验收：

- 所有阶段通过 `Artifact` 和 `Handoff` 交接。
- 所有关键状态进入 `TaskEvent` 或协作事件。
- 失败或缺信息能暂停，不强行编造。

### 阶段 5：代码工作区 Runtime 后续增强

目标：只有在通用协作闭环稳定后，再接入代码工作区，支撑真实研发执行。

注意：这不是本轮最小闭环的前置条件。真实代码执行属于后续增强，仍要作为通用 controlled workspace 能力建设，不写死研发。

---

## 2. 实施任务清单

### Task 1: 扩展 SQL 通用协作元模型

**Files:**
- Modify: `docs/sql/20260604_企业数字员工平台.sql`
- Test: `backend/src/test/java/com/xiaoai/agent/sql/SqlSchemaConventionTest.java`

**Step 1: 新增 SQL 表结构**

在现有 `task_artifact` 后、Runtime 注册前新增通用协作表：

```sql
-- ============================================================
-- 4. 通用多 Agent 协作
-- ============================================================

CREATE TABLE agent_role (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(128) NOT NULL,
    domain_code VARCHAR(64),
    description TEXT,
    responsibility_text TEXT,
    input_artifact_types JSONB NOT NULL DEFAULT '[]'::jsonb,
    output_artifact_types JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_agent_role_code UNIQUE (tenant_id, role_code)
);

CREATE TABLE capability_profile (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    profile_code VARCHAR(64) NOT NULL,
    profile_name VARCHAR(128) NOT NULL,
    role_id BIGINT,
    capability_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    tool_scope_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    knowledge_scope_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    policy_scope_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    cost_limit_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_capability_profile_code UNIQUE (tenant_id, profile_code)
);

CREATE TABLE artifact_type (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    type_code VARCHAR(64) NOT NULL,
    type_name VARCHAR(128) NOT NULL,
    domain_code VARCHAR(64),
    schema_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    renderer_type VARCHAR(64) NOT NULL DEFAULT 'markdown',
    validator_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_artifact_type_code UNIQUE (tenant_id, type_code)
);

CREATE TABLE collaboration_template (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    template_name VARCHAR(128) NOT NULL,
    domain_code VARCHAR(64),
    strategy_type VARCHAR(64) NOT NULL DEFAULT 'orchestrated_team',
    template_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_collaboration_template_code UNIQUE (tenant_id, template_code)
);

CREATE TABLE collaboration_session (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    session_code VARCHAR(64) NOT NULL,
    template_id BIGINT,
    root_task_id BIGINT,
    strategy_type VARCHAR(64) NOT NULL,
    goal_text TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'planning',
    current_stage_code VARCHAR(64),
    context_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_collaboration_session_code UNIQUE (tenant_id, session_code)
);

CREATE TABLE agent_thread (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    parent_thread_id BIGINT,
    task_id BIGINT,
    agent_id BIGINT NOT NULL,
    agent_version_id BIGINT,
    role_id BIGINT,
    thread_code VARCHAR(64) NOT NULL,
    thread_name VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    input_artifact_id BIGINT,
    output_artifact_id BIGINT,
    context_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_agent_thread_code UNIQUE (tenant_id, session_id, thread_code)
);

CREATE TABLE collaboration_plan (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    generated_by_thread_id BIGINT,
    plan_status VARCHAR(32) NOT NULL DEFAULT 'draft',
    validation_status VARCHAR(32) NOT NULL DEFAULT 'pending',
    plan_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    validation_result_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE agent_handoff (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    from_thread_id BIGINT,
    to_thread_id BIGINT,
    artifact_id BIGINT NOT NULL,
    handoff_type VARCHAR(64) NOT NULL DEFAULT 'artifact',
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    message_text TEXT,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE quality_gate (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    gate_code VARCHAR(64) NOT NULL,
    gate_name VARCHAR(128) NOT NULL,
    gate_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    required BOOLEAN NOT NULL DEFAULT TRUE,
    rule_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    result_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    fail_reason TEXT,
    passed_at TIMESTAMPTZ,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_quality_gate_code UNIQUE (tenant_id, session_id, gate_code)
);
```

**Step 2: 增加索引和注释**

补充索引：

```sql
CREATE INDEX idx_agent_role_domain ON agent_role (tenant_id, domain_code, status);
CREATE INDEX idx_capability_profile_role ON capability_profile (tenant_id, role_id, status);
CREATE INDEX idx_artifact_type_domain ON artifact_type (tenant_id, domain_code, status);
CREATE INDEX idx_collaboration_template_domain ON collaboration_template (tenant_id, domain_code, status);
CREATE INDEX idx_collaboration_session_status ON collaboration_session (tenant_id, status, created_at DESC);
CREATE INDEX idx_agent_thread_session ON agent_thread (tenant_id, session_id, status);
CREATE INDEX idx_collaboration_plan_session ON collaboration_plan (tenant_id, session_id, created_at DESC);
CREATE INDEX idx_agent_handoff_session ON agent_handoff (tenant_id, session_id, created_at DESC);
CREATE INDEX idx_quality_gate_session ON quality_gate (tenant_id, session_id, status);
```

**Step 3: Run SQL convention test**

Run:

```bash
cd backend && E:/Work/maven/apache-maven-3.9.9/bin/mvn.cmd -Dtest=SqlSchemaConventionTest test
```

Expected: PASS. All new tables must include `id, bid, tenant_id, created_by, created_at, updated_by, updated_at, deleted`.

---

### Task 2: 生成通用协作实体、Mapper、Service 骨架

**Files:**
- Create: `backend/src/main/java/com/xiaoai/agent/collaboration/entity/AgentRole.java`
- Create: `backend/src/main/java/com/xiaoai/agent/collaboration/entity/CapabilityProfile.java`
- Create: `backend/src/main/java/com/xiaoai/agent/collaboration/entity/ArtifactType.java`
- Create: `backend/src/main/java/com/xiaoai/agent/collaboration/entity/CollaborationTemplate.java`
- Create: `backend/src/main/java/com/xiaoai/agent/collaboration/entity/CollaborationSession.java`
- Create: `backend/src/main/java/com/xiaoai/agent/collaboration/entity/AgentThread.java`
- Create: `backend/src/main/java/com/xiaoai/agent/collaboration/entity/CollaborationPlan.java`
- Create: `backend/src/main/java/com/xiaoai/agent/collaboration/entity/AgentHandoff.java`
- Create: `backend/src/main/java/com/xiaoai/agent/collaboration/entity/QualityGate.java`
- Create corresponding Mapper / Service / ServiceImpl files under `backend/src/main/java/com/xiaoai/agent/collaboration/`
- Test: `backend/src/test/java/com/xiaoai/agent/collaboration/service/impl/*ServiceImplTest.java`

**Step 1: Use project code generation flow**

按照项目规范，实施时必须先使用 `/proj-gen` 或项目既有生成方式生成 Entity → Mapper → Service → Controller 骨架，不手写大段重复 CRUD。

**Step 2: Ensure entity fields match SQL**

Each entity extends `TenantEntity`, except future global templates if needed. For MVP keep tenant-scoped.

Example entity pattern:

```java
@Getter
@Setter
@TableName("collaboration_session")
public class CollaborationSession extends TenantEntity {
    private String sessionCode;
    private Long templateId;
    private Long rootTaskId;
    private String strategyType;
    private String goalText;
    private String status;
    private String currentStageCode;
    private String contextJson;
}
```

**Step 3: Add tenant-isolated get methods**

Each ServiceImpl must provide a tenant-safe detail method, e.g.:

```java
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
```

**Step 4: Test tenant isolation**

For each service, write at least one test proving cross-tenant ID lookup returns NOT_FOUND.

Run:

```bash
cd backend && E:/Work/maven/apache-maven-3.9.9/bin/mvn.cmd -Dtest=*Collaboration*Test test
```

Expected: PASS.

---

### Task 3: 实现 CollaborationSession 创建与查询 API

**Files:**
- Create: `backend/src/main/java/com/xiaoai/agent/collaboration/model/CreateCollaborationSessionCommand.java`
- Create: `backend/src/main/java/com/xiaoai/agent/collaboration/model/CollaborationSessionResponse.java`
- Create: `backend/src/main/java/com/xiaoai/agent/collaboration/model/CollaborationSessionPageQuery.java`
- Create: `backend/src/main/java/com/xiaoai/agent/collaboration/controller/CollaborationSessionController.java`
- Modify: `backend/src/main/java/com/xiaoai/agent/collaboration/service/CollaborationSessionService.java`
- Modify: `backend/src/main/java/com/xiaoai/agent/collaboration/service/impl/CollaborationSessionServiceImpl.java`
- Test: `backend/src/test/java/com/xiaoai/agent/collaboration/service/impl/CollaborationSessionServiceImplTest.java`
- Test: `backend/src/test/java/com/xiaoai/agent/collaboration/controller/CollaborationSessionControllerTest.java`

**Step 1: Write failing service test**

Test should verify:

- requires tenantId and userId;
- creates `sessionCode`;
- default status is `planning`;
- strategy type is accepted as a generic string;
- no software-development-specific field is required.

**Step 2: Implement command**

```java
@Getter
@Setter
public class CreateCollaborationSessionCommand {
    private Long templateId;
    private Long rootTaskId;
    private String strategyType;
    private String goalText;
    private String contextJson;
}
```

**Step 3: Implement service method**

```java
CollaborationSessionResponse createSession(CreateCollaborationSessionCommand command);
CollaborationSession getSession(Long sessionId);
PageResponse<CollaborationSession> pageSessions(CollaborationSessionPageQuery query);
```

**Step 4: Implement controller**

Endpoints:

```text
POST /api/v1/collaboration-sessions
GET  /api/v1/collaboration-sessions/{id}
GET  /api/v1/collaboration-sessions
```

**Step 5: Run tests**

```bash
cd backend && E:/Work/maven/apache-maven-3.9.9/bin/mvn.cmd -Dtest=CollaborationSessionServiceImplTest,CollaborationSessionControllerTest test
```

Expected: PASS.

---

### Task 4: 实现 CollaborationPlan 校验能力

**Files:**
- Create: `backend/src/main/java/com/xiaoai/agent/collaboration/model/SubmitCollaborationPlanCommand.java`
- Create: `backend/src/main/java/com/xiaoai/agent/collaboration/model/CollaborationPlanValidationResult.java`
- Create: `backend/src/main/java/com/xiaoai/agent/collaboration/service/CollaborationPlanValidator.java`
- Create: `backend/src/main/java/com/xiaoai/agent/collaboration/service/impl/DefaultCollaborationPlanValidator.java`
- Modify: `backend/src/main/java/com/xiaoai/agent/collaboration/service/CollaborationPlanService.java`
- Modify: `backend/src/main/java/com/xiaoai/agent/collaboration/service/impl/CollaborationPlanServiceImpl.java`
- Test: `backend/src/test/java/com/xiaoai/agent/collaboration/service/impl/DefaultCollaborationPlanValidatorTest.java`

**Step 1: Define generic plan shape**

Plan JSON must stay generic:

```json
{
  "goal": "根据需求文档完成一次多角色协作交付",
  "maxDepth": 1,
  "maxThreads": 6,
  "stages": [
    {
      "stageCode": "requirement_analysis",
      "roleCode": "software_product_manager",
      "inputArtifactTypes": ["requirement_document"],
      "outputArtifactTypes": ["requirement_analysis"],
      "requiresGate": "requirement_confirmed"
    }
  ]
}
```

The validator must not require these exact stage/role names. It only validates generic constraints.

**Step 2: Validator rules**

Implement checks:

- `goal` exists;
- `stages` is non-empty;
- `maxDepth <= 1` for MVP;
- `maxThreads <= configured max`;
- every `roleCode` exists in `agent_role` for current tenant;
- every input/output artifact type exists in `artifact_type`;
- every gate code exists in template or can be created;
- no stage uses forbidden strategy features.

**Step 3: Persist validation result**

When submitted:

- save `plan_json`;
- set `validation_status = passed / failed`;
- write `validation_result_json`;
- record `COLLABORATION_PLAN_SUBMITTED` / `COLLABORATION_PLAN_VALIDATED` event.

**Step 4: Run tests**

```bash
cd backend && E:/Work/maven/apache-maven-3.9.9/bin/mvn.cmd -Dtest=DefaultCollaborationPlanValidatorTest,CollaborationPlanServiceImplTest test
```

Expected: PASS.

---

### Task 5: 实现 AgentThread 创建与 Task 绑定

**Files:**
- Create: `backend/src/main/java/com/xiaoai/agent/collaboration/model/CreateAgentThreadCommand.java`
- Create: `backend/src/main/java/com/xiaoai/agent/collaboration/model/AgentThreadResponse.java`
- Modify: `backend/src/main/java/com/xiaoai/agent/collaboration/service/AgentThreadService.java`
- Modify: `backend/src/main/java/com/xiaoai/agent/collaboration/service/impl/AgentThreadServiceImpl.java`
- Test: `backend/src/test/java/com/xiaoai/agent/collaboration/service/impl/AgentThreadServiceImplTest.java`

**Step 1: Write failing tests**

Cover:

- creates thread under current tenant session;
- rejects missing session;
- rejects role from another tenant;
- can optionally create a `task` for the thread;
- child thread depth over 1 is rejected for MVP.

**Step 2: Implement command**

```java
@Getter
@Setter
public class CreateAgentThreadCommand {
    private Long sessionId;
    private Long parentThreadId;
    private Long agentId;
    private Long agentVersionId;
    private Long roleId;
    private String threadName;
    private Long inputArtifactId;
    private boolean createTask;
    private String inputText;
}
```

**Step 3: Implement service**

If `createTask = true`, call existing `TaskService.createTask` with:

- agentId;
- agentVersionId;
- title = threadName;
- inputText from command or input artifact summary;
- channelType = `collaboration`;
- taskType should remain generic, e.g. `agent_thread_task`.

**Step 4: Run tests**

```bash
cd backend && E:/Work/maven/apache-maven-3.9.9/bin/mvn.cmd -Dtest=AgentThreadServiceImplTest test
```

Expected: PASS.

---

### Task 6: 实现 AgentHandoff 与通用交付物类型校验

**Files:**
- Create: `backend/src/main/java/com/xiaoai/agent/collaboration/model/CreateAgentHandoffCommand.java`
- Modify: `backend/src/main/java/com/xiaoai/agent/collaboration/service/AgentHandoffService.java`
- Modify: `backend/src/main/java/com/xiaoai/agent/collaboration/service/impl/AgentHandoffServiceImpl.java`
- Modify: `backend/src/main/java/com/xiaoai/agent/task/service/TaskArtifactService.java`
- Modify: `backend/src/main/java/com/xiaoai/agent/task/service/impl/TaskArtifactServiceImpl.java`
- Test: `backend/src/test/java/com/xiaoai/agent/collaboration/service/impl/AgentHandoffServiceImplTest.java`

**Step 1: Write failing tests**

Cover:

- handoff must reference same-tenant session;
- artifact type must exist in `artifact_type`;
- from/to thread must belong to same session;
- handoff can be marked accepted/rejected;
- message-only handoff cannot replace required artifact handoff.

**Step 2: Implement create handoff**

```java
@Getter
@Setter
public class CreateAgentHandoffCommand {
    private Long sessionId;
    private Long fromThreadId;
    private Long toThreadId;
    private Long artifactId;
    private String handoffType;
    private String messageText;
}
```

**Step 3: Record events**

Record collaboration event through `TaskEventRecordService` if root task exists, otherwise through collaboration event method introduced in Task 8.

Event type examples:

```text
AGENT_HANDOFF_CREATED
AGENT_HANDOFF_ACCEPTED
AGENT_HANDOFF_REJECTED
```

**Step 4: Run tests**

```bash
cd backend && E:/Work/maven/apache-maven-3.9.9/bin/mvn.cmd -Dtest=AgentHandoffServiceImplTest test
```

Expected: PASS.

---

### Task 7: 实现 QualityGate 通用门禁

**Files:**
- Create: `backend/src/main/java/com/xiaoai/agent/collaboration/model/CreateQualityGateCommand.java`
- Create: `backend/src/main/java/com/xiaoai/agent/collaboration/model/UpdateQualityGateCommand.java`
- Modify: `backend/src/main/java/com/xiaoai/agent/collaboration/service/QualityGateService.java`
- Modify: `backend/src/main/java/com/xiaoai/agent/collaboration/service/impl/QualityGateServiceImpl.java`
- Test: `backend/src/test/java/com/xiaoai/agent/collaboration/service/impl/QualityGateServiceImplTest.java`

**Step 1: Write failing tests**

Cover:

- creates pending gate;
- marks gate passed with result JSON;
- marks gate failed with fail reason;
- required failed gate blocks session progression;
- non-required failed gate does not block progression.

**Step 2: Implement status transitions**

Allowed statuses:

```text
pending
passed
failed
skipped
```

No development-specific gate codes in code.

**Step 3: Integrate with session status**

If all required gates for current stage pass, session can move to next stage. For MVP expose method only; strategy calls it later.

**Step 4: Run tests**

```bash
cd backend && E:/Work/maven/apache-maven-3.9.9/bin/mvn.cmd -Dtest=QualityGateServiceImplTest test
```

Expected: PASS.

---

### Task 8: 实现 CollaborationStrategy 接口和固定编排策略骨架

**Files:**
- Create: `backend/src/main/java/com/xiaoai/agent/collaboration/strategy/CollaborationStrategy.java`
- Create: `backend/src/main/java/com/xiaoai/agent/collaboration/strategy/CollaborationStrategyContext.java`
- Create: `backend/src/main/java/com/xiaoai/agent/collaboration/strategy/CollaborationStrategyResult.java`
- Create: `backend/src/main/java/com/xiaoai/agent/collaboration/strategy/impl/OrchestratedTeamStrategy.java`
- Create: `backend/src/main/java/com/xiaoai/agent/collaboration/strategy/impl/SelfOrchestratedStrategy.java`
- Create: `backend/src/main/java/com/xiaoai/agent/collaboration/strategy/CollaborationStrategyRegistry.java`
- Test: `backend/src/test/java/com/xiaoai/agent/collaboration/strategy/CollaborationStrategyRegistryTest.java`
- Test: `backend/src/test/java/com/xiaoai/agent/collaboration/strategy/impl/OrchestratedTeamStrategyTest.java`

**Step 1: Define interface**

```java
public interface CollaborationStrategy {
    String strategyType();

    CollaborationStrategyResult start(CollaborationStrategyContext context);

    CollaborationStrategyResult continueAfterGate(CollaborationStrategyContext context);
}
```

**Step 2: Implement registry**

Registry maps strategy type to Spring bean. Missing type throws `BusinessException`.

**Step 3: Implement OrchestratedTeamStrategy minimal behavior**

MVP behavior:

- read validated `CollaborationPlan`;
- create first pending AgentThread;
- create required QualityGates;
- record strategy events;
- do not yet execute real code workspace.

**Step 4: Implement SelfOrchestratedStrategy as wrapper**

MVP behavior:

- requires a submitted and validated `CollaborationPlan`;
- delegates to same generic execution path;
- does not let Agent bypass platform validation.

**Step 5: Run tests**

```bash
cd backend && E:/Work/maven/apache-maven-3.9.9/bin/mvn.cmd -Dtest=CollaborationStrategyRegistryTest,OrchestratedTeamStrategyTest test
```

Expected: PASS.

---

### Task 9: 增加协作会话 API 编排入口

**Files:**
- Create: `backend/src/main/java/com/xiaoai/agent/collaboration/controller/CollaborationPlanController.java`
- Create: `backend/src/main/java/com/xiaoai/agent/collaboration/controller/AgentThreadController.java`
- Create: `backend/src/main/java/com/xiaoai/agent/collaboration/controller/AgentHandoffController.java`
- Create: `backend/src/main/java/com/xiaoai/agent/collaboration/controller/QualityGateController.java`
- Test: `backend/src/test/java/com/xiaoai/agent/collaboration/controller/*ControllerTest.java`

**Step 1: Add endpoints**

```text
POST /api/v1/collaboration-sessions/{sessionId}/plans
POST /api/v1/collaboration-sessions/{sessionId}/plans/{planId}/validate
POST /api/v1/collaboration-sessions/{sessionId}/start
GET  /api/v1/collaboration-sessions/{sessionId}/threads
POST /api/v1/collaboration-sessions/{sessionId}/threads
GET  /api/v1/collaboration-sessions/{sessionId}/handoffs
POST /api/v1/collaboration-sessions/{sessionId}/handoffs
GET  /api/v1/collaboration-sessions/{sessionId}/gates
POST /api/v1/collaboration-sessions/{sessionId}/gates/{gateId}/pass
POST /api/v1/collaboration-sessions/{sessionId}/gates/{gateId}/fail
```

**Step 2: Keep API generic**

Do not add endpoints like `/software-development/*` in this phase.

**Step 3: Run controller tests**

```bash
cd backend && E:/Work/maven/apache-maven-3.9.9/bin/mvn.cmd -Dtest=*Collaboration*ControllerTest test
```

Expected: PASS.

---

### Task 10: 添加软件研发全链路场景模板 Seed

**Files:**
- Create: `docs/sql/20260611_通用多Agent协作_场景种子.sql`
- Test: `backend/src/test/java/com/xiaoai/agent/sql/CollaborationTemplateSeedTest.java`

**Step 1: Create seed SQL**

Seed must use generic tables:

- insert `agent_role` rows with `domain_code = 'software_development'`;
- insert `artifact_type` rows with `domain_code = 'software_development'`;
- insert `collaboration_template` row with `template_code = 'software_requirement_to_delivery'`;
- template JSON defines stages, gates, and role/artifact requirements.

**Step 2: Ensure no schema customization**

No new table should be created specifically for software development.

**Step 3: Run seed test**

Test should parse SQL and assert:

- role codes live in data, not Java enums;
- template code is data;
- artifact schemas are JSONB data;
- no table name starts with `software_` except seed file name.

Run:

```bash
cd backend && E:/Work/maven/apache-maven-3.9.9/bin/mvn.cmd -Dtest=CollaborationTemplateSeedTest test
```

Expected: PASS.

---

### Task 11: 前端增加协作会话类型和服务

**Files:**
- Create: `frontend/src/types/collaboration.ts`
- Create: `frontend/src/services/collaboration-api.ts`
- Test: `frontend/src/services/collaboration-api.test.ts`

**Step 1: Define generic types**

```ts
export interface CollaborationSession {
  id: number;
  sessionCode: string;
  templateId?: number;
  rootTaskId?: number;
  strategyType: string;
  goalText: string;
  status: string;
  currentStageCode?: string;
  contextJson?: string;
}

export interface AgentThread {
  id: number;
  sessionId: number;
  parentThreadId?: number;
  taskId?: number;
  agentId: number;
  roleId?: number;
  threadCode: string;
  threadName?: string;
  status: string;
  inputArtifactId?: number;
  outputArtifactId?: number;
}

export interface QualityGate {
  id: number;
  sessionId: number;
  gateCode: string;
  gateName: string;
  gateType: string;
  status: string;
  required: boolean;
}
```

**Step 2: Implement API service**

Functions:

```ts
createCollaborationSession
getCollaborationSession
submitCollaborationPlan
startCollaborationSession
listAgentThreads
listQualityGates
passQualityGate
failQualityGate
```

**Step 3: Run tests**

```bash
cd frontend && npm test -- collaboration-api.test.ts
```

Expected: PASS.

---

### Task 12: 前端增加通用协作视图，不做研发定制页面

**Files:**
- Create: `frontend/src/pages/collaboration/CollaborationSessionPage.tsx`
- Create: `frontend/src/features/collaboration/components/CollaborationTimeline.tsx`
- Create: `frontend/src/features/collaboration/components/AgentThreadList.tsx`
- Create: `frontend/src/features/collaboration/components/HandoffList.tsx`
- Create: `frontend/src/features/collaboration/components/QualityGatePanel.tsx`
- Modify: `frontend/src/app/router.tsx`
- Test: `frontend/src/pages/collaboration/CollaborationSessionPage.test.tsx`

**Step 1: Build generic UI**

Page sections:

```text
协作目标
协作策略
Agent 线程
交接物
质量门禁
相关任务 / 事件 / 交付物
```

Do not label the page as software-development specific.

**Step 2: Add route**

```text
/collaboration/:sessionId
```

**Step 3: Add test**

Test renders:

- session goal;
- strategy type;
- agent thread names;
- gate statuses;
- handoff artifact names.

Run:

```bash
cd frontend && npm test -- CollaborationSessionPage.test.tsx
```

Expected: PASS.

---

### Task 13: Workbench 接入场景模板启动入口

**Files:**
- Modify: `frontend/src/pages/agent-workbench/AgentWorkbenchPage.tsx`
- Modify: `frontend/src/features/demo/DemoScenarioPanel.tsx`
- Modify: `frontend/src/mocks/demo-scenarios.ts`
- Test: `frontend/src/pages/agent-workbench/AgentWorkbenchPage.test.tsx`

**Step 1: Add generic scenario launch action**

Add a demo scenario:

```ts
{
  title: '需求文档到协作交付',
  templateCode: 'software_requirement_to_delivery',
  prompt: '请基于以下需求文档启动一次多 Agent 协作交付...'
}
```

This is allowed because it is seed/demo scenario data, not hardcoded platform logic.

**Step 2: On submit create CollaborationSession**

For scenario with `templateCode`, call collaboration API instead of direct task API.

**Step 3: Route to generic collaboration page**

After session creation, show link:

```text
查看协作会话
```

Route: `/collaboration/:sessionId`.

**Step 4: Run tests**

```bash
cd frontend && npm test -- AgentWorkbenchPage.test.tsx
```

Expected: PASS.

---

### Task 14: 补充通用协作集成测试

**Files:**
- Create: `backend/src/test/java/com/xiaoai/agent/collaboration/integration/CollaborationSessionFlowTest.java`

**Step 1: Write integration test**

Test flow:

1. Create generic roles and artifact types.
2. Create generic template.
3. Create collaboration session.
4. Submit plan.
5. Validate plan.
6. Start strategy.
7. Create first AgentThread.
8. Create handoff artifact.
9. Mark required quality gate passed.
10. Assert session can advance.

**Step 2: Ensure no software-specific dependency**

Use a generic domain in one test, e.g. `project_management`, proving the same flow is not limited to software development.

**Step 3: Run integration test**

```bash
cd backend && E:/Work/maven/apache-maven-3.9.9/bin/mvn.cmd -Dtest=CollaborationSessionFlowTest test
```

Expected: PASS.

---

### Task 15: 更新文档和恢复点

**Files:**
- Modify: `docs/task/20260604_企业数字员工平台_任务.md`
- Modify: `docs/design/20260604_企业数字员工平台_技术.md` after user confirms design sync
- Modify: `docs/plans/2026-06-10-project-assistant-agent-replan.md`

**Step 1: Update task document**

Add completed tasks as implementation progresses:

```text
- [ ] 补齐通用多 Agent 协作元模型
- [ ] 实现 CollaborationSession / AgentThread / Plan / Handoff / QualityGate 服务
- [ ] 增加 CollaborationStrategy 接口和默认策略
- [ ] 以软件研发全链路作为场景模板 seed 验证通用能力
- [ ] 前端增加通用协作会话视图
```

**Step 2: Update design document only after confirmation**

Design doc must describe generic architecture, not software-development-specific architecture.

**Step 3: Run full checks**

Backend:

```bash
cd backend && E:/Work/maven/apache-maven-3.9.9/bin/mvn.cmd test
```

Frontend:

```bash
cd frontend && npm run typecheck
cd frontend && npm test
cd frontend && npm run build
```

Expected: all pass.

---

## 3. MVP 验收标准

### 3.1 架构验收

- [ ] 底层没有写死产品经理、前端、后端、测试等研发角色枚举。
- [ ] 软件研发全链路通过 `collaboration_template` 和 seed 数据表达。
- [ ] 同一套 `CollaborationSession` / `AgentThread` / `Handoff` / `QualityGate` 可用于非研发领域。
- [ ] Agent 自编排必须先生成 `CollaborationPlan`，平台校验后执行。
- [ ] 编排器是 `CollaborationStrategy` 的一种实现，不是平台唯一中心。

### 3.2 场景验收

- [ ] 用户能从 Workbench 选择“需求文档到协作交付”场景。
- [ ] 系统创建通用 `CollaborationSession`。
- [ ] 系统能提交并校验 CollaborationPlan。
- [ ] 系统能创建多个 AgentThread。
- [ ] 交付物通过 Handoff 传递。
- [ ] QualityGate 能阻止不满足条件的阶段推进。
- [ ] 前端能看到协作会话、Agent 线程、交接物和门禁状态。

### 3.3 非目标

本计划不直接实现：

- 真实代码沙箱写代码。
- Git commit / push / PR。
- 完整前后端自动开发。
- 复杂多层递归 Agent 调度。
- 完整工作流画布。
- 研发专用管理后台。

这些属于后续基于通用协作运行时的增强。

---

## 4. 推荐实施顺序

```text
Task 1  SQL 通用协作元模型
Task 2  Entity / Mapper / Service 骨架
Task 3  CollaborationSession API
Task 4  CollaborationPlan 校验
Task 5  AgentThread 创建与 Task 绑定
Task 6  AgentHandoff 与 ArtifactType 校验
Task 7  QualityGate
Task 8  CollaborationStrategy 接口
Task 9  协作 API 编排入口
Task 10 软件研发全链路场景 seed
Task 11 前端类型和 API
Task 12 通用协作视图
Task 13 Workbench 场景入口
Task 14 集成测试
Task 15 文档同步和全量验证
```

---

## 5. 关键风险

1. **写偏成研发定制**  
   控制方式：研发角色、产物、门禁全部放 seed/template，不进入底层枚举。

2. **协作模型过重**  
   控制方式：第一版只实现元模型、计划校验、线程、交接和门禁，不做复杂画布和真实代码沙箱。

3. **Agent 自编排失控**  
   控制方式：MVP 限制 `maxDepth = 1`、`maxThreads`、策略白名单、工具权限和审批。

4. **现有 Task 体系重复**  
   控制方式：CollaborationSession 表示协作上下文，AgentThread 可绑定现有 Task，不替代 Task。

5. **前端复杂度上升**  
   控制方式：新增通用协作视图，复用现有 Task Detail、ExecutionTimeline、ArtifactPreview、ApprovalCard。
