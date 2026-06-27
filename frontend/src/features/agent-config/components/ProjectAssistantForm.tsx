import { Alert, Button, Card, Checkbox, Divider, Flex, Form, Input, InputNumber, Typography, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import type { AgentDraftResponse, AgentVersionResponse, CreateAgentDraftRequest, CreateAgentVersionRequest, ReplaceAgentVersionToolsRequest } from '../../../types/agent';
import type { CreateModelConfigRequest, CreateModelProviderRequest, ModelConfig, ModelProvider } from '../../../types/model';
import type { PluginManifest, PluginTool } from '../../../types/plugin';
import { createAgentDraft, createAgentVersion, replaceAgentVersionTools } from '../../../services/agent-api';
import { createModelConfig, createModelProvider } from '../../../services/model-api';
import { listPluginManifests } from '../../../services/plugin-api';

interface ProjectAssistantFormProps {
  onCreateDraft?: (request: CreateAgentDraftRequest) => Promise<AgentDraftResponse>;
  onCreateVersion?: (agentId: number, request: CreateAgentVersionRequest) => Promise<AgentVersionResponse>;
  onReplaceVersionTools?: (agentId: number, versionId: number, request: ReplaceAgentVersionToolsRequest) => Promise<AgentVersionResponse>;
  onCreateProvider?: (request: CreateModelProviderRequest) => Promise<ModelProvider>;
  onCreateModelConfig?: (request: CreateModelConfigRequest) => Promise<ModelConfig>;
  onListPlugins?: () => Promise<PluginManifest[]>;
}

interface ProjectAssistantFormValues extends CreateAgentDraftRequest {
  providerCode: string;
  providerName: string;
  providerType: string;
  baseUrl: string;
  apiKey: string;
  modelCode: string;
  modelName: string;
  modelType: string;
  contextWindow: number;
  configJson: string;
  modelPolicyJson: string;
  toolPolicyJson: string;
  contextPolicyJson: string;
  memoryPolicyJson: string;
  orchestrationPolicyJson: string;
  toolIds?: number[];
}

interface PluginToolGroup {
  pluginCode: string;
  pluginName: string;
  tools: PluginTool[];
}

const initialValues: ProjectAssistantFormValues = {
  name: '项目助理 Agent',
  roleDescription: '协助项目团队推进复杂项目工作',
  responsibilities: '会议纪要、行动项整理、项目周报、风险分析',
  boundaries: '不直接执行删除、外发敏感信息等高风险动作',
  providerCode: 'openai-prod',
  providerName: 'OpenAI Production',
  providerType: 'openai_compatible',
  baseUrl: 'https://api.openai.com/v1',
  apiKey: '',
  modelCode: 'gpt-4o-mini',
  modelName: 'GPT-4o Mini',
  modelType: 'chat',
  contextWindow: 128000,
  configJson: '{"temperature":0.2}',
  modelPolicyJson: '{"routing":"agent_default"}',
  toolPolicyJson: '{"approval":"risk_based"}',
  contextPolicyJson: '{"maxItems":8}',
  memoryPolicyJson: '{"write":"confirmed_only","enabled":true,"maxItems":5,"scopes":["task"]}',
  orchestrationPolicyJson: '{"executionMode":"dynamic_workflow"}',
  toolIds: [],
};

export function ProjectAssistantForm({
  onCreateDraft = createAgentDraft,
  onCreateVersion = createAgentVersion,
  onReplaceVersionTools = replaceAgentVersionTools,
  onCreateProvider = createModelProvider,
  onCreateModelConfig = createModelConfig,
  onListPlugins = listPluginManifests,
}: ProjectAssistantFormProps) {
  const [form] = Form.useForm<ProjectAssistantFormValues>();
  const [creating, setCreating] = useState(false);
  const [updatingTools, setUpdatingTools] = useState(false);
  const [draft, setDraft] = useState<AgentDraftResponse>();
  const [version, setVersion] = useState<AgentVersionResponse>();
  const [pluginToolGroups, setPluginToolGroups] = useState<PluginToolGroup[]>([]);
  const [toolSearch, setToolSearch] = useState('');
  const [messageApi, contextHolder] = message.useMessage();

  useEffect(() => {
    void onListPlugins()
      .then((plugins) => {
        setPluginToolGroups(
          plugins
            .map((plugin) => ({
              pluginCode: plugin.pluginCode,
              pluginName: plugin.pluginName,
              tools: plugin.tools.filter((tool) => tool.toolId && tool.status === 'active'),
            }))
            .filter((plugin) => plugin.tools.length > 0),
        );
      })
      .catch((error) => {
        messageApi.error(error instanceof Error ? error.message : '插件工具加载失败');
      });
  }, [messageApi, onListPlugins]);

  const filteredPluginToolGroups = useMemo(() => {
    const keyword = toolSearch.trim().toLowerCase();
    if (!keyword) {
      return pluginToolGroups;
    }

    return pluginToolGroups
      .map((plugin) => ({
        ...plugin,
        tools: plugin.tools.filter((tool) =>
          [tool.toolName, tool.toolCode, tool.riskLevel, tool.toolType, plugin.pluginName, plugin.pluginCode]
            .join(' ')
            .toLowerCase()
            .includes(keyword),
        ),
      }))
      .filter((plugin) => plugin.tools.length > 0);
  }, [pluginToolGroups, toolSearch]);

  const handleCreateDraft = async (values: ProjectAssistantFormValues) => {
    setCreating(true);
    try {
      const provider = await onCreateProvider({
        providerCode: values.providerCode,
        providerName: values.providerName,
        providerType: values.providerType,
        baseUrl: values.baseUrl,
        apiKey: values.apiKey,
      });
      const modelConfig = await onCreateModelConfig({
        providerId: provider.id,
        modelCode: values.modelCode,
        modelName: values.modelName,
        modelType: values.modelType,
        contextWindow: values.contextWindow,
        configJson: values.configJson,
      });
      const response = await onCreateDraft({
        name: values.name,
        description: values.description,
        roleDescription: values.roleDescription,
        responsibilities: values.responsibilities,
        boundaries: values.boundaries,
        modelProviderId: provider.id,
        modelConfigId: modelConfig.id,
      });
      const versionResponse = await onCreateVersion(response.agentId, {
        rolePrompt: values.roleDescription,
        responsibilityText: values.responsibilities,
        boundaryText: values.boundaries,
        configJson: JSON.stringify({
          modelProviderId: provider.id,
          modelConfigId: modelConfig.id,
          modelConfigJson: values.configJson,
        }),
        modelPolicyJson: values.modelPolicyJson,
        toolPolicyJson: values.toolPolicyJson,
        contextPolicyJson: values.contextPolicyJson,
        memoryPolicyJson: values.memoryPolicyJson,
        orchestrationPolicyJson: values.orchestrationPolicyJson,
        toolIds: values.toolIds ?? [],
      });
      setDraft(response);
      setVersion(versionResponse);
      messageApi.success('草稿创建成功');
    } catch (error) {
      messageApi.error(error instanceof Error ? error.message : '草稿创建失败');
    } finally {
      setCreating(false);
    }
  };

  const handleReplaceVersionTools = async () => {
    if (!draft || !version) {
      return;
    }
    setUpdatingTools(true);
    try {
      const toolIds = form.getFieldValue('toolIds') ?? [];
      const versionResponse = await onReplaceVersionTools(draft.agentId, version.agentVersionId, { toolIds });
      setVersion(versionResponse);
      messageApi.success('Draft version tool scope updated');
    } catch (error) {
      messageApi.error(error instanceof Error ? error.message : 'Draft version tool scope update failed');
    } finally {
      setUpdatingTools(false);
    }
  };

  return (
    <Card title="项目助理最小配置" variant="borderless">
      {contextHolder}
      <Form form={form} name="project-assistant-config" layout="vertical" initialValues={initialValues} onFinish={handleCreateDraft}>
        <Form.Item label="Agent 名称" name="name" rules={[{ required: true, message: '请输入 Agent 名称' }]}>
          <Input />
        </Form.Item>
        <Form.Item label="角色描述" name="roleDescription" rules={[{ required: true, message: '请输入角色描述' }]}>
          <Input.TextArea rows={3} />
        </Form.Item>
        <Form.Item label="主要职责" name="responsibilities" rules={[{ required: true, message: '请输入主要职责' }]}>
          <Input.TextArea rows={4} />
        </Form.Item>
        <Form.Item label="不处理事项" name="boundaries" rules={[{ required: true, message: '请输入边界说明' }]}>
          <Input.TextArea rows={4} />
        </Form.Item>

        <Divider titlePlacement="left">Agent 专属模型配置</Divider>
        <Typography.Paragraph type="secondary">
          每个 Agent 可绑定独立的模型供应商、URL、API Key 和模型配置，用于后续按 Agent 定制模型能力。
        </Typography.Paragraph>
        <Form.Item label="供应商编码" name="providerCode" rules={[{ required: true, message: '请输入供应商编码' }]}>
          <Input />
        </Form.Item>
        <Form.Item label="供应商名称" name="providerName" rules={[{ required: true, message: '请输入供应商名称' }]}>
          <Input />
        </Form.Item>
        <Form.Item label="供应商类型" name="providerType" rules={[{ required: true, message: '请输入供应商类型' }]}>
          <Input />
        </Form.Item>
        <Form.Item label="模型服务 URL" name="baseUrl" rules={[{ required: true, message: '请输入模型服务 URL' }]}>
          <Input />
        </Form.Item>
        <Form.Item label="API Key" name="apiKey" rules={[{ required: true, message: '请输入 API Key' }]}>
          <Input.Password />
        </Form.Item>
        <Form.Item label="模型编码" name="modelCode" rules={[{ required: true, message: '请输入模型编码' }]}>
          <Input />
        </Form.Item>
        <Form.Item label="模型名称" name="modelName" rules={[{ required: true, message: '请输入模型名称' }]}>
          <Input />
        </Form.Item>
        <Form.Item label="模型类型" name="modelType" rules={[{ required: true, message: '请输入模型类型' }]}>
          <Input />
        </Form.Item>
        <Form.Item label="上下文窗口" name="contextWindow" rules={[{ required: true, message: '请输入上下文窗口' }]}>
          <InputNumber min={1} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item label="模型参数 JSON" name="configJson" rules={[{ required: true, message: '请输入模型参数 JSON' }]}>
          <Input.TextArea rows={3} />
        </Form.Item>
        <Divider titlePlacement="left">Agent 运行策略</Divider>
        <Form.Item label="模型策略 JSON" name="modelPolicyJson" rules={[{ required: true, message: '请输入模型策略 JSON' }]}>
          <Input.TextArea rows={3} />
        </Form.Item>
        <Form.Item label="工具策略 JSON" name="toolPolicyJson" rules={[{ required: true, message: '请输入工具策略 JSON' }]}>
          <Input.TextArea rows={3} />
        </Form.Item>
        <Form.Item label="上下文策略 JSON" name="contextPolicyJson" rules={[{ required: true, message: '请输入上下文策略 JSON' }]}>
          <Input.TextArea rows={3} />
        </Form.Item>
        <Form.Item label="记忆策略 JSON" name="memoryPolicyJson" rules={[{ required: true, message: '请输入记忆策略 JSON' }]}>
          <Input.TextArea rows={3} />
        </Form.Item>
        <Form.Item label="编排策略 JSON" name="orchestrationPolicyJson" rules={[{ required: true, message: '请输入编排策略 JSON' }]}>
          <Input.TextArea rows={3} />
        </Form.Item>
        <Divider titlePlacement="left">Agent 工具范围</Divider>
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="Tool scope is saved into a new AgentVersion snapshot"
          description="Published versions are not edited in place. To change allowed tools, create and publish a new AgentVersion."
        />
        <Input.Search
          allowClear
          placeholder="Search tools"
          style={{ marginBottom: 12 }}
          value={toolSearch}
          onChange={(event) => setToolSearch(event.target.value)}
          onPressEnter={(event) => event.preventDefault()}
        />
        <Form.Item label="允许调用的插件工具" name="toolIds">
          {pluginToolGroups.length === 0 ? (
            <Typography.Text type="secondary">暂无可选插件工具</Typography.Text>
          ) : filteredPluginToolGroups.length === 0 ? (
            <Typography.Text type="secondary">No matching tools</Typography.Text>
          ) : (
            <Checkbox.Group style={{ width: '100%' }}>
              <Flex vertical gap={12}>
                {filteredPluginToolGroups.map((plugin) => (
                  <div key={plugin.pluginCode}>
                    <Typography.Text strong>
                      {plugin.pluginName} ({plugin.pluginCode})
                    </Typography.Text>
                    <Flex vertical gap={8} style={{ marginTop: 8 }}>
                      {plugin.tools.map((tool) => (
                        <Checkbox key={tool.toolId} value={tool.toolId as number}>
                          {tool.toolName} ({tool.toolCode})
                        </Checkbox>
                      ))}
                    </Flex>
                  </div>
                ))}
              </Flex>
            </Checkbox.Group>
          )}
        </Form.Item>
        <Flex gap={12} align="center">
          <Button type="primary" htmlType="submit" loading={creating}>
            创建草稿
          </Button>
          <Button disabled={!draft || !version} loading={updatingTools} onClick={handleReplaceVersionTools}>
            Sync draft tool scope
          </Button>
        </Flex>
      </Form>

      {draft ? (
        <Typography.Paragraph style={{ marginTop: 16 }}>
          草稿已创建：{draft.agentCode || `#${draft.agentId}`}，状态 {draft.status}。版本 {version?.versionNo || '-'} 已创建。
        </Typography.Paragraph>
      ) : null}
    </Card>
  );
}
