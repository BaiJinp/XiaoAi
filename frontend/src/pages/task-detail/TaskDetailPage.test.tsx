import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { PropsWithChildren } from 'react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { handleApproval } from '../../services/approval-api';
import { createAgentMemory, listAgentMemories } from '../../services/memory-api';
import { getTask, listTaskArtifacts, listTaskEvents } from '../../services/task-api';
import { TaskDetailPage } from './TaskDetailPage';

vi.mock('../../services/approval-api', () => ({
  handleApproval: vi.fn(),
}));

vi.mock('../../services/task-api', () => ({
  getTask: vi.fn(),
  listTaskEvents: vi.fn(),
  listTaskArtifacts: vi.fn(),
}));

vi.mock('../../services/memory-api', () => ({
  listAgentMemories: vi.fn(),
  createAgentMemory: vi.fn(),
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

function renderTaskDetail(taskId = 12) {
  return render(
    <MemoryRouter initialEntries={[`/tasks/${taskId}`]}>
      <Routes>
        <Route path="/tasks/:taskId" element={<TaskDetailPage />} />
      </Routes>
    </MemoryRouter>,
    { wrapper: createWrapper() },
  );
}

describe('TaskDetailPage', () => {
  beforeEach(() => {
    vi.mocked(getTask).mockReset();
    vi.mocked(listTaskEvents).mockReset();
    vi.mocked(listTaskArtifacts).mockReset();
    vi.mocked(handleApproval).mockReset();
    vi.mocked(listAgentMemories).mockReset();
    vi.mocked(createAgentMemory).mockReset();
    vi.mocked(listAgentMemories).mockResolvedValue({
      records: [],
      total: 0,
      pageNo: 1,
      pageSize: 20,
    });
  });

  it('shows approval card from approval required runtime event', async () => {
    vi.mocked(handleApproval).mockResolvedValue(undefined);
    vi.mocked(getTask).mockResolvedValue({
      id: 12,
      agentId: 1,
      title: '会议纪要转任务',
      input: '请根据会议纪要创建任务',
      status: 'suspended',
    });
    vi.mocked(listTaskEvents).mockResolvedValue([
      {
        id: 'event-approval-required',
        tenantId: 100,
        taskId: 12,
        runId: 22,
        eventType: 'APPROVAL_REQUIRED',
        message: 'Agent 需要调用项目管理工具创建任务',
        payload: {
          approvalRequestId: 101,
          title: '创建项目任务',
          riskLevel: 'high',
          approverUserId: 1000,
        },
      },
    ]);
    vi.mocked(listTaskArtifacts).mockResolvedValue([]);

    renderTaskDetail();

    expect(await screen.findByText('会议纪要转任务')).toBeInTheDocument();
    expect(await screen.findByText('创建项目任务')).toBeInTheDocument();
    expect(screen.getByText('高风险')).toBeInTheDocument();
    expect(screen.getByText('Agent 需要调用项目管理工具创建任务')).toBeInTheDocument();
  });

  it('shows task facts, execution events, artifacts, and confirmed memories for route task id', async () => {
    vi.mocked(getTask).mockResolvedValue({
      id: 12,
      agentId: 1,
      agentVersionId: 2,
      title: '生成项目周报',
      input: '请根据本周资料生成项目周报',
      status: 'completed',
      currentRunId: 22,
      createTime: '2026-06-10T10:00:00Z',
    });
    vi.mocked(listTaskEvents).mockResolvedValue([
      {
        id: 'event-run-started',
        tenantId: 100,
        taskId: 12,
        runId: 22,
        eventType: 'RUN_STARTED',
        message: '开始执行项目周报任务',
      },
    ]);
    vi.mocked(listTaskArtifacts).mockResolvedValue([
      {
        id: 6,
        taskId: 12,
        artifactType: 'markdown',
        title: '项目周报',
        content: '# 项目周报\n\n- 已完成前端工作台',
      },
    ]);
    vi.mocked(listAgentMemories).mockResolvedValue({
      records: [
        {
          id: 9,
          agentId: 1,
          taskId: 12,
          memoryType: 'session_summary',
          confidence: 'confirmed',
          summaryText: '周报必须按风险优先排序',
          status: 'confirmed',
        },
      ],
      total: 1,
      pageNo: 1,
      pageSize: 20,
    });

    renderTaskDetail();

    expect(await screen.findByText('生成项目周报')).toBeInTheDocument();
    expect(screen.getByText('请根据本周资料生成项目周报')).toBeInTheDocument();
    expect(screen.getByText('completed')).toBeInTheDocument();
    expect(screen.getByText('运行开始')).toBeInTheDocument();
    expect(screen.getByText('开始执行项目周报任务')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '项目周报' })).toBeInTheDocument();
    expect(screen.getByText('已完成前端工作台')).toBeInTheDocument();
    expect(await screen.findByText('周报必须按风险优先排序')).toBeInTheDocument();
    expect(getTask).toHaveBeenCalledWith(12);
    expect(listTaskEvents).toHaveBeenCalledWith(12);
    expect(listTaskArtifacts).toHaveBeenCalledWith(12);
    expect(listAgentMemories).toHaveBeenCalledWith({ taskId: 12, status: 'confirmed' });
  });

  it('creates confirmed memory from task detail', async () => {
    vi.mocked(getTask).mockResolvedValue({
      id: 12,
      agentId: 1,
      agentVersionId: 2,
      title: '生成项目周报',
      input: '请根据本周资料生成项目周报',
      status: 'completed',
      currentRunId: 22,
      resultSummary: '周报已生成',
    });
    vi.mocked(listTaskEvents).mockResolvedValue([]);
    vi.mocked(listTaskArtifacts).mockResolvedValue([]);
    vi.mocked(createAgentMemory).mockResolvedValue({
      id: 10,
      agentId: 1,
      agentVersionId: 2,
      taskId: 12,
      runId: 22,
      summaryText: '后续周报固定按风险优先排序',
      status: 'confirmed',
    });
    const user = userEvent.setup();

    renderTaskDetail();

    await user.type(await screen.findByLabelText('确认记忆摘要'), '后续周报固定按风险优先排序');
    await user.click(screen.getByRole('button', { name: '确认保存' }));

    await waitFor(() =>
      expect(createAgentMemory).toHaveBeenCalledWith({
        agentId: 1,
        agentVersionId: 2,
        taskId: 12,
        runId: 22,
        memoryType: 'session_summary',
        memoryScope: 'task',
        summaryText: '后续周报固定按风险优先排序',
        sourceText: '周报已生成',
      }),
    );
  });
});
