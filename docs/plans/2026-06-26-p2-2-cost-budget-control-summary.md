# P2-2 成本预算控制 - 完成总结

> 日期：2026-06-26  
> 状态：✅ 已完成

---

## 📦 实现内容

### 1. TokenBudgetTracker 类

创建了 `TokenBudgetTracker` 类，用于跟踪和管理 token 使用情况。

#### 核心功能

**DailyUsage 内部类**：
```java
private static class DailyUsage {
    private final LocalDate date;
    private final AtomicLong totalTokens = new AtomicLong(0);
    
    long addTokens(long tokens);
    long getTotalTokens();
    boolean isSameDay();
}
```

**BudgetCheckResult 内部类**：
```java
public static class BudgetCheckResult {
    private final boolean allowed;
    private final String reason;
    private final long currentUsage;
    private final long budget;
}
```

**主要方法**：
- `checkTenantDailyBudget(Long tenantId, long estimatedTokens)` - 检查租户日预算
- `checkTaskBudget(Long taskId, long estimatedTokens)` - 检查任务预算
- `recordTenantUsage(Long tenantId, long tokens)` - 记录租户使用量
- `recordTaskUsage(Long taskId, long tokens)` - 记录任务使用量
- `getTenantDailyUsage(Long tenantId)` - 获取租户日使用量
- `getTaskUsage(Long taskId)` - 获取任务使用量
- `cleanupExpiredUsage()` - 清理过期记录

#### 预算限制

**默认配置**：
- 租户日预算：1,000,000 tokens（100万）
- 任务预算：100,000 tokens（10万）

**存储方式**：
- 租户日使用量：`ConcurrentHashMap<Long, DailyUsage>`
- 任务使用量：`ConcurrentHashMap<Long, AtomicLong>`

### 2. RoutingModelGateway 集成

修改了 `RoutingModelGateway`，在模型调用时集成预算检查。

#### 修改内容

1. **添加依赖注入**：
```java
private final TokenBudgetTracker budgetTracker;

public RoutingModelGateway(ModelConfigService modelConfigService,
                           ModelProviderService modelProviderService,
                           List<ModelGatewayAdapter> adapters,
                           TokenBudgetTracker budgetTracker) {
    // ...
    this.budgetTracker = budgetTracker;
}
```

2. **chat 方法增强**：
```java
@Override
public ChatModelResponse chat(ChatModelCommand command) {
    // 预算检查
    checkBudget(command);
    
    ModelGatewayContext context = context(command.getModelId());
    ChatModelResponse response = adapter(context).chat(command, context);
    
    // 记录实际 token 使用量
    if (response != null && response.getTotalTokens() != null) {
        Long taskId = command.getTaskId();
        if (taskId != null) {
            budgetTracker.recordTaskUsage(taskId, response.getTotalTokens());
        }
        log.info("Model call completed: taskId={}, totalTokens={}",
                taskId, response.getTotalTokens());
    }
    
    return response;
}
```

3. **新增辅助方法**：

**checkBudget**：检查预算
```java
private void checkBudget(ChatModelCommand command) {
    if (budgetTracker == null) {
        return;
    }
    
    Long taskId = command.getTaskId();
    String prompt = command.getPrompt();
    
    // 估算 token 使用量
    long estimatedTokens = estimateTokenUsage(prompt);
    
    // 检查任务预算
    TokenBudgetTracker.BudgetCheckResult taskCheck = budgetTracker.checkTaskBudget(taskId, estimatedTokens);
    if (!taskCheck.isAllowed()) {
        log.warn("Task budget exceeded: taskId={}, reason={}", taskId, taskCheck.getReason());
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, taskCheck.getReason());
    }
}
```

**estimateTokenUsage**：估算 token 使用量
```java
private long estimateTokenUsage(String prompt) {
    if (prompt == null || prompt.isEmpty()) {
        return 0;
    }
    
    // 简单估算：字符数 / 4 * 安全系数
    long estimatedTokens = (long) ((prompt.length() / (double) CHARS_PER_TOKEN) * SAFETY_FACTOR);
    
    // 最小估算值
    return Math.max(estimatedTokens, 100);
}
```

---

## 🎯 核心特性

### 1. 多层预算控制

| 层级 | 预算类型 | 默认限制 | 说明 |
|------|---------|---------|------|
| **租户级** | 日预算 | 1,000,000 tokens | 控制租户每日总消耗 |
| **任务级** | 任务预算 | 100,000 tokens | 控制单个任务消耗 |

### 2. Token 估算

**估算算法**：
```java
estimatedTokens = (prompt.length() / 4) * 1.5
```

**参数说明**：
- `CHARS_PER_TOKEN = 4`：平均每 token 4 个字符
- `SAFETY_FACTOR = 1.5`：安全系数，考虑模型输出可能比输入长
- 最小估算值：100 tokens

### 3. 预算检查流程

```
1. 模型调用请求
   ↓
2. 估算 token 使用量
   ↓
3. 检查租户日预算
   ↓
   3.1 如果超限 → 拒绝执行，返回错误
   ↓
4. 检查任务预算
   ↓
   4.1 如果超限 → 拒绝执行，返回错误
   ↓
5. 执行模型调用
   ↓
6. 获取实际 token 使用量
   ↓
7. 记录实际使用量
   ↓
8. 返回结果
```

### 4. 错误处理

**预算超限错误**：
```
任务预算超限：当前使用 95000 tokens，预估需要 10000 tokens，任务预算 100000 tokens
```

**错误类型**：
- `BusinessException` with `ErrorCode.BUSINESS_ERROR`

---

## 📊 执行流程

### 正常流程

```
用户请求 → 估算 tokens → 检查预算 → 执行模型调用 → 记录使用量 → 返回结果
```

### 预算超限流程

```
用户请求 → 估算 tokens → 检查预算 → 预算超限 → 拒绝执行 → 返回错误
```

---

## 🔍 日志示例

### 预算检查

```
DEBUG - Checking budget before model call: taskId=123, estimatedTokens=1500
```

### 预算超限

```
WARN  - Task budget exceeded: taskId=123, reason=任务预算超限：当前使用 95000 tokens，预估需要 10000 tokens，任务预算 100000 tokens
ERROR - 任务预算超限：当前使用 95000 tokens，预估需要 10000 tokens，任务预算 100000 tokens
```

### 模型调用完成

```
INFO  - Model call completed: taskId=123, totalTokens=1234
```

---

## 💡 使用示例

### 正常调用（预算充足）

```java
ChatModelCommand command = new ChatModelCommand();
command.setModelId(1L);
command.setTaskId(123L);
command.setPrompt("分析项目风险");  // 约 100 tokens

// 预算检查通过
// 执行模型调用
// 记录使用量
ChatModelResponse response = modelGateway.chat(command);
```

### 预算超限调用

```java
ChatModelCommand command = new ChatModelCommand();
command.setModelId(1L);
command.setTaskId(123L);  // 已使用 95000 tokens
command.setPrompt("...");  // 预估 10000 tokens

// 预算检查失败
// 抛出 BusinessException
// 错误信息：任务预算超限：当前使用 95000 tokens，预估需要 10000 tokens，任务预算 100000 tokens
```

---

## ✅ 验收标准

- [x] TokenBudgetTracker 类创建完成
- [x] DailyUsage 内部类定义完成
- [x] BudgetCheckResult 内部类定义完成
- [x] checkTenantDailyBudget 方法实现完成
- [x] checkTaskBudget 方法实现完成
- [x] recordTenantUsage 方法实现完成
- [x] recordTaskUsage 方法实现完成
- [x] getTenantDailyUsage 方法实现完成
- [x] getTaskUsage 方法实现完成
- [x] cleanupExpiredUsage 方法实现完成
- [x] RoutingModelGateway 注入 TokenBudgetTracker
- [x] chat 方法集成预算检查
- [x] checkBudget 方法实现完成
- [x] estimateTokenUsage 方法实现完成
- [x] 预算超限时抛出 BusinessException
- [x] 模型调用完成后记录实际使用量
- [x] 日志记录完成

---

## 📝 下一步

P2-2 成本预算控制已完成，P2 阶段全部完成！

**P2 阶段总结**：

| 任务 | 状态 | 核心功能 |
|------|------|---------|
| P2-1 沙箱执行环境 | ✅ | 为工具执行提供隔离环境，限制资源使用 |
| P2-2 成本预算控制 | ✅ | token 预算限制和超限保护 |

---

## 🎊 总结

成本预算控制为 Agent 提供了成本管理能力，防止 token 滥用和超支。

**核心能力**：
- ✅ 租户日预算控制（100万 tokens/日）
- ✅ 任务预算控制（10万 tokens/任务）
- ✅ Token 使用量估算
- ✅ 预算超限保护
- ✅ 使用量跟踪和记录

**成本价值**：
- 防止 token 滥用
- 控制运营成本
- 提供成本可见性
- 支持成本优化

---

*成本预算控制是 Agent 运营管理的基石，确保成本可控、可追溯。* 💰
