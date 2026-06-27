import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  acceptAgentHandoff,
  createCollaborationSession,
  failQualityGate,
  getCollaborationTemplate,
  getCollaborationSession,
  listCollaborationRoleBindings,
  listAgentRoles,
  listCollaborationTemplates,
  listCollaborationPlans,
  listAgentHandoffs,
  listAgentThreads,
  listQualityGates,
  passQualityGate,
  rejectAgentHandoff,
  startAgentThreadTask,
  startCollaborationSession,
  submitCollaborationPlan,
  updateCollaborationRoleBinding,
  updateAgentRoleDefaultAgent,
} from './collaboration-api';
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

describe('collaboration-api', () => {
  beforeEach(() => {
    vi.mocked(http.get).mockReset();
    vi.mocked(http.post).mockReset();
    vi.mocked(http.put).mockReset();
  });

  it('creates a generic collaboration session', async () => {
    vi.mocked(http.post).mockResolvedValue({
      data: {
        code: 0,
        message: '成功',
        data: {
          sessionId: 12,
          sessionCode: 'CS202606110001',
          status: 'planning',
        },
      },
    });

    const response = await createCollaborationSession({
      templateId: 3,
      strategyType: 'orchestrated_team',
      goalText: '完成一次通用协作交付',
      contextJson: '{"domain":"software_development"}',
    });

    expect(http.post).toHaveBeenCalledWith('/v1/collaboration-sessions', {
      templateId: 3,
      strategyType: 'orchestrated_team',
      goalText: '完成一次通用协作交付',
      contextJson: '{"domain":"software_development"}',
    });
    expect(response).toEqual({
      sessionId: 12,
      sessionCode: 'CS202606110001',
      status: 'planning',
    });
  });

  it('lists collaboration templates by domain', async () => {
    vi.mocked(http.get).mockResolvedValue({
      data: {
        code: 0,
        message: 'success',
        data: [
          {
            templateId: 2,
            templateCode: 'software_requirement_to_delivery',
            templateName: 'Software Requirement To Delivery',
            domainCode: 'software_development',
            strategyType: 'orchestrated_team',
            status: 'active',
          },
        ],
      },
    });

    const response = await listCollaborationTemplates('software_development');

    expect(http.get).toHaveBeenCalledWith('/v1/collaboration-templates', {
      params: { domainCode: 'software_development' },
    });
    expect(response[0].templateCode).toBe('software_requirement_to_delivery');
  });

  it('lists agent roles by domain', async () => {
    vi.mocked(http.get).mockResolvedValue({
      data: {
        code: 0,
        message: 'success',
        data: [
          {
            id: 2,
            roleCode: 'software_product_manager',
            roleName: 'Product Manager',
            domainCode: 'software_development',
            defaultAgentId: 12,
            defaultAgentVersionId: 13,
            status: 'active',
          },
        ],
      },
    });

    const response = await listAgentRoles('software_development');

    expect(http.get).toHaveBeenCalledWith('/v1/agent-roles', {
      params: { domainCode: 'software_development' },
    });
    expect(response[0].roleCode).toBe('software_product_manager');
    expect(response[0].defaultAgentId).toBe(12);
    expect(response[0].defaultAgentVersionId).toBe(13);
  });

  it('updates an agent role default binding', async () => {
    vi.mocked(http.put).mockResolvedValue({
      data: {
        code: 0,
        message: 'success',
        data: {
          id: 2,
          roleCode: 'software_product_manager',
          defaultAgentId: 12,
          defaultAgentVersionId: 13,
        },
      },
    });

    const response = await updateAgentRoleDefaultAgent(2, {
      defaultAgentId: 12,
      defaultAgentVersionId: 13,
    });

    expect(http.put).toHaveBeenCalledWith('/v1/agent-roles/2/default-agent', {
      defaultAgentId: 12,
      defaultAgentVersionId: 13,
    });
    expect(response.defaultAgentVersionId).toBe(13);
  });

  it('lists collaboration role bindings for a template', async () => {
    vi.mocked(http.get).mockResolvedValue({
      data: {
        code: 0,
        message: 'success',
        data: [
          {
            roleCode: 'software_product_manager',
            templateId: 2,
            effectiveAgentId: 201,
            effectiveAgentVersionId: 2001,
            source: 'template',
          },
        ],
      },
    });

    const response = await listCollaborationRoleBindings(2, 'team', 'team-a');

    expect(http.get).toHaveBeenCalledWith('/v1/collaboration-templates/2/role-bindings', {
      params: {
        bindingScope: 'team',
        bindingKey: 'team-a',
      },
    });
    expect(response[0].effectiveAgentId).toBe(201);
    expect(response[0].source).toBe('template');
  });

  it('updates a collaboration role binding for a template', async () => {
    vi.mocked(http.put).mockResolvedValue({
      data: {
        code: 0,
        message: 'success',
        data: {
          roleCode: 'software_product_manager',
          templateId: 2,
          effectiveAgentId: 201,
          effectiveAgentVersionId: 2001,
          source: 'template',
        },
      },
    });

    const response = await updateCollaborationRoleBinding(2, 'software_product_manager', {
      agentId: 201,
      agentVersionId: 2001,
    });

    expect(http.put).toHaveBeenCalledWith('/v1/collaboration-templates/2/role-bindings/software_product_manager', {
      agentId: 201,
      agentVersionId: 2001,
    });
    expect(response.effectiveAgentVersionId).toBe(2001);
  });

  it('starts a bound collaboration thread task', async () => {
    vi.mocked(http.post).mockResolvedValue({
      data: {
        code: 0,
        message: 'success',
        data: {
          taskId: 88,
          runId: 99,
          runCode: 'RUN-99',
          status: 'running',
          runtimeType: 'java-in-process',
        },
      },
    });

    const response = await startAgentThreadTask(12, 30, { runtimeType: 'java-in-process' });

    expect(http.post).toHaveBeenCalledWith('/v1/collaboration-sessions/12/threads/30/start', {
      runtimeType: 'java-in-process',
    });
    expect(response.runId).toBe(99);
  });

  it('gets a collaboration template detail', async () => {
    vi.mocked(http.get).mockResolvedValue({
      data: {
        code: 0,
        message: 'success',
        data: {
          templateId: 2,
          templateCode: 'software_requirement_to_delivery',
          templateName: 'Software Requirement To Delivery',
          strategyType: 'orchestrated_team',
          templateJson: '{"stages":[]}',
          status: 'active',
        },
      },
    });

    const response = await getCollaborationTemplate(2);

    expect(http.get).toHaveBeenCalledWith('/v1/collaboration-templates/2');
    expect(response.templateJson).toContain('stages');
  });

  it('gets a collaboration session detail', async () => {
    vi.mocked(http.get).mockResolvedValue({
      data: {
        code: 0,
        message: '成功',
        data: {
          id: 12,
          sessionCode: 'CS202606110001',
          strategyType: 'orchestrated_team',
          goalText: '完成一次通用协作交付',
          status: 'planning',
        },
      },
    });

    const response = await getCollaborationSession(12);

    expect(http.get).toHaveBeenCalledWith('/v1/collaboration-sessions/12');
    expect(response.goalText).toBe('完成一次通用协作交付');
  });

  it('submits and starts a collaboration plan', async () => {
    vi.mocked(http.post)
      .mockResolvedValueOnce({
        data: {
          code: 0,
          message: '成功',
          data: {
            id: 20,
            sessionId: 12,
            planStatus: 'submitted',
            validationStatus: 'passed',
            planJson: '{"goal":"通用协作"}',
          },
        },
      })
      .mockResolvedValueOnce({
        data: {
          code: 0,
          message: '成功',
          data: {
            status: 'started',
            createdThreadCount: 1,
            createdGateCount: 2,
          },
        },
      });

    const plan = await submitCollaborationPlan(12, { planJson: '{"goal":"通用协作"}' });
    const started = await startCollaborationSession(12, { planId: 20 });

    expect(http.post).toHaveBeenNthCalledWith(1, '/v1/collaboration-sessions/12/plans', {
      planJson: '{"goal":"通用协作"}',
    });
    expect(http.post).toHaveBeenNthCalledWith(2, '/v1/collaboration-sessions/12/start', { planId: 20 });
    expect(plan.validationStatus).toBe('passed');
    expect(started).toEqual({
      status: 'started',
      createdThreadCount: 1,
      createdGateCount: 2,
    });
  });

  it('lists collaboration plans for a session', async () => {
    vi.mocked(http.get).mockResolvedValue({
      data: {
        code: 0,
        message: 'success',
        data: [
          {
            id: 20,
            sessionId: 12,
            planStatus: 'submitted',
            validationStatus: 'passed',
            planJson: '{"stages":[{"toolCalls":[]}]}',
          },
        ],
      },
    });

    const plans = await listCollaborationPlans(12);

    expect(http.get).toHaveBeenCalledWith('/v1/collaboration-sessions/12/plans');
    expect(plans[0].planJson).toContain('toolCalls');
  });

  it('lists threads and quality gates and handles gate decisions', async () => {
    vi.mocked(http.get)
      .mockResolvedValueOnce({
        data: {
          code: 0,
          message: '成功',
          data: [
            {
              id: 30,
              sessionId: 12,
              agentId: 7,
              threadCode: 'TH001',
              threadName: '需求分析',
              status: 'pending',
            },
          ],
        },
      })
      .mockResolvedValueOnce({
        data: {
          code: 0,
          message: '成功',
          data: [
            {
              id: 40,
              sessionId: 12,
              gateCode: 'requirement_confirmed',
              gateName: '需求确认',
              gateType: 'manual_confirmation',
              status: 'pending',
              required: true,
            },
          ],
        },
      });
    vi.mocked(http.post)
      .mockResolvedValueOnce({ data: { code: 0, message: '成功', data: { id: 40, status: 'passed' } } })
      .mockResolvedValueOnce({ data: { code: 0, message: '成功', data: { id: 40, status: 'failed' } } });

    const threads = await listAgentThreads(12);
    const gates = await listQualityGates(12);
    await passQualityGate(12, 40, { resultJson: '{"confirmed":true}' });
    await failQualityGate(12, 40, { failReason: '缺少确认' });

    expect(http.get).toHaveBeenNthCalledWith(1, '/v1/collaboration-sessions/12/threads');
    expect(http.get).toHaveBeenNthCalledWith(2, '/v1/collaboration-sessions/12/gates');
    expect(http.post).toHaveBeenNthCalledWith(1, '/v1/collaboration-sessions/12/gates/40/pass', {
      resultJson: '{"confirmed":true}',
    });
    expect(http.post).toHaveBeenNthCalledWith(2, '/v1/collaboration-sessions/12/gates/40/fail', {
      failReason: '缺少确认',
    });
    expect(threads[0].threadName).toBe('需求分析');
    expect(gates[0].gateCode).toBe('requirement_confirmed');
  });

  it('lists handoffs and handles handoff decisions', async () => {
    vi.mocked(http.get).mockResolvedValue({
      data: {
        code: 0,
        message: '成功',
        data: [
          {
            id: 60,
            sessionId: 12,
            artifactId: 99,
            handoffType: 'artifact',
            status: 'pending',
          },
        ],
      },
    });
    vi.mocked(http.post)
      .mockResolvedValueOnce({ data: { code: 0, message: '成功', data: null } })
      .mockResolvedValueOnce({ data: { code: 0, message: '成功', data: null } });

    const handoffs = await listAgentHandoffs(12);
    await acceptAgentHandoff(12, 60);
    await rejectAgentHandoff(12, 61);

    expect(http.get).toHaveBeenCalledWith('/v1/collaboration-sessions/12/handoffs');
    expect(http.post).toHaveBeenNthCalledWith(1, '/v1/collaboration-sessions/12/handoffs/60/accept');
    expect(http.post).toHaveBeenNthCalledWith(2, '/v1/collaboration-sessions/12/handoffs/61/reject');
    expect(handoffs[0].artifactId).toBe(99);
  });
});
