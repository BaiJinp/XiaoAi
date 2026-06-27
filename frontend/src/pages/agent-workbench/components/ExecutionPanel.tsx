import { useQuery } from '@tanstack/react-query';
import { Card, Space, Typography } from 'antd';
import { useEffect } from 'react';
import { ArtifactPreview } from '../../../features/artifact-preview/components/ArtifactPreview';
import { ExecutionTimeline } from '../../../features/runtime-events/components/ExecutionTimeline';
import { useRuntimeEventStore } from '../../../features/runtime-events/runtime-event-store';
import { listTaskArtifacts } from '../../../services/task-api';
import type { RuntimeEvent } from '../../../types/runtime-event';
import type { ApprovalRequest } from '../../../types/approval';

interface ExecutionPanelProps {
  taskId?: number;
  onApprovalHandled?: (approval: ApprovalRequest) => void | Promise<void>;
}

const emptyRuntimeEvents: RuntimeEvent[] = [];

export function ExecutionPanel({ taskId, onApprovalHandled }: ExecutionPanelProps) {
  const events = useRuntimeEventStore((state) =>
    taskId ? state.eventsByTaskId[taskId] || emptyRuntimeEvents : emptyRuntimeEvents,
  );
  const artifactsQuery = useQuery({
    queryKey: ['task-artifacts', taskId],
    queryFn: () => listTaskArtifacts(taskId!),
    enabled: taskId !== undefined,
  });
  const { refetch: refetchArtifacts } = artifactsQuery;
  const latestArtifactRefreshEventId = [...events]
    .reverse()
    .find((event) =>
      [
        'ASSISTANT_ARTIFACT',
        'ASSISTANT_TASK_COMPLETED',
        'TOOL_RESULT',
        'MODEL_RESULT',
        'RUN_COMPLETED',
        'RUN_FAILED',
        'RUN_SUSPENDED',
      ].includes(event.eventType),
    )?.id;

  useEffect(() => {
    if (taskId !== undefined && latestArtifactRefreshEventId) {
      void refetchArtifacts();
    }
  }, [latestArtifactRefreshEventId, refetchArtifacts, taskId]);

  return (
    <div className="agent-workbench-scroll" style={{ padding: 16 }}>
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <div>
          <Typography.Title level={4} style={{ margin: 0 }}>
            执行过程
          </Typography.Title>
          <Typography.Text type="secondary">展示 Runtime 事件、审批和交付物</Typography.Text>
        </div>

        <Card size="small" title="当前任务状态">
          <ExecutionTimeline events={events} onApprovalHandled={onApprovalHandled} />
        </Card>

        <Card size="small" title="交付物预览">
          <ArtifactPreview artifacts={artifactsQuery.data || []} />
        </Card>
      </Space>
    </div>
  );
}
