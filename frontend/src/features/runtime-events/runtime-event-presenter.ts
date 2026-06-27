import type { RuntimeEvent, RuntimeEventType } from '../../types/runtime-event';

export type UserEventStatus = 'process' | 'waiting' | 'success' | 'error' | 'default';

export interface UserRuntimeEvent {
  status: UserEventStatus;
  title: string;
  description?: string;
  meta?: string;
}

const eventTitleMap: Partial<Record<RuntimeEventType, string>> = {
  RUN_STARTED: '运行开始',
  PLAN_CREATED: '已生成执行计划',
  ASSISTANT_PLAN_CREATED: '已生成执行计划',
  STEP_STARTED: '步骤开始',
  STEP_COMPLETED: '步骤完成',
  KNOWLEDGE_RETRIEVE: '正在检索项目资料',
  KNOWLEDGE_RESULT: '已检索项目资料',
  KNOWLEDGE_RETRIEVED: '已检索项目资料',
  MEMORY_CONTEXT: '已加载确认记忆',
  MODEL_CALL: '正在调用模型',
  MODEL_CALLED: '已调用模型',
  MODEL_RESULT: '已生成分析内容',
  TOOL_CALL: '正在调用工具',
  TOOL_CALL_REQUESTED: '准备调用工具',
  TOOL_CALL_BLOCKED: '工具调用被策略拦截',
  TOOL_CALL_COMPLETED: '工具调用完成',
  TOOL_BLOCKED: '工具调用被策略拦截',
  TOOL_FAILED: '工具调用失败',
  TOOL_RESULT: '工具调用完成',
  ASSISTANT_ARTIFACT: '已生成交付物',
  ASSISTANT_TASK_COMPLETED: 'Agent 任务完成',
  ASSISTANT_TASK_SUSPENDED: 'Agent 任务已挂起',
  APPROVAL_REQUIRED: '需要审批',
  APPROVAL_APPROVED: '审批已通过',
  APPROVAL_REJECTED: '审批已拒绝',
  RUN_SUSPENDED: '任务已挂起',
  RUN_RESUMED: '任务已恢复',
  RUN_COMPLETED: '任务已完成',
  RUN_FAILED: '运行失败',
  RUN_CANCELLED: '任务已取消',
};

const eventStatusMap: Partial<Record<RuntimeEventType, UserEventStatus>> = {
  RUN_STARTED: 'process',
  PLAN_CREATED: 'process',
  ASSISTANT_PLAN_CREATED: 'process',
  STEP_STARTED: 'process',
  STEP_COMPLETED: 'success',
  KNOWLEDGE_RETRIEVE: 'process',
  KNOWLEDGE_RESULT: 'success',
  KNOWLEDGE_RETRIEVED: 'success',
  MEMORY_CONTEXT: 'success',
  MODEL_CALL: 'process',
  MODEL_CALLED: 'success',
  MODEL_RESULT: 'success',
  TOOL_CALL: 'process',
  TOOL_CALL_REQUESTED: 'process',
  TOOL_CALL_BLOCKED: 'waiting',
  TOOL_CALL_COMPLETED: 'success',
  TOOL_BLOCKED: 'waiting',
  TOOL_FAILED: 'error',
  TOOL_RESULT: 'success',
  ASSISTANT_ARTIFACT: 'success',
  ASSISTANT_TASK_COMPLETED: 'success',
  ASSISTANT_TASK_SUSPENDED: 'waiting',
  APPROVAL_REQUIRED: 'waiting',
  APPROVAL_APPROVED: 'success',
  APPROVAL_REJECTED: 'error',
  RUN_SUSPENDED: 'waiting',
  RUN_RESUMED: 'process',
  RUN_COMPLETED: 'success',
  RUN_FAILED: 'error',
  RUN_CANCELLED: 'default',
};

function textPayload(event: RuntimeEvent, fieldName: string) {
  const value = event.payload?.[fieldName];
  return typeof value === 'string' ? value : undefined;
}

function numberPayload(event: RuntimeEvent, fieldName: string) {
  const value = event.payload?.[fieldName];
  return typeof value === 'number' ? value : undefined;
}

function workflowStepCount(event: RuntimeEvent) {
  const workflowSteps = event.payload?.workflowSteps;
  if (!workflowSteps || typeof workflowSteps !== 'object' || !('steps' in workflowSteps)) {
    return undefined;
  }
  const steps = (workflowSteps as { steps?: unknown }).steps;
  return Array.isArray(steps) ? steps.length : undefined;
}

function toolMetadata(event: RuntimeEvent) {
  const toolCallIndex = numberPayload(event, 'toolCallIndex');
  const toolId = numberPayload(event, 'toolId');
  const approvalRequestId = numberPayload(event, 'approvalRequestId');
  const toolCode = textPayload(event, 'toolCode');
  const executorType = textPayload(event, 'executorType') || textPayload(event, 'toolType');
  const riskLevel = textPayload(event, 'riskLevel');
  return [
    toolCallIndex === undefined ? undefined : `toolCallIndex ${toolCallIndex}`,
    toolCode ? `tool ${toolCode}` : undefined,
    toolId === undefined ? undefined : `toolId ${toolId}`,
    executorType ? `executor ${executorType}` : undefined,
    riskLevel ? `risk ${riskLevel}` : undefined,
    approvalRequestId === undefined ? undefined : `approval ${approvalRequestId}`,
  ];
}

function titleFor(event: RuntimeEvent) {
  if (event.eventType === 'TOOL_DENIED') {
    return '工具不在当前 Agent 版本范围内';
  }
  const stepName = textPayload(event, 'stepName');
  if (event.eventType === 'STEP_STARTED' && stepName) {
    return `正在执行：${stepName}`;
  }
  if (event.eventType === 'STEP_COMPLETED' && stepName) {
    return `已完成：${stepName}`;
  }
  const artifactName = textPayload(event, 'artifactName');
  if (event.eventType === 'ASSISTANT_ARTIFACT' && artifactName) {
    return `已生成：${artifactName}`;
  }
  return event.title || eventTitleMap[event.eventType] || event.eventType;
}

function statusFor(event: RuntimeEvent) {
  if (event.eventType === 'TOOL_DENIED') {
    return 'error';
  }
  if (event.eventType === 'STEP_COMPLETED' && textPayload(event, 'status') === 'warning') {
    return 'waiting';
  }
  return eventStatusMap[event.eventType] || 'default';
}

export function toUserEvent(event: RuntimeEvent): UserRuntimeEvent {
  const errorMessage =
    typeof event.payload?.errorMessage === 'string'
      ? event.payload.errorMessage
      : typeof event.payload?.error === 'string'
        ? event.payload.error
        : undefined;
  const meta = [
    event.sequence === undefined ? undefined : `#${event.sequence}`,
    event.stepId === undefined ? undefined : `step ${event.stepId}`,
    ...toolMetadata(event),
  ]
    .filter(Boolean)
    .join(' · ');
  return {
    status: statusFor(event),
    title: titleFor(event),
    description: errorMessage || workflowDescription(event) || event.message,
    meta: meta || undefined,
  };
}

function workflowDescription(event: RuntimeEvent) {
  if (event.eventType === 'TOOL_DENIED') {
    const agentVersionId = event.payload?.agentVersionId;
    const versionText = typeof agentVersionId === 'number' ? `AgentVersion ${agentVersionId}` : '当前 AgentVersion';
    return `${versionText} 未授权调用该工具`;
  }
  if (event.eventType === 'TOOL_BLOCKED' || event.eventType === 'TOOL_CALL_BLOCKED') {
    const approvalRequestId = numberPayload(event, 'approvalRequestId');
    const reason = textPayload(event, 'reason');
    if (approvalRequestId !== undefined && reason) {
      return `Approval ${approvalRequestId} required: ${reason}`;
    }
    if (approvalRequestId !== undefined) {
      return `Approval ${approvalRequestId} required`;
    }
    return reason;
  }
  if (event.eventType === 'ASSISTANT_PLAN_CREATED') {
    const stepCount = workflowStepCount(event);
    return stepCount === undefined ? undefined : `已规划 ${stepCount} 个执行步骤`;
  }
  if (event.eventType === 'MEMORY_CONTEXT') {
    const memoryCount = event.payload?.memoryCount;
    return typeof memoryCount === 'number' ? `已注入 ${memoryCount} 条已确认记忆` : undefined;
  }
  if (event.eventType === 'STEP_COMPLETED' && textPayload(event, 'status') === 'warning') {
    return '步骤已完成，但结果需要复核';
  }
  if (event.eventType === 'ASSISTANT_TASK_SUSPENDED') {
    const reason = textPayload(event, 'reason');
    return reason ? `挂起原因：${reason}` : undefined;
  }
  return undefined;
}
