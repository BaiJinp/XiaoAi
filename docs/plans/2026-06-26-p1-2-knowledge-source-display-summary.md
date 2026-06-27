# P1-2 知识来源展示 - 完成总结

> 日期：2026-06-26  
> 状态：✅ 已完成

---

## 📦 实现内容

### 1. 数据模型扩展

#### KnowledgeSource 新增类
创建 `KnowledgeSource` 类描述知识来源：

```java
private static class KnowledgeSource {
    private String sourceTitle;  // 来源标题
    private String confidence;   // 可信度 (high/medium/low/unknown)
    private String quoted;       // 引用的内容片段
}
```

#### ReflectionResult 扩展
在 `ReflectionResult` 类中添加知识来源相关字段：

```java
private static class ReflectionResult {
    private boolean complete;
    private String summary;
    private List<KnowledgeSource> knowledgeSources;  // 新增
    private String lowConfidenceWarning;              // 新增
    private boolean needsAdjustment;
    private String adjustmentReason;
    private List<ExtractedMemory> extractedMemories;
}
```

### 2. 核心功能实现

#### buildReflectPrompt 方法增强
修改 `buildReflectPrompt()` 方法，要求模型在反思时：
1. 收集知识来源信息
2. 在 summary 中引用来源
3. 列出所有使用的知识来源
4. 对低可信度来源添加警告

**关键代码**：
```java
// 收集知识来源信息
List<KnowledgeContext> knowledgeSources = new ArrayList<>();
for (StepResult result : stepResults) {
    if ("knowledge_retrieve".equals(result.getStepType()) && result.getKnowledgeResults() != null) {
        for (KnowledgeRetrieveResult kr : result.getKnowledgeResults()) {
            knowledgeSources.add(new KnowledgeContext(
                    kr.getDocumentId(),
                    kr.getChunkId(),
                    kr.getChunkText(),
                    kr.getSourceTitle(),
                    kr.getConfidence()
            ));
        }
    }
}

// 在 prompt 中添加知识来源信息
if (!knowledgeSources.isEmpty()) {
    prompt.append("\n知识来源：\n");
    for (KnowledgeContext kc : knowledgeSources) {
        prompt.append("- 来源：").append(kc.sourceTitle() != null ? kc.sourceTitle() : "未知来源");
        prompt.append("，可信度：").append(kc.confidence() != null ? kc.confidence() : "unknown");
        prompt.append("\n");
    }
}

// 要求模型返回知识来源和警告
prompt.append("  \"knowledgeSources\": [ // 使用的知识来源列表\n");
prompt.append("    {\n");
prompt.append("      \"sourceTitle\": \"来源标题\",\n");
prompt.append("      \"confidence\": \"high/medium/low/unknown\",\n");
prompt.append("      \"quoted\": \"引用的内容片段\"\n");
prompt.append("    }\n");
prompt.append("  ],\n");
prompt.append("  \"lowConfidenceWarning\": \"如果存在低可信度来源，在此添加警告提示（否则为null）\",\n");
```

#### recordKnowledgeSources 方法新增
实现 `recordKnowledgeSources()` 方法，记录知识来源事件：

```java
private void recordKnowledgeSources(RunStartCommand command, List<RuntimeEvent> events,
                                    List<KnowledgeSource> knowledgeSources) {
    if (knowledgeSources == null || knowledgeSources.isEmpty()) {
        return;
    }

    StringBuilder payload = new StringBuilder();
    payload.append("{\"sources\":[");
    for (int i = 0; i < knowledgeSources.size(); i++) {
        KnowledgeSource source = knowledgeSources.get(i);
        if (i > 0) {
            payload.append(",");
        }
        payload.append("{");
        payload.append("\"sourceTitle\":\"").append(safeJson(source.getSourceTitle())).append("\"");
        payload.append(",\"confidence\":\"").append(safeJson(source.getConfidence())).append("\"");
        payload.append(",\"quoted\":\"").append(safeJson(source.getQuoted())).append("\"");
        payload.append("}");
    }
    payload.append("]}");

    recordEvent(command, events, "KNOWLEDGE_SOURCES", "Knowledge sources cited", payload.toString());
}
```

#### reflect 方法增强
修改 `reflect()` 方法，在反思完成后记录知识来源和低可信度警告：

```java
// 记录知识来源
if (reflection.getKnowledgeSources() != null && !reflection.getKnowledgeSources().isEmpty()) {
    recordKnowledgeSources(command, events, reflection.getKnowledgeSources());
}

// 记录低可信度警告
if (reflection.getLowConfidenceWarning() != null) {
    recordEvent(command, events, "LOW_CONFIDENCE_WARNING", "Low confidence warning",
            "{\"warning\":\"" + safeJson(reflection.getLowConfidenceWarning()) + "\"}");
}
```

---

## 🎯 核心特性

### 1. 知识来源追踪

**来源信息**：
- **sourceTitle** - 来源标题（文档名称）
- **confidence** - 可信度（high/medium/low/unknown）
- **quoted** - 引用的内容片段

**可信度等级**：
| 等级 | 说明 | 使用场景 |
|------|------|----------|
| **high** | 高可信度 | 权威来源、官方文档 |
| **medium** | 中可信度 | 一般参考资料 |
| **low** | 低可信度 | 非官方来源、过时信息 |
| **unknown** | 未知 | 无法判断来源可靠性 |

### 2. 低可信度警告

**触发条件**：
- 使用了 `low` 或 `unknown` 可信度的知识来源

**警告格式**：
```
注意：以下内容基于低可信度资料，请谨慎参考：[具体内容]
```

**事件记录**：
- 事件类型：`LOW_CONFIDENCE_WARNING`
- Payload：`{"warning":"警告内容"}`

### 3. 来源引用展示

**在 summary 中引用**：
模型在生成总结时，会明确引用知识来源，例如：
```
根据项目文档《需求规格说明书》（高可信度），系统需要支持...
参考资料《设计文档》（中可信度）中提到...
```

**在 knowledgeSources 中列出**：
```json
{
  "knowledgeSources": [
    {
      "sourceTitle": "需求规格说明书",
      "confidence": "high",
      "quoted": "系统需要支持用户认证和授权功能"
    },
    {
      "sourceTitle": "设计文档",
      "confidence": "medium",
      "quoted": "建议使用微服务架构"
    }
  ]
}
```

---

## 📊 执行流程

```
1. reflect 阶段开始
   ↓
2. 收集知识来源信息（从 stepResults 中提取）
   ↓
3. 构建 reflect prompt（包含知识来源）
   ↓
4. 调用模型评估结果
   ↓
5. 模型返回反思结果（包含 knowledgeSources 和 lowConfidenceWarning）
   ↓
6. 解析反思结果
   ↓
7. 记录知识来源事件（KNOWLEDGE_SOURCES）
   ↓
8. 记录低可信度警告事件（LOW_CONFIDENCE_WARNING，如果有）
   ↓
9. reflect 阶段完成
```

---

## 🔍 事件记录

知识来源展示过程中会记录以下事件：

| 事件类型 | 说明 | Payload |
|---------|------|---------|
| `KNOWLEDGE_SOURCES` | 知识来源被引用 | sources[] 数组，包含 sourceTitle、confidence、quoted |
| `LOW_CONFIDENCE_WARNING` | 低可信度警告 | warning 字符串 |

---

## 💡 使用示例

### 模型返回的反思结果示例

```json
{
  "complete": true,
  "summary": "根据《项目需求文档》（高可信度），系统需要实现用户认证功能。参考《技术设计文档》（中可信度），建议使用 OAuth 2.0 协议。注意：《第三方评估报告》（低可信度）中提到的性能数据需要进一步验证。",
  "knowledgeSources": [
    {
      "sourceTitle": "项目需求文档",
      "confidence": "high",
      "quoted": "系统需要实现用户认证和授权功能"
    },
    {
      "sourceTitle": "技术设计文档",
      "confidence": "medium",
      "quoted": "建议使用 OAuth 2.0 协议进行身份验证"
    },
    {
      "sourceTitle": "第三方评估报告",
      "confidence": "low",
      "quoted": "系统预计可支持 10000 并发用户"
    }
  ],
  "lowConfidenceWarning": "注意：以下内容基于低可信度资料，请谨慎参考：《第三方评估报告》中的性能数据（10000 并发用户）未经验证",
  "needsAdjustment": false,
  "adjustmentReason": null,
  "extractedMemories": []
}
```

---

## ✅ 验收标准

- [x] KnowledgeSource 类定义完成
- [x] ReflectionResult 类添加 knowledgeSources 和 lowConfidenceWarning 字段
- [x] buildReflectPrompt 方法收集知识来源信息
- [x] buildReflectPrompt 方法要求模型返回知识来源
- [x] buildReflectPrompt 方法要求模型对低可信度添加警告
- [x] recordKnowledgeSources 方法实现完成
- [x] reflect 方法记录知识来源事件
- [x] reflect 方法记录低可信度警告事件
- [x] 事件 payload 包含完整的来源信息

---

## 📝 下一步

P1-2 知识来源展示已完成，接下来可以继续：

1. **P1-3 Prompt 注入防护** - 基础输入校验和注入检测

---

*知识来源展示让用户能够追溯 Agent 回答的依据，提升透明度和可信度，同时在低可信度时提醒用户谨慎参考。* 📚
