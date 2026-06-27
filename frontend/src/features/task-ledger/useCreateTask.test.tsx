import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { PropsWithChildren } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { createTask } from '../../services/task-api';
import { useCreateTask } from './hooks/useCreateTask';

vi.mock('../../services/task-api', () => ({
  createTask: vi.fn(),
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

describe('useCreateTask', () => {
  it('creates a task through task API mutation', async () => {
    vi.mocked(createTask).mockResolvedValue({
      taskId: 21,
      taskCode: 'TASK-21',
      taskStatus: 'pending',
    });

    const { result } = renderHook(() => useCreateTask(), { wrapper: createWrapper() });

    const response = await result.current.mutateAsync({
      agentId: 7,
      input: '生成项目周报',
      channel: 'web',
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(createTask).toHaveBeenCalledWith({
      agentId: 7,
      input: '生成项目周报',
      channel: 'web',
    });
    expect(response).toEqual({
      taskId: 21,
      taskCode: 'TASK-21',
      taskStatus: 'pending',
    });
  });
});
