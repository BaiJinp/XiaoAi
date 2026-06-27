import { beforeEach, describe, expect, it, vi } from 'vitest';
import { handleApproval } from './approval-api';
import { http } from './http';

vi.mock('./http', async () => {
  const actual = await vi.importActual<typeof import('./http')>('./http');
  return {
    ...actual,
    http: {
      get: vi.fn(),
      post: vi.fn(),
    },
  };
});

describe('handleApproval', () => {
  beforeEach(() => {
    vi.mocked(http.post).mockReset();
  });

  it('maps frontend approval action to backend handle command', async () => {
    vi.mocked(http.post).mockResolvedValue({
      data: {
        code: 0,
        message: '成功',
        data: null,
      },
    });

    await handleApproval(12, { action: 'approve', comment: '确认执行' });

    expect(http.post).toHaveBeenCalledWith('/v1/approval-requests/12/handle', {
      operatorUserId: 1000,
      action: 'approve',
      commentText: '确认执行',
    });
  });
});
