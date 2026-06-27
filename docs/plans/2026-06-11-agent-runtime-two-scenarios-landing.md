# Agent Runtime 两个核心场景落地设计

## 1. 目标重新收敛

当前阶段只服务两个场景：

1. **完整敏捷开发团队 Agent**
   - 用户给出一个需求。
   - 系统自主完成需求理解、产品方案、任务拆分、开发、测试、质量门禁、交付汇总。
   - 角色包括产品、架构/技术负责人、开发、测试，也允许后续扩展安全、运维、数据等角色。

2. **现有业务系统 CLI 化后由 Agent 管理**
   - 把现有业务系统能力封装成受控 CLI / HTTP / Java 工具。
   - Agent 可以查询数据、添加数据、生成报表、执行特定业务流程。
   - 可以制作专门 Agent，例如报表 Agent、订单运营 Agent、项目管理 Agent、测试数据 Agent。

知识库、模型配置、审批、成本、审计都视为支撑能力或工具能力，不再作为当前主线产品目标。

## 2. 现有架构判断

结论：**现有架构可以作为底座继续演进，但不能直接支撑这两个场景完整落地。**

已有可复用能力：

| 能力 | 当前状态 | 是否可复用 |
|---|---|---|
| Task / TaskRun / TaskEvent / TaskArtifact | 已有任务账本、运行记录、事件、交付物 | 可作为 Agent Run 事实源 |
| RuntimeGateway | 已有启动、取消、恢复、审批结果提交接口 | 可作为 Runtime 边界 |
| JavaInProcessRuntimeGateway | 已能调用模型、知识、工具，并处理审批 checkpoint | 只能临时复用，当前逻辑硬编码项目助理 |
| ToolConfig / ToolCallLog | 已有工具配置、策略判断、审批、调用日志 | 可作为工具注册和审计基础 |
| Policy / Approval | 已有最小审批和恢复链路 | 可作为高风险工具护栏 |
| CollaborationSession / AgentThread / Plan / Handoff / QualityGate | 已有协作元模型和前端查看入口 | 可作为多 Agent 协作骨架 |
| ModelGateway | 已有 mock 和 OpenAI-compatible chat/embedding | 可作为模型调用统一入口 |

主要缺口：

| 缺口 | 影响 |
|---|---|
| Agent 定义不完整 | 缺 persona、职责、能力、工具范围、上下文策略、记忆策略、运行策略 |
| Runtime 不是真正的 Agent Run Loop | 当前按输入字段硬编码执行模型、知识、工具和项目助理交付物 |
| Collaboration 只创建结构，不驱动 Agent 执行 | 多 Agent 线程、交接、门禁没有真正串起 TaskRun |
| 工具执行器太弱 | 目前只有 `builtin.echo`，缺 CLI / HTTP / Java / MCP 等执行器协议 |
| 插件概念缺失 | 无法把一组工具、提示词、能力声明、权限和配置打包复用 |
| 会话与上下文管理不足 | 只有任务事件，没有面向 Agent 的 conversation/session/context package |
| 记忆管理缺失 | 无短期工作记忆、会话摘要、长期业务记忆、用户偏好 |
| Dynamic Workflow 边界不清 | 不应所有任务都上工作流，需要按任务复杂度选择执行模式 |

## 3. 推荐核心架构

当前应该把系统抽象成四层：

| 层级 | 职责 | 关键对象 |
|---|---|---|
| Agent 定义层 | 定义 Agent 是谁、能做什么、怎么受控运行 | AgentDefinition、AgentVersion、Capability、ToolBinding、MemoryPolicy |
| Agent Runtime 层 | 负责一次任务的计划、执行、观察、恢复、交付 | AgentRun、RunStep、RuntimeEvent、ContextPackage、Checkpoint |
| Tool / Plugin 层 | 把外部系统能力安全暴露给 Agent | ToolDefinition、ToolExecutor、PluginManifest、ToolPolicy |
| Collaboration 层 | 负责多 Agent 协作、角色分工、交接和门禁 | CollaborationSession、AgentThread、Handoff、QualityGate、CollaborationStrategy |

### 3.1 AgentDefinition

建议将 Agent 从当前简单草稿，升级为稳定的定义模型：

| 字段 | 说明 |
|---|---|
| agentCode / agentName | Agent 标识和名称 |
| agentType | `general`、`specialist`、`team_orchestrator`、`business_operator` |
| rolePrompt | 角色、人设、职责 |
| goal | Agent 目标 |
| responsibilities | 负责范围 |
| boundaries | 禁止事项和风险边界 |
| capabilityProfile | 能力标签，例如 `requirement_analysis`、`backend_dev`、`reporting` |
| modelPolicy | 默认模型、温度、上下文窗口、降级模型 |
| toolPolicy | 可用工具、是否允许写操作、审批策略 |
| contextPolicy | 可读取哪些上下文、最大上下文、摘要策略 |
| memoryPolicy | 是否启用短期/长期记忆、记忆写入规则 |
| orchestrationPolicy | `single_agent`、`dynamic_workflow`、`multi_agent_team` |

### 3.2 Agent Run Loop

Runtime 不应继续硬编码项目助理逻辑，应演进为通用循环：

```text
Receive Task
  -> Build ContextPackage
  -> Decide ExecutionMode
  -> Plan
  -> Execute Step
  -> Observe Result
  -> Update Context / Memory / Artifact
  -> Continue / Ask User / Request Approval / Handoff / Finish
```

每一步统一记录为 `RunStep` 和 `RuntimeEvent`：

| Step 类型 | 说明 |
|---|---|
| `plan` | 生成计划 |
| `model_call` | 调模型 |
| `tool_call` | 调工具 |
| `handoff` | 交接给其他 Agent |
| `quality_gate` | 等待门禁 |
| `user_input` | 等待用户补充 |
| `artifact` | 生成交付物 |
| `memory_write` | 写入记忆 |

## 4. Dynamic Workflow 怎么用

不建议所有任务都使用 Dynamic Workflow。

| 任务类型 | 推荐执行方式 | 示例 |
|---|---|---|
| 简单单步工具任务 | Direct Tool Run | 查询订单、添加数据、查库存 |
| 单 Agent 多步骤任务 | Single Agent Plan-Execute | 生成报表、整理数据、分析异常 |
| 复杂但角色单一任务 | Dynamic Workflow | 数据分析 Agent 自己拆查询、计算、生成报告 |
| 多角色协作任务 | Multi-Agent Collaboration | 敏捷团队从需求到测试闭环 |
| 高风险业务写操作 | Tool Policy + Approval + Checkpoint | 修改价格、添加生产数据、触发外部流程 |

Dynamic Workflow 应作为 Agent Runtime 的一种执行模式，而不是平台唯一主线。

## 5. 场景一：完整敏捷开发团队

### 5.1 最小可落地闭环

第一版不追求真实代码沙箱自动改生产代码，先跑通“需求到交付物”的闭环：

```text
用户需求
  -> Team Orchestrator 生成协作计划
  -> 产品 Agent 输出 PRD / 用户故事 / 验收标准
  -> 架构 Agent 输出技术方案 / 模块拆分
  -> 开发 Agent 输出实现计划 / 代码变更建议 / 可选代码补丁
  -> 测试 Agent 输出测试用例 / 风险清单
  -> QualityGate 检查交付完整性
  -> 汇总 Agent 生成最终交付包
```

### 5.2 需要的 Agent

| Agent | 职责 | 主要工具 |
|---|---|---|
| Team Orchestrator | 识别任务、生成计划、分配角色、推进门禁 | plan validator、handoff、quality gate |
| Product Agent | 需求澄清、用户故事、验收标准 | 文档生成、需求模板 |
| Architect Agent | 技术方案、模块边界、风险判断 | 代码搜索、架构模板 |
| Developer Agent | 实现方案、代码补丁、接口调整 | workspace CLI、git diff、build/test CLI |
| QA Agent | 测试计划、用例、缺陷风险 | test CLI、覆盖率、测试模板 |
| Delivery Agent | 汇总交付物和下一步 | artifact composer |

### 5.3 必须补齐的能力

| 优先级 | 能力 | 说明 |
|---|---|---|
| P0 | CollaborationStrategy 真正驱动 AgentThread 执行 | 不只是创建第一个线程，要能创建 TaskRun 并推进下一个 Agent |
| P0 | CollaborationPlan 标准结构 | stages、role、agent、input、expectedArtifact、gate、handoff |
| P0 | Agent Handoff 数据契约 | 上游交付物必须能成为下游输入 |
| P0 | QualityGate 自动/人工门禁 | 至少支持 artifact completeness 检查和人工确认 |
| P0 | Agent Runtime 通用计划执行 | Team Orchestrator 和成员 Agent 都走同一运行内核 |
| P1 | 工作区工具 | 代码搜索、读文件、测试、构建、生成 patch |
| P1 | 记忆和复盘 | 记录团队偏好、项目约束、历史决策 |

## 6. 场景二：业务系统 CLI 化后 Agent 管理

### 6.1 推荐形态

把现有系统封装成插件，每个插件声明一组工具：

```text
business-system-plugin
  -> tools:
     - customer.query
     - customer.create
     - order.query
     - report.generate
  -> executor:
     - cli
     - http
     - java
  -> schemas:
     - inputSchema
     - outputSchema
  -> policy:
     - read: allow
     - write: approval_required
```

### 6.2 ToolDefinition

| 字段 | 说明 |
|---|---|
| toolCode | 全局唯一，例如 `erp.customer.query` |
| toolName | 展示名 |
| toolType | `cli`、`http`、`java`、`mcp`、`builtin` |
| capabilityTags | 查询、写入、报表、审批等 |
| inputSchemaJson | 入参 JSON Schema |
| outputSchemaJson | 出参 JSON Schema |
| executorConfigJson | CLI 命令模板、HTTP endpoint、Java bean 名称等 |
| authConfigRef | 凭据引用，不直接暴露密钥 |
| riskLevel | low / medium / high |
| idempotencyPolicy | 写操作幂等策略 |
| rollbackPolicy | 是否支持回滚 |
| status | active / disabled |

### 6.3 ToolExecutor

工具执行从 `ToolConfigServiceImpl.executeAllowedTool` 抽成执行器注册表：

| 执行器 | 用途 |
|---|---|
| BuiltinToolExecutor | 平台内置工具 |
| CliToolExecutor | 调现有系统 CLI |
| HttpToolExecutor | 调现有系统 HTTP API |
| JavaBeanToolExecutor | 调平台内部 Java 服务 |
| McpToolExecutor | 后续接 MCP 工具 |

所有执行器必须经过同一条链路：

```text
ToolCallRequest
  -> schema validation
  -> permission / policy
  -> dry-run if supported
  -> approval if required
  -> execute
  -> normalize result
  -> audit log
```

### 6.4 专用业务 Agent

| Agent 类型 | 运行方式 | 示例 |
|---|---|---|
| 报表 Agent | 单 Agent 多步骤 | 查询多系统数据，生成日报/周报 |
| 数据维护 Agent | 工具优先 + 审批 | 添加客户、修改字段、导入数据 |
| 业务问答 Agent | 检索/查询工具 + 总结 | 查订单、查工单、查配置 |
| 流程 Agent | Dynamic Workflow | 根据业务规则串多个工具 |

## 7. 会话、上下文、记忆设计

### 7.1 会话分层

| 层级 | 说明 |
|---|---|
| ConversationSession | 用户与 Agent 的对话会话 |
| Task | 用户一次目标 |
| TaskRun | 一次执行尝试 |
| AgentThread | 多 Agent 协作中的某个角色线程 |
| RunStep | 一次模型/工具/交接/门禁步骤 |

### 7.2 ContextPackage

每次 Agent 执行前构建上下文包：

| 上下文 | 来源 |
|---|---|
| User Context | 用户、租户、权限、渠道 |
| Agent Context | Agent 定义、版本、能力、边界 |
| Session Context | 当前对话摘要、最近消息 |
| Task Context | 目标、历史步骤、状态 |
| Artifact Context | 上游交付物、已产物 |
| Tool Context | 可用工具、工具 schema、调用结果 |
| Memory Context | 相关长期记忆和偏好 |

### 7.3 Memory

第一版只做三类：

| 类型 | 用途 |
|---|---|
| Working Memory | 当前 Run 内临时状态 |
| Session Memory | 当前会话摘要、用户已确认的信息 |
| Long-term Memory | 项目约束、业务偏好、常用报表规则 |

记忆写入必须有策略，不能让 Agent 随意长期记忆所有内容。

## 8. 近期任务拆分

### 阶段 A：收敛架构，不再扩散平台功能

| 编号 | 任务 | 验收 |
|---|---|---|
| A-01 | 明确两个核心场景为当前唯一主线 | 文档、任务清单同步 |
| A-02 | 标记知识库、审计、成本、后台管理为支撑/后延 | 不再阻塞当前主线 |
| A-03 | 定义 AgentDefinition / ExecutionMode / ContextPackage | 出设计和最小 DTO |

### 阶段 B：Agent Runtime 通用化

| 编号 | 任务 | 验收 |
|---|---|---|
| B-01 | 从 JavaInProcessRuntimeGateway 抽出 AgentRunEngine | Runtime 不再硬编码项目助理 |
| B-02 | 增加 RunStep 模型 | 每次计划、工具、模型、交付都可追踪 |
| B-03 | 增加 ExecutionMode 路由 | direct、single_agent、dynamic_workflow、multi_agent |
| B-04 | 增加 ContextPackageBuilder | Agent 执行前统一组装上下文 |

### 阶段 C：工具 / 插件体系

| 编号 | 任务 | 验收 |
|---|---|---|
| C-01 | 抽象 ToolExecutor 接口和注册表 | builtin / cli 至少两个 executor |
| C-02 | 扩展 ToolConfig 为 ToolDefinition | 支持 schema、executorConfig、risk、authRef |
| C-03 | 实现 CLI 工具执行器 | 可调用一个本地安全示例 CLI |
| C-04 | 工具统一走 schema 校验、策略、审批、日志 | 写操作可审批挂起恢复 |

### 阶段 D：多 Agent 协作真正运行

| 编号 | 任务 | 验收 |
|---|---|---|
| D-01 | CollaborationStrategy 可创建并启动 AgentThread TaskRun | 第一个线程能真实执行 |
| D-02 | Handoff 输出成为下游输入 | 产品产物可交给架构/开发/测试 |
| D-03 | QualityGate 推进下一阶段 | 门禁通过后创建下一个线程 |
| D-04 | 敏捷团队 Demo Seed | 给需求后生成 PRD、技术方案、测试计划、交付汇总 |

### 阶段 E：业务 CLI Agent Demo

| 编号 | 任务 | 验收 |
|---|---|---|
| E-01 | 定义业务系统插件 manifest | 一个插件可声明多个 CLI 工具 |
| E-02 | 做一个示例业务 CLI | 查询、添加、报表至少覆盖两个动作 |
| E-03 | 创建报表 Agent / 数据维护 Agent | Agent 只拿绑定工具执行 |
| E-04 | 写操作审批闭环 | 添加数据前审批，审批后恢复执行 |

## 9. 当前不做

| 后延内容 | 原因 |
|---|---|
| 完整知识库产品化 | 作为工具，不影响 Agent Runtime 主线 |
| 完整模型管理后台 | 已有最小配置，后续再产品化 |
| 完整审计/成本看板 | 先保留基础日志和 token 记录 |
| 模板市场 | Agent / Plugin 稳定后再做 |
| 多租户管理后台 | 当前只保留租户隔离底座 |
| 复杂可视化工作流画布 | 当前先用 JSON plan 和策略执行 |

## 10. 总结

这两个场景可以落地，但要把项目主线从“平台功能堆叠”改成“Agent Runtime + Tool/Plugin + Collaboration”。

优先级最高的不是继续加知识库或管理后台，而是：

1. 让 Agent 定义完整。
2. 让 Runtime 成为通用 Agent Run Loop。
3. 让工具/插件能安全接业务系统。
4. 让多 Agent 协作能真实驱动每个 Agent 执行。
5. 把 Dynamic Workflow 降级为执行模式之一，而不是所有任务的唯一解。
