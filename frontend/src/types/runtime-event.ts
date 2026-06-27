export type RuntimeEventType =
  | 'RUN_STARTED'
  | 'PLAN_CREATED'
  | 'ASSISTANT_PLAN_CREATED'
  | 'STEP_STARTED'
  | 'STEP_COMPLETED'
  | 'KNOWLEDGE_RETRIEVE'
  | 'KNOWLEDGE_RESULT'
  | 'KNOWLEDGE_RETRIEVED'
  | 'MEMORY_CONTEXT'
  | 'MODEL_CALL'
  | 'MODEL_CALLED'
  | 'MODEL_RESULT'
  | 'TOOL_CALL'
  | 'TOOL_CALL_REQUESTED'
  | 'TOOL_CALL_BLOCKED'
  | 'TOOL_CALL_COMPLETED'
  | 'TOOL_BLOCKED'
  | 'TOOL_DENIED'
  | 'TOOL_FAILED'
  | 'TOOL_RESULT'
  | 'ASSISTANT_ARTIFACT'
  | 'ASSISTANT_TASK_COMPLETED'
  | 'ASSISTANT_TASK_SUSPENDED'
  | 'APPROVAL_REQUIRED'
  | 'APPROVAL_APPROVED'
  | 'APPROVAL_REJECTED'
  | 'RUN_SUSPENDED'
  | 'RUN_RESUMED'
  | 'RUN_COMPLETED'
  | 'RUN_FAILED'
  | 'RUN_CANCELLED';

export interface RuntimeEvent {
  id: string;
  tenantId: number;
  userId?: number;
  agentId?: number;
  taskId: number;
  runId: number;
  stepId?: number;
  traceId?: string;
  sequence?: number;
  lastEventId?: string;
  eventType: RuntimeEventType;
  title?: string;
  message?: string;
  payload?: Record<string, unknown>;
  createTime?: string;
}
