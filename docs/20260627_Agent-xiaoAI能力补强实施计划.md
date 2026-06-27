# Agent-xiaoAI 能力补强实施计划

> 日期：2026-06-27  
> 基于：《Agent-xiaoAI、OpenClaw、Hermes Agent 能力对比》  
> 目的：针对对比文档识别的 6 项关键短板，给出具体的代码级实施方案、涉及文件和验收标准。

---

## 现状总结

通过代码扫描确认的 6 项关键短板：

| # | 短板 | 现状 | 优先级 |
|---|------|------|--------|
| 1 | Skill 执行闭环 | `SkillExtractor` 可提取、`SkillExecutor` 可匹配，但 `DefaultAgentLoopExecutor` 中 **零调用**；4 个 apply 方法全是 TODO 空壳 | P0 |
| 2 | 模型网关能力 | 仅 `OpenAiCompatibleModelGateway` 一个同步适配器；无 Claude adapter、无 structured output、无 streaming | P0 |
| 3 | 成本预算闭环 | `BudgetAwareModelGateway` 已有预算检查，但工具执行成本追踪和预算超限阻断待验证 | P0 |
| 4 | 子 Agent 协作 | 仅 fire-and-forget 并行，无中间结果回传、无消息通信、无 event stream、无中途纠偏 | P1 |
| 5 | 多渠道入口 | `PlatformAdapter` 接口存在，但仅有未完成的 `TelegramAdapter` 空壳；无企业微信/飞书/钉钉 | P2 |
| 6 | 浏览器自动化 | 完全缺失，无 Selenium/Playwright 任何依赖 | P2 |

> **暂缓项**：容器级沙箱（当前 `SandboxExecutor` 为线程级 timeout 包装，资源限制配置未强制执行）——暂不执行，列为 P3 后续规划。

---

## P3（暂缓）：容器级沙箱

> **状态**：暂缓执行，列为后续规划。  
> **当前状态**：`SandboxExecutor` 为线程级 timeout 包装，`maxMemoryMb`/`allowNetworkAccess` 等配置字段存在但从未强制执行。  
> **后续方向**：当企业客户对安全隔离有明确要求时再启动。方案包括 Docker 容器隔离、cgroup 资源限制、per-run workspace、网络白名单等，预计需要 2-3 周。

---

## P0：Skill 执行闭环（预计 2 周）

### 问题定位

- `SkillExecutor` 的 4 个 apply 方法（`applyWorkflowSkill`/`applyToolChainSkill`/`applyPromptTemplateSkill`/`applyDecisionRuleSkill`）全部是 TODO 空壳
- `DefaultAgentLoopExecutor.execute()` 的 observe-plan-act-reflect 循环中 **零调用** SkillExecutor
- Skill 可以提取入库，但执行时从不检索和使用

### 实施方案

#### Phase 1：打通 Skill 检索注入链路（Week 1）

**修改 `DefaultAgentLoopExecutor.java`**：

在 observe 阶段之后、plan 阶段之前，注入 Skill 检索逻辑：

```java
// 当前代码结构（简化）：
// Phase 1: Observe → 收集 knowledge/tool context
// Phase 2: Plan → 构建 prompt，调用模型
// Phase 3: Act → 执行步骤
// Phase 4: Reflect → 评估结果

// 修改后：
// Phase 1: Observe
// Phase 1.5: Skill Retrieve  ← 新增
// Phase 2: Plan（将 matched skills 注入 prompt）
// Phase 3: Act
// Phase 4: Reflect
// Phase 5: Skill Extract & Improve  ← 新增
```

**具体修改点**：

```java
// DefaultAgentLoopExecutor.java 的 execute() 方法中

// Phase 1.5: 检索匹配的 Skill
List<Skill> matchedSkills = skillExecutor.matchSkills(
    taskInput.getUserMessage(),
    agentVersionId,
    topK  // 取 top 3 最相关 skill
);

// 将 skill 内容注入到 LayeredContext 中
if (!matchedSkills.isEmpty()) {
    contextPackage.addSkillContext(
        skillExecutor.buildSkillContext(matchedSkills)
    );
}
```

#### Phase 2：实现 4 个 Skill Apply 方法（Week 1-2）

**`applyWorkflowSkill`**：将 workflow 型 skill 的 `contentJson` 解析为预定义的执行步骤序列，注入到 plan 中作为推荐执行路径。

**`applyToolChainSkill`**：将 tool_chain 型 skill 解析为工具调用链，在 act 阶段按序预编排工具调用。

**`applyPromptTemplateSkill`**：将 prompt_template 型 skill 作为 system prompt 的补充片段注入到 plan 阶段的 prompt 中。

**`applyDecisionRuleSkill`**：将 decision_rule 型 skill 解析为条件-动作规则，在 reflect 阶段用于结果校验。

#### Phase 3：Skill 自动改进闭环（Week 2）

在 Reflect 阶段之后，新增 Skill 反馈环节：

```java
// Phase 5: Skill feedback
if (!matchedSkills.isEmpty()) {
    boolean taskSuccess = reflectResult.isSuccess();
    for (Skill skill : matchedSkills) {
        skillService.recordSkillUsage(
            skill.getSkillCode(), 
            taskSuccess,
            taskRun.getTokensUsed()
        );
    }
    
    // 如果连续失败，触发 skill 改进
    if (!taskSuccess && shouldImproveSkill(matchedSkills)) {
        skillImprovementService.improveAsync(
            matchedSkills, 
            taskRun.getExecutionTrace()
        );
    }
}
```

#### 验收标准

- [ ] 用户输入可匹配到已入库 Skill
- [ ] 匹配到的 Skill 内容被注入到 plan prompt 中
- [ ] workflow/tool_chain 型 Skill 可转化为实际执行步骤
- [ ] 任务完成后 Skill 使用记录被更新（成功率、使用次数）
- [ ] 集成测试覆盖完整闭环：提取 → 入库 → 检索 → 注入 → 执行 → 反馈

---

## P1：子 Agent 协作线程化（预计 3 周）

### 问题定位

- `SubAgentExecutor.executeParallel()` 是纯 fire-and-forget：提交所有子任务，等待全部完成，聚合结果
- 无中间结果回传机制
- 无子 Agent 间或父子 Agent 间消息通信
- 无 event stream，父 Agent 无法感知子 Agent 执行进度

### 实施方案

#### Phase 1：子 Agent Event Stream（Week 1）

**新增文件**：

```
backend/src/main/java/com/xiaoai/agent/subagent/
├── event/
│   ├── SubAgentEvent.java              # 子 Agent 事件基类
│   ├── SubAgentProgressEvent.java      # 进度事件（步骤完成、中间输出）
│   ├── SubAgentResultEvent.java        # 结果事件
│   └── SubAgentEventStream.java        # 事件流管理器
├── message/
│   ├── SubAgentMessage.java            # 消息实体
│   ├── SubAgentMessageBus.java         # 消息总线
│   └── MessageDirection.java           # PARENT_TO_CHILD / CHILD_TO_PARENT / SIBLING
```

**核心改造**：

```java
// SubAgentExecutor 改造为支持 event stream
public class SubAgentExecutor {
    
    // 每个子 Agent 执行时发布事件
    public void executeWithStream(SubAgent subAgent, ContextPackage context) {
        // 包装 agentLoopExecutor，在每轮 observe/plan/act/reflect 后发布事件
        AgentLoopExecutor instrumentedExecutor = new EventPublishingExecutor(
            agentLoopExecutor,
            eventStream  // 通过 eventStream 向父 Agent 发布进度
        );
        
        instrumentedExecutor.execute(context);
    }
}

// 父 Agent 可通过 eventStream 订阅子 Agent 进度
public class SubAgentEventStream {
    private final Sinks.Many<SubAgentEvent> sink = Sinks.many().multicast().onBackpressureBuffer();
    
    public Flux<SubAgentEvent> subscribe(String parentRunId) { ... }
    public void publish(SubAgentEvent event) { ... }
}
```

#### Phase 2：父子 Agent 消息通信（Week 2）

**新增数据模型**：

```java
@Entity
@Table(name = "sub_agent_message")
public class SubAgentMessage {
    private String messageCode;
    private String parentSubAgentCode;    // 发送方
    private String targetSubAgentCode;    // 接收方（null = 广播）
    private String direction;             // PARENT_TO_CHILD / CHILD_TO_PARENT
    private String messageType;           // TEXT / PROGRESS / RESULT / CANCEL
    private String content;               // 消息内容
    private String status;                // PENDING / DELIVERED / READ
    private LocalDateTime createdAt;
}
```

**父 Agent 中途纠偏能力**：

```java
// 父 Agent 可在子 Agent 执行中发送指令
public class SubAgentCoordinator {
    
    // 发送纠偏指令给运行中的子 Agent
    public void sendCorrection(String subAgentCode, String correction) {
        SubAgentMessage msg = new SubAgentMessage();
        msg.setDirection("PARENT_TO_CHILD");
        msg.setMessageType("CORRECTION");
        msg.setContent(correction);
        messageBus.send(subAgentCode, msg);
    }
    
    // 子 Agent 在每轮 observe 阶段检查是否有新消息
    // 如果有 CORRECTION 消息，注入到上下文中重新规划
}
```

#### Phase 3：子 Agent 结果验证与投票（Week 3）

```java
// 多子 Agent 结果聚合策略
public interface ResultAggregationStrategy {
    AggregatedResult aggregate(List<SubAgentResult> results);
}

// 实现：
// - MajorityVoteAggregation   → 多数投票
// - ConsensusAggregation      → 共识验证
// - BestScoreAggregation      → 最优得分
// - MergeAllAggregation       → 全量合并
```

#### 验收标准

- [ ] 父 Agent 可实时订阅子 Agent 执行进度
- [ ] 父 Agent 可在子 Agent 运行中发送纠偏指令
- [ ] 子 Agent 可将中间结果回传给父 Agent
- [ ] 支持多种结果聚合策略
- [ ] 集成测试：父 Agent 创建 3 个子 Agent，中途纠偏其中一个，最终聚合结果

---

## P0：模型网关增强（预计 2-3 周）

### 问题定位

- 仅 `OpenAiCompatibleModelGateway` 一个适配器，且是同步阻塞调用
- 无 Claude/Anthropic 原生 adapter
- 无 structured output 支持（`response_format` 参数未传递）
- 无模型响应 streaming（token-by-token）
- `ExecutionPlan` 的 JSON 解析依赖文本解析，存在不稳定风险

### 实施方案

#### Phase 1：Streaming 支持（Week 1）

**修改 `OpenAiCompatibleModelGateway.java`**：

```java
// 新增 streaming chat 方法
public Flux<ChatModelChunk> chatStream(ChatModelCommand command) {
    // 1. 设置 stream: true
    // 2. 使用 WebClient 发起 SSE 请求
    // 3. 解析 SSE data: 行
    // 4. 逐 chunk 返回 Flux<ChatModelChunk>
}
```

**修改 `ModelGateway.java` 接口**：

```java
public interface ModelGateway {
    ChatModelResponse chat(ChatModelCommand command);           // 已有
    Flux<ChatModelChunk> chatStream(ChatModelCommand command);  // 新增
    EmbeddingResponse embedding(EmbeddingCommand command);      // 已有
}
```

**修改 `StreamingService.java`**：

将模型 streaming 与客户端 SSE streaming 打通，实现真正的 token-by-token 前端流式输出。

#### Phase 2：Claude Native Adapter（Week 1-2）

**新增文件**：

```
backend/src/main/java/com/xiaoai/agent/model/gateway/anthropic/
├── AnthropicModelGateway.java          # Claude 原生 API 适配器
├── AnthropicChatRequest.java           # Messages API 请求体
├── AnthropicChatResponse.java          # Messages API 响应体
├── AnthropicSupport.java               # 协议转换工具
└── AnthropicStreamParser.java          # SSE stream 解析
```

**核心能力映射**：

| Claude 能力 | 实现方式 |
|------------|---------|
| streaming | SSE 解析 `event: content_block_delta` |
| structured outputs | `response_format: { type: "json_schema", ... }` |
| extended thinking | `thinking: { type: "enabled", budget_tokens: N }` |
| prompt caching | `cache_control: { type: "ephemeral" }` 标记 |
| tool use | `tools: [...]` + `tool_choice: { type: "auto/any/tool" }` |
| stop_reason 细分 | 解析 `stop_reason`: end_turn / max_tokens / tool_use / stop_sequence |

**修改 `RoutingModelGateway.java`**：

注册 `anthropic` provider type，路由到 `AnthropicModelGateway`。

#### Phase 3：Structured Output 支持（Week 2-3）

**核心目标**：`ExecutionPlan` 的 JSON 输出使用 structured output，消除解析不稳定。

**修改 `DefaultAgentLoopExecutor.java`**：

```java
// 当前：模型返回文本 → 正则/JSON parse 提取 ExecutionPlan
// 改造后：使用 structured output 强制返回 JSON schema

ChatModelCommand planCommand = buildPlanPrompt(context);
planCommand.setResponseFormat(ResponseFormat.jsonSchema(
    "execution_plan",
    ExecutionPlan.JSON_SCHEMA  // 定义 ExecutionPlan 的 JSON Schema
));

ChatModelResponse response = modelGateway.chat(planCommand);
ExecutionPlan plan = response.getParsedContent(ExecutionPlan.class);  //  guaranteed valid JSON
```

#### 验收标准

- [ ] 模型调用支持 streaming，前端可逐 token 显示
- [ ] Claude API 调用正常工作（chat + stream + tool_use）
- [ ] ExecutionPlan 使用 structured output，连续 100 次计划生成无解析失败
- [ ] prompt caching 在连续对话中降低 token 消耗
- [ ] 现有 OpenAI-compatible 调用不受影响

---

## P0：成本预算闭环验证（预计 1 周）

### 问题定位

对比文档指出"成本预算执行闭环待确认"。代码扫描发现：

- `BudgetAwareModelGateway` 已实现：调用前检查预算，调用后记录用量
- `TokenBudgetTracker` 已实现：内存级 tenant-daily 和 task-level 预算检查
- `UsageRecord` 实体已存在

**但需确认**：工具执行（CLI/HTTP）的 token/成本是否也被追踪？预算超限时是否会阻断 Agent Loop？

### 实施方案

1. **工具执行成本追踪**：在 `CliToolExecutor` 和 `HttpToolExecutor` 执行后，如果工具调用涉及模型（如工具内部调用 LLM），记录额外 token 消耗
2. **预算阻断测试**：编写集成测试，设置极低预算，验证 Agent Loop 在预算耗尽时正确终止
3. **预算告警**：新增 `BudgetAlertListener`，当用量达到 80%/100% 时发布告警事件

#### 验收标准

- [ ] 所有模型调用（包括子 Agent）的 token 消耗都被记录
- [ ] 预算耗尽时 Agent Loop 正确终止并返回预算超限错误
- [ ] 预算 80% 时触发告警事件
- [ ] 集成测试通过

---

## P2：多渠道入口补齐（预计 4-6 周）

### 问题定位

- `PlatformAdapter` 接口已定义，但仅有未完成的 `TelegramAdapter` 空壳
- 无任何可用的渠道实现
- 已有 `scheduled/` 包，定时任务基础设施部分存在

### 实施方案

#### 渠道优先级与排期

| 优先级 | 渠道 | 预计工期 | 说明 |
|-------|------|---------|------|
| 1 | 企业微信 | 1.5 周 | 中国企业首选，API 文档完善 |
| 2 | 飞书 | 1.5 周 | API 结构类似企微，可复用部分代码 |
| 3 | 钉钉 | 1 周 | 机器人 API 相对简单 |
| 4 | 邮件 | 1 周 | SMTP/IMAP，标准 JavaMail |
| 5 | Webhook | 0.5 周 | 通用 HTTP 回调 |
| 6 | 定时任务 | 0.5 周 | 已有 `scheduled/` 包，补全触发链路 |

#### 统一渠道抽象增强

**修改 `PlatformAdapter.java`**：

当前接口方法偏基础，需增强：

```java
public interface PlatformAdapter {
    // 已有
    String getPlatformType();
    void sendMessage(PlatformMessage message);
    void reply(PlatformMessage original, PlatformMessage reply);
    
    // 新增
    void sendRichMessage(RichMessage message);        // 支持 Markdown/卡片消息
    void sendFile(FileMessage message);               // 文件发送
    void sendInteractiveCard(CardMessage message);     // 交互式卡片（审批、确认等）
    PlatformUserInfo getUserInfo(String userId);       // 获取用户信息
    boolean supportsFeature(PlatformFeature feature); // 能力探测
}
```

#### 企业微信 Adapter 示例结构

```
backend/src/main/java/com/xiaoai/agent/platform/adapter/
├── wecom/
│   ├── WecomAdapter.java               # 企业微信适配器
│   ├── WecomApiClient.java             # 企微 API 客户端
│   ├── WecomMessageConverter.java      # 消息格式转换
│   ├── WecomWebhookController.java     # 接收企微回调
│   └── WecomConfig.java               # 企微配置（corpId, agentId, secret）
```

#### 验收标准

- [ ] 企业微信：可在企微群/单聊中与 Agent 对话，完成完整任务
- [ ] 飞书：可在飞书群/单聊中与 Agent 对话
- [ ] 钉钉：可在钉钉群中与机器人对话
- [ ] 所有渠道的消息都能正确路由到 RuntimeGateway 并启动任务
- [ ] 任务结果能正确回传到对应渠道

---

## P2：浏览器自动化（预计 2 周）

### 问题定位

完全缺失，无任何浏览器自动化代码或依赖。

### 实施方案

#### Phase 1：Playwright 集成（Week 1）

**新增依赖**（pom.xml）：

```xml
<dependency>
    <groupId>com.microsoft.playwright</groupId>
    <artifactId>playwright</artifactId>
    <version>1.44.0</version>
</dependency>
```

**新增文件**：

```
backend/src/main/java/com/xiaoai/agent/tool/executor/
├── BrowserToolExecutor.java            # 浏览器工具执行器
└── browser/
    ├── BrowserSession.java             # 浏览器会话管理
    ├── BrowserAction.java              # 浏览器操作（navigate, click, type, screenshot, extract）
    ├── BrowserActionResult.java        # 操作结果
    └── BrowserSessionPool.java         # 会话池管理
```

**工具定义**：

```json
{
    "toolCode": "controlled.browser.navigate",
    "description": "导航到指定 URL 并获取页面内容",
    "inputSchema": {
        "url": "string",
        "waitForSelector": "string (optional)",
        "screenshot": "boolean (optional)"
    }
}
```

**安全策略**：

- 遵循现有 `controlled.` 前缀命名规范
- 复用 `SandboxConfig` 的网络白名单策略
- 使用线程级沙箱 timeout 控制（容器级沙箱暂缓）
- 禁止访问内网地址（复用 HTTP 工具的私网限制）

#### Phase 2：页面智能提取（Week 2）

- 支持 `extract_text`：提取页面正文
- 支持 `extract_table`：提取页面表格为结构化数据
- 支持 `screenshot`：页面截图
- 支持 `fill_form`：自动填写表单
- 支持 `click_element`：点击指定元素

#### 验收标准

- [ ] 浏览器工具可导航到公开网站并提取内容
- [ ] 支持截图功能
- [ ] 在线程级沙箱中运行，timeout 控制生效
- [ ] 受控工具安全策略生效（URL 白名单、私网限制）

---

## 总体排期

```
Week 1-2:  [P0] Skill 执行闭环 + Streaming 支持 + Claude Adapter
Week 2-3:  [P0] Structured Output + 成本预算闭环验证
Week 3-5:  [P1] 子 Agent Event Stream + 消息通信 + 结果聚合
Week 5-7:  [P2] 企业微信 + 飞书
Week 7-8:  [P2] 钉钉 + 邮件 + Webhook
Week 8-10: [P2] 浏览器自动化
```

**总计约 10 周（2.5 个月）**，可将 Agent-xiaoAI 从 MVP+ 提升到与 OpenClaw / Hermes Agent 全面对标的企业级 Agent 平台。

---

## 关键里程碑

| 里程碑 | 时间 | 达成条件 |
|--------|------|---------|
| M1: 模型能力升级 | Week 2 | Streaming + Claude Adapter + Structured Output 可用 |
| M2: Skill 闭环 | Week 2 | Skill 提取→检索→注入→执行→反馈全链路打通 |
| M3: 预算闭环 | Week 3 | 成本预算全链路验证通过 |
| M4: 协作升级 | Week 5 | 子 Agent 支持 event stream + 中途纠偏 |
| M5: 多渠道上线 | Week 8 | 企微/飞书/钉钉至少 2 个渠道可用 |
| M6: 全面对标 | Week 10 | 浏览器自动化上线，所有短板补齐 |
