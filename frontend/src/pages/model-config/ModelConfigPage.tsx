import { useEffect, useState } from 'react';
import {
  Card,
  Table,
  Modal,
  Form,
  Input,
  Select,
  Switch,
  Tabs,
  Button,
  Space,
  Typography,
  Tag,
  message,
  Divider,
} from 'antd';
import { PlusOutlined, CheckCircleOutlined, ApiOutlined } from '@ant-design/icons';
import { http, unwrap } from '../../services/http';
import type { ApiResponse } from '../../types/api';

const { Title, Text } = Typography;
const { TextArea } = Input;

// Types
interface ModelProvider {
  id: string;
  providerName: string;
  providerType: string;
  baseUrl: string;
  authConfigJson: string;
  status: string;
}

interface ModelConfig {
  id: string;
  providerId: string;
  modelCode: string;
  modelName: string;
  configJson: string;
  status: string;
}

interface CreateModelProviderCommand {
  tenantId: string;
  providerName: string;
  providerType: string;
  baseUrl: string;
  authConfigJson: string;
  status: string;
}

interface CreateModelConfigCommand {
  tenantId: string;
  providerId: string;
  modelCode: string;
  modelName: string;
  configJson: string;
  status: string;
}

export function ModelConfigPage() {
  const [providers, setProviders] = useState<ModelProvider[]>([]);
  const [modelConfigs, setModelConfigs] = useState<ModelConfig[]>([]);
  const [providerLoading, setProviderLoading] = useState(false);
  const [configLoading, setConfigLoading] = useState(false);
  const [isProviderModalOpen, setIsProviderModalOpen] = useState(false);
  const [isConfigModalOpen, setIsConfigModalOpen] = useState(false);
  const [providerForm] = Form.useForm();
  const [configForm] = Form.useForm();

  const TENANT_ID = import.meta.env.VITE_DEV_TENANT_ID || '100';

  // Load providers and configs on mount
  useEffect(() => {
    loadProviders();
    loadModelConfigs();
  }, []);

  const loadProviders = async () => {
    try {
      setProviderLoading(true);
      // Since there's no list API yet, we'll just show empty state
      // In production, you would have a GET /api/v1/model-providers endpoint
      setProviders([]);
    } catch (error) {
      console.error('Failed to load providers:', error);
      message.error('加载模型提供商失败');
    } finally {
      setProviderLoading(false);
    }
  };

  const loadModelConfigs = async () => {
    try {
      setConfigLoading(true);
      // Since there's no list API yet, we'll just show empty state
      // In production, you would have a GET /api/v1/model-configs endpoint
      setModelConfigs([]);
    } catch (error) {
      console.error('Failed to load model configs:', error);
      message.error('加载模型配置失败');
    } finally {
      setConfigLoading(false);
    }
  };

  const handleCreateProvider = async (values: any) => {
    try {
      const command: CreateModelProviderCommand = {
        tenantId: TENANT_ID,
        providerName: values.providerName,
        providerType: values.providerType,
        baseUrl: values.baseUrl,
        authConfigJson: JSON.stringify({
          api_key: values.apiKey,
        }),
        status: values.status ? 'active' : 'inactive',
      };

      const response = await http.post<ApiResponse<any>>('/api/v1/model-providers', command);

      if (String(response.data.code) === '0') {
        message.success('模型提供商创建成功');
        setIsProviderModalOpen(false);
        providerForm.resetFields();
        // Reload providers (in real scenario)
        message.info('请刷新页面查看新创建的提供商');
      } else {
        message.error(response.data.message || '创建失败');
      }
    } catch (error: any) {
      console.error('Failed to create provider:', error);
      message.error(error.response?.data?.message || '创建模型提供商失败');
    }
  };

  const handleCreateConfig = async (values: any) => {
    try {
      let configJson = '{}';
      if (values.configJson) {
        // Validate JSON
        try {
          JSON.parse(values.configJson);
          configJson = values.configJson;
        } catch (e) {
          message.error('额外配置必须是有效的 JSON 格式');
          return;
        }
      }

      const command: CreateModelConfigCommand = {
        tenantId: TENANT_ID,
        providerId: values.providerId,
        modelCode: values.modelCode,
        modelName: values.modelName,
        configJson,
        status: values.status ? 'active' : 'inactive',
      };

      const response = await http.post<ApiResponse<any>>('/api/v1/model-configs', command);

      if (String(response.data.code) === '0') {
        message.success('模型配置创建成功');
        setIsConfigModalOpen(false);
        configForm.resetFields();
        // Reload model configs (in real scenario)
        message.info('请刷新页面查看新创建的配置');
      } else {
        message.error(response.data.message || '创建失败');
      }
    } catch (error: any) {
      console.error('Failed to create config:', error);
      message.error(error.response?.data?.message || '创建模型配置失败');
    }
  };

  const testConnection = async (baseUrl: string, apiKey: string) => {
    try {
      message.loading('正在测试连接...', 0);

      // Simple test - try to reach the base URL
      // In production, you would call a proper test endpoint
      const response = await fetch(baseUrl, {
        method: 'GET',
        headers: {
          'Authorization': `Bearer ${apiKey}`,
          'Content-Type': 'application/json',
        },
      });

      message.destroy();

      if (response.ok) {
        message.success('连接测试成功');
      } else {
        message.warning(`连接返回状态码: ${response.status}`);
      }
    } catch (error: any) {
      message.destroy();
      message.error('连接测试失败，请检查网络或配置');
      console.error('Test connection error:', error);
    }
  };

  const providerColumns = [
    {
      title: '名称',
      dataIndex: 'providerName',
      key: 'providerName',
    },
    {
      title: '类型',
      dataIndex: 'providerType',
      key: 'providerType',
      render: (type: string) => {
        const color = type === 'openai_compatible' ? 'blue' : 'green';
        return <Tag color={color}>{type}</Tag>;
      },
    },
    {
      title: '基础 URL',
      dataIndex: 'baseUrl',
      key: 'baseUrl',
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => {
        const color = status === 'active' ? 'success' : 'default';
        const text = status === 'active' ? '启用' : '禁用';
        return <Tag color={color}>{text}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: ModelProvider) => (
        <Space>
          <Button
            type="link"
            size="small"
            onClick={() => {
              const authConfig = JSON.parse(record.authConfigJson);
              testConnection(record.baseUrl, authConfig.api_key);
            }}
          >
            测试连接
          </Button>
        </Space>
      ),
    },
  ];

  const configColumns = [
    {
      title: '模型代码',
      dataIndex: 'modelCode',
      key: 'modelCode',
    },
    {
      title: '模型名称',
      dataIndex: 'modelName',
      key: 'modelName',
    },
    {
      title: '提供商 ID',
      dataIndex: 'providerId',
      key: 'providerId',
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => {
        const color = status === 'active' ? 'success' : 'default';
        const text = status === 'active' ? '启用' : '禁用';
        return <Tag color={color}>{text}</Tag>;
      },
    },
  ];

  const providerTabContent = (
    <div>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
        <Text type="secondary">
          配置 AI 模型提供商，支持 OpenAI 兼容接口和 Anthropic API
        </Text>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => setIsProviderModalOpen(true)}
        >
          新建提供商
        </Button>
      </div>
      <Table
        columns={providerColumns}
        dataSource={providers}
        loading={providerLoading}
        rowKey="id"
        locale={{ emptyText: '暂无模型提供商，点击右上角按钮创建' }}
        pagination={false}
      />
    </div>
  );

  const configTabContent = (
    <div>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
        <Text type="secondary">
          为已配置的提供商添加具体的模型配置，指定模型代码和名称
        </Text>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => setIsConfigModalOpen(true)}
        >
          新建模型配置
        </Button>
      </div>
      <Table
        columns={configColumns}
        dataSource={modelConfigs}
        loading={configLoading}
        rowKey="id"
        locale={{ emptyText: '暂无模型配置，点击右上角按钮创建' }}
        pagination={false}
      />
    </div>
  );

  const tabItems = [
    {
      key: 'providers',
      label: '模型提供商',
      children: providerTabContent,
    },
    {
      key: 'configs',
      label: '模型配置',
      children: configTabContent,
    },
  ];

  return (
    <main className="agent-workbench-shell" style={{ padding: 24 }}>
      <Card>
        <Space direction="vertical" size={20} style={{ width: '100%' }}>
          <div>
            <Title level={3} style={{ marginBottom: 8 }}>
              <ApiOutlined style={{ marginRight: 8 }} />
              模型配置
            </Title>
            <Text type="secondary">
              管理 AI 模型提供商和模型配置，这是 Agent 运行的核心配置
            </Text>
          </div>

          <Tabs items={tabItems} defaultActiveKey="providers" />
        </Space>
      </Card>

      {/* Provider Creation Modal */}
      <Modal
        title="新建模型提供商"
        open={isProviderModalOpen}
        onCancel={() => {
          setIsProviderModalOpen(false);
          providerForm.resetFields();
        }}
        footer={null}
        width={600}
      >
        <Form
          form={providerForm}
          layout="vertical"
          onFinish={handleCreateProvider}
          initialValues={{
            providerType: 'openai_compatible',
            status: true,
          }}
        >
          <Form.Item
            label="提供商名称"
            name="providerName"
            rules={[{ required: true, message: '请输入提供商名称' }]}
          >
            <Input placeholder="例如：OpenAI 主服务" />
          </Form.Item>

          <Form.Item
            label="提供商类型"
            name="providerType"
            rules={[{ required: true, message: '请选择提供商类型' }]}
          >
            <Select>
              <Select.Option value="openai_compatible">OpenAI 兼容接口</Select.Option>
              <Select.Option value="anthropic">Anthropic</Select.Option>
            </Select>
          </Form.Item>

          <Form.Item
            label="基础 URL"
            name="baseUrl"
            rules={[
              { required: true, message: '请输入基础 URL' },
              { type: 'url', message: '请输入有效的 URL' },
            ]}
          >
            <Input placeholder="https://api.openai.com/v1" />
          </Form.Item>

          <Form.Item
            label="API Key"
            name="apiKey"
            rules={[{ required: true, message: '请输入 API Key' }]}
          >
            <Input.Password placeholder="sk-..." />
          </Form.Item>

          <Form.Item
            label="状态"
            name="status"
            valuePropName="checked"
          >
            <Switch checkedChildren="启用" unCheckedChildren="禁用" />
          </Form.Item>

          <Divider />

          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                创建提供商
              </Button>
              <Button onClick={() => providerForm.resetFields()}>
                重置
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* Config Creation Modal */}
      <Modal
        title="新建模型配置"
        open={isConfigModalOpen}
        onCancel={() => {
          setIsConfigModalOpen(false);
          configForm.resetFields();
        }}
        footer={null}
        width={600}
      >
        <Form
          form={configForm}
          layout="vertical"
          onFinish={handleCreateConfig}
          initialValues={{
            status: true,
          }}
        >
          <Form.Item
            label="提供商"
            name="providerId"
            rules={[{ required: true, message: '请选择提供商' }]}
          >
            <Select
              placeholder="选择模型提供商"
              options={providers.map((p) => ({
                label: p.providerName,
                value: p.id,
              }))}
            />
          </Form.Item>

          <Form.Item
            label="模型代码"
            name="modelCode"
            rules={[{ required: true, message: '请输入模型代码' }]}
            help="模型的唯一标识符，如 gpt-4、claude-3-opus"
          >
            <Input placeholder="gpt-4" />
          </Form.Item>

          <Form.Item
            label="模型名称"
            name="modelName"
            rules={[{ required: true, message: '请输入模型名称' }]}
          >
            <Input placeholder="GPT-4 Turbo" />
          </Form.Item>

          <Form.Item
            label="额外配置（JSON）"
            name="configJson"
            help='可选的额外配置，如 temperature、max_tokens 等，必须是有效的 JSON'
          >
            <TextArea
              rows={4}
              placeholder='{"temperature": 0.7, "max_tokens": 2000}'
            />
          </Form.Item>

          <Form.Item
            label="状态"
            name="status"
            valuePropName="checked"
          >
            <Switch checkedChildren="启用" unCheckedChildren="禁用" />
          </Form.Item>

          <Divider />

          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                创建配置
              </Button>
              <Button onClick={() => configForm.resetFields()}>
                重置
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </main>
  );
}
