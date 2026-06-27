import { Button, Empty, Flex, Space, Tag, Typography } from 'antd';
import type { AgentHandoff } from '../../../types/collaboration';

interface HandoffListProps {
  handoffs: AgentHandoff[];
  actingHandoff?: { handoffId: number; action: 'accept' | 'reject' };
  onAcceptHandoff?: (handoff: AgentHandoff) => void;
  onRejectHandoff?: (handoff: AgentHandoff) => void;
}

interface HandoffListItemProps {
  handoff: AgentHandoff;
  actingHandoff?: { handoffId: number; action: 'accept' | 'reject' };
  onAcceptHandoff?: (handoff: AgentHandoff) => void;
  onRejectHandoff?: (handoff: AgentHandoff) => void;
}

interface HandoffMetadata {
  fromThreadName?: string;
  toThreadName?: string;
  artifactMetadata?: {
    artifactVersion?: number;
    producerAgentId?: number;
    producerAgentVersionId?: number;
    stageCode?: string;
  };
}

function parseHandoffMetadata(metadataJson?: string): HandoffMetadata {
  if (!metadataJson?.trim()) {
    return {};
  }
  try {
    const metadata = JSON.parse(metadataJson);
    return metadata && typeof metadata === 'object' && !Array.isArray(metadata) ? metadata : {};
  } catch {
    return {};
  }
}

function metadataTags(metadata: HandoffMetadata) {
  const tags: string[] = [];
  if (metadata.fromThreadName || metadata.toThreadName) {
    tags.push(`${metadata.fromThreadName || 'source'} -> ${metadata.toThreadName || 'target'}`);
  }
  if (metadata.artifactMetadata?.artifactVersion !== undefined) {
    tags.push(`artifact v${metadata.artifactMetadata.artifactVersion}`);
  }
  if (metadata.artifactMetadata?.producerAgentId !== undefined) {
    tags.push(`producer Agent ${metadata.artifactMetadata.producerAgentId}`);
  }
  if (metadata.artifactMetadata?.producerAgentVersionId !== undefined) {
    tags.push(`version ${metadata.artifactMetadata.producerAgentVersionId}`);
  }
  if (metadata.artifactMetadata?.stageCode) {
    tags.push(metadata.artifactMetadata.stageCode);
  }
  return tags;
}

export function HandoffList({ handoffs, actingHandoff, onAcceptHandoff, onRejectHandoff }: HandoffListProps) {
  if (handoffs.length === 0) {
    return <Empty description="暂无交接物" />;
  }

  return (
    <Space orientation="vertical" size={12} style={{ width: '100%' }}>
      {handoffs.map((handoff) => (
        <HandoffListItem
          key={handoff.id}
          handoff={handoff}
          actingHandoff={actingHandoff}
          onAcceptHandoff={onAcceptHandoff}
          onRejectHandoff={onRejectHandoff}
        />
      ))}
    </Space>
  );
}

function HandoffListItem({ handoff, actingHandoff, onAcceptHandoff, onRejectHandoff }: HandoffListItemProps) {
  const tags = metadataTags(parseHandoffMetadata(handoff.metadataJson));
  return (
    <Flex justify="space-between" align="center" gap={12}>
      <Space orientation="vertical" size={2}>
        <Typography.Text strong>{`Artifact #${handoff.artifactId}`}</Typography.Text>
        <Typography.Text type="secondary">{handoff.messageText || `handoff type: ${handoff.handoffType}`}</Typography.Text>
        {tags.length > 0 ? (
          <Space wrap size={4}>
            {tags.map((tag) => (
              <Tag key={tag}>{tag}</Tag>
            ))}
          </Space>
        ) : null}
      </Space>
      <Space>
        <Tag>{handoff.status}</Tag>
        <Typography.Text type="secondary">{handoff.handoffType}</Typography.Text>
        {handoff.status === 'pending' ? (
          <>
            <Button
              size="small"
              type="primary"
              aria-label={`Accept handoff ${handoff.id}`}
              loading={actingHandoff?.handoffId === handoff.id && actingHandoff.action === 'accept'}
              onClick={() => onAcceptHandoff?.(handoff)}
            >
              Accept
            </Button>
            <Button
              size="small"
              danger
              aria-label={`Reject handoff ${handoff.id}`}
              loading={actingHandoff?.handoffId === handoff.id && actingHandoff.action === 'reject'}
              onClick={() => onRejectHandoff?.(handoff)}
            >
              Reject
            </Button>
          </>
        ) : null}
      </Space>
    </Flex>
  );
}
