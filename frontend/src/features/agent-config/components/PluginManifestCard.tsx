import { Button, Card, Empty, Form, Input, InputNumber, Space, Table, Tag, Typography, message } from 'antd';
import { useEffect, useState } from 'react';
import type { ImportPluginManifestRequest, PluginManifest, PluginTool } from '../../../types/plugin';
import { disablePluginManifest, enablePluginManifest, importPluginManifest, listPluginManifests } from '../../../services/plugin-api';

interface PluginManifestCardProps {
  onImport?: (request: ImportPluginManifestRequest) => Promise<PluginManifest>;
  onList?: () => Promise<PluginManifest[]>;
  onEnable?: (pluginId: number) => Promise<PluginManifest>;
  onDisable?: (pluginId: number) => Promise<PluginManifest>;
}

const sampleManifest = JSON.stringify(
  {
    pluginCode: 'project-business-suite',
    pluginName: 'Project Business Suite',
    pluginVersion: '1.0.0',
    tools: [
      {
        toolCode: 'controlled.http.project-query',
        toolName: 'Project Query',
        toolType: 'http',
        riskLevel: 'low',
        endpointUrl: 'https://api.example.com/project/query',
        schema: {
          required: ['query'],
          properties: {
            query: { type: 'string' },
          },
          additionalProperties: false,
        },
        authConfig: {
          headers: {
            'X-Tenant': 'demo',
          },
        },
      },
      {
        toolCode: 'controlled.cli.project-update',
        toolName: 'Project Update',
        toolType: 'cli',
        riskLevel: 'medium',
        endpointUrl: 'E:\\tools\\project-update.exe',
        schema: {
          required: ['id', 'status'],
          properties: {
            id: { type: 'integer' },
            status: { type: 'string' },
          },
          additionalProperties: false,
        },
        authConfig: {
          workingDirectory: 'E:\\tools',
        },
      },
    ],
  },
  null,
  2,
);

function shortHash(value?: string) {
  return value ? value.slice(0, 12) : '-';
}

export function PluginManifestCard({
  onImport = importPluginManifest,
  onList = listPluginManifests,
  onEnable = enablePluginManifest,
  onDisable = disablePluginManifest,
}: PluginManifestCardProps) {
  const [plugins, setPlugins] = useState<PluginManifest[]>([]);
  const [loading, setLoading] = useState(false);
  const [importing, setImporting] = useState(false);
  const [togglingPluginId, setTogglingPluginId] = useState<number>();
  const [messageApi, contextHolder] = message.useMessage();
  const [form] = Form.useForm<ImportPluginManifestRequest>();

  const refreshPlugins = async () => {
    setLoading(true);
    try {
      setPlugins(await onList());
    } catch (error) {
      messageApi.error(error instanceof Error ? error.message : '插件列表加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void refreshPlugins();
  }, []);

  const handleImport = async (values: ImportPluginManifestRequest) => {
    setImporting(true);
    try {
      const request = {
        manifestJson: values.manifestJson,
        ...(values.agentId ? { agentId: values.agentId } : {}),
        ...(values.agentVersionId ? { agentVersionId: values.agentVersionId } : {}),
      };
      const plugin = await onImport(request);
      const versionTip = plugin.boundAgentVersionId ? `，Draft Version #${plugin.boundAgentVersionId} 已同步 ${plugin.agentVersionToolIds?.length ?? 0} 个工具` : '';
      messageApi.success(`插件已导入：${plugin.pluginName}${versionTip}`);
      form.setFieldsValue({ manifestJson: values.manifestJson });
      await refreshPlugins();
    } catch (error) {
      messageApi.error(error instanceof Error ? error.message : '插件导入失败');
    } finally {
      setImporting(false);
    }
  };

  const handleTogglePlugin = async (plugin: PluginManifest) => {
    if (!plugin.pluginId) {
      return;
    }
    setTogglingPluginId(plugin.pluginId);
    try {
      if (plugin.status === 'active') {
        await onDisable(plugin.pluginId);
        messageApi.success(`插件已停用：${plugin.pluginName}`);
      } else {
        await onEnable(plugin.pluginId);
        messageApi.success(`插件已启用：${plugin.pluginName}`);
      }
      await refreshPlugins();
    } catch (error) {
      messageApi.error(error instanceof Error ? error.message : '插件状态更新失败');
    } finally {
      setTogglingPluginId(undefined);
    }
  };

  return (
    <Card title="业务插件 Manifest" variant="borderless">
      {contextHolder}
      <Space orientation="vertical" size={16} style={{ width: '100%' }}>
        <Typography.Paragraph type="secondary">
          导入业务系统 CLI/HTTP 插件后，工具会注册到现有 ToolConfig，并复用审批、审计和执行器链路。
        </Typography.Paragraph>
        <Form form={form} layout="vertical" initialValues={{ manifestJson: sampleManifest }} onFinish={handleImport}>
          <Space wrap align="start">
            <Form.Item label="绑定 Agent ID" name="agentId" tooltip="可选：填写后会把本次导入的工具绑定到指定 Agent">
              <InputNumber min={1} precision={0} style={{ width: 220 }} placeholder="仅注册工具可留空" />
            </Form.Item>
            <Form.Item label="Draft AgentVersion ID" name="agentVersionId" tooltip="可选：追加到指定草稿 AgentVersion 的工具范围">
              <InputNumber min={1} precision={0} style={{ width: 220 }} placeholder="草稿版本，可留空" />
            </Form.Item>
          </Space>
          <Form.Item label="Manifest JSON" name="manifestJson" rules={[{ required: true, message: '请输入 Manifest JSON' }]}>
            <Input.TextArea rows={10} />
          </Form.Item>
          <Space>
            <Button type="primary" htmlType="submit" loading={importing}>
              导入 Manifest
            </Button>
            <Button onClick={refreshPlugins} loading={loading}>
              刷新列表
            </Button>
          </Space>
        </Form>

        {plugins.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无已导入插件" />
        ) : (
          plugins.map((plugin) => (
            <Card key={`${plugin.pluginCode}-${plugin.pluginVersion}`} size="small" title={`${plugin.pluginName} ${plugin.pluginVersion}`}>
              <Space orientation="vertical" size={12} style={{ width: '100%' }}>
                <Space wrap>
                  <Tag>{plugin.pluginCode}</Tag>
                  <Tag color={plugin.status === 'active' ? 'success' : 'default'}>{plugin.status}</Tag>
                  {plugin.manifestHash ? <Tag>sha256 {shortHash(plugin.manifestHash)}</Tag> : null}
                  {plugin.pluginId ? (
                    <Button size="small" loading={togglingPluginId === plugin.pluginId} onClick={() => handleTogglePlugin(plugin)}>
                      {plugin.status === 'active' ? 'Disable' : 'Enable'}
                    </Button>
                  ) : null}
                  <Typography.Text type="secondary">{plugin.tools.length} 个工具</Typography.Text>
                </Space>
                <Table<PluginTool>
                  rowKey={(tool) => tool.toolCode}
                  size="small"
                  pagination={false}
                  dataSource={plugin.tools}
                  columns={[
                    { title: '工具', dataIndex: 'toolName' },
                    { title: '编码', dataIndex: 'toolCode' },
                    { title: '执行器', dataIndex: 'toolType', width: 100 },
                    {
                      title: '绑定',
                      dataIndex: 'bound',
                      width: 120,
                      render: (_, tool) =>
                        tool.bound && tool.boundAgentId ? (
                          <Space direction="vertical" size={2}>
                            <Tag color="blue">Agent {tool.boundAgentId}</Tag>
                            {tool.manifestHash ? (
                              <Typography.Text type="secondary">sha256 {shortHash(tool.manifestHash)}</Typography.Text>
                            ) : null}
                          </Space>
                        ) : (
                          <Space direction="vertical" size={2}>
                            <Typography.Text type="secondary">未绑定</Typography.Text>
                            {tool.manifestHash ? (
                              <Typography.Text type="secondary">sha256 {shortHash(tool.manifestHash)}</Typography.Text>
                            ) : null}
                          </Space>
                        ),
                    },
                    {
                      title: '风险',
                      dataIndex: 'riskLevel',
                      width: 100,
                      render: (riskLevel) => <Tag color={riskLevel === 'high' ? 'red' : riskLevel === 'medium' ? 'orange' : 'green'}>{riskLevel}</Tag>,
                    },
                    { title: '状态', dataIndex: 'status', width: 100 },
                  ]}
                />
              </Space>
            </Card>
          ))
        )}
      </Space>
    </Card>
  );
}
