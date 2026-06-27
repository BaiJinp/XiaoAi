import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import type { PropsWithChildren } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useRuntimeEventStore } from '../../../features/runtime-events/runtime-event-store';
import { listTaskArtifacts } from '../../../services/task-api';
import type { RuntimeEvent } from '../../../types/runtime-event';
import { ExecutionPanel } from './ExecutionPanel';

vi.mock('../../../services/task-api', () => ({
  listTaskArtifacts: vi.fn(),
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

function createRuntimeEvent(eventType: RuntimeEvent['eventType'], message?: string): RuntimeEvent {
  return {
    id: `event-${eventType}`,
    tenantId: 100,
    taskId: 10,
    runId: 20,
    eventType,
    message,
  };
}

describe('ExecutionPanel', () => {
  beforeEach(() => {
    useRuntimeEventStore.getState().clearEvents();
    vi.mocked(listTaskArtifacts).mockReset();
    vi.mocked(listTaskArtifacts).mockResolvedValue([]);
  });

  it('renders artifacts for selected task', async () => {
    vi.mocked(listTaskArtifacts).mockResolvedValue([
      {
        id: 1,
        taskId: 10,
        artifactType: 'markdown',
        title: '项目周报',
        content: '# 项目周报\n\n- 已完成需求分析',
      },
    ]);

    render(<ExecutionPanel taskId={10} />, { wrapper: createWrapper() });

    expect(await screen.findByRole('heading', { name: '项目周报' })).toBeInTheDocument();
    expect(screen.getByText('已完成需求分析')).toBeInTheDocument();
  });

  it('renders runtime events for selected task', () => {
    useRuntimeEventStore.getState().appendEvent(10, createRuntimeEvent('RUN_STARTED'));
    useRuntimeEventStore.getState().appendEvent(10, createRuntimeEvent('APPROVAL_REQUIRED', '需要负责人审批'));

    render(<ExecutionPanel taskId={10} />, { wrapper: createWrapper() });

    expect(screen.getByText('运行开始')).toBeInTheDocument();
    expect(screen.getByText('需要审批')).toBeInTheDocument();
    expect(screen.getByText('需要负责人审批')).toBeInTheDocument();
  });
});
