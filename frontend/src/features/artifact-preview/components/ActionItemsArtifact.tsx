import { Table } from 'antd';

interface ActionItem {
  title?: string;
  owner?: string;
  dueDate?: string;
  status?: string;
}

interface ActionItemsArtifactProps {
  content: string;
}

function parseRows(content: string): ActionItem[] {
  try {
    const parsed = JSON.parse(content);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function ActionItemsArtifact({ content }: ActionItemsArtifactProps) {
  return (
    <Table
      size="small"
      pagination={false}
      rowKey={(_, index) => String(index)}
      dataSource={parseRows(content)}
      columns={[
        { title: '行动项', dataIndex: 'title' },
        { title: '负责人', dataIndex: 'owner' },
        { title: '截止时间', dataIndex: 'dueDate' },
        { title: '状态', dataIndex: 'status' },
      ]}
    />
  );
}
