import type { RuntimeEvent, RuntimeEventType } from '../types/runtime-event';

export interface RuntimeEventHandlers {
  onEvent: (event: RuntimeEvent) => void;
  onError?: () => void;
}

interface BackendTaskEvent {
  id: number | string;
  tenantId: number;
  userId?: number;
  agentId?: number;
  taskId: number;
  runId: number;
  stepId?: number;
  traceId?: string;
  sequence?: number;
  eventType: RuntimeEventType;
  eventSummary?: string;
  payloadJson?: string;
  occurredAt?: string;
}

const runtimeEventTypes: RuntimeEventType[] = [
  'RUN_STARTED',
  'PLAN_CREATED',
  'ASSISTANT_PLAN_CREATED',
  'STEP_STARTED',
  'STEP_COMPLETED',
  'KNOWLEDGE_RETRIEVE',
  'KNOWLEDGE_RESULT',
  'KNOWLEDGE_RETRIEVED',
  'MODEL_CALL',
  'MODEL_CALLED',
  'MODEL_RESULT',
  'TOOL_CALL',
  'TOOL_CALL_REQUESTED',
  'TOOL_CALL_BLOCKED',
  'TOOL_CALL_COMPLETED',
  'TOOL_BLOCKED',
  'TOOL_DENIED',
  'TOOL_FAILED',
  'TOOL_RESULT',
  'ASSISTANT_ARTIFACT',
  'ASSISTANT_TASK_COMPLETED',
  'ASSISTANT_TASK_SUSPENDED',
  'APPROVAL_REQUIRED',
  'APPROVAL_APPROVED',
  'APPROVAL_REJECTED',
  'RUN_SUSPENDED',
  'RUN_RESUMED',
  'RUN_COMPLETED',
  'RUN_FAILED',
  'RUN_CANCELLED',
];

function parsePayload(payloadJson?: string) {
  if (!payloadJson) {
    return undefined;
  }
  try {
    return JSON.parse(payloadJson) as Record<string, unknown>;
  } catch {
    return { raw: payloadJson };
  }
}

function normalizeRuntimeEvent(rawEvent: RuntimeEvent | BackendTaskEvent): RuntimeEvent {
  if ('payloadJson' in rawEvent || 'eventSummary' in rawEvent || 'occurredAt' in rawEvent) {
    return {
      id: String(rawEvent.id),
      tenantId: rawEvent.tenantId,
      userId: rawEvent.userId,
      agentId: rawEvent.agentId,
      taskId: rawEvent.taskId,
      runId: rawEvent.runId,
      stepId: rawEvent.stepId,
      traceId: rawEvent.traceId,
      sequence: rawEvent.sequence ?? Number(rawEvent.id),
      lastEventId: String(rawEvent.sequence ?? rawEvent.id),
      eventType: rawEvent.eventType,
      message: rawEvent.eventSummary,
      payload: parsePayload(rawEvent.payloadJson),
      createTime: rawEvent.occurredAt,
    };
  }
  return rawEvent as RuntimeEvent;
}

export function subscribeRuntimeEvents(taskId: number, handlers: RuntimeEventHandlers, lastEventId?: string) {
  const params = new URLSearchParams({
    tenantId: String(import.meta.env.VITE_DEV_TENANT_ID || '100'),
    userId: String(import.meta.env.VITE_DEV_USER_ID || '1000'),
  });
  if (lastEventId) {
    params.set('lastEventId', lastEventId);
  }
  const source = new EventSource(`/api/v1/tasks/${taskId}/events/stream?${params.toString()}`);
  let receivedEvent = false;
  const handleEventData = (data: string) => {
    receivedEvent = true;
    handlers.onEvent(normalizeRuntimeEvent(JSON.parse(data) as RuntimeEvent | BackendTaskEvent));
  };
  const handleMessage = (event: MessageEvent<string>) => {
    handleEventData(event.data);
  };
  source.onmessage = handleMessage;
  runtimeEventTypes.forEach((eventType) => {
    source.addEventListener(eventType, handleMessage as EventListener);
  });
  source.onerror = () => {
    if (receivedEvent) {
      source.close();
      return;
    }
    handlers.onError?.();
  };
  return () => source.close();
}
