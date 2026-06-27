# Agent 循环执行器集成总结

> 日期：2026-06-26  
> 目标：完成 Agent 循环执行器的实现和集成，让模型真正驱动执行流程

---

## 一、已完成的工作

### 1.1 核心组件实现

#### ✅ AgentLoopExecutor 接口
**文件**：`backend/src/main/java/com/xiaoai/agent/runtime/engine/AgentLoopExecutor.java`

定义了 Agent 循环执行器的标准接口，包含：
- `execute()` 方法：执行 Agent 循环
- `AgentLoopResult` 内部类：封装执行结果

#### ✅ DefaultAgentLoopExecutor 实现
**文件**：`backend/src/main/java/com/xiaoai/agent/runtime/engine/DefaultAgentLoopExecutor.java`

实现了完整的 observe → plan → act → reflect 循环：

**核心功能**：
1. **循环控制**：最大 5 次迭代，5 分钟超时保护
2. **Observe 阶段**：收集上下文（知识、工具调用结果、记忆）
3. **Plan 阶段**：调用模型生成执行计划（JSON 格式）
4. **Act 阶段**：执行计划中的步骤（知识检索、工具调用、模型调用）
5. **Reflect 阶段**：调用模型评估结果，决定是否继续

**关键特性**：
- 支持动态工具调用（由模型决策）
- 上下文增强（知识、工具结果、记忆注入 prompt）
- 错误处理和恢复机制
- 事件记录（所有阶段都有详细的 RuntimeEvent）

#### ✅ ExecutionPlan 数据模型
**文件**：`backend/src/main/java/com/xiaoai/agent/runtime/engine/ExecutionPlan.java`

定义了执行计划的结构：
- `goal`：执行目标
- `steps`：执行步骤列表
- `outputType`：预期输出类型
- `ExecutionStep`：单个步骤定义（支持 knowledge_retrieve、tool_call、model_call）

#### ✅ ExecutionMode 扩展
**文件**：`backend/src/main/java/com/xiaoai/agent/runtime/engine/ExecutionMode.java`

新增 `AGENT_LOOP` 执行模式，支持切换到新的循环执行器。

#### ✅ JavaInProcessRuntimeGateway 集成
**文件**：`backend/src/main/java/com/xiaoai/agent/runtime/gateway/JavaInProcessRuntimeGateway.java`

**修改内容**：
1. 添加 `AgentLoopExecutor` 依赖注入
2. 修改 `startRun()` 方法，根据 `ExecutionMode` 路由到不同执行器
3. 新增 `executeAgentLoop()` 方法：执行 Agent 循环
4. 新增 `recordAgentLoopArtifact()` 方法：记录循环执行结果
5. 新增 `buildAgentLoopArtifactContent()` 方法：生成交付物内容

---

## 二、代码审查要点

### 2.1 编译检查清单

请在本地执行以下编译检查：

```bash
cd backend
mvn clean compile
```

**预期结果**：编译成功，无错误

### 2.2 常见问题排查

#### 问题 1：找不到 AgentLoopExecutor
**原因**：`DefaultAgentLoopExecutor` 没有正确实现 `AgentLoopExecutor` 接口

**解决**：检查 `DefaultAgentLoopExecutor` 类是否正确实现了 `execute()` 方法

#### 问题 2：找不到 ExecutionPlan 类
**原因**：`ExecutionPlan.java` 文件未创建或路径错误

**解决**：确认文件存在于 `backend/src/main/java/com/xiaoai/agent/runtime/engine/ExecutionPlan.java`

#### 问题 3：DefaultAgentLoopExecutor 构造函数参数不匹配
**原因**：构造函数参数顺序或类型错误

**解决**：检查 `JavaInProcessRuntimeGateway` 中创建 `DefaultAgentLoopExecutor` 的参数是否正确：
```java
new DefaultAgentLoopExecutor(
    modelGateway,           // ModelGateway
    toolConfigService,      // ToolConfigService
    knowledgeDocumentService, // KnowledgeDocumentService
    agentMemoryService,     // AgentMemoryService
    objectMapper,           // ObjectMapper
    eventCache              // Map<Long, List<RuntimeEvent>>
)
```

---

## 三、测试指南

### 3.1 单元测试

创建测试文件：`backend/src/test/java/com/xiaoai/agent/runtime/engine/DefaultAgentLoopExecutorTest.java`

```java
package com.xiaoai.agent.runtime.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.knowledge.service.KnowledgeDocumentService;
import com.xiaoai.agent.memory.service.AgentMemoryService;
import com.xiaoai.agent.model.gateway.ModelGateway;
import com.xiaoai.agent.model.model.ChatModelResponse;
import com.xiaoai.agent.runtime.model.RunStartCommand;
import com.xiaoai.agent.runtime.model.RuntimeEvent;
import com.xiaoai.agent.tool.service.ToolConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DefaultAgentLoopExecutorTest {

    @Mock
    private ModelGateway modelGateway;
    
    @Mock
    private ToolConfigService toolConfigService;
    
    @Mock
    private KnowledgeDocumentService knowledgeDocumentService;
    
    @Mock
    private AgentMemoryService agentMemoryService;
    
    private DefaultAgentLoopExecutor executor;
    private ObjectMapper objectMapper;
    private Map<Long, List<RuntimeEvent>> eventCache;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        eventCache = new java.util.concurrent.ConcurrentHashMap<>();
        
        executor = new DefaultAgentLoopExecutor(
            modelGateway,
            toolConfigService,
            knowledgeDocumentService,
            agentMemoryService,
            objectMapper,
            eventCache
        );
    }

    @Test
    void shouldExecuteAgentLoopSuccessfully() {
        // 准备测试数据
        RunStartCommand command = RunStartCommand.builder()
            .tenantId(100L)
            .userId(1000L)
            .agentId(101L)
            .agentVersionId(1001L)
            .taskId(1L)
            .runId(1L)
            .traceId("test-trace")
            .inputText("{\"inputText\":\"分析项目风险\"}")
            .runtimeSnapshotJson("{}")
            .build();
        
        ContextPackage context = ContextPackage.builder()
            .tenantId(100L)
            .userId(1000L)
            .agentId(101L)
            .agentVersionId(1001L)
            .taskId(1L)
            .runId(1L)
            .inputText("分析项目风险")
            .runtimeSnapshotJson("{}")
            .executionMode(ExecutionMode.AGENT_LOOP)
            .build();
        
        // Mock 模型调用返回执行计划
        String planJson = "{\"goal\":\"分析项目风险\",\"steps\":[{\"stepId\":\"1\",\"stepType\":\"model_call\",\"description\":\"生成风险分析\",\"modelId\":1,\"prompt\":\"请分析以下项目的风险：分析项目风险\"}],\"outputType\":\"risk_analysis\"}";
        ChatModelResponse planResponse = ChatModelResponse.builder()
            .content(planJson)
            .promptTokens(10)
            .completionTokens(20)
            .totalTokens(30)
            .modelCallLogId(1L)
            .build();
        
        // Mock 模型调用返回反思结果
        String reflectionJson = "{\"complete\":true,\"summary\":\"风险分析完成\"}";
        ChatModelResponse reflectionResponse = ChatModelResponse.builder()
            .content(reflectionJson)
            .promptTokens(10)
            .completionTokens(15)
            .totalTokens(25)
            .modelCallLogId(2L)
            .build();
        
        when(modelGateway.chat(any())).thenReturn(planResponse).thenReturn(reflectionResponse);
        
        // 执行
        AgentLoopExecutor.AgentLoopResult result = executor.execute(command, context);
        
        // 验证
        assertNotNull(result);
        assertEquals("success", result.getFinalStatus());
        assertEquals(1, result.getLoopCount());
        assertTrue(result.getElapsedMs() >= 0);
        
        // 验证模型调用了 2 次（plan + reflect）
        verify(modelGateway, times(2)).chat(any());
    }

    @Test
    void shouldHandleModelCallFailure() {
        // 准备测试数据
        RunStartCommand command = RunStartCommand.builder()
            .tenantId(100L)
            .userId(1000L)
            .agentId(101L)
            .agentVersionId(1001L)
            .taskId(1L)
            .runId(1L)
            .traceId("test-trace")
            .inputText("{\"inputText\":\"测试失败场景\"}")
            .runtimeSnapshotJson("{}")
            .build();
        
        ContextPackage context = ContextPackage.builder()
            .tenantId(100L)
            .userId(1000L)
            .agentId(101L)
            .agentVersionId(1001L)
            .taskId(1L)
            .runId(1L)
            .inputText("测试失败场景")
            .runtimeSnapshotJson("{}")
            .executionMode(ExecutionMode.AGENT_LOOP)
            .build();
        
        // Mock 模型调用抛出异常
        when(modelGateway.chat(any())).thenThrow(new RuntimeException("模型调用失败"));
        
        // 执行
        AgentLoopExecutor.AgentLoopResult result = executor.execute(command, context);
        
        // 验证
        assertNotNull(result);
        assertEquals("failed", result.getFinalStatus());
        assertTrue(result.getResultSummary().contains("Execution failed"));
    }
}
```

### 3.2 集成测试

在本地启动后端服务后，可以通过以下方式测试 Agent 循环：

#### 方式 1：API 测试

```bash
# 创建一个使用 Agent Loop 模式的任务
curl -X POST http://localhost:8080/api/v1/tasks \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 100" \
  -H "X-User-Id: 1000" \
  -d '{
    "agentId": 101,
    "agentVersionId": 1001,
    "inputText": "{\"inputText\":\"分析项目风险\",\"executionMode\":\"agent_loop\"}"
  }'

# 启动任务
curl -X POST http://localhost:8080/api/v1/tasks/{taskId}/start \
  -H "X-Tenant-Id: 100" \
  -H "X-User-Id: 1000"

# 查询任务事件
curl http://localhost:8080/api/v1/tasks/{taskId}/events \
  -H "X-Tenant-Id: 100" \
  -H "X-User-Id: 1000"
```

#### 方式 2：前端测试

1. 启动后端：`cd backend && mvn spring-boot:run`
2. 启动前端：`cd frontend && npm run dev`
3. 访问前端页面，进入 Agent 工作台
4. 在 Chat 输入框中输入："分析项目风险"
5. 观察执行过程，应该能看到：
   - `AGENT_LOOP_START` 事件
   - `LOOP_STARTED` 事件
   - `LOOP_ITERATION` 事件
   - `OBSERVE_STARTED` / `OBSERVE_COMPLETED` 事件
   - `PLAN_STARTED` / `PLAN_GENERATED` / `PLAN_COMPLETED` 事件
   - `ACT_STARTED` / `STEP_STARTED` / `STEP_COMPLETED` / `ACT_COMPLETED` 事件
   - `REFLECT_STARTED` / `REFLECT_COMPLETED` 事件
   - `LOOP_COMPLETED` 事件
   - `AGENT_LOOP_COMPLETE` 事件

---

## 四、配置指南

### 4.1 启用 Agent Loop 模式

在创建任务时，通过 `inputText` 中的 `executionMode` 字段指定执行模式：

```json
{
  "inputText": "分析项目风险",
  "executionMode": "agent_loop"
}
```

或者在 Agent 版本的 `runtimeSnapshotJson` 中配置：

```json
{
  "orchestrationPolicy": {
    "executionMode": "agent_loop"
  }
}
```

### 4.2 模型配置

确保已配置真实模型服务（参见 `docs/guides/2026-06-26-model-service-configuration-guide.md`），并在 Agent 版本中绑定模型配置。

### 4.3 循环参数调整

如需调整循环参数，可以修改 `DefaultAgentLoopExecutor` 中的常量：

```java
private static final int MAX_LOOP_ITERATIONS = 5;        // 最大循环次数
private static final long MAX_EXECUTION_TIME_MS = 300_000; // 最大执行时间（毫秒）
```

---

## 五、已知限制和后续优化

### 5.1 当前限制

1. **工具描述缺失**：当前实现中，模型无法看到可用工具的详细描述，只能盲目选择工具
2. **流式输出缺失**：当前是同步执行，没有流式输出支持
3. **计划质量依赖模型**：执行计划的质量完全依赖模型能力，没有额外的校验机制
4. **上下文窗口管理**：当前没有实现上下文窗口裁剪，可能导致 token 超限

### 5.2 后续优化方向

1. **工具描述注入**：在 plan prompt 中注入可用工具的详细描述（toolCode、description、parameters schema）
2. **流式输出**：实现 SSE 流式输出，让用户实时看到执行过程
3. **计划校验**：在 plan 生成后增加校验逻辑，确保计划合理可执行
4. **上下文裁剪**：实现上下文窗口管理，避免 token 超限
5. **记忆自动抽取**：在 reflect 阶段自动抽取关键信息并保存为记忆

---

## 六、下一步行动

### 立即行动（今天）

1. **本地编译验证**
   ```bash
   cd backend
   mvn clean compile
   ```

2. **运行单元测试**
   ```bash
   mvn test -Dtest=DefaultAgentLoopExecutorTest
   ```

3. **启动服务测试**
   ```bash
   mvn spring-boot:run
   ```

4. **前端测试**
   ```bash
   cd frontend
   npm run dev
   ```

### 短期行动（本周）

1. 完成 P0-3 动态工具调用（工具描述注入）
2. 完成 P0-4 上下文管理优化（系统 prompt、用户历史）
3. 补充更多单元测试和集成测试

### 中期行动（下周）

1. 实现流式输出（SSE）
2. 实现记忆自动抽取
3. 实现知识来源展示

---

## 七、关键代码位置

| 组件 | 文件路径 |
|------|---------|
| Agent 循环接口 | `backend/src/main/java/com/xiaoai/agent/runtime/engine/AgentLoopExecutor.java` |
| 默认实现 | `backend/src/main/java/com/xiaoai/agent/runtime/engine/DefaultAgentLoopExecutor.java` |
| 执行计划模型 | `backend/src/main/java/com/xiaoai/agent/runtime/engine/ExecutionPlan.java` |
| 执行模式枚举 | `backend/src/main/java/com/xiaoai/agent/runtime/engine/ExecutionMode.java` |
| Runtime 集成 | `backend/src/main/java/com/xiaoai/agent/runtime/gateway/JavaInProcessRuntimeGateway.java` |

---

## 八、验收标准

- [x] AgentLoopExecutor 接口定义完成
- [x] DefaultAgentLoopExecutor 实现完成
- [x] ExecutionPlan 数据模型定义完成
- [x] ExecutionMode 扩展完成
- [x] JavaInProcessRuntimeGateway 集成完成
- [ ] 本地编译通过
- [ ] 单元测试通过
- [ ] 集成测试通过
- [ ] 前端端到端测试通过

---

*完成本地编译和测试后，请告诉我结果，我会继续推进下一步开发。*
