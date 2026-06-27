import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { PropsWithChildren } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { handleApproval } from '../../services/approval-api';
import type { RuntimeEvent } from '../../types/runtime-event';
import { ExecutionTimeline } from './components/ExecutionTimeline';

vi.mock('../../services/approval-api', () => ({
  handleApproval: vi.fn(),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return function Wrapper({ children }: PropsWithChildren) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

function createEvent(id: string, eventType: RuntimeEvent['eventType'], message?: string): RuntimeEvent {
  return {
    id,
    tenantId: 100,
    taskId: 10,
    runId: 20,
    eventType,
    message,
  };
}

describe('ExecutionTimeline', () => {
  it('renders approval card for approval required events and handles approve action', async () => {
    vi.mocked(handleApproval).mockResolvedValue(undefined);
    const onApprovalHandled = vi.fn();
    const user = userEvent.setup();

    render(
      <ExecutionTimeline
        onApprovalHandled={onApprovalHandled}
        events={[
          {
            ...createEvent('event-approval', 'APPROVAL_REQUIRED', 'Agent 需要创建项目任务'),
            payload: {
              approvalRequestId: 99,
              title: '创建项目任务',
              riskLevel: 'high',
              executorType: 'cli',
              approverUserId: 1000,
            },
          },
        ]}
      />,
      { wrapper: createWrapper() },
    );

    expect(screen.getByText('创建项目任务')).toBeInTheDocument();
    expect(screen.getByText('Agent 需要创建项目任务')).toBeInTheDocument();
    expect(screen.getByText('高风险')).toBeInTheDocument();
    expect(screen.getByText('cli')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /同意执行/ }));

    expect(handleApproval).toHaveBeenCalledWith(99, { action: 'approve', comment: undefined });
    await waitFor(() =>
      expect(onApprovalHandled).toHaveBeenCalledWith(
        expect.objectContaining({
          id: 99,
          status: 'pending',
        }),
      ),
    );
  });

  it('renders readable runtime event titles', () => {
    render(
      <ExecutionTimeline
        events={[
          createEvent('event-1', 'RUN_STARTED'),
          createEvent('event-2', 'KNOWLEDGE_RETRIEVED'),
          createEvent('event-3', 'APPROVAL_REQUIRED', '需要项目负责人确认'),
        ]}
      />,
      { wrapper: createWrapper() },
    );

    expect(screen.getByText('运行开始')).toBeInTheDocument();
    expect(screen.getByText('已检索项目资料')).toBeInTheDocument();
    expect(screen.getByText('需要审批')).toBeInTheDocument();
    expect(screen.getByText('需要项目负责人确认')).toBeInTheDocument();
  });

  it('renders handled approval as read only status', () => {
    render(
      <ExecutionTimeline
        events={[
          {
            ...createEvent('event-approval', 'APPROVAL_REQUIRED', 'Agent 需要创建项目任务'),
            payload: {
              approvalRequestId: 99,
              title: '创建项目任务',
              riskLevel: 'high',
            },
          },
          {
            ...createEvent('event-approved', 'APPROVAL_APPROVED', 'Approval approved'),
            payload: {
              approvalRequestId: 99,
            },
          },
        ]}
      />,
      { wrapper: createWrapper() },
    );

    expect(screen.getByText('已通过')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /同意执行/ })).not.toBeInTheDocument();
  });

  it('renders sequence metadata and error detail for failed events', () => {
    render(
      <ExecutionTimeline
        events={[
          {
            ...createEvent('event-failed', 'RUN_FAILED', 'fallback message'),
            sequence: 8,
            stepId: 2,
            payload: { errorMessage: '模型调用超时' },
          },
        ]}
      />,
      { wrapper: createWrapper() },
    );

    expect(screen.getByText('运行失败')).toBeInTheDocument();
    expect(screen.getByText('#8 · step 2')).toBeInTheDocument();
    expect(screen.getByText('模型调用超时')).toBeInTheDocument();
  });

  it('renders tool chain metadata for blocked tool events', () => {
    render(
      <ExecutionTimeline
        events={[
          {
            ...createEvent('event-tool-blocked', 'TOOL_BLOCKED'),
            sequence: 12,
            payload: {
              toolCallIndex: 1,
              toolCode: 'controlled.cli.project-update',
              executorType: 'cli',
              riskLevel: 'high',
              approvalRequestId: 101,
              reason: 'requires_human_approval',
            },
          },
        ]}
      />,
      { wrapper: createWrapper() },
    );

    expect(screen.getByText('工具调用被策略拦截')).toBeInTheDocument();
    expect(
      screen.getByText(
        '#12 · toolCallIndex 1 · tool controlled.cli.project-update · executor cli · risk high · approval 101',
      ),
    ).toBeInTheDocument();
    expect(screen.getByText('Approval 101 required: requires_human_approval')).toBeInTheDocument();
  });

  it('renders workflow step events with readable titles', () => {
    render(
      <ExecutionTimeline
        events={[
          {
            ...createEvent('event-step-started', 'STEP_STARTED'),
            stepId: 2,
            payload: { stepName: '检索项目资料' },
          },
          {
            ...createEvent('event-step-completed', 'STEP_COMPLETED'),
            stepId: 5,
            payload: { stepName: '规则自检', status: 'warning' },
          },
        ]}
      />,
      { wrapper: createWrapper() },
    );

    expect(screen.getByText('正在执行：检索项目资料')).toBeInTheDocument();
    expect(screen.getByText('已完成：规则自检')).toBeInTheDocument();
    expect(screen.getByText('步骤已完成，但结果需要复核')).toBeInTheDocument();
  });
});
