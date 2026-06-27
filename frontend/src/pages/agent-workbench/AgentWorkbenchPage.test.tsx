import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { PropsWithChildren } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useChatStore } from '../../features/chat/chat-store';
import { useRuntimeEventStore } from '../../features/runtime-events/runtime-event-store';
import { subscribeRuntimeEvents } from '../../services/runtime-sse';
import { handleApproval } from '../../services/approval-api';
import { createCollaborationSession } from '../../services/collaboration-api';
import { createTask, listTaskEvents, startTask } from '../../services/task-api';
import type { RuntimeEvent } from '../../types/runtime-event';
import type { TaskCreateResponse } from '../../types/task';
import { AgentWorkbenchPage } from './AgentWorkbenchPage';

vi.mock('../../services/task-api', () => ({
  createTask: vi.fn(),
  listTaskEvents: vi.fn(),
  startTask: vi.fn(),
}));

vi.mock('../../services/collaboration-api', () => ({
  createCollaborationSession: vi.fn(),
}));

vi.mock('../../services/runtime-sse', () => ({
  subscribeRuntimeEvents: vi.fn(),
}));

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
    return (
      <MemoryRouter>
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      </MemoryRouter>
    );
  };
}

function createDeferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve;
    reject = promiseReject;
  });
  return { promise, resolve, reject };
}

describe('AgentWorkbenchPage', () => {
  beforeEach(() => {
    useChatStore.getState().clearMessages();
    useRuntimeEventStore.getState().clearEvents();
    vi.mocked(createTask).mockReset();
    vi.mocked(createCollaborationSession).mockReset();
    vi.mocked(listTaskEvents).mockReset();
    vi.mocked(listTaskEvents).mockResolvedValue([]);
    vi.mocked(handleApproval).mockReset();
    vi.mocked(handleApproval).mockResolvedValue(undefined);
    vi.mocked(startTask).mockReset();
    vi.mocked(startTask).mockResolvedValue({ taskId: 1, runId: 1, status: 'running' });
    vi.mocked(subscribeRuntimeEvents).mockReset();
    vi.mocked(subscribeRuntimeEvents).mockReturnValue(() => {});
  });

  it('creates a task from submitted project assistant input', async () => {
    vi.mocked(createTask).mockResolvedValue({
      taskId: 21,
      taskCode: 'TASK-21',
      taskStatus: 'pending',
    });
    const user = userEvent.setup();

    render(<AgentWorkbenchPage />, { wrapper: createWrapper() });

    const input = screen.getByPlaceholderText(/输入项目助理任务/);
    await user.type(input, '请生成本周项目周报 ABC');
    await user.click(screen.getByRole('button', { name: /发送/ }));

    expect(input).toHaveValue('');
    expect(screen.getByText('你')).toBeInTheDocument();
    expect(screen.getByText('请生成本周项目周报 ABC')).toBeInTheDocument();
    const createTaskRequest = vi.mocked(createTask).mock.calls[0][0];
    expect(createTaskRequest.agentId).toBe(1);
    expect(createTaskRequest.channel).toBe('web');
    expect(JSON.parse(createTaskRequest.input)).toMatchObject({
      assistantTaskType: 'weekly_report',
      modelId: 1,
      prompt: '请生成本周项目周报 ABC',
      query: '请生成本周项目周报 ABC',
    });
    await waitFor(() => expect(startTask).toHaveBeenCalledWith(21, { agentId: 1 }));
    expect(await screen.findByText(/任务已创建/)).toBeInTheDocument();
    expect(screen.getByText(/TASK-21/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '查看任务详情' })).toHaveAttribute('href', '/tasks/21');
  });

  it('subscribes runtime events after task creation and renders execution progress', async () => {
    vi.mocked(createTask).mockResolvedValue({
      taskId: 23,
      taskCode: 'TASK-23',
      taskStatus: 'pending',
    });
    vi.mocked(subscribeRuntimeEvents).mockImplementation((_taskId, handlers) => {
      const event: RuntimeEvent = {
        id: 'event-run-started',
        tenantId: 100,
        taskId: 23,
        runId: 33,
        eventType: 'RUN_STARTED',
      };
      handlers.onEvent(event);
      return () => {};
    });
    const user = userEvent.setup();

    render(<AgentWorkbenchPage />, { wrapper: createWrapper() });

    await user.type(screen.getByPlaceholderText(/输入项目助理任务/), '启动项目风险分析');
    await user.click(screen.getByRole('button', { name: /发送/ }));

    await waitFor(() => expect(subscribeRuntimeEvents).toHaveBeenCalledWith(23, expect.any(Object), undefined));
    expect(await screen.findByText('运行开始')).toBeInTheDocument();
  });

  it('pulls missed events with lastEventId when runtime stream errors', async () => {
    vi.mocked(createTask).mockResolvedValue({
      taskId: 26,
      taskCode: 'TASK-26',
      taskStatus: 'pending',
    });
    vi.mocked(listTaskEvents).mockResolvedValue([
      {
        id: 'event-completed',
        tenantId: 100,
        taskId: 26,
        runId: 36,
        eventType: 'RUN_COMPLETED',
        sequence: 2,
        lastEventId: '2',
      },
    ]);
    vi.mocked(subscribeRuntimeEvents).mockImplementation((_taskId, handlers) => {
      handlers.onEvent({
        id: 'event-run-started',
        tenantId: 100,
        taskId: 26,
        runId: 36,
        eventType: 'RUN_STARTED',
        sequence: 1,
        lastEventId: '1',
      });
      void handlers.onError?.();
      return () => {};
    });
    const user = userEvent.setup();

    render(<AgentWorkbenchPage />, { wrapper: createWrapper() });

    await user.type(screen.getByPlaceholderText(/输入项目助理任务/), '启动项目周报');
    await user.click(screen.getByRole('button', { name: /发送/ }));

    await waitFor(() => expect(listTaskEvents).toHaveBeenCalledWith(26, '1'));
    expect(await screen.findByText('任务已完成')).toBeInTheDocument();
  });

  it('wraps meeting action input with high risk tool approval context', async () => {
    vi.mocked(createTask).mockResolvedValue({
      taskId: 25,
      taskCode: 'TASK-25',
      taskStatus: 'pending',
    });
    const user = userEvent.setup();

    render(<AgentWorkbenchPage />, { wrapper: createWrapper() });

    await user.type(screen.getByPlaceholderText(/输入项目助理任务/), '根据会议纪要创建项目任务');
    await user.click(screen.getByText('发送').closest('button')!);

    await waitFor(() => expect(createTask).toHaveBeenCalled());
    const createTaskRequest = vi.mocked(createTask).mock.calls[0][0];
    expect(JSON.parse(createTaskRequest.input)).toMatchObject({
      assistantTaskType: 'meeting_minutes',
      toolId: 10,
      fallbackApproverUserId: 1000,
      callPayloadJson: {
        source: 'agent-workbench',
        action: 'create_project_task',
        content: '根据会议纪要创建项目任务',
      },
    });
  });

  it('adds inline approval card to chat when approval is required', async () => {
    vi.mocked(createTask).mockResolvedValue({
      taskId: 24,
      taskCode: 'TASK-24',
      taskStatus: 'pending',
    });
    vi.mocked(subscribeRuntimeEvents).mockImplementation((_taskId, handlers) => {
      handlers.onEvent({
        id: 'event-approval-required',
        tenantId: 100,
        taskId: 24,
        runId: 34,
        eventType: 'APPROVAL_REQUIRED',
        message: 'Agent 需要调用项目管理工具创建任务',
        payload: {
          approvalRequestId: 101,
          title: '创建项目任务',
          riskLevel: 'high',
          approverUserId: 1000,
        },
      });
      return () => {};
    });
    const user = userEvent.setup();

    render(<AgentWorkbenchPage />, { wrapper: createWrapper() });

    await user.type(screen.getByPlaceholderText(/输入项目助理任务/), '根据会议纪要创建任务');
    await user.click(screen.getByRole('button', { name: /发送/ }));

    expect(await screen.findAllByText('创建项目任务')).toHaveLength(2);
    expect(screen.getAllByText('高风险')).toHaveLength(2);
  });

  it('disables composer while creating task', async () => {
    const deferred = createDeferred<TaskCreateResponse>();
    vi.mocked(createTask).mockReturnValue(deferred.promise);
    const user = userEvent.setup();

    render(<AgentWorkbenchPage />, { wrapper: createWrapper() });

    await user.type(screen.getByPlaceholderText(/输入项目助理任务/), '分析项目延期风险');
    await user.click(screen.getByRole('button', { name: /发送/ }));

    const input = screen.getByPlaceholderText(/输入项目助理任务/);
    await waitFor(() => expect(input).toBeDisabled());
    deferred.resolve({ taskId: 22, taskCode: 'TASK-22', taskStatus: 'pending' });
    await waitFor(() => expect(input).not.toBeDisabled());
  });

  it('fills composer from demo scenario without submitting automatically', async () => {
    const user = userEvent.setup();

    render(<AgentWorkbenchPage />, { wrapper: createWrapper() });

    await user.click(screen.getByRole('button', { name: /项目周报生成/ }));

    expect((screen.getByPlaceholderText(/输入项目助理任务/) as HTMLTextAreaElement).value).toContain('生成项目周报');
    expect(createTask).not.toHaveBeenCalled();
  });

  it('starts collaboration session for template demo scenario and links to collaboration page', async () => {
    vi.mocked(createCollaborationSession).mockResolvedValue({
      sessionId: 66,
      sessionCode: 'CS-66',
      status: 'planning',
    });
    const user = userEvent.setup();

    render(<AgentWorkbenchPage />, { wrapper: createWrapper() });

    await user.click(screen.getByRole('button', { name: /需求文档到协作交付/ }));
    await user.click(screen.getByRole('button', { name: /发送/ }));

    await waitFor(() => expect(createCollaborationSession).toHaveBeenCalled());
    expect(createCollaborationSession).toHaveBeenCalledWith({
      strategyType: 'orchestrated_team',
      goalText: expect.stringContaining('请基于以下需求文档启动一次多 Agent 协作交付'),
      contextJson: JSON.stringify({
        source: 'agent-workbench',
        templateCode: 'software_requirement_to_delivery',
        scenarioTitle: '需求文档到协作交付',
      }),
    });
    expect(createTask).not.toHaveBeenCalled();
    expect(startTask).not.toHaveBeenCalled();
    expect(await screen.findByText(/协作会话已创建/)).toBeInTheDocument();
    expect(screen.getByText(/CS-66/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '查看协作会话' })).toHaveAttribute('href', '/collaboration/66');
  });
});
