import { Link, Outlet } from 'react-router-dom';
import { Layout, Space, Typography } from 'antd';

const navLinks = [
  { label: 'Agent 工作台', to: '/workbench' },
  { label: '模型配置', to: '/model-config' },
  { label: '协作会话', to: '/collaboration' },
  { label: '知识入口', to: '/knowledge' },
  { label: '项目助理配置', to: '/agent-config' },
  { label: 'Tool Audit', to: '/tool-audit' },
  { label: '技能管理', to: '/skill-manage' },
  { label: '记忆管理', to: '/memory-manage' },
];

export function App() {
  return (
    <Layout style={{ minHeight: '100vh', background: '#f5f7fb' }}>
      <Layout.Header
        style={{
          alignItems: 'center',
          background: '#ffffff',
          borderBottom: '1px solid #edf0f5',
          display: 'flex',
          justifyContent: 'space-between',
          padding: '0 24px',
        }}
      >
        <Typography.Text strong>企业数字员工 MVP</Typography.Text>
        <Space size={20}>
          {navLinks.map((link) => (
            <Link key={link.to} to={link.to}>
              {link.label}
            </Link>
          ))}
        </Space>
      </Layout.Header>
      <Layout.Content>
        <Outlet />
      </Layout.Content>
    </Layout>
  );
}
