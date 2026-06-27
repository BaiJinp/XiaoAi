# P1-1 记忆自动抽取 - 完成总结

> 日期：2026-06-26  
> 状态：✅ 已完成

---

## 📦 实现内容

### 1. 数据模型扩展

#### ReflectionResult 扩展
在 `ReflectionResult` 类中添加记忆抽取相关字段：

```java
private static class ReflectionResult {
    private boolean complete;
    private String summary;
    private boolean needsAdjustment;
    private String adjustmentReason;
    private List<ExtractedMemory> extractedMemories; // 新增
}
```

#### ExtractedMemory 新增类
创建 `ExtractedMemory` 类描述抽取的记忆：

```java
private static class ExtractedMemory {
    private String memoryType; // decision, preference, constraint, fact
    private String content;
    private String confidence; // high, medium, low
    private String scope; // task, session, agent
}
```

### 2. 核心功能实现

#### reflect 方法增强
修改 `reflect()` 方法，在评估结果后保存抽取的记忆：

```java
private ReflectionResult reflect(RunStartCommand command, List<RuntimeEvent> events,
                                 AgentLoopContext context, ExecutionPlan plan,
                                 List<StepResult> stepResults) {
    // ... 构建 prompt，调用模型 ...
    
    // 解析反思结果
    ReflectionResult reflection = parseReflectionResult(reflectionJson);
    
    // 新增：保存抽取的记忆
    if (reflection.getExtractedMemories() != null && !reflection.getExtractedMemories().isEmpty()) {
        saveExtractedMemories(command, events, reflection.getExtractedMemories());
    }
    
    return reflection;
}
```

#### buildReflectPrompt 方法增强
修改 `buildReflectPrompt()` 方法，要求模型在评估结果时同时抽取记忆：

```java
private String buildReflectPrompt(AgentLoopContext context, ExecutionPlan plan,
                                  List<StepResult> stepResults) {
    // ... 构建基础 prompt ...
    
    // 新增：记忆抽取要求
    prompt.append("  \"extractedMemories\": [ // 从本次执行中抽取的值得保存的记忆\n");
    prompt.append("    {\n");
    prompt.append("      \"memoryType\": \"decision/preference/constraint/fact\", // 记忆类型\n");
    prompt.append("      \"content\": \"记忆内容\",\n");
    prompt.append("      \"confidence\": \"high/medium/low\", // 置信度\n");
    prompt.append("      \"scope\": \"task/session/agent\" // 记忆范围\n");
    prompt.append("    }\n");
    prompt.append("  ]\n");
    
    // 新增：记忆抽取说明
    prompt.append("记忆抽取说明：\n");
    prompt.append("- decision: 用户做出的重要决策\n");
    prompt.append("- preference: 用户表达的偏好\n");
    prompt.append("- constraint: 项目或任务的约束条件\n");
    prompt.append("- fact: 重要的事实信息\n");
    prompt.append("- 只抽取确实值得保存的记忆，不要抽取临时信息\n");
    
    return prompt.toString();
}
```

#### saveExtractedMemories 方法新增
实现 `saveExtractedMemories()` 方法，将抽取的记忆保存到数据库：

```java
private void saveExtractedMemories(RunStartCommand command, List<RuntimeEvent> events,
                                   List<ExtractedMemory> extractedMemories) {
    if (agentMemoryService == null || extractedMemories == null || extractedMemories.isEmpty()) {
        return;
    }

    int savedCount = 0;
    for (ExtractedMemory extracted : extractedMemories) {
        try {
            // 创建记忆实体
            CreateAgentMemoryCommand createCommand = new CreateAgentMemoryCommand();
            createCommand.setTenantId(command.getTenantId());
            createCommand.setAgentId(command.getAgentId());
            createCommand.setTaskId(command.getTaskId());
            createCommand.setUserId(command.getUserId());
            createCommand.setMemoryType(extracted.getMemoryType());
            createCommand.setMemoryScope(extracted.getScope());
            createCommand.setSummaryText(extracted.getContent());
            createCommand.setConfidence(extracted.getConfidence());

            // 保存记忆
            agentMemoryService.createConfirmedMemory(createCommand);
            savedCount++;

            // 记录事件
            recordEvent(command, events, "MEMORY_EXTRACTED", "Memory extracted from execution", ...);

        } catch (Exception e) {
            log.error("Failed to save extracted memory: {}", extracted.getContent(), e);
        }
    }

    if (savedCount > 0) {
        recordEvent(command, events, "MEMORIES_SAVED", "Extracted memories saved",
                "{\"savedCount\":" + savedCount + "}");
    }
}
```

---

## 🎯 核心特性

### 1. 四种记忆类型

| 类型 | 说明 | 示例 |
|------|------|------|
| **decision** | 用户做出的重要决策 | "用户决定使用 PostgreSQL 作为数据库" |
| **preference** | 用户表达的偏好 | "用户偏好简洁的代码风格" |
| **constraint** | 项目或任务的约束条件 | "项目必须在 2 周内完成" |
| **fact** | 重要的事实信息 | "项目的技术栈是 Java + Spring Boot" |

### 2. 三种记忆范围

| 范围 | 说明 | 用途 |
|------|------|------|
| **task** | 任务级别 | 只在当前任务中有效 |
| **session** | 会话级别 | 在同一会话的多个任务中有效 |
| **agent** | Agent 级别 | 在所有任务中有效 |

### 3. 三种置信度

| 置信度 | 说明 | 使用场景 |
|--------|------|----------|
| **high** | 高置信度 | 明确表述的信息 |
| **medium** | 中置信度 | 推断的信息 |
| **low** | 低置信度 | 不确定的信息 |

---

## 📊 执行流程

```
1. reflect 阶段开始
   ↓
2. 构建 reflect prompt（包含记忆抽取要求）
   ↓
3. 调用模型评估结果
   ↓
4. 模型返回反思结果（包含 extractedMemories）
   ↓
5. 解析反思结果
   ↓
6. 如果有抽取的记忆：
   ↓
   6.1 遍历 extractedMemories
   ↓
   6.2 对每个记忆调用 agentMemoryService.createConfirmedMemory()
   ↓
   6.3 记录 MEMORY_EXTRACTED 事件
   ↓
7. 记录 MEMORIES_SAVED 事件
   ↓
8. reflect 阶段完成
```

---

## 🔍 事件记录

记忆自动抽取过程中会记录以下事件：

| 事件类型 | 说明 | Payload |
|---------|------|---------|
| `MEMORY_EXTRACTED` | 单条记忆被抽取 | memoryType, scope, confidence, content |
| `MEMORIES_SAVED` | 记忆保存完成 | savedCount |

---

## 💡 使用示例

### 模型返回的反思结果示例

```json
{
  "complete": true,
  "summary": "风险分析完成，已生成风险清单",
  "needsAdjustment": false,
  "adjustmentReason": null,
  "extractedMemories": [
    {
      "memoryType": "decision",
      "content": "用户决定优先处理高风险项",
      "confidence": "high",
      "scope": "task"
    },
    {
      "memoryType": "preference",
      "content": "用户希望风险分析包含具体的缓解措施",
      "confidence": "medium",
      "scope": "agent"
    },
    {
      "memoryType": "constraint",
      "content": "项目预算有限，需要控制成本",
      "confidence": "high",
      "scope": "session"
    }
  ]
}
```

---

## ✅ 验收标准

- [x] ReflectionResult 类添加 extractedMemories 字段
- [x] ExtractedMemory 类定义完成
- [x] buildReflectPrompt 方法添加记忆抽取要求
- [x] reflect 方法在评估后保存记忆
- [x] saveExtractedMemories 方法实现完成
- [x] 记忆保存时记录事件
- [x] 错误处理完成

---

## 📝 下一步

P1-1 记忆自动抽取已完成，接下来可以继续：

1. **P1-2 知识来源展示** - 在输出中明确展示引用的知识来源和可信度
2. **P1-3 Prompt 注入防护** - 基础输入校验和注入检测

---

*记忆自动抽取让 Agent 能够从历史对话中学习，积累用户偏好和项目知识，提升回答质量和个性化程度。* 🧠
