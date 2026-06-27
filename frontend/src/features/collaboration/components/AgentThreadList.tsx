import { Button, Empty, Flex, Space, Tag, Typography } from 'antd';
import { Link } from 'react-router-dom';
import type { ApprovalAction } from '../../../types/approval';
import type { AgentHandoff, AgentThread } from '../../../types/collaboration';
import { ThreadRuntimeSummaryBadge } from './ThreadRuntimeSummaryBadge';
import type { ThreadRuntimeSummary } from './threadRuntimeSummary';

interface AgentThreadListProps {
  threads: AgentThread[];
  handoffs?: AgentHandoff[];
  runtimeSummaries?: Record<number, ThreadRuntimeSummary | undefined>;
  startingThreadId?: number;
  handlingApprovalId?: number;
  onStartThreadTask?: (thread: AgentThread) => void;
  onHandleApproval?: (approvalRequestId: number, action: ApprovalAction) => void;
}

export function AgentThreadList({
  threads,
  handoffs = [],
  runtimeSummaries,
  startingThreadId,
  handlingApprovalId,
  onStartThreadTask,
  onHandleApproval,
}: AgentThreadListProps) {
  if (threads.length === 0) {
    return <Empty description="暂无 Agent 线程" />;
  }

  return (
    <Space orientation="vertical" size={12} style={{ width: '100%' }}>
      {threads.map((thread) => (
        <Flex key={thread.id} justify="space-between" align="center">
          <Space orientation="vertical" size={2}>
            <Typography.Text strong>{thread.threadName || thread.threadCode}</Typography.Text>
            <Typography.Text type="secondary">
              {thread.threadCode} · Agent #{thread.agentId}
            </Typography.Text>
            {inputArtifactSummary(thread) ? <Typography.Text type="secondary">{inputArtifactSummary(thread)}</Typography.Text> : null}
            {thread.taskId ? <Link to={`/tasks/${thread.taskId}`}>Task #{thread.taskId}</Link> : null}
            {thread.taskId ? (
              <ThreadRuntimeSummaryBadge
                summary={runtimeSummaries?.[thread.taskId]}
                handlingApprovalId={handlingApprovalId}
                onHandleApproval={onHandleApproval}
              />
            ) : null}
          </Space>
          <Space>
            {isWaitingForHandoff(thread, handoffs) ? <Tag color="orange">waiting handoff</Tag> : null}
            {thread.taskId && onStartThreadTask && !['running', 'completed'].includes(thread.status) && !isWaitingForHandoff(thread, handoffs) ? (
              <Button size="small" loading={startingThreadId === thread.id} onClick={() => onStartThreadTask(thread)}>
                Start
              </Button>
            ) : null}
            <Tag>{thread.status}</Tag>
          </Space>
        </Flex>
      ))}
    </Space>
  );
}

function inputArtifactSummary(thread: AgentThread) {
  const ids = inputArtifactIds(thread);
  if (ids.length === 0) {
    return undefined;
  }
  return `Input artifacts ${ids.map((id: number) => `#${id}`).join(', ')}`;
}

function inputArtifactIds(thread: AgentThread) {
  if (thread.contextJson?.trim()) {
    try {
      const context = JSON.parse(thread.contextJson);
      if (Array.isArray(context?.inputArtifactIds)) {
        return context.inputArtifactIds.filter((id: unknown): id is number => typeof id === 'number' && Number.isFinite(id));
      }
    } catch {
      return thread.inputArtifactId ? [thread.inputArtifactId] : [];
    }
  }
  return thread.inputArtifactId ? [thread.inputArtifactId] : [];
}

function isWaitingForHandoff(thread: AgentThread, handoffs: AgentHandoff[]) {
  if (!requiresAcceptedInputHandoff(thread)) {
    return false;
  }
  return handoffs.some(
    (handoff) =>
      handoff.toThreadId === thread.id &&
      handoff.artifactId === thread.inputArtifactId &&
      handoff.handoffType === 'artifact' &&
      handoff.status !== 'accepted',
  );
}

function requiresAcceptedInputHandoff(thread: AgentThread) {
  if (!thread.contextJson?.trim()) {
    return false;
  }
  try {
    const context = JSON.parse(thread.contextJson);
    return Boolean(context?.requireAcceptedInputHandoff);
  } catch {
    return false;
  }
}
