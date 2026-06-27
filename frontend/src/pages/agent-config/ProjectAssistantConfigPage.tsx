import { SettingOutlined } from '@ant-design/icons';
import { Space, Typography } from 'antd';
import { ExistingAgentVersionToolsCard } from '../../features/agent-config/components/ExistingAgentVersionToolsCard';
import { PluginManifestCard } from '../../features/agent-config/components/PluginManifestCard';
import { ProjectAssistantForm } from '../../features/agent-config/components/ProjectAssistantForm';

export function ProjectAssistantConfigPage() {
  return (
    <main className="agent-workbench-shell" style={{ padding: 24 }}>
      <Space direction="vertical" size={20} style={{ width: '100%', maxWidth: 960, margin: '0 auto' }}>
        <Space align="center" size={12}>
          <SettingOutlined style={{ color: '#1677ff', fontSize: 26 }} />
          <div>
            <Typography.Title level={3} style={{ margin: 0 }}>
              项目助理配置
            </Typography.Title>
            <Typography.Text type="secondary">
              MVP 阶段只提供创建草稿所需的最小字段，完整版本生成、发布和策略配置后续接入。
            </Typography.Text>
          </div>
        </Space>
        <ProjectAssistantForm />
        <PluginManifestCard />
        <ExistingAgentVersionToolsCard />
      </Space>
    </main>
  );
}
