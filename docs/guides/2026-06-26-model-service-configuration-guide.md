# 真实模型服务配置指南

> 日期：2026-06-26  
> 目标：配置并接入 OpenAI 兼容模型服务

---

## 一、配置步骤

### 1.1 数据库配置

需要在数据库中插入两条记录：`model_provider` 和 `model_config`。

#### 步骤 1：创建模型供应商

```sql
INSERT INTO model_provider (
    bid,
    tenant_id,
    created_by,
    created_at,
    updated_by,
    updated_at,
    deleted,
    provider_code,
    provider_name,
    provider_type,
    base_url,
    auth_config_json,
    status
) VALUES (
    'PROVIDER-' || EXTRACT(EPOCH FROM NOW())::TEXT,  -- 唯一业务ID
    100,                                              -- 租户ID（默认100）
    0,                                                -- 创建人
    NOW(),                                            -- 创建时间
    0,                                                -- 更新人
    NOW(),                                            -- 更新时间
    false,                                            -- 未删除
    'your-provider-code',                             -- 供应商标识（自定义，如 'openai', 'azure', 'local-ollama'）
    'Your Provider Name',                             -- 供应商名称
    'openai_compatible',                              -- 供应商类型（必须是 'openai_compatible'）
    'https://api.openai.com',                         -- API 基础URL（见下方说明）
    '{"apiKey":"your-api-key-here"}',                 -- 认证配置JSON（API Key）
    'active'                                          -- 状态（必须是 'active'）
);
```

**关键参数说明**：

| 参数 | 说明 | 示例 |
|------|------|------|
| `provider_code` | 供应商标识，自定义 | `'openai'`, `'azure'`, `'local-ollama'` |
| `provider_type` | 必须是 `'openai_compatible'` | `'openai_compatible'` |
| `base_url` | API 基础URL | 见下方常用服务URL |
| `auth_config_json` | 认证配置，必须包含 `apiKey` | `'{"apiKey":"sk-xxx"}'` |
| `status` | 必须是 `'active'` | `'active'` |

**常用服务URL**：

| 服务 | base_url |
|------|----------|
| OpenAI | `https://api.openai.com` |
| Azure OpenAI | `https://{your-resource-name}.openai.azure.com` |
| 本地 Ollama | `http://localhost:11434` |
| 其他兼容服务 | 按实际URL填写 |

#### 步骤 2：创建模型配置

```sql
INSERT INTO model_config (
    bid,
    tenant_id,
    created_by,
    created_at,
    updated_by,
    updated_at,
    deleted,
    provider_id,
    model_code,
    model_name,
    model_type,
    context_window,
    config_json,
    status
) VALUES (
    'MODEL-' || EXTRACT(EPOCH FROM NOW())::TEXT,  -- 唯一业务ID
    100,                                           -- 租户ID（默认100）
    0,                                             -- 创建人
    NOW(),                                         -- 创建时间
    0,                                             -- 更新人
    NOW(),                                         -- 更新时间
    false,                                         -- 未删除
    {上一步插入的 model_provider.id},              -- 供应商ID
    'gpt-4o-mini',                                 -- 模型代码（见下方说明）
    'GPT-4o Mini',                                 -- 模型名称
    'chat',                                        -- 模型类型（必须是 'chat' 或 'embedding'）
    128000,                                        -- 上下文窗口大小（token数）
    '{"temperature":0.7,"maxTokens":4096}',        -- 模型参数JSON
    'active'                                       -- 状态（必须是 'active'）
);
```

**关键参数说明**：

| 参数 | 说明 | 示例 |
|------|------|------|
| `provider_id` | 上一步插入的 `model_provider.id` | `1`, `2`, ... |
| `model_code` | 模型代码，按实际模型填写 | `'gpt-4o-mini'`, `'gpt-4'`, `'llama3'` |
| `model_type` | 模型类型 | `'chat'` 或 `'embedding'` |
| `context_window` | 上下文窗口大小（token数） | `128000`（GPT-4o）, `8192`（Llama3） |
| `config_json` | 模型参数JSON | 见下方参数说明 |
| `status` | 必须是 `'active'` | `'active'` |

**模型参数说明**：

```json
{
  "temperature": 0.7,      // 温度，0-2，越高越随机
  "maxTokens": 4096,       // 最大输出token数
  "topP": 1.0              // Top-p采样，0-1
}
```

### 1.2 验证配置

配置完成后，可以通过以下方式验证：

#### 方式 1：API 验证

```bash
# 查询模型供应商
curl http://localhost:8080/api/v1/model-providers

# 查询模型配置
curl http://localhost:8080/api/v1/model-configs

# 测试模型调用
curl -X POST http://localhost:8080/api/v1/model-gateway/chat \
  -H "Content-Type: application/json" \
  -d '{
    "modelId": {你的model_config.id},
    "prompt": "Hello, how are you?"
  }'
```

#### 方式 2：前端验证

1. 启动后端：`cd backend && mvn spring-boot:run`
2. 启动前端：`cd frontend && npm run dev`
3. 访问前端页面，进入"项目助理配置"
4. 在"模型配置"区域选择刚创建的供应商和模型
5. 创建测试任务，验证模型调用

### 1.3 常见问题

#### 问题 1：模型调用失败，返回 "Model provider is not active"

**原因**：`model_provider.status` 不是 `'active'`

**解决**：更新状态
```sql
UPDATE model_provider SET status = 'active' WHERE id = {你的provider_id};
```

#### 问题 2：模型调用失败，返回 "Model provider API key is missing"

**原因**：`auth_config_json` 中没有 `apiKey` 字段

**解决**：更新认证配置
```sql
UPDATE model_provider 
SET auth_config_json = '{"apiKey":"your-api-key-here"}' 
WHERE id = {你的provider_id};
```

#### 问题 3：模型调用失败，返回 "OpenAI-compatible chat call failed"

**原因**：可能是网络问题、API Key 无效、或模型代码错误

**解决**：
1. 检查网络连接
2. 验证 API Key 是否有效
3. 检查 `model_code` 是否正确（如 `gpt-4o-mini` 而不是 `gpt-4o`）
4. 查看后端日志获取详细错误信息

#### 问题 4：模型调用成功，但返回内容为空

**原因**：模型返回格式不符合预期

**解决**：检查模型返回格式，确保包含 `choices[0].message.content`

---

## 二、配置示例

### 2.1 OpenAI 配置示例

```sql
-- 创建 OpenAI 供应商
INSERT INTO model_provider (
    bid, tenant_id, created_by, created_at, updated_by, updated_at, deleted,
    provider_code, provider_name, provider_type, base_url, auth_config_json, status
) VALUES (
    'PROVIDER-OPENAI', 100, 0, NOW(), 0, NOW(), false,
    'openai', 'OpenAI', 'openai_compatible', 
    'https://api.openai.com', 
    '{"apiKey":"sk-your-openai-api-key"}', 
    'active'
);

-- 创建 GPT-4o Mini 模型配置
INSERT INTO model_config (
    bid, tenant_id, created_by, created_at, updated_by, updated_at, deleted,
    provider_id, model_code, model_name, model_type, context_window, config_json, status
) VALUES (
    'MODEL-GPT4O-MINI', 100, 0, NOW(), 0, NOW(), false,
    {上一步的id}, 'gpt-4o-mini', 'GPT-4o Mini', 'chat', 128000,
    '{"temperature":0.7,"maxTokens":4096}', 
    'active'
);
```

### 2.2 本地 Ollama 配置示例

```sql
-- 创建 Ollama 供应商
INSERT INTO model_provider (
    bid, tenant_id, created_by, created_at, updated_by, updated_at, deleted,
    provider_code, provider_name, provider_type, base_url, auth_config_json, status
) VALUES (
    'PROVIDER-OLLAMA', 100, 0, NOW(), 0, NOW(), false,
    'ollama', 'Local Ollama', 'openai_compatible', 
    'http://localhost:11434', 
    '{"apiKey":"ollama"}',  -- Ollama 不需要真实 API Key，但字段必须存在
    'active'
);

-- 创建 Llama3 模型配置
INSERT INTO model_config (
    bid, tenant_id, created_by, created_at, updated_by, updated_at, deleted,
    provider_id, model_code, model_name, model_type, context_window, config_json, status
) VALUES (
    'MODEL-LLAMA3', 100, 0, NOW(), 0, NOW(), false,
    {上一步的id}, 'llama3', 'Llama 3', 'chat', 8192,
    '{"temperature":0.7,"maxTokens":2048}', 
    'active'
);
```

---

## 三、下一步

配置完成后，我们将开始实现 **P0-2 Agent 循环重构**，让模型真正驱动执行流程。

请完成配置后告诉我，我会继续下一步开发。
