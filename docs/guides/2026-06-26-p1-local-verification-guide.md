# P1 阶段本地验证指南

> 日期：2026-06-26  
> 目标：在本地环境中验证 P0 和 P1-1 的实现

---

## 📋 验证清单

### 1. 编译验证

```bash
cd backend
mvn clean compile
```

**预期结果**：BUILD SUCCESS

**常见问题**：

#### 问题 1：找不到 ExtractedMemory 类
**错误信息**：
```
error: cannot find symbol
  symbol:   class ExtractedMemory
```

**解决方案**：
确认 `DefaultAgentLoopExecutor.java` 中包含 `ExtractedMemory` 内部类定义。

#### 问题 2：ReflectionResult 缺少 extractedMemories 字段
**错误信息**：
```
error: cannot find symbol
  symbol:   variable extractedMemories
  location: class ReflectionResult
```

**解决方案**：
确认 `ReflectionResult` 类中包含 `private List<ExtractedMemory> extractedMemories;` 字段。

#### 问题 3：找不到 saveExtractedMemories 方法
**错误信息**：
```
error: cannot find symbol
  symbol:   method saveExtractedMemories(...)
```

**解决方案**：
确认 `saveExtractedMemories()` 方法已添加到 `DefaultAgentLoopExecutor` 类中。

---

### 2. 单元测试验证

```bash
mvn test
```

**预期结果**：所有测试通过

**新增测试**（建议添加）：

创建测试文件：`backend/src/test/java/com/xiaoai/agent/runtime/engine/MemoryExtractionTest.java`

```java
package com.xiaoai.agent.runtime.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoai.agent.memory.service.AgentMemoryService;
import com.xiaoai.agent.model.gateway.ModelGateway;
import com.xiaoai.agent.model.model.ChatModelResponse;
import com.xiaoai.agent.runtime.model.RunStartCommand;
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

class MemoryExtractionTest {

    @Mock
    private ModelGateway modelGateway;
    
    @Mock
    private ToolConfigService toolConfigService;
    
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
            null, // agentVersionService
            null, // knowledgeDocumentService
            agentMemoryService,
            objectMapper,
            eventCache
        );
    }

    @Test
    void shouldExtractAndSaveMemories() {
        // 准备测试数据
        RunStartCommand command = RunStartCommand.builder()
            .tenantId(100L)
            .userId(1000L)
            .agentId(101L)
            .taskId(1L)
            .runId(1L)
            .traceId("test-trace")
            .inputText("{\"inputText\":\"测试记忆抽取\"}")
            .build();
        
        // Mock 模型返回包含记忆的反思结果
        String reflectionJson = "{"
            + "\"complete\":true,"
            + "\"summary\":\"测试完成\","
            + "\"needsAdjustment\":false,"
            + "\"extractedMemories\":["
            + "{\"memoryType\":\"decision\",\"content\":\"测试决策\",\"confidence\":\"high\",\"scope\":\"task\"},"
            + "{\"memoryType\":\"preference\",\"content\":\"测试偏好\",\"confidence\":\"medium\",\"scope\":\"agent\"}"
            + "]}";
        
        ChatModelResponse response = ChatModelResponse.builder()
            .content(reflectionJson)
            .promptTokens(10)
            .completionTokens(20)
            .totalTokens(30)
            .modelCallLogId(1L)
            .build();
        
        when(modelGateway.chat(any())).thenReturn(response);
        
        // 执行
        // ... 调用 reflect 方法 ...
        
        // 验证记忆被保存
        verify(agentMemoryService, times(2)).createConfirmedMemory(any());
    }
}
```

---

### 3. 集成测试验证

#### 3.1 启动服务

```bash
cd backend
mvn spring-boot:run
```

**预期结果**：服务成功启动在 8080 端口

#### 3.2 API 测试

```bash
# 创建 Agent Loop 任务
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

# 查询事件
curl http://localhost:8080/api/v1/tasks/{taskId}/events \
  -H "X-Tenant-Id: 100" \
  -H "X-User-Id: 1000"
```

**预期事件序列**：

应该能看到以下记忆相关事件：

1. `AGENT_LOOP_START` - 开始 Agent 循环
2. `LOOP_STARTED` - 循环启动
3. `LOOP_ITERATION` - 第 1 次迭代
4. `OBSERVE_STARTED` / `OBSERVE_COMPLETED` - Observe 阶段
5. `PLAN_STARTED` / `PLAN_GENERATED` / `PLAN_COMPLETED` - Plan 阶段
6. `ACT_STARTED` / `STEP_STARTED` / `STEP_COMPLETED` / `ACT_COMPLETED` - Act 阶段
7. `REFLECT_STARTED` - Reflect 阶段开始
8. `MODEL_CALL` - 调用模型评估结果
9. `MEMORY_EXTRACTED` - 记忆被抽取（如果有）
10. `MEMORIES_SAVED` - 记忆保存完成（如果有）
11. `REFLECT_COMPLETED` - Reflect 阶段完成
12. `LOOP_COMPLETED` - 循环完成
13. `AGENT_LOOP_COMPLETE` - Agent 循环完成

---

### 4. 数据库验证

```bash
# 查询抽取的记忆
SELECT * FROM agent_memory 
WHERE tenant_id = 100 
  AND task_id = {你的taskId}
ORDER BY created_at DESC;
```

**预期结果**：
- 应该能看到新抽取的记忆记录
- `memory_type` 应该是 decision/preference/constraint/fact 之一
- `memory_scope` 应该是 task/session/agent 之一
- `confidence` 应该是 high/medium/low 之一
- `status` 应该是 'confirmed'

---

### 5. 前端验证

#### 5.1 启动前端

```bash
cd frontend
npm run dev
```

#### 5.2 测试记忆展示

1. 访问 http://localhost:5173
2. 创建一个 Agent Loop 任务
3. 启动任务
4. 查看任务详情页
5. 在"记忆"标签页中，应该能看到抽取的记忆

---

## 🔍 关键检查点

### 检查点 1：记忆抽取是否正确

**验证方法**：
```bash
# 查询最近抽取的记忆
SELECT 
    memory_type,
    memory_scope,
    confidence,
    summary_text,
    created_at
FROM agent_memory
WHERE tenant_id = 100
ORDER BY created_at DESC
LIMIT 10;
```

**预期结果**：
- 记忆类型多样化（decision、preference、constraint、fact）
- 置信度分布合理（high、medium、low）
- 范围分布合理（task、session、agent）

### 检查点 2：事件记录是否完整

**验证方法**：
```bash
# 查询记忆相关事件
curl http://localhost:8080/api/v1/tasks/{taskId}/events?type=MEMORY_EXTRACTED \
  -H "X-Tenant-Id: 100" \
  -H "X-User-Id: 1000"
```

**预期结果**：
- 应该能看到 `MEMORY_EXTRACTED` 事件
- 事件 payload 包含 memoryType、scope、confidence、content

### 检查点 3：记忆是否在后续任务中被使用

**验证方法**：
1. 创建一个新任务
2. 在任务详情页查看"记忆"标签页
3. 应该能看到之前抽取的记忆被加载

---

## 🐛 常见问题排查

### 问题 1：记忆没有被抽取

**可能原因**：
1. 模型没有在反思结果中返回 `extractedMemories`
2. `saveExtractedMemories()` 方法没有被调用
3. `agentMemoryService` 为 null

**排查步骤**：
1. 检查日志中是否有 `MEMORY_EXTRACTED` 事件
2. 检查模型返回的 JSON 是否包含 `extractedMemories` 字段
3. 检查 `agentMemoryService` 是否正确注入

### 问题 2：记忆保存失败

**可能原因**：
1. `CreateAgentMemoryCommand` 参数不正确
2. 数据库约束违反（如必填字段为空）
3. `agentMemoryService.createConfirmedMemory()` 抛出异常

**排查步骤**：
1. 检查日志中的错误信息
2. 检查数据库表结构，确认必填字段
3. 检查 `CreateAgentMemoryCommand` 的参数设置

### 问题 3：记忆没有在后续任务中使用

**可能原因**：
1. 记忆的 `memory_scope` 设置不正确
2. `loadRuntimeMemories()` 方法没有加载该记忆
3. 记忆的 `status` 不是 'confirmed'

**排查步骤**：
1. 检查记忆的 `memory_scope` 是否正确
2. 检查 `loadRuntimeMemories()` 方法的查询条件
3. 检查记忆的 `status` 字段

---

## ✅ 验证报告模板

完成验证后，请填写以下报告：

```markdown
# P1 阶段验证报告

## 环境信息
- Java 版本：
- Maven 版本：
- 操作系统：

## 编译结果
- [ ] 编译成功
- [ ] 无警告
- [ ] 无错误

## 测试结果
- 单元测试：
  - 总数：
  - 通过：
  - 失败：
  - 跳过：
- 集成测试：
  - 总数：
  - 通过：
  - 失败：
  - 跳过：

## 功能验证
- [ ] Agent Loop 模式可以正常启动
- [ ] Observe → Plan → Act → Reflect 循环正常
- [ ] 动态工具调用正常
- [ ] 分层上下文正常
- [ ] 记忆自动抽取正常
- [ ] 记忆保存到数据库正常
- [ ] 记忆在后续任务中使用正常

## 记忆抽取统计
- 抽取记忆总数：
- decision 类型：
- preference 类型：
- constraint 类型：
- fact 类型：

## 发现的问题
1. 
2. 
3. 

## 建议
1. 
2. 
3. 
```

---

## 📚 相关文档

| 文档 | 路径 |
|------|------|
| P0 完成总结 | `docs/plans/2026-06-26-p0-completion-summary.md` |
| P1-1 记忆抽取总结 | `docs/plans/2026-06-26-p1-1-memory-extraction-summary.md` |
| 模型配置指南 | `docs/guides/2026-06-26-model-service-configuration-guide.md` |
| 本地验证指南 | `docs/guides/2026-06-26-local-compilation-verification-guide.md` |

---

## 🎯 下一步

完成验证后：

1. **如果一切正常**：
   - 继续 P1-2 知识来源展示
   - 继续 P1-3 Prompt 注入防护

2. **如果发现问题**：
   - 根据问题排查指南修复
   - 重新验证
   - 如无法解决，提供错误日志

---

*请完成验证后告诉我结果，我会根据反馈继续推进！* 🚀
