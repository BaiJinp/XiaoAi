import { describe, expect, it } from 'vitest';
import type { RuntimeEvent } from '../../../types/runtime-event';
import { summarizeThreadRuntime } from './threadRuntimeSummary';

function event(id: string, eventType: RuntimeEvent['eventType'], payload?: RuntimeEvent['payload']): RuntimeEvent {
  return {
    id,
    tenantId: 100,
    taskId: 88,
    runId: 99,
    eventType,
    payload,
  };
}

describe('summarizeThreadRuntime', () => {
  it('uses the latest tool event as thread runtime summary', () => {
    expect(
      summarizeThreadRuntime([
        event('1', 'RUN_STARTED'),
        event('2', 'TOOL_CALL', { toolCode: 'controlled.http.project-query', toolCallIndex: 0 }),
        event('3', 'TOOL_BLOCKED', {
          toolCode: 'controlled.cli.project-update',
          toolCallIndex: 1,
          riskLevel: 'high',
          approvalRequestId: 101,
          reason: 'requires_human_approval',
        }),
      ]),
    ).toEqual({
      eventType: 'TOOL_BLOCKED',
      label: 'Tool blocked',
      status: 'warning',
      toolCode: 'controlled.cli.project-update',
      toolCallIndex: 1,
      riskLevel: 'high',
      approvalRequestId: 101,
      reason: 'requires_human_approval',
    });
  });

  it('returns undefined when no tool event exists', () => {
    expect(summarizeThreadRuntime([event('1', 'RUN_STARTED')])).toBeUndefined();
  });
});
