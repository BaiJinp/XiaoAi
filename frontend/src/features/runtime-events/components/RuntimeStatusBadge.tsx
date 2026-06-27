import { Tag } from 'antd';
import type { UserEventStatus } from '../runtime-event-presenter';

interface RuntimeStatusBadgeProps {
  status: UserEventStatus;
}

const statusConfig: Record<UserEventStatus, { color: string; label: string }> = {
  process: { color: 'processing', label: '运行中' },
  waiting: { color: 'warning', label: '等待中' },
  success: { color: 'success', label: '成功' },
  error: { color: 'error', label: '失败' },
  default: { color: 'default', label: '已结束' },
};

export function RuntimeStatusBadge({ status }: RuntimeStatusBadgeProps) {
  const config = statusConfig[status];
  return <Tag color={config.color}>{config.label}</Tag>;
}
