import { Empty, Space, Timeline, Typography } from 'antd';
import { ApprovalCard } from '../../approval-card/components/ApprovalCard';
import { useHandleApproval } from '../../approval-card/hooks/useHandleApproval';
import type { ApprovalRequest } from '../../../types/approval';
import type { RuntimeEvent } from '../../../types/runtime-event';
import { RuntimeStatusBadge } from './RuntimeStatusBadge';
import { toUserEvent } from '../runtime-event-presenter';

interface ExecutionTimelineProps {
  events: RuntimeEvent[];
  onApprovalHandled?: (approval: ApprovalRequest) => void | Promise<void>;
}

function toApprovalRequest(event: RuntimeEvent, events: RuntimeEvent[]): ApprovalRequest | undefined {
  const approvalRequestId = event.payload?.approvalRequestId;
  if (event.eventType !== 'APPROVAL_REQUIRED' || typeof approvalRequestId !== 'number') {
    return undefined;
  }
  return {
    id: approvalRequestId,
    taskId: event.taskId,
    runId: event.runId,
    title: typeof event.payload?.title === 'string' ? event.payload.title : '需要审批',
    reason: event.message,
    riskLevel:
      event.payload?.riskLevel === 'low' || event.payload?.riskLevel === 'medium' || event.payload?.riskLevel === 'high'
        ? event.payload.riskLevel
        : undefined,
    executorType:
      typeof event.payload?.executorType === 'string'
        ? event.payload.executorType
        : typeof event.payload?.toolType === 'string'
          ? event.payload.toolType
          : undefined,
    status: getApprovalStatus(events, approvalRequestId),
    approverUserId: typeof event.payload?.approverUserId === 'number' ? event.payload.approverUserId : undefined,
  };
}

function getApprovalStatus(events: RuntimeEvent[], approvalRequestId: number): ApprovalRequest['status'] {
  for (let index = events.length - 1; index >= 0; index -= 1) {
    const event = events[index];
    if (
      (event.eventType === 'APPROVAL_APPROVED' || event.eventType === 'APPROVAL_REJECTED') &&
      event.payload?.approvalRequestId === approvalRequestId
    ) {
      return event.eventType === 'APPROVAL_APPROVED' ? 'approved' : 'rejected';
    }
  }
  return 'pending';
}

export function ExecutionTimeline({ events, onApprovalHandled }: ExecutionTimelineProps) {
  const handleApprovalMutation = useHandleApproval();
  if (events.length === 0) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="运行后将在这里展示 Agent 执行过程" />;
  }

  return (
    <Timeline
      items={events.map((event) => {
        const userEvent = toUserEvent(event);
        const approval = toApprovalRequest(event, events);
        return {
          children: approval ? (
            <ApprovalCard
              approval={approval}
              loading={handleApprovalMutation.isPending}
              onApprove={(approvalRequest) =>
                handleApprovalMutation.mutate(
                  { requestId: approvalRequest.id, action: 'approve' },
                  { onSuccess: () => void onApprovalHandled?.(approvalRequest) },
                )
              }
              onReject={(approvalRequest) =>
                handleApprovalMutation.mutate(
                  { requestId: approvalRequest.id, action: 'reject' },
                  { onSuccess: () => void onApprovalHandled?.(approvalRequest) },
                )
              }
            />
          ) : (
            <Space direction="vertical" size={4}>
              <Space wrap>
                <Typography.Text strong>{userEvent.title}</Typography.Text>
                <RuntimeStatusBadge status={userEvent.status} />
                {userEvent.meta && <Typography.Text type="secondary">{userEvent.meta}</Typography.Text>}
              </Space>
              {userEvent.description && (
                <Typography.Text type="secondary">{userEvent.description}</Typography.Text>
              )}
            </Space>
          ),
        };
      })}
    />
  );
}
