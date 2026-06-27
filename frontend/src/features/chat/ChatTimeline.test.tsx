import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { PropsWithChildren } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { handleApproval } from '../../services/approval-api';
import { ChatTimeline } from './components/ChatTimeline';
import type { ChatMessage } from './chat-store';

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

describe('ChatTimeline', () => {
  it('renders inline approval card messages and handles approve action', async () => {
    vi.mocked(handleApproval).mockResolvedValue(undefined);
    const user = userEvent.setup();
    const messages: ChatMessage[] = [
      {
        id: 'message-approval',
        role: 'assistant',
        content: '需要审批',
        approval: {
          id: 12,
          taskId: 21,
          runId: 31,
          title: '创建项目任务',
          reason: 'Agent 需要调用项目管理工具创建任务',
          riskLevel: 'high',
          status: 'pending',
          approverUserId: 1000,
        },
      },
    ];

    render(<ChatTimeline messages={messages} />, { wrapper: createWrapper() });

    expect(screen.getByText('创建项目任务')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /同意执行/ }));

    expect(handleApproval).toHaveBeenCalledWith(12, { action: 'approve', comment: undefined });
  });

  it('renders user and assistant messages with markdown content', () => {
    const messages: ChatMessage[] = [
      {
        id: 'message-1',
        role: 'user',
        content: '生成项目周报',
      },
      {
        id: 'message-2',
        role: 'assistant',
        content: '## 周报草稿\n\n- 已完成需求分析',
      },
    ];

    render(<ChatTimeline messages={messages} />, { wrapper: createWrapper() });

    expect(screen.getByText('生成项目周报')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '周报草稿' })).toBeInTheDocument();
    expect(screen.getByText('已完成需求分析')).toBeInTheDocument();
  });
});
