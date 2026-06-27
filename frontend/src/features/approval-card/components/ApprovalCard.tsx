import { ExclamationCircleOutlined } from '@ant-design/icons';
import { Button, Card, Descriptions, Space, Tag, Typography } from 'antd';
import type { ApprovalRequest } from '../../../types/approval';

interface ApprovalCardProps {
  approval: ApprovalRequest;
  loading?: boolean;
  onApprove: (approval: ApprovalRequest) => void;
  onReject: (approval: ApprovalRequest) => void;
}

const riskLevelLabel: Record<NonNullable<ApprovalRequest['riskLevel']>, { color: string; label: string }> = {
  low: { color: 'green', label: '低风险' },
  medium: { color: 'orange', label: '中风险' },
  high: { color: 'red', label: '高风险' },
};

const approvalStatusLabel: Record<ApprovalRequest['status'], { color: string; label: string }> = {
  pending: { color: 'processing', label: '待审批' },
  approved: { color: 'success', label: '已通过' },
  rejected: { color: 'error', label: '已拒绝' },
  timeout: { color: 'default', label: '已超时' },
};

export function ApprovalCard({ approval, loading = false, onApprove, onReject }: ApprovalCardProps) {
  const riskLevel = approval.riskLevel ? riskLevelLabel[approval.riskLevel] : undefined;
  const status = approvalStatusLabel[approval.status];
  const isPending = approval.status === 'pending';

  return (
    <Card
      size="small"
      title={
        <Space>
          <ExclamationCircleOutlined style={{ color: '#faad14' }} />
          <span>{approval.title || '需要审批'}</span>
        </Space>
      }
      extra={riskLevel && <Tag color={riskLevel.color}>{riskLevel.label}</Tag>}
    >
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Tag color={status.color}>{status.label}</Tag>
        <Typography.Text type="secondary">Agent 需要你确认高风险动作后才能继续执行。</Typography.Text>
        <Descriptions column={1} size="small">
          <Descriptions.Item label="为什么需要审批">
            {approval.reason || '该动作需要人工确认后执行'}
          </Descriptions.Item>
          <Descriptions.Item label="影响范围">任务 #{approval.taskId || '-'}</Descriptions.Item>
          <Descriptions.Item label="执行器类型">{approval.executorType || '-'}</Descriptions.Item>
          <Descriptions.Item label="审批人">{approval.approverUserId || '-'}</Descriptions.Item>
        </Descriptions>
        <Typography.Text>审批人：{approval.approverUserId || '-'}</Typography.Text>
        {isPending && (
          <Space>
            <Button type="primary" loading={loading} onClick={() => onApprove(approval)}>
              同意执行
            </Button>
            <Button danger disabled={loading} onClick={() => onReject(approval)}>
              拒绝
            </Button>
          </Space>
        )}
      </Space>
    </Card>
  );
}
