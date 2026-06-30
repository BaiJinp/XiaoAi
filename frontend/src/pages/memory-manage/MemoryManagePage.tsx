import { useState } from 'react';
import {
  Button,
  Form,
  Input,
  message,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { http, unwrap } from '../../services/http';
import type { ApiResponse, PageResult } from '../../types/api';

const { Title, Text } = Typography;
const { Option } = Select;

interface AgentMemory {
  id: string;
  tenantId: string;
  agentId?: string;
  taskId?: string;
  userId: string;
  memoryType: 'decision' | 'preference' | 'constraint' | 'fact';
  memoryScope: 'task' | 'session' | 'agent';
  summaryText: string;
  confidence: 'high' | 'medium' | 'low';
  createdAt: string;
}

const memoryTypeColorMap: Record<string, string> = {
  decision: 'blue',
  preference: 'green',
  constraint: 'red',
  fact: 'purple',
};

const memoryTypeLabelMap: Record<string, string> = {
  decision: '决策记忆',
  preference: '偏好记忆',
  constraint: '约束记忆',
  fact: '事实记忆',
};

const scopeColorMap: Record<string, string> = {
  task: 'cyan',
  session: 'orange',
  agent: 'geekblue',
};

const scopeLabelMap: Record<string, string> = {
  task: '任务级',
  session: '会话级',
  agent: 'Agent级',
};

const confidenceColorMap: Record<string, string> = {
  high: 'success',
  medium: 'warning',
  low: 'error',
};

const confidenceLabelMap: Record<string, string> = {
  high: '高',
  medium: '中',
  low: '低',
};

export function MemoryManagePage() {
  const [memories, setMemories] = useState<AgentMemory[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [createModalVisible, setCreateModalVisible] = useState(false);
  const [filterType, setFilterType] = useState<string | undefined>();
  const [filterScope, setFilterScope] = useState<string | undefined>();
  const [form] = Form.useForm();

  const fetchMemories = async (page: number, size: number) => {
    try {
      setLoading(true);
      const params = new URLSearchParams({
        tenantId: '100',
        userId: '1000',
        page: String(page),
        size: String(size),
      });
      if (filterType) {
        params.append('memoryType', filterType);
      }
      if (filterScope) {
        params.append('memoryScope', filterScope);
      }
      const data = await unwrap<PageResult<AgentMemory>>(
        http.get(`/api/v1/agent-memories?${params.toString()}`),
      );
      setMemories(data.records || []);
      setTotal(data.total || 0);
      setCurrentPage(data.current || page);
      setPageSize(data.size || size);
    } catch (err) {
      message.error('获取记忆列表失败');
    } finally {
      setLoading(false);
    }
  };

  const handleCreateMemory = async (values: any) => {
    try {
      await unwrap(
        http.post('/api/v1/agent-memories', {
          tenantId: 100,
          agentId: values.agentId,
          taskId: values.taskId,
          userId: 1000,
          memoryType: values.memoryType,
          memoryScope: values.memoryScope,
          summaryText: values.summaryText,
          confidence: values.confidence,
        }),
      );
      message.success('创建记忆成功');
      setCreateModalVisible(false);
      form.resetFields();
      fetchMemories(currentPage, pageSize);
    } catch (err) {
      message.error('创建记忆失败');
    }
  };

  const handleArchiveMemory = async (id: string) => {
    try {
      await unwrap(http.post(`/api/v1/agent-memories/${id}/archive`));
      message.success('归档记忆成功');
      fetchMemories(currentPage, pageSize);
    } catch (err) {
      message.error('归档记忆失败');
    }
  };

  const columns: ColumnsType<AgentMemory> = [
    {
      title: '类型',
      dataIndex: 'memoryType',
      key: 'memoryType',
      width: 100,
      render: (type: string) => (
        <Tag color={memoryTypeColorMap[type] || 'default'}>
          {memoryTypeLabelMap[type] || type}
        </Tag>
      ),
    },
    {
      title: '范围',
      dataIndex: 'memoryScope',
      key: 'memoryScope',
      width: 100,
      render: (scope: string) => (
        <Tag color={scopeColorMap[scope] || 'default'}>
          {scopeLabelMap[scope] || scope}
        </Tag>
      ),
    },
    {
      title: '摘要',
      dataIndex: 'summaryText',
      key: 'summaryText',
      ellipsis: true,
    },
    {
      title: '置信度',
      dataIndex: 'confidence',
      key: 'confidence',
      width: 100,
      render: (confidence: string) => (
        <Tag color={confidenceColorMap[confidence] || 'default'}>
          {confidenceLabelMap[confidence] || confidence}
        </Tag>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (createdAt: string) => dayjs(createdAt).format('YYYY-MM-DD HH:mm:ss'),
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) => (
        <Button size="small" danger onClick={() => handleArchiveMemory(record.id)}>
          归档
        </Button>
      ),
    },
  ];

  return (
    <main className="agent-workbench-shell" style={{ padding: 24 }}>
      <Space direction="vertical" size={20} style={{ width: '100%', maxWidth: 1200, margin: '0 auto' }}>
        <div>
          <Title level={3}>记忆管理</Title>
          <Text type="secondary">管理 Agent 的记忆数据，包括决策记忆、偏好记忆、约束记忆和事实记忆。</Text>
        </div>

        <Space wrap>
          <Select
            placeholder="记忆类型"
            allowClear
            style={{ width: 150 }}
            value={filterType}
            onChange={(value) => {
              setFilterType(value);
              fetchMemories(1, pageSize);
            }}
          >
            <Option value="decision">决策记忆</Option>
            <Option value="preference">偏好记忆</Option>
            <Option value="constraint">约束记忆</Option>
            <Option value="fact">事实记忆</Option>
          </Select>
          <Select
            placeholder="记忆范围"
            allowClear
            style={{ width: 150 }}
            value={filterScope}
            onChange={(value) => {
              setFilterScope(value);
              fetchMemories(1, pageSize);
            }}
          >
            <Option value="task">任务级</Option>
            <Option value="session">会话级</Option>
            <Option value="agent">Agent级</Option>
          </Select>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateModalVisible(true)}>
            新建记忆
          </Button>
          <Button onClick={() => fetchMemories(1, pageSize)}>刷新</Button>
        </Space>

        <Table
          columns={columns}
          dataSource={memories}
          loading={loading}
          rowKey="id"
          pagination={{
            current: currentPage,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (page, size) => {
              fetchMemories(page, size);
            },
          }}
        />
      </Space>

      <Modal
        title="新建记忆"
        open={createModalVisible}
        onCancel={() => {
          setCreateModalVisible(false);
          form.resetFields();
        }}
        footer={null}
        width={600}
      >
        <Form form={form} layout="vertical" onFinish={handleCreateMemory}>
          <Form.Item
            name="memoryType"
            label="记忆类型"
            rules={[{ required: true, message: '请选择记忆类型' }]}
          >
            <Select placeholder="请选择记忆类型">
              <Option value="decision">决策记忆</Option>
              <Option value="preference">偏好记忆</Option>
              <Option value="constraint">约束记忆</Option>
              <Option value="fact">事实记忆</Option>
            </Select>
          </Form.Item>
          <Form.Item
            name="memoryScope"
            label="记忆范围"
            rules={[{ required: true, message: '请选择记忆范围' }]}
          >
            <Select placeholder="请选择记忆范围">
              <Option value="task">任务级</Option>
              <Option value="session">会话级</Option>
              <Option value="agent">Agent级</Option>
            </Select>
          </Form.Item>
          <Form.Item
            name="summaryText"
            label="摘要文本"
            rules={[{ required: true, message: '请输入摘要文本' }]}
          >
            <Input.TextArea rows={4} placeholder="请输入记忆摘要" />
          </Form.Item>
          <Form.Item
            name="confidence"
            label="置信度"
            rules={[{ required: true, message: '请选择置信度' }]}
            initialValue="medium"
          >
            <Select placeholder="请选择置信度">
              <Option value="high">高</Option>
              <Option value="medium">中</Option>
              <Option value="low">低</Option>
            </Select>
          </Form.Item>
          <Form.Item name="agentId" label="Agent ID">
            <Input placeholder="可选" />
          </Form.Item>
          <Form.Item name="taskId" label="Task ID">
            <Input placeholder="可选" />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                创建
              </Button>
              <Button onClick={() => setCreateModalVisible(false)}>取消</Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </main>
  );
}
