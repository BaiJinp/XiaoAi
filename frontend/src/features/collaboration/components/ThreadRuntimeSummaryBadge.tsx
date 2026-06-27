import { Button, Space, Tag, Typography } from 'antd';
import type { ApprovalAction } from '../../../types/approval';
import type { ThreadRuntimeSummary } from './threadRuntimeSummary';

interface ThreadRuntimeSummaryBadgeProps {
  summary?: ThreadRuntimeSummary;
  handlingApprovalId?: number;
  onHandleApproval?: (approvalRequestId: number, action: ApprovalAction) => void;
}

const statusColor: Record<ThreadRuntimeSummary['status'], string> = {
  process: 'blue',
  success: 'green',
  warning: 'orange',
  error: 'red',
  default: 'default',
};

export function ThreadRuntimeSummaryBadge({
  summary,
  handlingApprovalId,
  onHandleApproval,
}: ThreadRuntimeSummaryBadgeProps) {
  if (!summary) {
    return null;
  }
  const canHandleApproval = summary.approvalRequestId !== undefined && onHandleApproval;
  const approvalLoading = handlingApprovalId === summary.approvalRequestId;

  return (
    <Space size={4} wrap>
      <Tag color={statusColor[summary.status]}>{summary.label}</Tag>
      {summary.toolCallIndex !== undefined ? <Tag>call #{summary.toolCallIndex}</Tag> : null}
      {summary.toolCode ? <Typography.Text type="secondary">{summary.toolCode}</Typography.Text> : null}
      {summary.riskLevel ? <Tag color={summary.riskLevel === 'high' ? 'red' : summary.riskLevel === 'medium' ? 'orange' : 'green'}>{summary.riskLevel}</Tag> : null}
      {summary.approvalRequestId !== undefined ? <Tag color="gold">approval #{summary.approvalRequestId}</Tag> : null}
      {summary.reason ? <Typography.Text type="secondary">{summary.reason}</Typography.Text> : null}
      {canHandleApproval ? (
        <>
          <Button
            size="small"
            type="primary"
            loading={approvalLoading}
            onClick={() => onHandleApproval(summary.approvalRequestId!, 'approve')}
          >
            Approve
          </Button>
          <Button
            size="small"
            danger
            disabled={approvalLoading}
            onClick={() => onHandleApproval(summary.approvalRequestId!, 'reject')}
          >
            Reject
          </Button>
        </>
      ) : null}
    </Space>
  );
}
