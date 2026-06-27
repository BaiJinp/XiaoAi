# 平台化能力后延清单

## 1. 文档目的

当前主线只服务两个落地场景：

1. 完整敏捷开发团队 Agent。
2. 业务系统 CLI / 插件化后由 Agent 管理。

本文件收纳暂时不进入当前主线的平台化能力，避免产品和研发继续发散。

## 2. 后延能力总览

| 能力 | 当前处理方式 | 后续建设时机 |
|---|---|---|
| 知识库产品化 | 作为 Tool / Plugin 能力使用 | Agent Runtime 和插件体系稳定后 |
| 完整模型管理后台 | 只保留模型网关和最小配置 | 真实模型联调稳定后 |
| 工具市场 | 先做 ToolDefinition / PluginManifest | 有 3 个以上业务插件后 |
| 模板市场 | 先做 AgentDefinition 和协作模板 seed | 敏捷团队和业务 CLI 两个场景跑通后 |
| 审计后台 | 先记录 TaskEvent、ToolCallLog、ModelCallLog | 合规演示或生产试点前 |
| 成本控制台 | 先保留 token 统计和预算字段 | 真实模型规模使用后 |
| 租户管理后台 | 先保留租户字段和上下文隔离 | 多租户用户进入试点前 |
| 可视化工作流画布 | 先用 JSON plan 和策略执行 | Dynamic Workflow 模型稳定后 |
| 插件商店 | 先支持本地插件 manifest | 内部插件复用需求明确后 |
| 企业聊天渠道 | 先跑通 Web Workbench | Web 场景稳定后 |
| 代码执行沙箱 | 先做受控 workspace CLI 工具 | 敏捷团队 Demo 需要真实改代码时 |
| 长期自治任务 | 暂不做 | 基础会话、记忆、审批、成本稳定后 |

## 3. 平台化建设边界

后续平台化建设必须遵守：

| 原则 | 说明 |
|---|---|
| Agent Runtime 优先 | 所有平台能力必须服务 Agent 执行，不反过来拖慢主线 |
| Tool / Plugin 优先 | 知识库、业务系统、报表、代码工作区都优先作为插件接入 |
| 策略可插拔 | 审批、成本、权限、记忆写入都通过策略扩展 |
| 会话事实源统一 | 任务、运行、事件、步骤、交付物统一沉淀 |
| 不写死行业 | 敏捷开发只是一个模板，业务系统 Agent 是另一个模板 |

## 4. 后续平台模块建议

| 模块 | 核心对象 | 与当前主线关系 |
|---|---|---|
| Agent Center | AgentDefinition、AgentVersion、CapabilityProfile | 当前必须做最小版 |
| Plugin Center | PluginManifest、ToolDefinition、ExecutorConfig | 当前必须做最小版 |
| Runtime Center | AgentRun、RunStep、Checkpoint、RuntimeNode | 当前必须做最小版 |
| Collaboration Center | Session、Thread、Plan、Handoff、QualityGate | 当前必须做最小版 |
| Memory Center | WorkingMemory、SessionMemory、LongTermMemory | 当前做最小策略 |
| Policy Center | PolicyRule、ApprovalRoute、BudgetRule | 当前只做工具审批 |
| Knowledge Center | KnowledgeSource、Retriever、Citation | 当前作为插件 |
| Audit Center | AuditLog、SecurityEvent、Trace | 当前只保留日志 |
| Cost Center | UsageRecord、BudgetPolicy | 当前只保留 token 记录 |
| Channel Center | Web、IM、Webhook | 当前只做 Web |

## 5. 不进入当前迭代的明确事项

- 不做完整企业数字员工平台后台。
- 不做模板市场和插件商店。
- 不做复杂可视化工作流编排器。
- 不做完整成本报表。
- 不做完整审计检索后台。
- 不做多租户组织管理后台。
- 不做企业 IM 渠道。
- 不做完全自主长期任务调度。
- 不做行业专用定制模型。

## 6. 恢复条件

只有满足以下条件后，才恢复平台化建设：

1. 敏捷开发团队 Agent 能跑通至少一次需求到交付物闭环。
2. 业务系统 CLI Agent 能完成查询、写入、报表三个代表动作。
3. 工具审批、运行事件、交付物、会话上下文都可追踪。
4. 至少一个真实模型、一个真实 CLI/HTTP 工具完成联调。
5. 用户确认两个核心场景的产品价值成立。
