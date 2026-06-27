import { Button, Card, Checkbox, Empty, Flex, Form, Input, InputNumber, Radio, Space, Tag, Typography, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import type { AgentVersionResponse, ReplaceAgentVersionToolsRequest } from '../../../types/agent';
import type { PluginManifest, PluginTool } from '../../../types/plugin';
import { listAgentVersions, replaceAgentVersionTools } from '../../../services/agent-api';
import { listPluginManifests } from '../../../services/plugin-api';

interface ExistingAgentVersionToolsCardProps {
  onListVersions?: (agentId: number) => Promise<AgentVersionResponse[]>;
  onReplaceVersionTools?: (agentId: number, versionId: number, request: ReplaceAgentVersionToolsRequest) => Promise<AgentVersionResponse>;
  onListPlugins?: () => Promise<PluginManifest[]>;
}

interface FormValues {
  agentId?: number;
  versionId?: number;
  toolIds?: number[];
}

interface PluginToolGroup {
  pluginCode: string;
  pluginName: string;
  tools: PluginTool[];
}

export function ExistingAgentVersionToolsCard({
  onListVersions = listAgentVersions,
  onReplaceVersionTools = replaceAgentVersionTools,
  onListPlugins = listPluginManifests,
}: ExistingAgentVersionToolsCardProps) {
  const [form] = Form.useForm<FormValues>();
  const [searchParams] = useSearchParams();
  const [versions, setVersions] = useState<AgentVersionResponse[]>([]);
  const [pluginToolGroups, setPluginToolGroups] = useState<PluginToolGroup[]>([]);
  const [loadingVersions, setLoadingVersions] = useState(false);
  const [saving, setSaving] = useState(false);
  const [toolSearch, setToolSearch] = useState('');
  const [messageApi, contextHolder] = message.useMessage();
  const queryAgentId = Number(searchParams.get('agentId'));
  const queryVersionId = Number(searchParams.get('versionId'));

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

  useEffect(() => {
    const initialValues: FormValues = {};
    if (Number.isFinite(queryAgentId) && queryAgentId > 0) {
      initialValues.agentId = queryAgentId;
    }
    if (Number.isFinite(queryVersionId) && queryVersionId > 0) {
      initialValues.versionId = queryVersionId;
    }
    if (initialValues.agentId || initialValues.versionId) {
      form.setFieldsValue(initialValues);
    }
  }, [form, queryAgentId, queryVersionId]);

  const selectedVersionId = Form.useWatch('versionId', form);
  const selectedVersion = versions.find((version) => version.agentVersionId === selectedVersionId);

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

  const handleLoadVersions = async () => {
    const agentId = form.getFieldValue('agentId');
    if (!agentId) {
      messageApi.warning('请先输入 Agent ID');
      return;
    }
    setLoadingVersions(true);
    try {
      const nextVersions = await onListVersions(agentId);
      setVersions(nextVersions);
      const requestedVersionId = form.getFieldValue('versionId');
      const requestedVersion = nextVersions.find((version) => version.agentVersionId === requestedVersionId);
      const firstDraft = requestedVersion || nextVersions.find((version) => version.versionStatus === 'draft');
      form.setFieldsValue({
        versionId: firstDraft?.agentVersionId,
        toolIds: firstDraft?.toolIds ?? [],
      });
    } catch (error) {
      messageApi.error(error instanceof Error ? error.message : 'AgentVersion 加载失败');
    } finally {
      setLoadingVersions(false);
    }
  };

  const handleVersionChange = (versionId: number) => {
    const version = versions.find((item) => item.agentVersionId === versionId);
    form.setFieldsValue({ toolIds: version?.toolIds ?? [] });
  };

  const handleSave = async () => {
    const values = form.getFieldsValue();
    const version = versions.find((item) => item.agentVersionId === values.versionId);
    if (!values.agentId || !version) {
      messageApi.warning('请先加载并选择 AgentVersion');
      return;
    }
    if (version.versionStatus !== 'draft') {
      messageApi.warning('只能更新 draft AgentVersion 的工具范围');
      return;
    }
    setSaving(true);
    try {
      const response = await onReplaceVersionTools(values.agentId, version.agentVersionId, { toolIds: values.toolIds ?? [] });
      setVersions((current) => current.map((item) => (item.agentVersionId === response.agentVersionId ? response : item)));
      form.setFieldsValue({ toolIds: response.toolIds ?? [] });
      messageApi.success('Draft AgentVersion 工具范围已同步');
    } catch (error) {
      messageApi.error(error instanceof Error ? error.message : '工具范围同步失败');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card title="已有 AgentVersion 工具范围" variant="borderless">
      {contextHolder}
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Typography.Paragraph type="secondary">
          维护已有草稿版本允许调用的插件工具。已发布版本保持只读，需要新建版本后再调整工具范围。
        </Typography.Paragraph>
        <Form form={form} layout="vertical">
          <Flex gap={12} align="end" wrap>
            <Form.Item label="Agent ID" name="agentId">
              <InputNumber min={1} precision={0} style={{ width: 220 }} placeholder="输入已有 Agent ID" />
            </Form.Item>
            <Button onClick={handleLoadVersions} loading={loadingVersions}>
              加载版本
            </Button>
          </Flex>

          {versions.length === 0 ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无已加载版本" />
          ) : (
            <Form.Item label="AgentVersion" name="versionId">
              <Radio.Group onChange={(event) => handleVersionChange(event.target.value)}>
                <Space direction="vertical">
                  {versions.map((version) => (
                    <Radio key={version.agentVersionId} value={version.agentVersionId}>
                      <Space>
                        <Typography.Text>{version.versionNo}</Typography.Text>
                        <Tag color={version.versionStatus === 'draft' ? 'blue' : 'default'}>{version.versionStatus}</Tag>
                        <Typography.Text type="secondary">#{version.agentVersionId}</Typography.Text>
                      </Space>
                    </Radio>
                  ))}
                </Space>
              </Radio.Group>
            </Form.Item>
          )}

          <Input.Search
            allowClear
            placeholder="Search tools"
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
              <Checkbox.Group disabled={!selectedVersion || selectedVersion.versionStatus !== 'draft'} style={{ width: '100%' }}>
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
          <Button type="primary" disabled={!selectedVersion || selectedVersion.versionStatus !== 'draft'} loading={saving} onClick={handleSave}>
            同步 draft 工具范围
          </Button>
        </Form>
      </Space>
    </Card>
  );
}
