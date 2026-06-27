import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createTask, getTask, listTaskArtifacts, listTaskEvents, listTaskEventsByType } from './task-api';
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

describe('createTask', () => {
  beforeEach(() => {
    vi.mocked(http.post).mockReset();
    vi.mocked(http.get).mockReset();
  });

  it('maps workbench task input to backend create task command', async () => {
    vi.mocked(http.post).mockResolvedValue({
      data: {
        code: '0',
        message: '成功',
        data: {
          taskId: 12,
          taskCode: 'TASK-20260609-001',
          status: 'pending',
        },
      },
    });

    const response = await createTask({
      agentId: 7,
      input: '生成项目周报',
      channel: 'web',
    });

    expect(http.post).toHaveBeenCalledWith('/v1/tasks', {
      agentId: 7,
      userId: 1000,
      channelType: 'web',
      inputText: '生成项目周报',
      title: '生成项目周报',
    });
    expect(response).toEqual({
      taskId: 12,
      taskCode: 'TASK-20260609-001',
      taskStatus: 'pending',
    });
  });

  it('normalizes backend task detail fields for frontend display', async () => {
    vi.mocked(http.get).mockResolvedValue({
      data: {
        code: 0,
        message: '成功',
        data: {
          id: 12,
          taskCode: 'TASK-12',
          agentId: 1,
          agentVersionId: 2,
          title: '生成项目周报',
          inputText: '请根据本周资料生成项目周报',
          status: 'completed',
          currentRunId: 22,
          resultSummary: '周报已生成',
          createdAt: '2026-06-10T10:00:00Z',
        },
      },
    });

    const response = await getTask(12);

    expect(http.get).toHaveBeenCalledWith('/v1/tasks/12');
    expect(response).toEqual({
      id: 12,
      taskCode: 'TASK-12',
      agentId: 1,
      agentVersionId: 2,
      title: '生成项目周报',
      input: '请根据本周资料生成项目周报',
      status: 'completed',
      currentRunId: 22,
      resultSummary: '周报已生成',
      createTime: '2026-06-10T10:00:00Z',
    });
  });

  it('normalizes backend task events for execution timeline', async () => {
    vi.mocked(http.get).mockResolvedValue({
      data: {
        code: 0,
        message: '成功',
        data: [
          {
            id: 88,
            tenantId: 100,
            taskId: 12,
            runId: 22,
            eventType: 'RUN_STARTED',
            eventSummary: '开始执行',
            payloadJson: '{"trace":"abc"}',
            occurredAt: '2026-06-10T10:00:00Z',
          },
        ],
      },
    });

    const response = await listTaskEvents(12);

    expect(http.get).toHaveBeenCalledWith('/v1/tasks/12/events', {
      params: undefined,
    });
    expect(response).toEqual([
      {
        id: '88',
        tenantId: 100,
        taskId: 12,
        runId: 22,
        sequence: 88,
        lastEventId: '88',
        eventType: 'RUN_STARTED',
        message: '开始执行',
        payload: { trace: 'abc' },
        createTime: '2026-06-10T10:00:00Z',
      },
    ]);
  });

  it('passes lastEventId when listing missed task events', async () => {
    vi.mocked(http.get).mockResolvedValue({
      data: {
        code: 0,
        message: '成功',
        data: [],
      },
    });

    await listTaskEvents(12, '88');

    expect(http.get).toHaveBeenCalledWith('/v1/tasks/12/events', {
      params: { lastEventId: '88' },
    });
  });

  it('lists denied tool events for audit views', async () => {
    vi.mocked(http.get).mockResolvedValue({
      data: {
        code: 0,
        message: 'success',
        data: [
          {
            id: 91,
            tenantId: 100,
            taskId: 12,
            runId: 22,
            eventType: 'TOOL_DENIED',
            eventSummary: 'Tool denied',
            payloadJson: '{"agentVersionId":11,"reason":"tool_not_in_agent_version_scope"}',
            occurredAt: '2026-06-12T10:00:00Z',
          },
        ],
      },
    });

    const response = await listTaskEventsByType('TOOL_DENIED', 20);

    expect(http.get).toHaveBeenCalledWith('/v1/task-events', {
      params: { eventType: 'TOOL_DENIED', limit: 20 },
    });
    expect(response).toEqual({
      records: [
        {
          id: '91',
          tenantId: 100,
          taskId: 12,
          runId: 22,
          sequence: 91,
          lastEventId: '91',
          eventType: 'TOOL_DENIED',
          message: 'Tool denied',
          payload: {
            agentVersionId: 11,
            reason: 'tool_not_in_agent_version_scope',
          },
          createTime: '2026-06-12T10:00:00Z',
        },
      ],
      total: 1,
    });
  });

  it('normalizes paged task event results by type', async () => {
    vi.mocked(http.get).mockResolvedValue({
      data: {
        code: 0,
        message: 'success',
        data: {
          records: [
            {
              id: 91,
              tenantId: 100,
              taskId: 12,
              runId: 22,
              eventType: 'TOOL_DENIED',
              eventSummary: 'Tool denied',
              payloadJson: '{"agentVersionId":11,"reason":"tool_not_in_agent_version_scope"}',
              occurredAt: '2026-06-12T10:00:00Z',
            },
          ],
          total: 86,
          pageNo: 2,
          pageSize: 20,
        },
      },
    });

    const response = await listTaskEventsByType('TOOL_DENIED', {
      pageNo: 2,
      pageSize: 20,
      keyword: 'controlled.cli',
    });

    expect(http.get).toHaveBeenCalledWith('/v1/task-events', {
      params: {
        eventType: 'TOOL_DENIED',
        pageNo: 2,
        pageSize: 20,
        keyword: 'controlled.cli',
      },
    });
    expect(response).toEqual({
      records: [
        {
          id: '91',
          tenantId: 100,
          taskId: 12,
          runId: 22,
          sequence: 91,
          lastEventId: '91',
          eventType: 'TOOL_DENIED',
          message: 'Tool denied',
          payload: {
            agentVersionId: 11,
            reason: 'tool_not_in_agent_version_scope',
          },
          createTime: '2026-06-12T10:00:00Z',
        },
      ],
      total: 86,
      pageNo: 2,
      pageSize: 20,
    });
  });

  it('passes optional filters when listing task events by type', async () => {
    vi.mocked(http.get).mockResolvedValue({
      data: {
        code: 0,
        message: 'success',
        data: [],
      },
    });

    await listTaskEventsByType('TOOL_DENIED', {
      limit: 50,
      pageNo: 3,
      pageSize: 10,
      taskId: '12',
      runId: 22,
      agentVersionId: '11',
      keyword: 'controlled.cli',
    });

    expect(http.get).toHaveBeenCalledWith('/v1/task-events', {
      params: {
        eventType: 'TOOL_DENIED',
        limit: 50,
        pageNo: 3,
        pageSize: 10,
        taskId: '12',
        runId: 22,
        agentVersionId: '11',
        keyword: 'controlled.cli',
      },
    });
  });

  it('normalizes backend task artifacts for artifact preview', async () => {
    vi.mocked(http.get).mockResolvedValue({
      data: {
        code: 0,
        message: '成功',
        data: [
          {
            id: 6,
            taskId: 12,
            artifactType: 'markdown',
            artifactName: '项目周报',
            contentText: '# 项目周报',
            metadataJson: '{"knowledgeConfidence":"low","lowConfidence":true,"sourceRefs":[{"title":"项目资料"}]}',
            createdAt: '2026-06-10T10:01:00Z',
          },
        ],
      },
    });

    const response = await listTaskArtifacts(12);

    expect(http.get).toHaveBeenCalledWith('/v1/tasks/12/artifacts');
    expect(response).toEqual([
      {
        id: 6,
        taskId: 12,
        artifactType: 'markdown',
        title: '项目周报',
        content: '# 项目周报',
        metadata: {
          knowledgeConfidence: 'low',
          lowConfidence: true,
          sourceRefs: [{ title: '项目资料' }],
        },
        createTime: '2026-06-10T10:01:00Z',
      },
    ]);
  });
});
