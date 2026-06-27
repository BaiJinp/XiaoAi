import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createAgentDraft, createAgentVersion, listAgentVersions, listAgents, replaceAgentVersionTools } from './agent-api';
import { http } from './http';

vi.mock('./http', async () => {
  const actual = await vi.importActual<typeof import('./http')>('./http');
  return {
    ...actual,
    http: {
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
    },
  };
});

describe('agent-api', () => {
  beforeEach(() => {
    vi.mocked(http.get).mockReset();
    vi.mocked(http.post).mockReset();
    vi.mocked(http.put).mockReset();
  });

  it('maps project assistant draft form to backend create draft command', async () => {
    vi.mocked(http.post).mockResolvedValue({
      data: {
        code: 0,
        message: '成功',
        data: {
          agentId: 9,
          agentCode: 'AGENT-9',
          status: 'draft',
        },
      },
    });

    const response = await createAgentDraft({
      name: '项目助理',
      roleDescription: '负责项目推进',
      responsibilities: '周报、风险分析',
      boundaries: '不直接删除任务',
      modelProviderId: 10,
      modelConfigId: 11,
    });

    expect(http.post).toHaveBeenCalledWith('/v1/agents/drafts', {
      agentName: '项目助理',
      description: '角色：负责项目推进\n职责：周报、风险分析\n边界：不直接删除任务',
      ownerUserId: 1000,
      modelProviderId: 10,
      modelConfigId: 11,
    });
    expect(response).toEqual({
      agentId: 9,
      agentCode: 'AGENT-9',
      status: 'draft',
    });
  });

  it('creates agent version with selected tool ids', async () => {
    vi.mocked(http.post).mockResolvedValue({
      data: {
        code: 0,
        message: 'success',
        data: {
          agentVersionId: 13,
          versionNo: 'v20260612230000',
          versionStatus: 'draft',
          runtimeSnapshotJson: '{"orchestrationPolicy":{"executionMode":"dynamic_workflow"}}',
          modelPolicyJson: '{"routing":"agent_default"}',
          toolPolicyJson: '{"approval":"risk_based"}',
          contextPolicyJson: '{"maxItems":8}',
          memoryPolicyJson: '{"write":"confirmed_only","enabled":true,"maxItems":5,"scopes":["task"]}',
          orchestrationPolicyJson: '{"executionMode":"dynamic_workflow"}',
          toolIds: [22, 23],
        },
      },
    });

    const response = await createAgentVersion(12, {
      rolePrompt: 'project assistant',
      responsibilityText: 'report',
      boundaryText: 'no delete',
      modelPolicyJson: '{"routing":"agent_default"}',
      toolPolicyJson: '{"approval":"risk_based"}',
      contextPolicyJson: '{"maxItems":8}',
      memoryPolicyJson: '{"write":"confirmed_only","enabled":true,"maxItems":5,"scopes":["task"]}',
      orchestrationPolicyJson: '{"executionMode":"dynamic_workflow"}',
      toolIds: [22, 23],
    });

    expect(http.post).toHaveBeenCalledWith('/v1/agent-versions/agents/12/versions', {
      rolePrompt: 'project assistant',
      responsibilityText: 'report',
      boundaryText: 'no delete',
      modelPolicyJson: '{"routing":"agent_default"}',
      toolPolicyJson: '{"approval":"risk_based"}',
      contextPolicyJson: '{"maxItems":8}',
      memoryPolicyJson: '{"write":"confirmed_only","enabled":true,"maxItems":5,"scopes":["task"]}',
      orchestrationPolicyJson: '{"executionMode":"dynamic_workflow"}',
      toolIds: [22, 23],
    });
    expect(response.agentVersionId).toBe(13);
    expect(response.runtimeSnapshotJson).toBe('{"orchestrationPolicy":{"executionMode":"dynamic_workflow"}}');
    expect(response.orchestrationPolicyJson).toBe('{"executionMode":"dynamic_workflow"}');
  });

  it('replaces draft agent version tools', async () => {
    vi.mocked(http.put).mockResolvedValue({
      data: {
        code: 0,
        message: 'success',
        data: {
          agentVersionId: 13,
          versionNo: 'v20260612230000',
          versionStatus: 'draft',
          toolIds: [22],
        },
      },
    });

    const response = await replaceAgentVersionTools(12, 13, { toolIds: [22] });

    expect(http.put).toHaveBeenCalledWith('/v1/agent-versions/agents/12/versions/13/tools', { toolIds: [22] });
    expect(response.toolIds).toEqual([22]);
  });

  it('lists agent versions by agent id', async () => {
    vi.mocked(http.get).mockResolvedValue({
      data: {
        code: 0,
        message: 'success',
        data: [
          {
            agentVersionId: 13,
            versionNo: 'v20260612230000',
            versionStatus: 'draft',
            toolIds: [22],
          },
        ],
      },
    });

    const response = await listAgentVersions(12);

    expect(http.get).toHaveBeenCalledWith('/v1/agent-versions/agents/12/versions');
    expect(response).toHaveLength(1);
    expect(response[0].toolIds).toEqual([22]);
  });

  it('lists agents for collaboration binding', async () => {
    vi.mocked(http.get).mockResolvedValue({
      data: {
        code: 0,
        message: 'success',
        data: {
          pageNo: 1,
          pageSize: 20,
          total: 1,
          records: [
            {
              id: 12,
              agentName: 'Product Agent',
              status: 'published',
              latestStableVersionId: 13,
            },
          ],
        },
      },
    });

    const response = await listAgents({ status: 'published', pageNo: 1, pageSize: 50 });

    expect(http.get).toHaveBeenCalledWith('/v1/agents', {
      params: { status: 'published', pageNo: 1, pageSize: 50 },
    });
    expect(response.records[0].latestStableVersionId).toBe(13);
  });
});
