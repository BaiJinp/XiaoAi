import { ClockCircleOutlined, RobotOutlined } from '@ant-design/icons';
import { Button, List, Space, Tag, Typography } from 'antd';

const demoTasks = [
  { id: 1, title: '会议纪要转任务', status: '样例' },
  { id: 2, title: '项目周报生成', status: '样例' },
  { id: 3, title: '项目风险分析', status: '样例' },
];

export function ConversationSidebar() {
  return (
    <div className="agent-workbench-scroll" style={{ padding: 16 }}>
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <div>
          <Typography.Title level={4} style={{ margin: 0 }}>
            项目助理
          </Typography.Title>
          <Typography.Text type="secondary">围绕真实项目任务运行的 Agent</Typography.Text>
        </div>

        <Button type="primary" block icon={<RobotOutlined />}>
          新建 Agent 任务
        </Button>

        <List
          dataSource={demoTasks}
          renderItem={(item) => (
            <List.Item style={{ paddingInline: 0 }}>
              <List.Item.Meta
                avatar={<ClockCircleOutlined style={{ color: '#1677ff' }} />}
                title={item.title}
                description={<Tag color="blue">{item.status}</Tag>}
              />
            </List.Item>
          )}
        />
      </Space>
    </div>
  );
}
