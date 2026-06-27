# 项目助理 Agent MVP 重新规划

> 日期：2026-06-10  
> 背景：项目目标调整为 Agent-first / Platform-ready。当前阶段先验证一个真正可用的项目助理 Agent，而不是优先建设完整企业数字员工平台后台。

## 1. 当前阶段目标

第一阶段只围绕一个问题交付：**项目助理 Agent 是否能帮助用户推进真实项目工作**。

MVP 成功标准：

1. 用户能在 Agent Workbench 输入会议纪要、项目资料或项目问题。
2. Agent 能识别任务类型，进入对应执行流程。
3. Agent 能展示计划、知识依据、模型分析、工具动作、审批等待和最终结果。
4. 高风险动作能触发审批，审批通过后任务能恢复，审批拒绝后能给出可理解反馈。
5. 最终结果是可直接复用的交付物，而不是调试日志或原始 JSON。
6. 任务、事件、工具调用、审批和基础模型调用有记录，便于排障和后续治理。

## 2. 当前能力判断

已经具备：

- 前端 Workbench、任务详情、执行时间线、审批卡片、交付物预览、知识入口、项目助理配置入口。
- 后端 Task、Runtime、Model、Knowledge、Tool、Policy、Approval、Agent、Audit、Cost、Tenant/User 等模块骨架。
- JavaInProcessRuntimeGateway 已能启动任务、调用 Mock 模型、检索知识、调用工具、触发审批、checkpoint 恢复。
- 高风险审批链路已跑通：任务挂起、审批通过、Runtime 恢复、工具完成、任务完成、产物刷新。

主要缺口：

- Mock 模型输出还像调试内容，不像真实项目助理交付物。
- Workbench 任务还没有自然接入默认知识库和来源展示。
- 交付物结构需要强化为周报、行动项、风险清单三类业务结果。
- Agent 配置尚未真正影响 Runtime snapshot 和任务执行边界。
- 审批拒绝、任务取消、知识无结果、工具失败等失败路径还不够产品化。
- 平台模块较多，但当前不应继续横向铺后台页面。

## 3. 技术路线约束

- 后端主体系继续采用 Java / Spring Boot。
- 第一阶段 Runtime 继续使用 Java 内置 Runtime。
- TypeScript Runtime 只做架构预留，不进入当前 MVP 主线。
- Python 只作为后续 AI 实验、RAG 评测、文档处理实验工具，不进入在线主链路。
- Java 后端必须继续作为任务、审批、权限、审计、成本和 Agent 配置的事实源。

## 3.1 通用多 Agent 协作边界

本项目的长期架构目标仍是通用企业数字员工 / 多 Agent 协作平台，不应被写偏成单一的软件研发 Agent 平台。软件研发全链路只是一个高复杂度场景实践，用来验证多角色协作、结构化交接、质量门禁和人机协同能力。

后续设计必须遵守以下边界：

- Agent 角色是通用能力，不写死为产品经理、前端、后端、测试等研发角色；这些只是 software_development 场景下的角色实例。
- 角色应抽象为 Agent Role / Persona / Capability Profile，包含职责、能力范围、工具权限、知识范围、可产出交付物、协作边界和审批策略。
- 编排器不是平台唯一中心，而是 Collaboration Strategy 的一种实现；平台应支持 single_agent、orchestrated_team、self_orchestrated、peer_review、parallel_expert、debate、human_led 等多种协作模式。
- Agent 可以具备自编排能力，但必须先产出 CollaborationPlan，由平台校验角色、权限、深度、成本、工具和审批边界后再执行。
- Agent 间沟通不应作为不可控自由群聊主链路；关键结论必须沉淀为 HandoffArtifact、Decision、QualityGate 或 TaskEvent，保证可审计、可恢复、可测试。
- Artifact 类型也必须保持通用抽象，通过 artifact_type / schema / renderer / validator 配置；研发产物、项目管理产物、销售产物、财务产物都应复用同一套交付物机制。
- 平台负责状态、权限、审批、工具、任务、产物、门禁和成本边界；Agent 负责理解目标、拆解任务、选择角色、执行专业工作和汇总结果。

后续如设计“需求文档到开发交付”的全链路，应落为 Scenario Template / Collaboration Template，而不是替换平台主架构。该场景可以配置产品经理、架构、前端、后端、测试、Review 等角色实例，但底层模型必须仍是通用 Role、Capability、Strategy、Artifact 和 Gate。

## 4. 开发阶段规划

### 阶段 A：项目助理交付物成型

目标：让用户看到的结果像真正的项目助理产出。

P0 任务：

| 编号 | 任务 | 交付物 | 验收标准 |
|---|---|---|---|
| A-01 | 定义三类交付物结构 | weekly_report、action_items、risk_list schema | 前后端类型一致，ArtifactPreview 可识别 |
| A-02 | Runtime 按任务类型生成结构化结果 | 周报、行动项、风险分析生成逻辑 | 三个 starter prompt 均产出对应交付物 |
| A-03 | 模型输出从 Mock response 改为业务化模板 | 可读 Markdown / JSON 内容 | 页面不再展示调试式 Mock response |
| A-04 | 事件标题和 payload 业务化 | 计划、分析、工具、完成事件 | 时间线能看懂 Agent 正在做什么 |
| A-05 | 前端交付物预览增强 | 周报、行动项、风险清单视图 | 用户可以直接复制/复用结果 |

阶段验收：

- 周报任务能生成结构化周报。
- 风险任务能生成风险清单。
- 会议纪要任务能生成行动项，并在创建任务动作前触发审批。

### 阶段 B：知识资料进入 Agent 主链路

目标：Agent 不只是根据输入回答，而是能使用项目资料和来源依据。

P0 任务：

| 编号 | 任务 | 交付物 | 验收标准 |
|---|---|---|---|
| B-01 | Workbench 支持默认知识库参数 | 创建任务携带 knowledgeBaseId | 任务输入进入 Runtime 时包含知识库 |
| B-02 | Runtime 自动检索知识 | 不同任务类型使用不同 query | 时间线出现知识检索事件 |
| B-03 | 交付物展示来源 | sources / references 字段 | 周报/风险/行动项能看到来源 |
| B-04 | 知识无结果处理 | no_knowledge_found 事件和提示 | 页面提示资料不足，不强行编造 |
| B-05 | 知识页面与 Workbench 串联 | 入库后可回到 Workbench 使用 | 用户能完成入库 -> 使用闭环 |

阶段验收：

- 用户粘贴项目资料入库后，Workbench 发起任务能检索并使用资料。
- 最终产物能展示来源或说明依据不足。

### 阶段 C：失败、取消、拒绝路径产品化

目标：让 Agent 不只会成功，也能解释失败和恢复选择。

P0 任务：

| 编号 | 任务 | 交付物 | 验收标准 |
|---|---|---|---|
| C-01 | 审批拒绝路径完善 | APPROVAL_REJECTED、RUN_FAILED、失败 artifact | 用户能看到拒绝原因和影响 |
| C-02 | 任务取消前后端闭环 | 取消按钮、CancelTask API、RUN_CANCELLED | 运行中/挂起任务可取消 |
| C-03 | 工具失败展示 | TOOL_FAILED 业务说明 | 页面展示失败环节和建议动作 |
| C-04 | 模型失败展示 | MODEL_FAILED / RUN_FAILED | 用户看到可理解失败原因 |
| C-05 | 重试入口预留 | 基于原输入重新创建任务 | 失败后可重新提交 |

阶段验收：

- 审批拒绝不会表现成页面卡死。
- 任务取消后状态一致，时间线和任务详情同步。
- 失败原因是业务可理解文本。

### 阶段 D：项目助理配置真正生效

目标：最小配置页不只是创建草稿，而是影响 Agent 的执行边界。

P1 任务：

| 编号 | 任务 | 交付物 | 验收标准 |
|---|---|---|---|
| D-01 | Runtime snapshot 接入 AgentVersion | run.snapshotJson 保存版本快照 | 任务运行可追溯配置版本 |
| D-02 | 知识范围生效 | Agent knowledge scope -> Runtime input | 未绑定知识库不可检索 |
| D-03 | 工具范围生效 | Agent tool scope -> Tool execute | 未绑定工具不可调用 |
| D-04 | 基础预算生效 | taskTokenLimit / dailyTokenLimit 检查 | 超限任务被拦截并提示 |
| D-05 | 配置页发布/试运行增强 | 创建草稿、版本、试运行联动 | 配置后能直接试运行 |

阶段验收：

- 同一输入在不同 Agent 配置下会受不同知识、工具、预算边界影响。
- Runtime 不直接读取散落配置，而使用运行快照。

### 阶段 E：MVP 冒烟与质量基线

目标：形成稳定可演示版本。

P0 任务：

| 编号 | 任务 | 交付物 | 验收标准 |
|---|---|---|---|
| E-01 | 三条核心链路自动化冒烟 | 周报、风险、会议审批 | 一键跑通 |
| E-02 | 后端核心集成测试补齐 | Runtime + Task + Approval + Artifact | Maven test 通过 |
| E-03 | 前端关键交互测试补齐 | Workbench、审批、Artifact | npm test 通过 |
| E-04 | MVP profile 初始化数据整理 | schema-mvp.sql seed 数据 | 新环境启动可演示 |
| E-05 | 演示脚本整理 | demo prompts 和验收步骤 | 可按脚本稳定展示 |

阶段验收：

- 新环境启动后能按照固定脚本完成完整演示。
- 前端 typecheck、test、build 通过。
- 后端 test 通过。

## 5. 暂不进入当前主线

以下能力保留架构边界，但不阻塞 MVP：

- TypeScript Runtime。
- MCP Registry。
- 完整多 Agent 协作。
- 完整 Workflow 画布。
- 多领域 Agent 模板市场。
- 完整模型管理后台。
- 完整工具管理后台。
- 完整审计/成本/租户管理后台。
- 企业聊天渠道接入。
- RPA 或浏览器自动化执行。

## 6. 推荐执行顺序

当前最优先执行：

1. A-01：定义三类交付物结构。
2. A-02：Runtime 按任务类型生成结构化结果。
3. A-05：前端交付物预览增强。
4. B-01/B-02：Workbench 默认知识库接入 Runtime 检索。
5. B-03：交付物来源展示。
6. C-01/C-02：审批拒绝和任务取消路径。
7. D-01：Runtime snapshot 接入 AgentVersion。
8. E-01：固定三条 MVP 冒烟链路。

## 7. 当前开发原则

- 每次只围绕一个 Agent 用户价值闭环改动，不横向扩展管理后台。
- 后端平台模块服务于 Agent 执行闭环，不把 CRUD 完整后台作为当前目标。
- 前端第一屏必须保持 Agent Workbench，不改回传统 Dashboard。
- 所有高风险工具调用必须走审批，不允许 Runtime 绕过策略。
- SQL 保持通用字段规范：id、bid、tenant_id、created_by、created_at、updated_by、updated_at、deleted。
- 新增功能必须有最小测试覆盖，优先覆盖任务、审批、Runtime、交付物链路。
