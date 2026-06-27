import { BookOutlined } from '@ant-design/icons';
import { Space, Typography } from 'antd';
import { KnowledgeUploadCard } from '../../features/knowledge-source/components/KnowledgeUploadCard';

export function KnowledgePage() {
  return (
    <main className="agent-workbench-shell" style={{ padding: 24 }}>
      <Space direction="vertical" size={20} style={{ width: '100%', maxWidth: 960, margin: '0 auto' }}>
        <Space align="center" size={12}>
          <BookOutlined style={{ color: '#1677ff', fontSize: 26 }} />
          <div>
            <Typography.Title level={3} style={{ margin: 0 }}>
              项目知识入口
            </Typography.Title>
            <Typography.Text type="secondary">
              MVP 阶段支持文本资料入库和关键词检索调试，优先验证项目助理能使用项目资料。
            </Typography.Text>
          </div>
        </Space>
        <KnowledgeUploadCard />
      </Space>
    </main>
  );
}
