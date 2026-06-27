import { CopyOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Empty, Space, Tag, Typography } from 'antd';
import type { TaskArtifact } from '../../../types/artifact';
import { ActionItemsArtifact } from './ActionItemsArtifact';
import { MarkdownArtifact } from './MarkdownArtifact';
import { RiskListArtifact } from './RiskListArtifact';

interface ArtifactPreviewProps {
  artifacts: TaskArtifact[];
}

function renderArtifactContent(artifact: TaskArtifact) {
  if (
    artifact.artifactType === 'markdown' ||
    artifact.artifactType === 'weekly_report' ||
    artifact.artifactType === 'knowledge_answer'
  ) {
    return <MarkdownArtifact content={artifact.content} />;
  }
  if (artifact.artifactType === 'action_items' || artifact.artifactType === 'meeting_action_items') {
    return <ActionItemsArtifact content={artifact.content} />;
  }
  if (artifact.artifactType === 'risk_list' || artifact.artifactType === 'risk_analysis') {
    return <RiskListArtifact content={artifact.content} />;
  }
  return (
    <Typography.Paragraph style={{ whiteSpace: 'pre-wrap' }}>{artifact.content}</Typography.Paragraph>
  );
}

const confidenceColor: Record<string, string> = {
  high: 'green',
  medium: 'blue',
  low: 'orange',
  conflicting: 'red',
  insufficient: 'default',
};

function confidenceLabel(confidence?: string) {
  if (!confidence) {
    return '未评估';
  }
  const labels: Record<string, string> = {
    high: '高可信',
    medium: '中可信',
    low: '低可信',
    conflicting: '来源冲突',
    insufficient: '依据不足',
  };
  return labels[confidence] || confidence;
}

function renderEvidenceSummary(artifact: TaskArtifact) {
  const metadata = artifact.metadata;
  if (!metadata) {
    return null;
  }
  const sourceRefs = metadata.sourceRefs || metadata.sources || [];
  return (
    <Space direction="vertical" size={8} style={{ width: '100%', marginBottom: 12 }}>
      <Space wrap size={8}>
        {metadata.knowledgeConfidence ? (
          <Tag color={confidenceColor[metadata.knowledgeConfidence] || 'default'}>
            {confidenceLabel(metadata.knowledgeConfidence)}
          </Tag>
        ) : null}
        {sourceRefs.length > 0 ? <Tag>{sourceRefs.length} 个来源</Tag> : <Tag>暂无来源</Tag>}
      </Space>
      {metadata.lowConfidence ? (
        <Alert
          type="warning"
          showIcon
          message="依据有限，内容需要人工复核"
        />
      ) : null}
    </Space>
  );
}

export function ArtifactPreview({ artifacts }: ArtifactPreviewProps) {
  if (artifacts.length === 0) {
    return <Empty description="任务完成后显示周报、行动项或风险清单" />;
  }

  return (
    <Space direction="vertical" size={12} style={{ width: '100%' }}>
      {artifacts.map((artifact) => (
        <Card
          key={artifact.id}
          size="small"
          title={artifact.title}
          extra={
            <Button icon={<CopyOutlined />} onClick={() => navigator.clipboard.writeText(artifact.content)}>
              复制
            </Button>
          }
        >
          {renderEvidenceSummary(artifact)}
          {renderArtifactContent(artifact)}
        </Card>
      ))}
    </Space>
  );
}
