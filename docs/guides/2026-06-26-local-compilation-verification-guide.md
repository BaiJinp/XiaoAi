# Agent 循环执行器 - 本地编译验证指南

> 日期：2026-06-26  
> 目标：在本地环境中编译和验证新实现的 Agent 循环执行器

---

## 一、快速验证步骤

### 1.1 编译后端代码

```bash
cd backend
mvn clean compile
```

**预期结果**：BUILD SUCCESS

### 1.2 运行单元测试

```bash
mvn test -Dtest=DefaultAgentLoopExecutorTest
```

**预期结果**：Tests run: 2, Failures: 0, Errors: 0, Skipped: 0

### 1.3 全量测试

```bash
mvn test
```

**预期结果**：所有测试通过（当前应该有 225+ 个测试）

### 1.4 启动服务

```bash
mvn spring-boot:run
```

**预期结果**：服务成功启动在 8080 端口

---

## 二、关键代码文件清单

### 2.1 新增文件

| 文件 | 路径 | 说明 |
|------|------|------|
| AgentLoopExecutor | `backend/src/main/java/com/xiaoai/agent/runtime/engine/AgentLoopExecutor.java` | Agent 循环执行器接口 |
| DefaultAgentLoopExecutor | `backend/src/main/java/com/xiaoai/agent/runtime/engine/DefaultAgentLoopExecutor.java` | 默认实现（~600 行） |
| ExecutionPlan | `backend/src/main/java/com/xiaoai/agent/runtime/engine/ExecutionPlan.java` | 执行计划数据模型 |

### 2.2 修改文件

| 文件 | 路径 | 修改内容 |
|------|------|---------|
| ExecutionMode | `backend/src/main/java/com/xiaoai/agent/runtime/engine/ExecutionMode.java` | 新增 `AGENT_LOOP` 枚举值 |
| JavaInProcessRuntimeGateway | `backend/src/main/java/com/xiaoai/agent/runtime/gateway/JavaInProcessRuntimeGateway.java` | 集成 Agent 循环执行器，添加路由逻辑 |

---

## 三、常见编译错误及解决方案

### 错误 1：找不到符号 AgentLoopExecutor

**错误信息**：
```
error: cannot find symbol
  symbol:   class AgentLoopExecutor
  location: package com.xiaoai.agent.runtime.engine
```

**解决方案**：
1. 检查 `AgentLoopExecutor.java` 文件是否存在
2. 确认包名正确：`package com.xiaoai.agent.runtime.engine;`
3. 重新编译：`mvn clean compile`

### 错误 2：找不到符号 ExecutionPlan

**错误信息**：
```
error: cannot find symbol
  symbol:   class ExecutionPlan
  location: package com.xiaoai.agent.runtime.engine
```

**解决方案**：
1. 检查 `ExecutionPlan.java` 文件是否存在
2. 确认包含 `ExecutionStep` 内部类
3. 确认使用了 Lombok 注解：`@Getter`, `@Setter`, `@Builder`

### 错误 3：DefaultAgentLoopExecutor 构造函数不匹配

**错误信息**：
```
error: constructor DefaultAgentLoopExecutor in class DefaultAgentLoopExecutor cannot be applied to given types
```

**解决方案**：
检查 `JavaInProcessRuntimeGateway.java` 中创建 `DefaultAgentLoopExecutor` 的参数：
```java
this.agentLoopExecutor = new DefaultAgentLoopExecutor(
    modelGateway,              // ModelGateway
    toolConfigService,         // ToolConfigService
    knowledgeDocumentService,  // KnowledgeDocumentService
    agentMemoryService,        // AgentMemoryService (可能为 null)
    objectMapper,              // ObjectMapper
    eventCache                 // Map<Long, List<RuntimeEvent>>
);
```

确保 `DefaultAgentLoopExecutor` 构造函数签名匹配：
```java
public DefaultAgentLoopExecutor(
    ModelGateway modelGateway,
    ToolConfigService toolConfigService,
    KnowledgeDocumentService knowledgeDocumentService,
    AgentMemoryService agentMemoryService,
    ObjectMapper objectMapper,
    Map<Long, List<RuntimeEvent>> eventCache
)
```

### 错误 4：ExecutionMode.AGENT_LOOP 找不到

**错误信息**：
```
error: cannot find symbol
  symbol:   variable AGENT_LOOP
  location: class ExecutionMode
```

**解决方案**：
检查 `ExecutionMode.java` 是否包含：
```java
public enum ExecutionMode {
    DIRECT_TOOL("direct_tool"),
    SINGLE_AGENT("single_agent"),
    DYNAMIC_WORKFLOW("dynamic_workflow"),
    MULTI_AGENT("multi_agent"),
    AGENT_LOOP("agent_loop");  // 确保这一行存在
    ...
}
```

### 错误 5：Lombok 注解处理器未启用

**错误信息**：
```
error: cannot find symbol
  symbol:   method builder()
```

**解决方案**：
1. 检查 `pom.xml` 中是否包含 Lombok 依赖
2. 确保 IDE 已安装 Lombok 插件
3. 清理并重新导入项目：`mvn clean install`

---

## 四、集成测试指南

### 4.1 API 测试

#### 测试 1：创建 Agent Loop 任务

```bash
# 创建任务
curl -X POST http://localhost:8080/api/v1/tasks \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 100" \
  -H "X-User-Id: 1000" \
  -d '{
    "agentId": 101,
    "agentVersionId": 1001,
    "inputText": "{\"inputText\":\"分析项目风险\",\"executionMode\":\"agent_loop\"}"
  }'

# 记录返回的 taskId
```

#### 测试 2：启动任务

```bash
curl -X POST http://localhost:8080/api/v1/tasks/{taskId}/start \
  -H "X-Tenant-Id: 100" \
  -H "X-User-Id: 1000"
```

#### 测试 3：查询事件

```bash
curl http://localhost:8080/api/v1/tasks/{taskId}/events \
  -H "X-Tenant-Id: 100" \
  -H "X-User-Id: 1000"
```

**预期事件序列**：
1. `AGENT_LOOP_START` - 开始 Agent 循环
2. `LOOP_STARTED` - 循环启动
3. `LOOP_ITERATION` - 第 1 次迭代
4. `OBSERVE_STARTED` / `OBSERVE_COMPLETED` - Observe 阶段
5. `PLAN_STARTED` / `MODEL_CALL` / `PLAN_GENERATED` / `PLAN_COMPLETED` - Plan 阶段
6. `ACT_STARTED` / `STEP_STARTED` / `STEP_COMPLETED` / `ACT_COMPLETED` - Act 阶段
7. `REFLECT_STARTED` / `MODEL_CALL` / `REFLECT_COMPLETED` - Reflect 阶段
8. `LOOP_COMPLETED` - 循环完成
9. `AGENT_LOOP_COMPLETE` - Agent 循环完成
10. `ASSISTANT_ARTIFACT` - 生成交付物

### 4.2 前端测试

1. 启动后端：`cd backend && mvn spring-boot:run`
2. 启动前端：`cd frontend && npm run dev`
3. 访问 http://localhost:5173
4. 在 Chat 输入框输入："分析项目风险"
5. 观察执行时间线，应该能看到完整的 Agent 循环过程

---

## 五、代码审查检查清单

### 5.1 接口和类定义

- [ ] `AgentLoopExecutor` 接口定义了 `execute()` 方法
- [ ] `AgentLoopExecutor.AgentLoopResult` 内部类正确定义
- [ ] `DefaultAgentLoopExecutor` 正确实现了 `AgentLoopExecutor` 接口
- [ ] `ExecutionPlan` 类使用了 Lombok 注解
- [ ] `ExecutionPlan.ExecutionStep` 内部类正确定义

### 5.2 依赖注入

- [ ] `JavaInProcessRuntimeGateway` 构造函数正确注入 `AgentLoopExecutor`
- [ ] `DefaultAgentLoopExecutor` 构造函数参数顺序正确
- [ ] 所有依赖服务（ModelGateway、ToolConfigService 等）正确传递

### 5.3 执行逻辑

- [ ] `startRun()` 方法正确路由到 `executeAgentLoop()` 或 `executeCurrentRuntimePlan()`
- [ ] `executeAgentLoop()` 方法正确调用 `agentLoopExecutor.execute()`
- [ ] 循环控制逻辑（最大迭代次数、超时）正确实现
- [ ] Observe → Plan → Act → Reflect 四个阶段完整实现

### 5.4 事件记录

- [ ] 所有阶段都有对应的 `RuntimeEvent` 记录
- [ ] 事件 payload 包含必要的上下文信息
- [ ] 错误和异常都有对应的事件记录

### 5.5 错误处理

- [ ] 模型调用失败有异常处理
- [ ] 工具调用失败有异常处理
- [ ] JSON 解析失败有异常处理
- [ ] 所有异常都有日志记录

---

## 六、性能验证

### 6.1 循环次数验证

修改 `DefaultAgentLoopExecutor.java` 中的常量进行测试：

```java
// 测试小循环
private static final int MAX_LOOP_ITERATIONS = 2;

// 测试大循环
private static final int MAX_LOOP_ITERATIONS = 10;
```

### 6.2 超时验证

```java
// 测试短超时
private static final long MAX_EXECUTION_TIME_MS = 10_000; // 10秒
```

### 6.3 内存使用

使用 JVisualVM 或 JConsole 监控内存使用：

```bash
# 启动服务时添加 JVM 参数
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx512m -XX:+HeapDumpOnOutOfMemoryError"
```

---

## 七、验证报告模板

完成验证后，请填写以下报告：

```markdown
# Agent 循环执行器验证报告

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
- [ ] Observe 阶段正确收集上下文
- [ ] Plan 阶段生成可执行的执行计划
- [ ] Act 阶段正确执行步骤
- [ ] Reflect 阶段正确评估结果
- [ ] 循环可以正常终止
- [ ] 交付物正确生成

## 性能指标
- 平均循环次数：
- 平均执行时间：
- 内存使用峰值：

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

## 八、下一步行动

完成验证后：

1. **如果编译成功**：
   - 运行单元测试
   - 运行集成测试
   - 启动服务进行端到端测试
   - 填写验证报告

2. **如果编译失败**：
   - 根据错误信息修复代码
   - 重新编译验证
   - 如无法解决，提供错误日志

3. **如果测试失败**：
   - 分析失败原因
   - 修复代码或测试
   - 重新运行测试

---

*请完成验证后告诉我结果，我会根据反馈继续推进后续开发。*
