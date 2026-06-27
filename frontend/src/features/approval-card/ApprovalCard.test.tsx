import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { ApprovalRequest } from '../../types/approval';
import { ApprovalCard } from './components/ApprovalCard';

const approvalRequest: ApprovalRequest = {
  id: 12,
  taskId: 21,
  runId: 31,
  title: '创建项目任务',
  reason: 'Agent 需要调用项目管理工具创建任务',
  riskLevel: 'high',
  executorType: 'cli',
  status: 'pending',
  approverUserId: 1000,
};

describe('ApprovalCard', () => {
  it('renders approval details and handles approve action', async () => {
    const onApprove = vi.fn();
    const onReject = vi.fn();
    const user = userEvent.setup();

    render(<ApprovalCard approval={approvalRequest} onApprove={onApprove} onReject={onReject} />);

    expect(screen.getByText('创建项目任务')).toBeInTheDocument();
    expect(screen.getByText('Agent 需要调用项目管理工具创建任务')).toBeInTheDocument();
    expect(screen.getByText('高风险')).toBeInTheDocument();
    expect(screen.getByText('cli')).toBeInTheDocument();
    expect(screen.getByText('审批人：1000')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /同意执行/ }));

    expect(onApprove).toHaveBeenCalledWith(approvalRequest);
    expect(onReject).not.toHaveBeenCalled();
  });
});
