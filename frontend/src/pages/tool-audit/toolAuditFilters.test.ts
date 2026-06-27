import { describe, expect, it } from 'vitest';
import type { RuntimeEvent } from '../../types/runtime-event';
import { filterToolAuditEvents } from './toolAuditFilters';

function createEvent(event: Partial<RuntimeEvent> & Pick<RuntimeEvent, 'id'>): RuntimeEvent {
  return {
    tenantId: 100,
    taskId: 10,
    runId: 20,
    eventType: 'TOOL_DENIED',
    ...event,
  };
}

describe('filterToolAuditEvents', () => {
  const events: RuntimeEvent[] = [
    createEvent({
      id: 'event-1',
      taskId: 101,
      runId: 201,
      message: 'Tool denied by policy',
      payload: {
        toolCode: 'Controlled.Cli.CreateTask',
        reason: 'tool_not_in_agent_version_scope',
        agentVersionId: 301,
      },
    }),
    createEvent({
      id: 'event-2',
      taskId: 102,
      runId: 202,
      message: 'Manual review required',
      payload: {
        toolCode: 'knowledge.search',
        reason: 'requires_human_approval',
        agentVersionId: '302',
      },
    }),
  ];

  it('returns all events when no filters are provided', () => {
    expect(filterToolAuditEvents(events, {})).toEqual(events);
  });

  it('matches keyword fields case-insensitively as strings', () => {
    expect(filterToolAuditEvents(events, { keyword: 'controlled.cli' }).map((event) => event.id)).toEqual(['event-1']);
    expect(filterToolAuditEvents(events, { keyword: 'HUMAN_APPROVAL' }).map((event) => event.id)).toEqual(['event-2']);
    expect(filterToolAuditEvents(events, { keyword: 'denied BY' }).map((event) => event.id)).toEqual(['event-1']);
    expect(filterToolAuditEvents(events, { keyword: '102' }).map((event) => event.id)).toEqual(['event-2']);
    expect(filterToolAuditEvents(events, { keyword: '202' }).map((event) => event.id)).toEqual(['event-2']);
    expect(filterToolAuditEvents(events, { keyword: '301' }).map((event) => event.id)).toEqual(['event-1']);
  });

  it('filters taskId, runId, and agentVersionId by exact string value', () => {
    expect(filterToolAuditEvents(events, { taskId: 101 }).map((event) => event.id)).toEqual(['event-1']);
    expect(filterToolAuditEvents(events, { runId: '202' }).map((event) => event.id)).toEqual(['event-2']);
    expect(filterToolAuditEvents(events, { agentVersionId: 302 }).map((event) => event.id)).toEqual(['event-2']);
    expect(filterToolAuditEvents(events, { taskId: '10' })).toEqual([]);
  });

  it('combines filters with AND semantics', () => {
    expect(filterToolAuditEvents(events, { keyword: 'review', agentVersionId: '302' }).map((event) => event.id)).toEqual([
      'event-2',
    ]);
    expect(filterToolAuditEvents(events, { keyword: 'review', agentVersionId: '301' })).toEqual([]);
  });
});
