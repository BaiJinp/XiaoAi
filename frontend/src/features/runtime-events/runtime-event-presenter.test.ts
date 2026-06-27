import { describe, expect, it } from 'vitest';
import type { RuntimeEvent } from '../../types/runtime-event';
import { toUserEvent } from './runtime-event-presenter';

function createRuntimeEvent(event: Partial<RuntimeEvent> & Pick<RuntimeEvent, 'eventType'>): RuntimeEvent {
  return {
    id: 'event-1',
    tenantId: 100,
    taskId: 10,
    runId: 20,
    ...event,
  };
}

describe('toUserEvent', () => {
  it('converts approval required event to waiting user event', () => {
    expect(toUserEvent(createRuntimeEvent({ eventType: 'APPROVAL_REQUIRED', message: 'create task' }))).toEqual({
      status: 'waiting',
      title: '需要审批',
      description: 'create task',
    });
  });

  it('converts completed and failed runtime events', () => {
    expect(toUserEvent(createRuntimeEvent({ eventType: 'RUN_COMPLETED' }))).toMatchObject({
      status: 'success',
      title: '任务已完成',
    });
    expect(toUserEvent(createRuntimeEvent({ eventType: 'RUN_FAILED', message: '模型调用失败' }))).toEqual({
      status: 'error',
      title: '运行失败',
      description: '模型调用失败',
    });
  });

  it('converts backend runtime event names', () => {
    expect(toUserEvent(createRuntimeEvent({ eventType: 'MODEL_RESULT' }))).toMatchObject({
      status: 'success',
      title: '已生成分析内容',
    });
    expect(toUserEvent(createRuntimeEvent({ eventType: 'TOOL_BLOCKED' }))).toMatchObject({
      status: 'waiting',
      title: '工具调用被策略拦截',
    });
    expect(toUserEvent(createRuntimeEvent({ eventType: 'ASSISTANT_TASK_COMPLETED' }))).toMatchObject({
      status: 'success',
      title: 'Agent 任务完成',
    });
    expect(toUserEvent(createRuntimeEvent({ eventType: 'ASSISTANT_ARTIFACT' }))).toMatchObject({
      status: 'success',
      title: '已生成交付物',
    });
    expect(toUserEvent(createRuntimeEvent({ eventType: 'KNOWLEDGE_RETRIEVE' }))).toMatchObject({
      status: 'process',
      title: '正在检索项目资料',
    });
    expect(toUserEvent(createRuntimeEvent({ eventType: 'KNOWLEDGE_RESULT' }))).toMatchObject({
      status: 'success',
      title: '已检索项目资料',
    });
    expect(toUserEvent(createRuntimeEvent({ eventType: 'MEMORY_CONTEXT', payload: { memoryCount: 2 } }))).toMatchObject({
      status: 'success',
      title: '已加载确认记忆',
      description: '已注入 2 条已确认记忆',
    });
    expect(toUserEvent(createRuntimeEvent({ eventType: 'MODEL_CALL' }))).toMatchObject({
      status: 'process',
      title: '正在调用模型',
    });
    expect(toUserEvent(createRuntimeEvent({ eventType: 'TOOL_FAILED' }))).toMatchObject({
      status: 'error',
      title: '工具调用失败',
    });
  });

  it('uses payload error and sequence metadata when present', () => {
    expect(
      toUserEvent(
        createRuntimeEvent({
          eventType: 'RUN_FAILED',
          message: 'fallback message',
          sequence: 9,
          stepId: 3,
          payload: { errorMessage: '模型超时' },
        }),
      ),
    ).toEqual({
      status: 'error',
      title: '运行失败',
      description: '模型超时',
      meta: '#9 · step 3',
    });
  });

  it('renders tool denied as agent version scope error', () => {
    expect(
      toUserEvent(
        createRuntimeEvent({
          eventType: 'TOOL_DENIED',
          payload: { agentVersionId: 11, toolCode: 'controlled.cli.denied' },
        }),
      ),
    ).toMatchObject({
      status: 'error',
      title: '工具不在当前 Agent 版本范围内',
      description: 'AgentVersion 11 未授权调用该工具',
    });
  });

  it('adds tool executor and risk metadata when present', () => {
    expect(
      toUserEvent(
        createRuntimeEvent({
          eventType: 'TOOL_CALL',
          sequence: 10,
          payload: {
            toolCallIndex: 1,
            toolCode: 'controlled.cli.project-update',
            toolId: 22,
            executorType: 'cli',
            riskLevel: 'high',
            approvalRequestId: 88,
          },
        }),
      ),
    ).toMatchObject({
      meta:
        '#10 · toolCallIndex 1 · tool controlled.cli.project-update · toolId 22 · executor cli · risk high · approval 88',
    });
  });

  it('renders blocked tool approval details', () => {
    expect(
      toUserEvent(
        createRuntimeEvent({
          eventType: 'TOOL_BLOCKED',
          sequence: 11,
          payload: {
            toolCallIndex: 2,
            toolCode: 'controlled.cli.project-update',
            approvalRequestId: 99,
            reason: 'requires_human_approval',
          },
        }),
      ),
    ).toMatchObject({
      status: 'waiting',
      description: 'Approval 99 required: requires_human_approval',
      meta: '#11 · toolCallIndex 2 · tool controlled.cli.project-update · approval 99',
    });
  });

  it('renders workflow step titles and warning status', () => {
    expect(
      toUserEvent(
        createRuntimeEvent({
          eventType: 'STEP_STARTED',
          payload: { stepName: '检索项目资料' },
        }),
      ),
    ).toMatchObject({
      status: 'process',
      title: '正在执行：检索项目资料',
    });

    expect(
      toUserEvent(
        createRuntimeEvent({
          eventType: 'STEP_COMPLETED',
          payload: { stepName: '规则自检', status: 'warning' },
        }),
      ),
    ).toMatchObject({
      status: 'waiting',
      title: '已完成：规则自检',
      description: '步骤已完成，但结果需要复核',
    });
  });

  it('renders assistant workflow planning and artifact details', () => {
    expect(
      toUserEvent(
        createRuntimeEvent({
          eventType: 'ASSISTANT_PLAN_CREATED',
          payload: { workflowSteps: { steps: [{ stepId: 1 }, { stepId: 2 }] } },
        }),
      ),
    ).toMatchObject({
      title: '已生成执行计划',
      description: '已规划 2 个执行步骤',
    });

    expect(
      toUserEvent(
        createRuntimeEvent({
          eventType: 'ASSISTANT_ARTIFACT',
          payload: { artifactName: '项目周报' },
        }),
      ),
    ).toMatchObject({
      title: '已生成：项目周报',
    });
  });
});
