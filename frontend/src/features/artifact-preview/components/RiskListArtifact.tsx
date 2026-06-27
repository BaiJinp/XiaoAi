import { Table, Tag } from 'antd';

interface RiskItem {
  risk?: string;
  impact?: string;
  mitigation?: string;
  level?: string;
}

interface RiskListArtifactProps {
  content: string;
}

function parseRows(content: string): RiskItem[] {
  try {
    const parsed = JSON.parse(content);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function RiskListArtifact({ content }: RiskListArtifactProps) {
  return (
    <Table
      size="small"
      pagination={false}
      rowKey={(_, index) => String(index)}
      dataSource={parseRows(content)}
      columns={[
        { title: '风险', dataIndex: 'risk' },
        { title: '影响', dataIndex: 'impact' },
        { title: '建议动作', dataIndex: 'mitigation' },
        {
          title: '等级',
          dataIndex: 'level',
          render: (level?: string) => (level ? <Tag color={level === 'high' ? 'red' : 'orange'}>{level}</Tag> : '-'),
        },
      ]}
    />
  );
}
