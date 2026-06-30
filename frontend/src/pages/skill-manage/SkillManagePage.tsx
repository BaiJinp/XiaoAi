import { useState } from 'react';
import {
  Button,
  Card,
  Col,
  Form,
  Input,
  List,
  message,
  Modal,
  Row,
  Select,
  Space,
  Tag,
  Typography,
} from 'antd';
import { SearchOutlined, PlusOutlined, ImportOutlined } from '@ant-design/icons';
import { http, unwrap } from '../../services/http';
import type { ApiResponse } from '../../types/api';

const { Title, Text, Paragraph } = Typography;
const { Option } = Select;

interface Skill {
  id: string;
  name: string;
  code: string;
  type: 'workflow' | 'tool_chain' | 'prompt_template' | 'decision_rule';
  description?: string;
  successRate?: number;
  usageCount?: number;
}

const typeColorMap: Record<string, string> = {
  workflow: 'blue',
  tool_chain: 'green',
  prompt_template: 'purple',
  decision_rule: 'orange',
};

const typeLabelMap: Record<string, string> = {
  workflow: '工作流',
  tool_chain: '工具链',
  prompt_template: '提示词模板',
  decision_rule: '决策规则',
};

export function SkillManagePage() {
  const [skills, setSkills] = useState<Skill[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [createModalVisible, setCreateModalVisible] = useState(false);
  const [importModalVisible, setImportModalVisible] = useState(false);
  const [trendingSkills, setTrendingSkills] = useState<Skill[]>([]);
  const [jsonInput, setJsonInput] = useState('');
  const [form] = Form.useForm();

  const fetchTrendingSkills = async () => {
    try {
      setLoading(true);
      const data = await unwrap<{ items: Skill[] }>(
        http.get('/api/v1/skill-hub/skills/trending?limit=20'),
      );
      setTrendingSkills(data.items || []);
    } catch (err) {
      message.error('获取热门技能失败');
    } finally {
      setLoading(false);
    }
  };

  const searchSkills = async (keyword: string) => {
    if (!keyword.trim()) {
      setSkills([]);
      return;
    }
    try {
      setLoading(true);
      const data = await unwrap<{ items: Skill[] }>(
        http.get(`/api/v1/skill-hub/skills/search?keyword=${encodeURIComponent(keyword)}&limit=20`),
      );
      setSkills(data.items || []);
    } catch (err) {
      message.error('搜索技能失败');
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = () => {
    searchSkills(searchKeyword);
  };

  const handleCreateSkill = async (values: any) => {
    try {
      message.info('新建技能功能待后端支持');
      setCreateModalVisible(false);
      form.resetFields();
    } catch (err) {
      message.error('新建技能失败');
    }
  };

  const handleImportSkill = async () => {
    if (!jsonInput.trim()) {
      message.warning('请输入技能 JSON');
      return;
    }
    try {
      await unwrap(
        http.post('/api/v1/skill-hub/skills/import?tenantId=100&userId=1000', jsonInput, {
          headers: { 'Content-Type': 'application/json' },
        }),
      );
      message.success('导入技能成功');
      setImportModalVisible(false);
      setJsonInput('');
    } catch (err) {
      message.error('导入技能失败');
    }
  };

  return (
    <main className="agent-workbench-shell" style={{ padding: 24 }}>
      <Space direction="vertical" size={20} style={{ width: '100%', maxWidth: 1200, margin: '0 auto' }}>
        <div>
          <Title level={3}>技能管理</Title>
          <Text type="secondary">管理和导入项目助理的技能，包括工作流、工具链、提示词模板和决策规则。</Text>
        </div>

        <Card>
          <Space wrap style={{ marginBottom: 16 }}>
            <Input.Search
              placeholder="搜索技能名称或描述"
              allowClear
              enterButton={<><SearchOutlined /> 搜索</>}
              style={{ width: 400 }}
              value={searchKeyword}
              onChange={(e) => setSearchKeyword(e.target.value)}
              onSearch={handleSearch}
            />
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateModalVisible(true)}>
              新建技能
            </Button>
            <Button icon={<ImportOutlined />} onClick={() => setImportModalVisible(true)}>
              导入技能
            </Button>
            <Button onClick={fetchTrendingSkills}>查看热门技能</Button>
          </Space>

          {skills.length > 0 && (
            <List
              grid={{ gutter: 16, xs: 1, sm: 2, md: 2, lg: 3, xl: 3, xxl: 4 }}
              dataSource={skills}
              loading={loading}
              renderItem={(skill) => (
                <List.Item>
                  <Card
                    title={skill.name}
                    size="small"
                    extra={
                      <Tag color={typeColorMap[skill.type] || 'default'}>
                        {typeLabelMap[skill.type] || skill.type}
                      </Tag>
                    }
                  >
                    <Paragraph ellipsis={{ rows: 2 }} style={{ minHeight: 44 }}>
                      {skill.description || '暂无描述'}
                    </Paragraph>
                    <Space split={<Text type="secondary">|</Text>}>
                      <Text type="secondary">
                        成功率: {skill.successRate !== undefined ? `${skill.successRate}%` : '-'}
                      </Text>
                      <Text type="secondary">
                        使用次数: {skill.usageCount ?? '-'}
                      </Text>
                    </Space>
                  </Card>
                </List.Item>
              )}
            />
          )}

          {trendingSkills.length > 0 && (
            <div style={{ marginTop: 24 }}>
              <Title level={5}>热门技能</Title>
              <List
                grid={{ gutter: 16, xs: 1, sm: 2, md: 2, lg: 3, xl: 3, xxl: 4 }}
                dataSource={trendingSkills}
                renderItem={(skill) => (
                  <List.Item>
                    <Card
                      title={skill.name}
                      size="small"
                      extra={
                        <Tag color={typeColorMap[skill.type] || 'default'}>
                          {typeLabelMap[skill.type] || skill.type}
                        </Tag>
                      }
                    >
                      <Paragraph ellipsis={{ rows: 2 }} style={{ minHeight: 44 }}>
                        {skill.description || '暂无描述'}
                      </Paragraph>
                      <Space split={<Text type="secondary">|</Text>}>
                        <Text type="secondary">
                          成功率: {skill.successRate !== undefined ? `${skill.successRate}%` : '-'}
                        </Text>
                        <Text type="secondary">
                          使用次数: {skill.usageCount ?? '-'}
                        </Text>
                      </Space>
                    </Card>
                  </List.Item>
                )}
              />
            </div>
          )}
        </Card>
      </Space>

      <Modal
        title="新建技能"
        open={createModalVisible}
        onCancel={() => {
          setCreateModalVisible(false);
          form.resetFields();
        }}
        footer={null}
        width={600}
      >
        <Form form={form} layout="vertical" onFinish={handleCreateSkill}>
          <Form.Item
            name="name"
            label="技能名称"
            rules={[{ required: true, message: '请输入技能名称' }]}
          >
            <Input placeholder="请输入技能名称" />
          </Form.Item>
          <Form.Item
            name="code"
            label="技能编码"
            rules={[{ required: true, message: '请输入技能编码' }]}
          >
            <Input placeholder="请输入技能编码" />
          </Form.Item>
          <Form.Item
            name="type"
            label="技能类型"
            rules={[{ required: true, message: '请选择技能类型' }]}
          >
            <Select placeholder="请选择技能类型">
              <Option value="workflow">工作流</Option>
              <Option value="tool_chain">工具链</Option>
              <Option value="prompt_template">提示词模板</Option>
              <Option value="decision_rule">决策规则</Option>
            </Select>
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} placeholder="请输入技能描述" />
          </Form.Item>
          <Form.Item name="triggerConditions" label="触发条件（JSON）">
            <Input.TextArea rows={3} placeholder='例如: {"event": "task_created"}' />
          </Form.Item>
          <Form.Item name="content" label="技能内容（JSON）">
            <Input.TextArea rows={5} placeholder='请输入技能内容的 JSON 字符串' />
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

      <Modal
        title="导入技能"
        open={importModalVisible}
        onCancel={() => {
          setImportModalVisible(false);
          setJsonInput('');
        }}
        onOk={handleImportSkill}
        okText="导入"
        cancelText="取消"
        width={600}
      >
        <Input.TextArea
          rows={12}
          placeholder="请粘贴技能 JSON 数据"
          value={jsonInput}
          onChange={(e) => setJsonInput(e.target.value)}
        />
      </Modal>
    </main>
  );
}
