import { describe, expect, it } from 'vitest';
import type { RuntimeEvent } from '../../types/runtime-event';
import { buildToolAuditCsv } from './toolAuditExport';

function toolDeniedEvent(overrides: Partial<RuntimeEvent> = {}): RuntimeEvent {
  return {
    id: 'evt-1',
    tenantId: 100,
    taskId: 12,
    runId: 22,
    eventType: 'TOOL_DENIED',
    message: 'Tool denied',
    payload: {
      toolId: 10,
      toolCode: 'controlled.cli.create-task',
      agentVersionId: 11,
      reason: 'tool_not_in_agent_version_scope',
    },
    createTime: '2026-06-12T10:00:00Z',
    ...overrides,
  };
}

describe('buildToolAuditCsv', () => {
  it('exports tool audit events with the required headers', () => {
    const csv = buildToolAuditCsv([toolDeniedEvent()]);

    expect(csv).toBe(
      [
        'eventId,taskId,runId,toolId,toolCode,agentVersionId,reason,occurredAt,message',
        'evt-1,12,22,10,controlled.cli.create-task,11,tool_not_in_agent_version_scope,2026-06-12T10:00:00Z,Tool denied',
      ].join('\n'),
    );
  });

  it('escapes commas quotes and newlines in CSV fields', () => {
    const csv = buildToolAuditCsv([
      toolDeniedEvent({
        id: 'evt,"2"',
        message: 'Denied because "scope" failed\nAsk admin',
        payload: {
          toolId: 'tool,10',
          toolCode: 'controlled."cli"',
          agentVersionId: '11',
          reason: 'scope, denied',
        },
      }),
    ]);

    expect(csv).toBe(
      [
        'eventId,taskId,runId,toolId,toolCode,agentVersionId,reason,occurredAt,message',
        '"evt,""2""",12,22,"tool,10","controlled.""cli""",11,"scope, denied",2026-06-12T10:00:00Z,"Denied because ""scope"" failed\nAsk admin"',
      ].join('\n'),
    );
  });

  it('leaves missing optional export values blank', () => {
    const csv = buildToolAuditCsv([
      toolDeniedEvent({
        message: undefined,
        payload: undefined,
        createTime: undefined,
      }),
    ]);

    expect(csv).toBe(
      [
        'eventId,taskId,runId,toolId,toolCode,agentVersionId,reason,occurredAt,message',
        'evt-1,12,22,,,,,,',
      ].join('\n'),
    );
  });
});
