import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { PropsWithChildren } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { handleApproval } from '../../services/approval-api';
import { useHandleApproval } from './hooks/useHandleApproval';

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

describe('useHandleApproval', () => {
  it('handles approval through approval API mutation', async () => {
    vi.mocked(handleApproval).mockResolvedValue(undefined);

    const { result } = renderHook(() => useHandleApproval(), { wrapper: createWrapper() });

    await result.current.mutateAsync({ requestId: 12, action: 'approve', comment: '确认执行' });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(handleApproval).toHaveBeenCalledWith(12, {
      action: 'approve',
      comment: '确认执行',
    });
  });
});
