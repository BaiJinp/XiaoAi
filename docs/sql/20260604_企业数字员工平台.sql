-- 企业数字员工平台 MVP 初始化脚本
-- 数据库：PostgreSQL
-- 日期：2026-06-08
-- 设计目标：
-- 1. 支撑项目助理 Agent MVP 端到端闭环。
-- 2. 所有业务表保留 tenant_id，满足多租户隔离预留。
-- 3. Java 后端作为任务、权限、审批、审计和配置事实源。
-- 4. Runtime 只保存执行快照和事件，不作为业务事实源。
-- 5. 所有表统一通用字段：id、bid、created_by、created_at、updated_by、updated_at、deleted。
--    id 为数据库自增主键，bid 为业务唯一标识，不对外暴露 id。

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- 1. 租户与用户
-- ============================================================

CREATE TABLE tenant (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_code VARCHAR(64) NOT NULL UNIQUE,
    tenant_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE tenant IS '企业租户';
COMMENT ON COLUMN tenant.config_json IS '租户级扩展配置';

CREATE TABLE user_account (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    user_code VARCHAR(64) NOT NULL,
    username VARCHAR(128) NOT NULL,
    display_name VARCHAR(128),
    email VARCHAR(256),
    mobile VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    role_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_user_account_tenant_code UNIQUE (tenant_id, user_code)
);

COMMENT ON TABLE user_account IS '平台用户账号';
COMMENT ON COLUMN user_account.role_codes IS '用户角色编码列表';

CREATE INDEX idx_user_account_tenant ON user_account (tenant_id);

CREATE TABLE user_identity_mapping (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    channel_type VARCHAR(64) NOT NULL,
    channel_user_id VARCHAR(128) NOT NULL,
    channel_user_name VARCHAR(128),
    bind_status VARCHAR(32) NOT NULL DEFAULT 'bound',
    bind_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_identity_mapping_channel UNIQUE (tenant_id, channel_type, channel_user_id)
);

COMMENT ON TABLE user_identity_mapping IS '跨渠道用户身份映射';

CREATE INDEX idx_identity_mapping_user ON user_identity_mapping (tenant_id, user_id);

-- ============================================================
-- 2. Agent 配置与版本
-- ============================================================

CREATE TABLE agent (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    agent_code VARCHAR(64) NOT NULL,
    agent_name VARCHAR(128) NOT NULL,
    agent_type VARCHAR(64) NOT NULL DEFAULT 'project_assistant',
    description TEXT,
    role_prompt TEXT,
    responsibility_text TEXT,
    boundary_text TEXT,
    capability_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    tool_policy_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    context_policy_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    memory_policy_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    orchestration_policy_json JSONB NOT NULL DEFAULT '{"executionMode":"single_agent"}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    current_version_id BIGINT,
    latest_stable_version_id BIGINT,
    owner_user_id BIGINT,
    config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_agent_tenant_code UNIQUE (tenant_id, agent_code)
);

COMMENT ON TABLE agent IS 'Agent 主表';
COMMENT ON COLUMN agent.status IS 'draft/published/disabled/archived';

CREATE INDEX idx_agent_tenant_status ON agent (tenant_id, status);

CREATE TABLE agent_version (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    version_no VARCHAR(64) NOT NULL,
    version_status VARCHAR(32) NOT NULL DEFAULT 'draft',
    role_prompt TEXT,
    responsibility_text TEXT,
    boundary_text TEXT,
    config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    knowledge_scope_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    tool_scope_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    permission_policy_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    budget_policy_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    runtime_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_agent_version UNIQUE (tenant_id, agent_id, version_no)
);

COMMENT ON TABLE agent_version IS 'Agent 不可变版本配置';
COMMENT ON COLUMN agent_version.runtime_snapshot_json IS 'Runtime 执行快照';

CREATE INDEX idx_agent_version_agent ON agent_version (tenant_id, agent_id);

CREATE TABLE agent_publish_scope (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    agent_version_id BIGINT NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    scope_value VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE agent_publish_scope IS 'Agent 发布范围';
COMMENT ON COLUMN agent_publish_scope.scope_type IS 'user/department/tenant';

CREATE INDEX idx_agent_publish_scope ON agent_publish_scope (tenant_id, agent_id, status);

CREATE TABLE agent_tool_binding (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    agent_version_id BIGINT,
    tool_id BIGINT NOT NULL,
    binding_status VARCHAR(32) NOT NULL DEFAULT 'active',
    policy_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE agent_tool_binding IS 'Agent 工具绑定';

CREATE INDEX idx_agent_tool_binding_agent ON agent_tool_binding (tenant_id, agent_id);

CREATE TABLE agent_knowledge_binding (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    agent_version_id BIGINT,
    knowledge_base_id BIGINT NOT NULL,
    binding_status VARCHAR(32) NOT NULL DEFAULT 'active',
    policy_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE agent_knowledge_binding IS 'Agent 知识库绑定';

CREATE INDEX idx_agent_knowledge_binding_agent ON agent_knowledge_binding (tenant_id, agent_id);

-- ============================================================
-- 3. 任务账本
-- ============================================================

CREATE TABLE task (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    task_code VARCHAR(64) NOT NULL,
    agent_id BIGINT NOT NULL,
    agent_version_id BIGINT,
    user_id BIGINT NOT NULL,
    channel_type VARCHAR(64) NOT NULL DEFAULT 'web_chat',
    title VARCHAR(256),
    input_text TEXT,
    task_type VARCHAR(64) NOT NULL DEFAULT 'chat_task',
    complexity VARCHAR(32) NOT NULL DEFAULT 'unknown',
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    current_run_id BIGINT,
    result_summary TEXT,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_task_tenant_code UNIQUE (tenant_id, task_code)
);

COMMENT ON TABLE task IS '任务主账本';
COMMENT ON COLUMN task.status IS 'pending/running/suspended/completed/failed/cancelled';
COMMENT ON COLUMN task.complexity IS 'unknown/simple/medium/complex';

CREATE INDEX idx_task_tenant_user ON task (tenant_id, user_id, created_at DESC);
CREATE INDEX idx_task_tenant_agent ON task (tenant_id, agent_id, created_at DESC);
CREATE INDEX idx_task_tenant_status ON task (tenant_id, status);

CREATE TABLE task_run (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    run_code VARCHAR(64) NOT NULL,
    task_id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    agent_version_id BIGINT,
    runtime_node_id BIGINT,
    runtime_type VARCHAR(64) NOT NULL DEFAULT 'java-in-process',
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    start_time TIMESTAMPTZ,
    end_time TIMESTAMPTZ,
    suspend_reason TEXT,
    fail_reason TEXT,
    trace_id VARCHAR(128),
    snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    usage_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_task_run_tenant_code UNIQUE (tenant_id, run_code)
);

COMMENT ON TABLE task_run IS '任务单次运行记录';
COMMENT ON COLUMN task_run.snapshot_json IS '本次运行使用的 Agent、工具、知识、预算快照';

CREATE INDEX idx_task_run_task ON task_run (tenant_id, task_id, created_at DESC);
CREATE INDEX idx_task_run_trace ON task_run (trace_id);

CREATE TABLE runtime_checkpoint (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    run_id BIGINT NOT NULL,
    checkpoint_type VARCHAR(64) NOT NULL,
    checkpoint_status VARCHAR(32) NOT NULL DEFAULT 'suspended',
    approval_request_id BIGINT,
    payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    resume_payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_runtime_checkpoint_run_type UNIQUE (tenant_id, run_id, checkpoint_type, checkpoint_status)
);

COMMENT ON TABLE runtime_checkpoint IS 'Runtime 断点恢复检查点';
COMMENT ON COLUMN runtime_checkpoint.payload_json IS '挂起时的工具、模型或流程上下文';
COMMENT ON COLUMN runtime_checkpoint.resume_payload_json IS '审批或用户输入恢复上下文';

CREATE INDEX idx_runtime_checkpoint_run ON runtime_checkpoint (tenant_id, run_id, checkpoint_status);
CREATE INDEX idx_runtime_checkpoint_approval ON runtime_checkpoint (tenant_id, approval_request_id);

CREATE TABLE task_step (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    run_id BIGINT NOT NULL,
    parent_step_id BIGINT,
    step_code VARCHAR(64) NOT NULL,
    step_name VARCHAR(256) NOT NULL,
    step_type VARCHAR(64) NOT NULL DEFAULT 'runtime_step',
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    sort_order INTEGER NOT NULL DEFAULT 0,
    input_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    output_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    fail_reason TEXT,
    start_time TIMESTAMPTZ,
    end_time TIMESTAMPTZ,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_task_step_code UNIQUE (tenant_id, run_id, step_code)
);

COMMENT ON TABLE task_step IS 'Dynamic Workflow 步骤账本';

CREATE INDEX idx_task_step_run ON task_step (tenant_id, run_id, sort_order);
CREATE INDEX idx_task_step_status ON task_step (tenant_id, status);

CREATE TABLE task_event (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    run_id BIGINT,
    step_id BIGINT,
    event_type VARCHAR(64) NOT NULL,
    event_level VARCHAR(32) NOT NULL DEFAULT 'info',
    event_summary VARCHAR(512),
    payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    trace_id VARCHAR(128),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE task_event IS 'Runtime 执行事件';

CREATE INDEX idx_task_event_task ON task_event (tenant_id, task_id, occurred_at);
CREATE INDEX idx_task_event_run ON task_event (tenant_id, run_id, occurred_at);
CREATE INDEX idx_task_event_type ON task_event (tenant_id, event_type, occurred_at);

CREATE TABLE task_artifact (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    run_id BIGINT,
    artifact_type VARCHAR(64) NOT NULL,
    artifact_name VARCHAR(256) NOT NULL,
    content_text TEXT,
    storage_url VARCHAR(1024),
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE task_artifact IS '任务产物';

CREATE INDEX idx_task_artifact_task ON task_artifact (tenant_id, task_id);

CREATE TABLE agent_memory (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    memory_code VARCHAR(64) NOT NULL,
    agent_id BIGINT NOT NULL,
    agent_version_id BIGINT,
    task_id BIGINT,
    run_id BIGINT,
    session_id BIGINT,
    user_id BIGINT,
    memory_type VARCHAR(64) NOT NULL DEFAULT 'session_summary',
    memory_scope VARCHAR(64) NOT NULL DEFAULT 'task',
    summary_text TEXT NOT NULL,
    source_text TEXT,
    confidence VARCHAR(64) NOT NULL DEFAULT 'confirmed',
    status VARCHAR(32) NOT NULL DEFAULT 'confirmed',
    policy_json JSONB NOT NULL DEFAULT '{"write":"confirmed_only"}'::jsonb,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_agent_memory_code UNIQUE (tenant_id, memory_code)
);

COMMENT ON TABLE agent_memory IS 'Agent 已确认记忆';
COMMENT ON COLUMN agent_memory.summary_text IS '用户确认后的会话摘要或约束';

CREATE INDEX idx_agent_memory_agent ON agent_memory (tenant_id, agent_id, status, created_at DESC);
CREATE INDEX idx_agent_memory_task ON agent_memory (tenant_id, task_id, status, created_at DESC);
CREATE INDEX idx_agent_memory_session ON agent_memory (tenant_id, session_id, status, created_at DESC);

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
    default_agent_id BIGINT,
    default_agent_version_id BIGINT,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_agent_role_code UNIQUE (tenant_id, role_code)
);

COMMENT ON TABLE agent_role IS '通用 Agent 角色定义';
COMMENT ON COLUMN agent_role.domain_code IS '业务领域编码，软件研发、项目管理、销售、财务等均通过数据表达';
COMMENT ON COLUMN agent_role.default_agent_id IS '该角色默认绑定的 Agent，可为空，为模板导入时提供建议绑定';
COMMENT ON COLUMN agent_role.default_agent_version_id IS '该角色默认绑定的 AgentVersion，可为空，运行时仍以计划中的显式版本为准';

CREATE INDEX idx_agent_role_domain ON agent_role (tenant_id, domain_code, status);
CREATE INDEX idx_agent_role_default_agent ON agent_role (tenant_id, default_agent_id, default_agent_version_id);

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

COMMENT ON TABLE capability_profile IS '通用 Agent 能力画像';
COMMENT ON COLUMN capability_profile.capability_json IS '协作、工具、知识、交付物和风险边界能力配置';

CREATE INDEX idx_capability_profile_role ON capability_profile (tenant_id, role_id, status);

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

COMMENT ON TABLE artifact_type IS '通用交付物类型注册表';
COMMENT ON COLUMN artifact_type.schema_json IS '交付物结构 schema，不同领域通过数据配置扩展';

CREATE INDEX idx_artifact_type_domain ON artifact_type (tenant_id, domain_code, status);

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

COMMENT ON TABLE collaboration_template IS '通用多 Agent 协作场景模板';
COMMENT ON COLUMN collaboration_template.strategy_type IS 'single_agent/orchestrated_team/self_orchestrated/peer_review/parallel_expert/debate/human_led';

CREATE INDEX idx_collaboration_template_domain ON collaboration_template (tenant_id, domain_code, status);

CREATE TABLE collaboration_role_binding (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    binding_scope VARCHAR(64) NOT NULL DEFAULT 'template',
    binding_key VARCHAR(128) NOT NULL DEFAULT 'default',
    agent_id BIGINT,
    agent_version_id BIGINT,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_collaboration_role_binding UNIQUE (tenant_id, template_id, role_code, binding_scope, binding_key)
);

COMMENT ON TABLE collaboration_role_binding IS '协作模板角色到具体 Agent/AgentVersion 的团队或环境级绑定';
COMMENT ON COLUMN collaboration_role_binding.binding_scope IS '绑定维度，默认 template，后续可扩展为 team/environment';
COMMENT ON COLUMN collaboration_role_binding.binding_key IS '绑定维度取值，默认 default';

CREATE INDEX idx_collaboration_role_binding_template ON collaboration_role_binding (tenant_id, template_id, binding_scope, binding_key, status);
CREATE INDEX idx_collaboration_role_binding_agent ON collaboration_role_binding (tenant_id, agent_id, agent_version_id);

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

COMMENT ON TABLE collaboration_session IS '通用多 Agent 协作会话';
COMMENT ON COLUMN collaboration_session.goal_text IS '协作目标，不绑定具体业务领域';

CREATE INDEX idx_collaboration_session_status ON collaboration_session (tenant_id, status, created_at DESC);

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

COMMENT ON TABLE agent_thread IS '协作会话内的 Agent 独立执行线程';

CREATE INDEX idx_agent_thread_session ON agent_thread (tenant_id, session_id, status);

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

COMMENT ON TABLE collaboration_plan IS 'Agent 自编排或模板生成的协作计划';
COMMENT ON COLUMN collaboration_plan.validation_status IS 'pending/passed/failed';

CREATE INDEX idx_collaboration_plan_session ON collaboration_plan (tenant_id, session_id, created_at DESC);

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
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE agent_handoff IS 'Agent 之间的结构化交接记录';

CREATE INDEX idx_agent_handoff_session ON agent_handoff (tenant_id, session_id, created_at DESC);

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

COMMENT ON TABLE quality_gate IS '通用协作质量门禁';
COMMENT ON COLUMN quality_gate.gate_type IS 'manual/automatic/policy/test/review 等通用门禁类型';

CREATE INDEX idx_quality_gate_session ON quality_gate (tenant_id, session_id, status);

-- ============================================================
-- 5. Runtime 注册与快照
-- ============================================================

CREATE TABLE runtime_node (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT,
    runtime_code VARCHAR(64) NOT NULL,
    runtime_type VARCHAR(64) NOT NULL,
    runtime_name VARCHAR(128) NOT NULL,
    endpoint_url VARCHAR(512),
    capability_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    last_heartbeat_at TIMESTAMPTZ,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_runtime_node_code UNIQUE (runtime_code)
);

COMMENT ON TABLE runtime_node IS 'Runtime 节点注册表预留';
COMMENT ON COLUMN runtime_node.runtime_type IS 'java-in-process/java-worker/typescript-worker/mock-remote';

CREATE INDEX idx_runtime_node_status ON runtime_node (runtime_type, status);

CREATE TABLE runtime_run_snapshot (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    run_id BIGINT NOT NULL,
    snapshot_type VARCHAR(64) NOT NULL,
    snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE runtime_run_snapshot IS 'Runtime 单次运行快照';

CREATE INDEX idx_runtime_run_snapshot ON runtime_run_snapshot (tenant_id, run_id, snapshot_type);

-- ============================================================
-- 6. 模型网关
-- ============================================================

CREATE TABLE model_provider (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    provider_code VARCHAR(64) NOT NULL,
    provider_name VARCHAR(128) NOT NULL,
    provider_type VARCHAR(64) NOT NULL,
    base_url VARCHAR(512),
    auth_config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_model_provider_code UNIQUE (tenant_id, provider_code)
);

COMMENT ON TABLE model_provider IS '模型供应商';

CREATE TABLE model_config (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    model_code VARCHAR(128) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    model_type VARCHAR(64) NOT NULL,
    context_window INTEGER,
    config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_model_config_code UNIQUE (tenant_id, model_code)
);

COMMENT ON TABLE model_config IS '模型配置';
COMMENT ON COLUMN model_config.model_type IS 'chat/embedding/rerank';

CREATE INDEX idx_model_config_provider ON model_config (provider_id, status);

CREATE TABLE model_call_log (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    task_id BIGINT,
    run_id BIGINT,
    step_id BIGINT,
    provider_id BIGINT,
    model_id BIGINT,
    call_type VARCHAR(64) NOT NULL,
    prompt_tokens INTEGER NOT NULL DEFAULT 0,
    completion_tokens INTEGER NOT NULL DEFAULT 0,
    total_tokens INTEGER NOT NULL DEFAULT 0,
    cost_amount NUMERIC(18, 6),
    status VARCHAR(32) NOT NULL DEFAULT 'success',
    error_message TEXT,
    latency_ms INTEGER,
    trace_id VARCHAR(128),
    request_summary TEXT,
    response_summary TEXT,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE model_call_log IS '模型调用账本';

CREATE INDEX idx_model_call_log_task ON model_call_log (tenant_id, task_id, created_at DESC);
CREATE INDEX idx_model_call_log_run ON model_call_log (tenant_id, run_id, created_at DESC);

-- ============================================================
-- 7. 知识库
-- ============================================================

CREATE TABLE knowledge_base (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    kb_code VARCHAR(64) NOT NULL,
    kb_name VARCHAR(128) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_knowledge_base_code UNIQUE (tenant_id, kb_code)
);

COMMENT ON TABLE knowledge_base IS '知识库';

CREATE INDEX idx_knowledge_base_tenant ON knowledge_base (tenant_id, status);

CREATE TABLE knowledge_document (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    document_code VARCHAR(64) NOT NULL,
    document_name VARCHAR(256) NOT NULL,
    file_type VARCHAR(64),
    storage_url VARCHAR(1024),
    parse_status VARCHAR(32) NOT NULL DEFAULT 'pending',
    parse_error TEXT,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_knowledge_document_code UNIQUE (tenant_id, document_code)
);

COMMENT ON TABLE knowledge_document IS '知识库文档';

CREATE INDEX idx_knowledge_document_kb ON knowledge_document (tenant_id, knowledge_base_id, created_at DESC);

CREATE TABLE knowledge_chunk (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    chunk_index INTEGER NOT NULL,
    chunk_text TEXT NOT NULL,
    embedding_ref VARCHAR(256),
    token_count INTEGER,
    source_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_knowledge_chunk_index UNIQUE (tenant_id, document_id, chunk_index)
);

COMMENT ON TABLE knowledge_chunk IS '知识切块';
COMMENT ON COLUMN knowledge_chunk.embedding_ref IS '向量存储引用，MVP 可为空或外部引用';

CREATE INDEX idx_knowledge_chunk_doc ON knowledge_chunk (tenant_id, document_id, chunk_index);
CREATE INDEX idx_knowledge_chunk_kb ON knowledge_chunk (tenant_id, knowledge_base_id);

-- ============================================================
-- 8. 工具
-- ============================================================

CREATE TABLE tool_config (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    tool_code VARCHAR(64) NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    tool_type VARCHAR(64) NOT NULL,
    risk_level VARCHAR(32) NOT NULL DEFAULT 'low',
    endpoint_url VARCHAR(512),
    schema_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    auth_config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_tool_config_code UNIQUE (tenant_id, tool_code)
);

COMMENT ON TABLE tool_config IS '工具配置';
COMMENT ON COLUMN tool_config.tool_type IS 'http/openapi/internal/mcp/cli';
COMMENT ON COLUMN tool_config.risk_level IS 'low/medium/high';

CREATE INDEX idx_tool_config_tenant ON tool_config (tenant_id, status);

CREATE TABLE plugin_manifest (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    plugin_code VARCHAR(128) NOT NULL,
    plugin_name VARCHAR(255) NOT NULL,
    plugin_version VARCHAR(64) NOT NULL,
    manifest_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_plugin_manifest_code_version UNIQUE (tenant_id, plugin_code, plugin_version)
);

COMMENT ON TABLE plugin_manifest IS '插件 Manifest';

CREATE INDEX idx_plugin_manifest_tenant ON plugin_manifest (tenant_id, status);

CREATE TABLE tool_call_log (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    task_id BIGINT,
    run_id BIGINT,
    step_id BIGINT,
    tool_id BIGINT NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    call_status VARCHAR(32) NOT NULL,
    request_summary TEXT,
    response_summary TEXT,
    error_message TEXT,
    latency_ms INTEGER,
    approval_request_id BIGINT,
    trace_id VARCHAR(128),
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE tool_call_log IS '工具调用日志';

CREATE INDEX idx_tool_call_log_task ON tool_call_log (tenant_id, task_id, created_at DESC);
CREATE INDEX idx_tool_call_log_tool ON tool_call_log (tenant_id, tool_id, created_at DESC);

-- ============================================================
-- 9. 权限与审批
-- ============================================================

CREATE TABLE policy_rule (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    rule_code VARCHAR(64) NOT NULL,
    rule_name VARCHAR(128) NOT NULL,
    rule_type VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id BIGINT,
    effect VARCHAR(32) NOT NULL,
    condition_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    approver_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    priority INTEGER NOT NULL DEFAULT 100,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_policy_rule_code UNIQUE (tenant_id, rule_code)
);

COMMENT ON TABLE policy_rule IS '权限与审批策略';
COMMENT ON COLUMN policy_rule.effect IS 'allow/confirm/approve/deny';

CREATE INDEX idx_policy_rule_target ON policy_rule (tenant_id, target_type, target_id, status);

CREATE TABLE approval_request (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    request_code VARCHAR(64) NOT NULL,
    task_id BIGINT,
    run_id BIGINT,
    step_id BIGINT,
    applicant_user_id BIGINT NOT NULL,
    approver_user_id BIGINT,
    approval_type VARCHAR(64) NOT NULL,
    approval_status VARCHAR(32) NOT NULL DEFAULT 'pending',
    reason TEXT,
    request_payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    expire_at TIMESTAMPTZ,
    approved_at TIMESTAMPTZ,
    rejected_at TIMESTAMPTZ,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_approval_request_code UNIQUE (tenant_id, request_code)
);

COMMENT ON TABLE approval_request IS '审批请求';

CREATE INDEX idx_approval_request_approver ON approval_request (tenant_id, approver_user_id, approval_status);
CREATE INDEX idx_approval_request_task ON approval_request (tenant_id, task_id, created_at DESC);

CREATE TABLE approval_record (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    approval_request_id BIGINT NOT NULL,
    operator_user_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    comment_text TEXT,
    action_payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE approval_record IS '审批处理记录';
COMMENT ON COLUMN approval_record.action IS 'approve/reject/timeout/cancel';

CREATE INDEX idx_approval_record_request ON approval_record (tenant_id, approval_request_id, created_at);

-- ============================================================
-- 10. 审计与成本
-- ============================================================

CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT,
    agent_id BIGINT,
    task_id BIGINT,
    run_id BIGINT,
    audit_type VARCHAR(64) NOT NULL,
    action VARCHAR(128) NOT NULL,
    resource_type VARCHAR(64),
    resource_id VARCHAR(128),
    risk_level VARCHAR(32) NOT NULL DEFAULT 'low',
    summary TEXT,
    detail_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    trace_id VARCHAR(128),
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE audit_log IS '审计日志';

CREATE INDEX idx_audit_log_task ON audit_log (tenant_id, task_id, created_at DESC);
CREATE INDEX idx_audit_log_user ON audit_log (tenant_id, user_id, created_at DESC);
CREATE INDEX idx_audit_log_type ON audit_log (tenant_id, audit_type, created_at DESC);

CREATE TABLE security_event (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT,
    agent_id BIGINT,
    task_id BIGINT,
    run_id BIGINT,
    event_type VARCHAR(64) NOT NULL,
    risk_level VARCHAR(32) NOT NULL DEFAULT 'medium',
    event_status VARCHAR(32) NOT NULL DEFAULT 'open',
    summary TEXT,
    detail_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE security_event IS '安全事件';

CREATE INDEX idx_security_event_status ON security_event (tenant_id, event_status, created_at DESC);

CREATE TABLE budget_policy (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    policy_code VARCHAR(64) NOT NULL,
    policy_name VARCHAR(128) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id BIGINT,
    daily_token_limit INTEGER,
    task_token_limit INTEGER,
    daily_cost_limit NUMERIC(18, 6),
    policy_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_budget_policy_code UNIQUE (tenant_id, policy_code)
);

COMMENT ON TABLE budget_policy IS '预算策略';
COMMENT ON COLUMN budget_policy.target_type IS 'tenant/user/agent/model/tool';

CREATE INDEX idx_budget_policy_target ON budget_policy (tenant_id, target_type, target_id, status);

CREATE TABLE usage_record (
    id BIGSERIAL PRIMARY KEY,
    bid VARCHAR(64) NOT NULL DEFAULT replace(uuid_generate_v4()::text, '-', '') UNIQUE,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT,
    agent_id BIGINT,
    task_id BIGINT,
    run_id BIGINT,
    usage_type VARCHAR(64) NOT NULL,
    usage_amount NUMERIC(18, 6) NOT NULL DEFAULT 0,
    token_count INTEGER NOT NULL DEFAULT 0,
    cost_amount NUMERIC(18, 6),
    usage_date DATE NOT NULL DEFAULT CURRENT_DATE,
    detail_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE usage_record IS '用量记录';

CREATE INDEX idx_usage_record_agent_date ON usage_record (tenant_id, agent_id, usage_date);
CREATE INDEX idx_usage_record_user_date ON usage_record (tenant_id, user_id, usage_date);
CREATE INDEX idx_usage_record_task ON usage_record (tenant_id, task_id);

-- ============================================================
-- 11. MVP 基础种子数据
-- ============================================================

INSERT INTO runtime_node (
    tenant_id,
    runtime_code,
    runtime_type,
    runtime_name,
    capability_json,
    status
) VALUES (
    NULL,
    'java-in-process-default',
    'java-in-process',
    'Java In-Process Runtime',
    '{"supportsCancel": true, "supportsResume": true, "supportsApproval": true}'::jsonb,
    'active'
) ON CONFLICT (runtime_code) DO NOTHING;
