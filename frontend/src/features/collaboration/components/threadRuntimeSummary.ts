import type { RuntimeEvent } from '../../../types/runtime-event';

export interface ThreadRuntimeSummary {
  eventType: RuntimeEvent['eventType'];
  label: string;
  status: 'process' | 'success' | 'warning' | 'error' | 'default';
  toolCode?: string;
  toolCallIndex?: number;
  riskLevel?: string;
  approvalRequestId?: number;
  reason?: string;
}

const toolEventTypes = new Set<RuntimeEvent['eventType']>([
  'TOOL_CALL',
  'TOOL_RESULT',
  'TOOL_BLOCKED',
  'TOOL_DENIED',
  'TOOL_FAILED',
]);

function textPayload(event: RuntimeEvent, fieldName: string) {
  const value = event.payload?.[fieldName];
  return typeof value === 'string' ? value : undefined;
}

function numberPayload(event: RuntimeEvent, fieldName: string) {
  const value = event.payload?.[fieldName];
  return typeof value === 'number' ? value : undefined;
}

function labelFor(eventType: RuntimeEvent['eventType']) {
  switch (eventType) {
    case 'TOOL_CALL':
      return 'Tool calling';
    case 'TOOL_RESULT':
      return 'Tool completed';
    case 'TOOL_BLOCKED':
      return 'Tool blocked';
    case 'TOOL_DENIED':
      return 'Tool denied';
    case 'TOOL_FAILED':
      return 'Tool failed';
    default:
      return eventType;
  }
}

function statusFor(eventType: RuntimeEvent['eventType']): ThreadRuntimeSummary['status'] {
  switch (eventType) {
    case 'TOOL_CALL':
      return 'process';
    case 'TOOL_RESULT':
      return 'success';
    case 'TOOL_BLOCKED':
      return 'warning';
    case 'TOOL_DENIED':
    case 'TOOL_FAILED':
      return 'error';
    default:
      return 'default';
  }
}

export function summarizeThreadRuntime(events: RuntimeEvent[]): ThreadRuntimeSummary | undefined {
  const latestToolEvent = [...events].reverse().find((event) => toolEventTypes.has(event.eventType));
  if (!latestToolEvent) {
    return undefined;
  }

  return {
    eventType: latestToolEvent.eventType,
    label: labelFor(latestToolEvent.eventType),
    status: statusFor(latestToolEvent.eventType),
    toolCode: textPayload(latestToolEvent, 'toolCode'),
    toolCallIndex: numberPayload(latestToolEvent, 'toolCallIndex'),
    riskLevel: textPayload(latestToolEvent, 'riskLevel'),
    approvalRequestId: numberPayload(latestToolEvent, 'approvalRequestId'),
    reason: textPayload(latestToolEvent, 'reason') || textPayload(latestToolEvent, 'errorMessage'),
  };
}
