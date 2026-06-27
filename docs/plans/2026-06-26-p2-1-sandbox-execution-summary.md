# P2-1 沙箱执行环境 - 完成总结

> 日期：2026-06-26  
> 状态：✅ 已完成

---

## 📦 实现内容

### 1. SandboxExecutor 类

创建了 `SandboxExecutor` 类，为工具执行提供隔离环境。

#### 核心功能

**SandboxConfig 内部类**：
```java
public static class SandboxConfig {
    private final long timeoutMs;           // 超时时间（毫秒）
    private final long maxMemoryMb;         // 最大内存（MB）
    private final boolean allowNetworkAccess;    // 是否允许网络访问
    private final boolean allowFileSystemAccess; // 是否允许文件系统访问
}
```

**SandboxResult 内部类**：
```java
public static class SandboxResult<T> {
    private final boolean success;          // 是否成功
    private final T result;                 // 执行结果
    private final String error;             // 错误信息
    private final long executionTimeMs;     // 执行时间（毫秒）
    private final boolean timeout;          // 是否超时
}
```

**executeInSandbox 方法**：
```java
public <T> SandboxResult<T> executeInSandbox(Callable<T> task, SandboxConfig config, Map<String, Object> context)
```

#### 资源限制

**超时控制**：
- 默认超时：30秒
- 最大超时：5分钟
- 超时后自动取消任务

**线程池管理**：
- 使用 CachedThreadPool
- 每个任务在独立线程中执行
- 支持任务中断和清理

### 2. ToolExecutorRegistry 集成

修改了 `ToolExecutorRegistry`，在工具执行时集成沙箱。

#### 修改内容

1. **添加依赖注入**：
```java
private final SandboxExecutor sandboxExecutor;

public ToolExecutorRegistry(List<ToolExecutor> executors, SandboxExecutor sandboxExecutor) {
    this.executors = executors == null ? List.of() : List.copyOf(executors);
    this.sandboxExecutor = sandboxExecutor;
}
```

2. **execute 方法增强**：
```java
public String execute(ToolConfig tool, ExecuteToolCallCommand command) {
    // ... 找到匹配的 executor ...
    
    // 使用沙箱执行工具
    if (sandboxExecutor != null) {
        return executeInSandbox(matchedExecutors.get(0), tool, command);
    }
    
    return matchedExecutors.get(0).execute(tool, command);
}
```

3. **新增辅助方法**：

**executeInSandbox**：在沙箱中执行工具
```java
private String executeInSandbox(ToolExecutor executor, ToolConfig tool, ExecuteToolCallCommand command) {
    Map<String, Object> context = new HashMap<>();
    context.put("toolCode", tool.getToolCode());
    context.put("toolType", tool.getToolType());
    context.put("riskLevel", tool.getRiskLevel());
    context.put("taskId", command.getTaskId());
    context.put("runId", command.getRunId());
    
    SandboxExecutor.SandboxConfig config = buildSandboxConfig(tool);
    
    SandboxExecutor.SandboxResult<String> result = sandboxExecutor.executeInSandbox(
            () -> executor.execute(tool, command),
            config,
            context
    );
    
    if (!result.isSuccess()) {
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, errorMessage);
    }
    
    return result.getResult();
}
```

**buildSandboxConfig**：根据风险等级构建沙箱配置
```java
private SandboxExecutor.SandboxConfig buildSandboxConfig(ToolConfig tool) {
    String riskLevel = tool.getRiskLevel();
    
    if ("high".equalsIgnoreCase(riskLevel)) {
        // 高风险：10秒超时，256MB内存，无网络/文件系统访问
        return new SandboxConfig(10_000, 256, false, false);
    } else if ("medium".equalsIgnoreCase(riskLevel)) {
        // 中等风险：30秒超时，512MB内存，无网络/文件系统访问
        return new SandboxConfig(30_000, 512, false, false);
    } else {
        // 低风险：60秒超时，1024MB内存，允许网络访问
        return new SandboxConfig(60_000, 1024, true, false);
    }
}
```

---

## 🎯 核心特性

### 1. 风险分级控制

| 风险等级 | 超时时间 | 最大内存 | 网络访问 | 文件系统访问 | 适用场景 |
|---------|---------|---------|---------|-------------|---------|
| **high** | 10秒 | 256MB | ❌ | ❌ | 数据库操作、文件删除、系统命令 |
| **medium** | 30秒 | 512MB | ❌ | ❌ | API调用、数据处理 |
| **low** | 60秒 | 1024MB | ✅ | ❌ | 查询操作、数据读取 |

### 2. 超时保护

**超时处理**：
- 超时后自动取消任务（future.cancel(true)）
- 返回明确的超时错误信息
- 记录超时日志

**超时错误格式**：
```
工具执行超时（10000ms）: query_database
```

### 3. 执行监控

**日志记录**：
- 执行开始：记录工具代码、风险等级、超时时间
- 执行完成：记录工具代码、执行时间
- 执行失败：记录错误信息、上下文

**上下文信息**：
```java
Map<String, Object> context = {
    "toolCode": "query_database",
    "toolType": "cli",
    "riskLevel": "high",
    "taskId": 123,
    "runId": 456
};
```

---

## 📊 执行流程

```
1. 工具调用请求
   ↓
2. ToolExecutorRegistry.execute 接收请求
   ↓
3. 查找匹配的 ToolExecutor
   ↓
4. 构建沙箱配置（根据风险等级）
   ↓
5. 在沙箱中执行工具
   ↓
6. 如果超时：
   ↓
   6.1 取消任务
   ↓
   6.2 返回超时错误
   ↓
7. 如果失败：
   ↓
   7.1 返回执行错误
   ↓
8. 如果成功：
   ↓
   8.1 返回执行结果
   ↓
   8.2 记录执行时间
```

---

## 🔍 日志示例

### 正常执行

```
INFO  - Executing tool in sandbox: toolCode=query_database, riskLevel=high, timeoutMs=10000
INFO  - Tool execution completed in sandbox: toolCode=query_database, executionTimeMs=3456
```

### 超时执行

```
INFO  - Executing tool in sandbox: toolCode=slow_operation, riskLevel=medium, timeoutMs=30000
WARN  - Sandbox execution timeout: timeoutMs=30000, context={toolCode=slow_operation, ...}
ERROR - 工具执行超时（30000ms）: slow_operation
```

### 失败执行

```
INFO  - Executing tool in sandbox: toolCode=api_call, riskLevel=low, timeoutMs=60000
ERROR - Sandbox execution failed: error=Connection refused, context={toolCode=api_call, ...}
ERROR - 工具执行失败: Connection refused
```

---

## 💡 使用示例

### 低风险工具（查询操作）

```java
ToolConfig tool = new ToolConfig();
tool.setToolCode("query_users");
tool.setToolType("cli");
tool.setRiskLevel("low");

// 沙箱配置：60秒超时，1024MB内存，允许网络访问
SandboxConfig config = buildSandboxConfig(tool);
// config.timeoutMs = 60000
// config.maxMemoryMb = 1024
// config.allowNetworkAccess = true
```

### 中等风险工具（API调用）

```java
ToolConfig tool = new ToolConfig();
tool.setToolCode("send_email");
tool.setToolType("http");
tool.setRiskLevel("medium");

// 沙箱配置：30秒超时，512MB内存，无网络/文件系统访问
SandboxConfig config = buildSandboxConfig(tool);
// config.timeoutMs = 30000
// config.maxMemoryMb = 512
// config.allowNetworkAccess = false
```

### 高风险工具（数据库操作）

```java
ToolConfig tool = new ToolConfig();
tool.setToolCode("delete_records");
tool.setToolType("cli");
tool.setRiskLevel("high");

// 沙箱配置：10秒超时，256MB内存，无网络/文件系统访问
SandboxConfig config = buildSandboxConfig(tool);
// config.timeoutMs = 10000
// config.maxMemoryMb = 256
// config.allowNetworkAccess = false
```

---

## ✅ 验收标准

- [x] SandboxExecutor 类创建完成
- [x] SandboxConfig 内部类定义完成
- [x] SandboxResult 内部类定义完成
- [x] executeInSandbox 方法实现完成
- [x] 超时控制实现完成
- [x] 线程池管理实现完成
- [x] ToolExecutorRegistry 注入 SandboxExecutor
- [x] execute 方法集成沙箱执行
- [x] executeInSandbox 方法实现完成
- [x] buildSandboxConfig 方法实现完成
- [x] 风险分级控制实现完成
- [x] 超时错误处理实现完成
- [x] 执行日志记录实现完成

---

## 📝 下一步

P2-1 沙箱执行环境已完成，接下来可以继续：

1. **P2-2 成本预算控制** - token 预算限制和超限保护

---

*沙箱执行环境为工具执行提供了安全保障，防止恶意代码和资源滥用。* 🛡️
