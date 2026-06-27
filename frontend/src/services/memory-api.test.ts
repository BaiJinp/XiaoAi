import { beforeEach, describe, expect, it, vi } from 'vitest';
import { archiveAgentMemory, createAgentMemory, listAgentMemories } from './memory-api';
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

describe('memory-api', () => {
  beforeEach(() => {
    vi.mocked(http.get).mockReset();
    vi.mocked(http.post).mockReset();
  });

  it('lists confirmed task memories with compact filters', async () => {
    vi.mocked(http.get).mockResolvedValue({
      data: {
        code: 0,
        message: 'success',
        data: {
          records: [],
          total: 0,
          pageNo: 1,
          pageSize: 20,
        },
      },
    });

    const response = await listAgentMemories({ taskId: 12, status: 'confirmed' });

    expect(http.get).toHaveBeenCalledWith('/v1/agent-memories', {
      params: {
        taskId: 12,
        status: 'confirmed',
      },
    });
    expect(response.records).toEqual([]);
  });

  it('creates a confirmed agent memory', async () => {
    vi.mocked(http.post).mockResolvedValue({
      data: {
        code: 0,
        message: 'success',
        data: {
          id: 10,
          agentId: 1,
          taskId: 12,
          summaryText: '周报必须按风险优先排序',
          status: 'confirmed',
        },
      },
    });

    const response = await createAgentMemory({
      agentId: 1,
      taskId: 12,
      runId: 22,
      summaryText: '周报必须按风险优先排序',
    });

    expect(http.post).toHaveBeenCalledWith('/v1/agent-memories', {
      agentId: 1,
      taskId: 12,
      runId: 22,
      summaryText: '周报必须按风险优先排序',
    });
    expect(response.status).toBe('confirmed');
  });

  it('archives an agent memory', async () => {
    vi.mocked(http.post).mockResolvedValue({
      data: {
        code: 0,
        message: 'success',
        data: {
          id: 10,
          agentId: 1,
          summaryText: '旧约束',
          status: 'archived',
        },
      },
    });

    const response = await archiveAgentMemory(10);

    expect(http.post).toHaveBeenCalledWith('/v1/agent-memories/10/archive');
    expect(response.status).toBe('archived');
  });
});
