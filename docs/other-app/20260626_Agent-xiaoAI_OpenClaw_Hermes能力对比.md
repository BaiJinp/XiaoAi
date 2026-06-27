# Agent-xiaoAI、OpenClaw、Hermes Agent 能力对比

> 日期：2026-06-26  
> 范围：基于当前 `Agent-xiaoAI` 代码现状，以及 OpenClaw / Hermes Agent 公开资料进行能力对比。  
> 目的：判断当前 Agent 平台与外部开源 Agent 的能力差异，为后续企业数字员工平台建设确定补强方向。

---

## 1. 结论摘要

当前 `Agent-xiaoAI` 已经不是简单 Agent Demo，而是具备企业平台特征的 **企业级可控 Agent 平台 MVP+**。

和 OpenClaw、Hermes Agent 相比：

- **Agent-xiaoAI 强在企业治理与平台化能力**：多租户、任务运行、运行事件、知识库、记忆、工具受控执行、审批/审计、安全、成本预算、Skill 沉淀、子 Agent 并行。
- **OpenClaw 强在开箱即用和多渠道生态**：聊天渠道、技能生态、插件市场、个人助理自动化。
- **Hermes Agent 强在开发者终端体验、自我学习、执行后端和多模态能力**：终端 UX、持久记忆、自进化技能、隔离子代理、Docker/SSH/Modal/Daytona 等执行后端、视觉/TTS/图像等能力。

一句话判断：

> 如果目标是企业数字员工平台，`Agent-xiaoAI` 的架构方向更适合继续演进；如果目标是开箱即用个人 Agent 或开发者终端 Agent，OpenClaw / Hermes 当前成熟度更高。

---

## 2. 三者定位对比

| 维度 | Agent-xiaoAI | OpenClaw | Hermes Agent |
|---|---|---|---|
| 核心定位 | 企业数字员工 / Agent 平台后端 | 开源自治 AI Agent / 多渠道个人助手 | 终端优先、自我改进的开发者/研究型 Agent |
| 主要优势 | 企业治理、任务运行、受控工具、知识库、记忆、Skill、子 Agent | 多渠道、技能生态、开箱自动化 | 终端体验、执行后端、自我学习、子代理、多模态 |
| 更适合 | 企业内部数字员工平台 | 个人助理、多聊天渠道自动化 | 长期任务、终端自动化、研究/开发辅助 |
| 部署倾向 | 私有化企业后端 | 自托管 + managed option | 本地/容器/远程后端 |
| 成熟度判断 | 企业平台 MVP+ | 产品化开源 Agent | 开发者/研究型 Agent 产品 |

---

## 3. Agent-xiaoAI 当前能力盘点

### 3.1 Agent 执行循环

当前 Agent 已经实现标准 Agent Loop：

```text
observe → plan → act → reflect
```

能力特点：

- 最大循环次数：5
- 最大执行时间：5 分钟
- 每轮执行观察、规划、行动、反思
- 反思结果可决定任务完成或继续调整计划

代码依据：

- `backend/src/main/java/com/xiaoai/agent/runtime/engine/DefaultAgentLoopExecutor.java`
- `backend/src/main/java/com/xiaoai/agent/runtime/engine/AgentLoopExecutor.java`

能力判断：已具备多步 Agent 运行能力，不是单轮 ChatBot。

---

### 3.2 计划步骤能力

当前执行计划支持三类步骤：

| 步骤类型 | 能力说明 |
|---|---|
| `knowledge_retrieve` | 调用知识库检索，支持 RAG 场景 |
| `tool_call` | 调用平台工具，支持外部动作执行 |
| `model_call` | 执行中间模型推理步骤 |

这说明当前 Agent 可以完成：

1. 检索企业知识；
2. 调用受控工具；
3. 多轮模型推理；
4. 根据反思结果调整执行。

---

### 3.3 知识库 / RAG

当前 Agent 已支持知识检索步骤：

- 指定 `knowledgeBaseId`
- 指定 query
- 支持 `topK`
- 调用 `KnowledgeDocumentService.retrieve`
- 将知识片段加入后续上下文

能力判断：适合企业制度、产品文档、操作手册、业务知识问答等场景。

---

### 3.4 记忆能力

当前项目已经具备结构化记忆能力。

记忆字段包括：

- `agentId`
- `agentVersionId`
- `taskId`
- `runId`
- `sessionId`
- `userId`
- `memoryType`
- `memoryScope`
- `summaryText`
- `sourceText`
- `confidence`
- `policyJson`

并且最新代码中已经具备运行中自动抽取记忆的能力：

- 从执行过程中抽取 `ExtractedMemory`
- 创建 `CreateAgentMemoryCommand`
- 调用 `agentMemoryService.createConfirmedMemory`
- 记录 `MEMORY_EXTRACTED` / `MEMORIES_SAVED` 事件

能力判断：

> Agent-xiaoAI 已经从“手动记忆存储”进入“运行中自动记忆抽取”的阶段，但距离 Hermes 那种成熟自我学习闭环仍有差距。

---

### 3.5 Skill 系统

当前项目已具备 Skill 系统雏形。

`Skill` 实体支持：

- `skillCode`
- `skillName`
- `description`
- `skillType`
- `triggerConditionJson`
- `contentJson`
- `sourceTaskId`
- `usageCount`
- `successCount`
- `successRate`
- `version`
- `status`
- `tagsJson`
- `createdByUserId`
- `lastUsedAt`

Skill 类型包括：

| Skill 类型 | 说明 |
|---|---|
| `workflow` | 工作流型技能 |
| `tool_chain` | 工具链型技能 |
| `prompt_template` | 提示词模板型技能 |
| `decision_rule` | 决策规则型技能 |

`SkillService` 支持：

- 创建技能
- 根据 code 获取技能
- 搜索技能
- 记录技能使用
- 基于反馈改进技能
- 从任务中自动提取技能
- 获取热门技能
- 废弃技能

能力判断：

> Agent-xiaoAI 已经有企业平台型 Skill 系统基础，但技能生态规模、自动提取质量、执行闭环和分发机制还不如 OpenClaw / Hermes。

---

### 3.6 子 Agent 并行能力

当前项目有 `SubAgent` 模块，支持：

- 创建子 Agent
- 根据父任务查询子 Agent
- 查询 pending/running 子 Agent
- 取消子 Agent
- 并行执行多个子 Agent
- 聚合执行结果

并行执行能力：

- 固定线程池：10
- 每个子 Agent 调用完整 Agent Loop
- 聚合成功数、失败数、Token 使用、合并结果

能力判断：

| 能力 | 当前状态 |
|---|---|
| 子 Agent 实体 | 已具备 |
| 并行执行 | 已具备 |
| 结果聚合 | 已具备 |
| 状态更新 | 已具备 |
| 子 Agent 长期线程 | 未看到完整实现 |
| 子 Agent 间通信 | 未看到完整实现 |
| 多轮协作 | 仍需增强 |

---

### 3.7 工具执行与安全策略

当前工具执行器包括：

| 工具执行器 | 说明 |
|---|---|
| `BuiltinEchoToolExecutor` | 内置 echo 工具 |
| `CliToolExecutor` | CLI 工具执行 |
| `HttpToolExecutor` | HTTP 工具执行 |

#### CLI 工具安全策略

当前 CLI 工具具备以下限制：

- `toolCode` 必须以 `controlled.cli.` 开头；
- 禁止直接执行 shell：`cmd`、`powershell`、`pwsh`、`bash`、`sh`；
- 校验 payload JSON；
- 校验 payload schema；
- working directory 必须是绝对路径；
- 支持 `_policy.allowedExecutable`；
- 支持 `_policy.allowedWorkingDirectory`；
- 输出脱敏；
- 输出截断。

#### HTTP 工具安全策略

当前 HTTP 工具具备以下限制：

- `toolCode` 必须以 `controlled.http.` 开头；
- 默认禁止访问私网地址；
- 只允许 `http` / `https`；
- 支持 `_policy.allowedHost`；
- 支持 `_policy.allowedScheme`；
- 校验 header name；
- 校验 payload schema；
- 输出脱敏；
- 输出截断。

能力判断：

> Agent-xiaoAI 在“企业受控工具执行”方向明显强于通用开源 Agent。它不是单纯给 Agent 放开权限，而是在做可审计、可限制、可治理的工具执行。

---

### 3.8 沙箱执行

当前项目有 `SandboxExecutor` 和 `SandboxConfig`。

支持配置：

- `timeoutMs`
- `maxMemoryMb`
- `allowNetworkAccess`
- `allowFileSystemAccess`

工具执行会经过 `ToolExecutorRegistry` 包装进入沙箱执行，并根据风险等级配置：

| 风险等级 | timeout | memory | network | filesystem |
|---|---:|---:|---|---|
| high | 10s | 256MB | false | false |
| medium | 30s | 512MB | false | false |
| low | 60s | 1024MB | true | false |

但需要注意：

> 当前沙箱主要是线程级执行包装和超时控制，不是真正容器级 / OS 级隔离。`maxMemoryMb`、`allowNetworkAccess`、`allowFileSystemAccess` 在当前实现中没有看到底层强制执行。

因此准确状态是：

| 能力 | 当前状态 |
|---|---|
| 工具 timeout | 已具备 |
| 风险等级策略 | 已具备 |
| 线程级执行包装 | 已具备 |
| Docker 容器隔离 | 未看到 |
| OS 级内存限制 | 未看到 |
| 网络 namespace 隔离 | 未看到 |
| 文件系统挂载隔离 | 未看到 |

---

### 3.9 对话历史与会话治理

当前 `ConversationService` 支持：

- 创建对话
- 添加消息
- 获取消息列表
- 压缩对话
- 搜索对话
- 创建对话分支
- 回退到指定消息
- 导出对话
- 归档对话
- 获取统计

`Conversation` 实体支持：

- 对话编号
- 标题
- 用户 ID
- Agent ID
- 状态
- 消息数量
- 摘要
- 标签
- 父对话 ID
- 分支点消息 ID
- 最后交互时间
- 总 Token 使用量

能力判断：

> 这部分已经接近长期会话管理能力，为后续跨会话记忆、任务恢复、对话分支调试提供了基础。

---

### 3.10 成本预算能力

当前已有 `BudgetPolicy` 实体，支持：

- `targetType`
- `targetId`
- `dailyTokenLimit`
- `taskTokenLimit`
- `dailyCostLimit`
- `policyJson`
- `status`

能力判断：

> 成本预算模型已经存在，但是否已经在 ModelGateway / AgentLoop / Tool execution 中形成强制拦截，需要进一步验证。当前不能简单认定为完整预算控制闭环。

---

## 4. OpenClaw 能力概览

根据 OpenClaw 公开资料，OpenClaw 更偏“开箱即用的多渠道通用 Agent 产品”。

主要能力包括：

- MIT 开源许可；
- 支持自托管和托管服务；
- 支持多模型：OpenAI、Anthropic、Google、Mistral、Ollama、OpenAI-compatible endpoint；
- 支持 50+ 通信渠道；
- 支持 Slack、Teams、Discord；
- 支持 Telegram、WhatsApp、Signal；
- 支持 Web chat、REST API、Email；
- 支持 SMS via Twilio/Vonage；
- 声称有 5,700+ built-in skills；
- 支持 ClawHub 社区技能；
- 支持短期/长期持久记忆；
- 支持多个 Agent 同时运行；
- 支持事件驱动和主动调度。

OpenClaw 的强项：

1. 多渠道接入；
2. 大量现成技能；
3. 个人助理/通用自动化体验；
4. 社区生态；
5. 开箱部署体验。

OpenClaw 相对 Agent-xiaoAI 的不足：

1. 企业级审批、审计、预算、租户隔离不是其公开资料中的核心重点；
2. 如果用于企业数字员工平台，可能需要较多二次改造；
3. 工具受控执行和合规治理能力需要进一步验证。

---

## 5. Hermes Agent 能力概览

Hermes Agent 是 Nous Research 的开源 Agent，公开资料显示其特点包括：

- MIT 开源；
- 当前页面列出版本：v0.17.0 / 2026-06-19；
- terminal-first UX；
- persistent searchable memory；
- agent-curated memory；
- periodic nudging；
- session search：FTS5 + LLM summaries；
- 自我改进 skill loop；
- 自动从复杂任务中生成/改进技能；
- CLI + messaging；
- 支持 Telegram、Discord、Slack、WhatsApp、Signal；
- voice memo transcription；
- natural-language scheduling / cron；
- isolated subagents；
- parallel workflows；
- browser / web automation；
- vision；
- image generation；
- TTS；
- 多执行后端：local、Docker、SSH、Singularity、Modal、Daytona；
- 多模型提供商：Nous Portal、OpenRouter、NovitaAI、NVIDIA NIM、Xiaomi MiMo、z.ai/GLM、Kimi/Moonshot、MiniMax、Hugging Face、OpenAI、own endpoint。

Hermes 的强项：

1. 终端体验成熟；
2. 执行后端丰富；
3. 自我学习闭环成熟；
4. isolated subagents 和 parallel workflows 更完整；
5. 多模态能力更丰富；
6. 长期研究和轨迹压缩能力更突出。

Hermes 相对 Agent-xiaoAI 的不足：

1. 企业租户、审批、审计、预算、权限模型不是其主要公开卖点；
2. 更偏开发者工具和个人/研究型 Agent；
3. 用作企业数字员工平台仍需补企业治理层。

---

## 6. 能力矩阵

### 6.1 企业治理能力

| 能力 | Agent-xiaoAI | OpenClaw | Hermes Agent |
|---|---:|---:|---:|
| 多租户 | 强 | 未重点体现 | 未重点体现 |
| Agent 版本 | 已具备 | 部分/未确认 | 部分/未确认 |
| 任务/运行记录 | 已具备 | 已具备 | 已具备 |
| Runtime Event | 已具备 | 已具备 | 已具备 |
| 审批/权限 | 有模块和流程迹象 | 未确认 | 部分依赖后端 |
| 审计日志 | 有 audit 模块 | 未确认 | 未确认 |
| 安全事件 | 有 security event 模块 | 未确认 | 未确认 |
| 成本预算 | 有模型，执行闭环待确认 | 未确认 | 未确认 |
| 企业治理适配 | 最强 | 需二开 | 需二开 |

结论：企业治理维度，Agent-xiaoAI 最强。

---

### 6.2 Agent 执行能力

| 能力 | Agent-xiaoAI | OpenClaw | Hermes Agent |
|---|---:|---:|---:|
| Agent loop | 已具备 | 已具备 | 已具备 |
| 多步计划 | 已具备 | 已具备 | 已具备 |
| 反思/调整 | 已具备 | 已具备 | 已具备 |
| 子 Agent | 已具备 | 已具备 | 已具备 |
| 并行子 Agent | 已具备 | 已具备 | 已具备 |
| 子 Agent 长期线程 | 未看到完整实现 | 部分/未确认 | 更强 |
| 子 Agent 通信 | 未看到完整实现 | 部分/未确认 | 更强 |
| 动态异步协作 | 基础并行 | 部分/未确认 | 强 |
| 执行恢复 | 有 runtime/resume 模型，完整度待确认 | 未确认 | 部分/较强 |

结论：Agent-xiaoAI 已有子 Agent 并行，但 Hermes 在长期子代理协作上仍更强。

---

### 6.3 工具与沙箱能力

| 能力 | Agent-xiaoAI | OpenClaw | Hermes Agent |
|---|---:|---:|---:|
| HTTP 工具 | 受控实现 | 支持 | 支持 |
| CLI 工具 | 受控实现 | 支持 | 强 |
| Shell 直接执行 | 显式禁止 | 支持 | 支持 |
| 工具 schema 校验 | 已具备 | 未确认 | 未确认 |
| 工具输出脱敏 | 已具备 | 未确认 | 未确认 |
| 工具风险等级 | 已具备 | 未确认 | 未确认 |
| 线程级 timeout 沙箱 | 已具备 | 部分/未确认 | 支持 |
| 容器级沙箱 | 未看到 | 部分/未确认 | 强 |
| SSH / Modal / Daytona 后端 | 未看到 | 未确认 | 强 |
| 浏览器自动化 | 未看到 | 支持 | 支持 |
| 文件系统 workspace | 未完整看到 | 支持 | 支持 |

结论：Agent-xiaoAI 的工具安全策略更企业化；Hermes 的执行环境更强。

---

### 6.4 记忆与学习能力

| 能力 | Agent-xiaoAI | OpenClaw | Hermes Agent |
|---|---:|---:|---:|
| 持久记忆 | 已具备 | 已具备 | 已具备 |
| 运行时加载记忆 | 已具备 | 已具备 | 已具备 |
| 自动记忆抽取 | 已具备 | 部分/未确认 | 强 |
| 记忆 scope | 已具备 | 未确认 | 支持 |
| 记忆 confidence | 已具备 | 未确认 | 部分/未确认 |
| 会话搜索 | 已具备 | 部分/未确认 | 强 |
| 会话压缩 | 已具备 | 部分/未确认 | 强 |
| 自我学习闭环 | 正在形成 | 部分/未确认 | 强 |
| 从任务提取 Skill | 有接口 | 未确认 | 强 |
| Skill 自动改进 | 有接口 | 未确认 | 强 |

结论：Agent-xiaoAI 已经具备自学习基础，但 Hermes 的闭环成熟度更高。

---

### 6.5 Skill / 插件生态能力

| 能力 | Agent-xiaoAI | OpenClaw | Hermes Agent |
|---|---:|---:|---:|
| Skill 实体 | 已具备 | 已具备 | 已具备 |
| Skill 类型 | workflow/tool_chain/prompt_template/decision_rule | 大量技能 | 支持 |
| Skill 搜索 | 已具备 | 支持 | 支持 |
| Skill 使用统计 | 已具备 | 未确认 | 支持 |
| Skill 成功率 | 已具备 | 未确认 | 支持 |
| Skill 改进 | 有接口 | 未确认 | 强 |
| 从任务提取 Skill | 有接口 | 未确认 | 强 |
| Skill Hub / 市场 | 有迹象，成熟度待确认 | ClawHub | agentskills.io 标准 |
| 生态规模 | 初期 | 强 | 较强 |

结论：Agent-xiaoAI 有 Skill 系统基础，但生态规模不如 OpenClaw / Hermes。

---

### 6.6 多渠道能力

| 能力 | Agent-xiaoAI | OpenClaw | Hermes Agent |
|---|---:|---:|---:|
| REST API | 已具备 | 已具备 | 已具备 |
| Web 控制台 | 有前端计划/实现迹象 | 支持 | 部分/未确认 |
| Telegram | 未看到 | 支持 | 支持 |
| Discord | 未看到 | 支持 | 支持 |
| Slack | 未看到 | 支持 | 支持 |
| WhatsApp / Signal | 未看到 | 支持 | 支持 |
| Email | 未看到 | 支持 | 部分/未确认 |
| 企业微信 / 飞书 / 钉钉 | 未看到 | 未确认 | 未确认 |
| 定时任务 | 未完整确认 | 支持 | 强 |

结论：多渠道入口仍是 Agent-xiaoAI 短板，OpenClaw / Hermes 更成熟。

---

### 6.7 模型接入能力

| 能力 | Agent-xiaoAI | OpenClaw | Hermes Agent |
|---|---:|---:|---:|
| OpenAI-compatible | 已具备 | 支持 | 支持 |
| Embedding | 已具备 | 部分/未确认 | 部分/未确认 |
| 多 provider 管理 | 已具备 | 支持 | 支持 |
| Claude 原生 API | 未看到专用 adapter | 支持 Anthropic | 可接 Anthropic/兼容端 |
| Streaming | 需继续确认 | 支持/未确认 | 支持 |
| Structured Output | 未看到原生支持 | 未确认 | 未确认 |
| Tool Calling 原生协议 | 当前偏自研计划/工具 | 部分/未确认 | 部分/未确认 |
| Prompt Caching | 未看到 | 未确认 | 未确认 |
| 多模态模型能力 | 未看到 | 部分资料称支持 | 强 |

结论：Agent-xiaoAI 当前模型层偏 OpenAI-compatible 基础协议，后续建议补 Claude native adapter。

---

## 7. 三者综合排名

### 7.1 企业数字员工平台能力

1. Agent-xiaoAI
2. Hermes Agent
3. OpenClaw

原因：Agent-xiaoAI 的企业治理、任务运行、审批、审计、成本、知识库、工具受控执行更贴近企业数字员工平台。

---

### 7.2 开箱即用个人 Agent 能力

1. OpenClaw / Hermes Agent
2. Agent-xiaoAI

原因：Agent-xiaoAI 偏后端平台和企业能力，不是开箱个人助手。

---

### 7.3 自我学习 / Skill 进化能力

1. Hermes Agent
2. Agent-xiaoAI
3. OpenClaw

原因：Agent-xiaoAI 已有记忆抽取、Skill 提取、Skill 改进接口，但 Hermes 的 self-improving loop 更成熟。

---

### 7.4 多渠道能力

1. OpenClaw
2. Hermes Agent
3. Agent-xiaoAI

原因：OpenClaw 明确强调 50+ communication channels，Agent-xiaoAI 当前未看到完整多渠道入口。

---

### 7.5 工具安全与企业受控执行

1. Agent-xiaoAI
2. Hermes Agent
3. OpenClaw

原因：Agent-xiaoAI 已有 toolCode 白名单、schema 校验、私网限制、执行 policy、输出脱敏、风险等级等机制。

---

### 7.6 执行沙箱 / 终端自动化

1. Hermes Agent
2. OpenClaw
3. Agent-xiaoAI

原因：Hermes 明确支持多种执行后端；Agent-xiaoAI 当前沙箱还不是容器级隔离。

---

## 8. Agent-xiaoAI 当前关键短板

### 8.1 沙箱需要升级为真实隔离

当前 `SandboxExecutor` 是线程级包装，不是真正系统级沙箱。

建议后续补：

- Docker 容器隔离；
- per-run workspace；
- cgroup 内存/CPU 限制；
- 网络白名单；
- 文件系统只读/读写挂载；
- 工具执行产物管理；
- 容器生命周期管理。

---

### 8.2 Skill 执行闭环需要打通

当前已有 Skill 数据模型和服务接口，但下一步要形成闭环：

```text
任务执行成功
  → 自动提取 Skill
  → 人工/模型确认
  → Skill 入库
  → 后续任务检索 Skill
  → 注入 Agent 上下文或转成执行步骤
  → 记录成功/失败
  → 自动改进 Skill
```

重点不是再建表，而是要打通执行链路。

---

### 8.3 子 Agent 需要从“并行任务”升级为“协作线程”

当前更像：

```text
主任务创建多个子任务 → 并行执行 → 聚合结果
```

后续应演进为：

```text
主 Agent 动态委派
  → 子 Agent 独立上下文执行
  → 子 Agent 回传中间结果
  → 主 Agent 可追问/纠偏
  → 多轮协作完成任务
```

需要补：

- sub-agent thread；
- sub-agent message；
- 子 Agent event stream；
- coordinator/subagent 通信；
- 子 Agent 取消/恢复；
- 子 Agent 结果验证。

---

### 8.4 模型网关需要补 Claude 原生能力

当前 OpenAI-compatible 通用性好，但高级 Agent 能力不足。

建议新增 Claude native adapter，支持：

- streaming；
- structured outputs；
- adaptive thinking；
- prompt caching；
- tool use / strict tool use；
- stop_reason 细分；
- refusal handling；
- context compaction。

尤其是 `ExecutionPlan` 的 JSON 输出，建议优先用 structured output，降低计划解析不稳定风险。

---

### 8.5 多渠道入口仍需补齐

如果目标是中国企业数字员工，优先级建议：

1. 企业微信；
2. 飞书；
3. 钉钉；
4. 邮件；
5. Webhook；
6. REST API trigger；
7. 定时任务 trigger；
8. Web 控制台。

OpenClaw / Hermes 的 Telegram、Discord、Slack 等渠道可以参考，但不应优先于企业微信/飞书/钉钉。

---

## 9. 建议路线图

### P0：夯实企业 Agent 核心闭环

1. 真实容器级 Sandbox；
2. Claude native / structured output 模型适配；
3. Tool approval → pause → resume 完整闭环；
4. 成本预算接入 runtime/model/tool；
5. Skill 检索、注入、执行、反馈、改进闭环。

---

### P1：增强多 Agent 协作

1. 子 Agent thread 化；
2. 主 Agent 与子 Agent 消息通信；
3. 子 Agent event stream；
4. 子 Agent 中途纠偏；
5. 子 Agent 结果验证和投票聚合。

---

### P2：补渠道与触发器

1. 企业微信；
2. 飞书；
3. 钉钉；
4. 邮件；
5. Webhook；
6. 定时任务；
7. REST API trigger。

---

### P3：建设 Skill / 插件生态

1. Skill Hub；
2. Skill 导入/导出；
3. Skill 版本管理；
4. Skill 权限和依赖；
5. Skill 成功率评估；
6. 从历史任务自动沉淀 Skill；
7. 人工确认后发布 Skill。

---

## 10. 最终判断

当前 `Agent-xiaoAI` 的最新代码已经体现出比较完整的企业 Agent 平台能力：

- Agent Loop；
- RAG；
- 记忆抽取；
- Skill 系统；
- 子 Agent 并行；
- 工具受控执行；
- 沙箱包装；
- 对话历史；
- 审批/审计/安全/成本模块；
- 模型供应商管理。

因此它和 OpenClaw / Hermes 的竞争关系不是“谁更像 Agent”，而是定位不同：

| 目标 | 更适合参考 |
|---|---|
| 企业数字员工平台 | Agent-xiaoAI 当前方向 |
| 开箱多渠道个人 Agent | OpenClaw |
| 终端自动化 / 自我学习 Agent | Hermes Agent |
| Skill 自进化 | Hermes Agent |
| 多渠道生态 | OpenClaw |
| 企业治理与受控执行 | Agent-xiaoAI |

最终建议：

> Agent-xiaoAI 不应直接照搬 OpenClaw 或 Hermes，而应以当前企业平台能力为主线，吸收 OpenClaw 的多渠道和 Skill Hub，吸收 Hermes 的自学习、子 Agent 协作和真实执行后端。

---

## 11. 参考来源

- [OpenClaw docs — What is OpenClaw?](https://openclawdoc.com/docs/getting-started/what-is-openclaw/)
- [OpenClaw docs — Agents overview](https://openclawdoc.com/docs/agents/overview/)
- [Open Claw Agent](https://agentopenclaw.io/)
- [Nous Research — Hermes Agent](https://nousresearch.net/hermes-agent/)
- [NousResearch/hermes-agent GitHub](https://github.com/nousresearch/hermes-agent)
- [TechRadar — How to automate workflows using open-source AI agents](https://www.techradar.com/pro/how-to-automate-workflows-using-open-source-ai-agents)
