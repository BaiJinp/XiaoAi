import type { RuntimeEvent } from '../../types/runtime-event';

export interface ToolAuditFilters {
  keyword?: string;
  taskId?: string | number;
  runId?: string | number;
  agentVersionId?: string | number;
}

function payloadString(event: RuntimeEvent, fieldName: string) {
  const value = event.payload?.[fieldName];
  if (typeof value === 'string' || typeof value === 'number') {
    return String(value);
  }
  return undefined;
}

function hasFilterValue(value: string | number | undefined) {
  return value !== undefined && String(value) !== '';
}

function matchesExact(value: string | number | undefined, filterValue: string | number | undefined) {
  if (!hasFilterValue(filterValue)) {
    return true;
  }
  return value !== undefined && String(value) === String(filterValue);
}

function matchesKeyword(event: RuntimeEvent, keyword: string | undefined) {
  const normalizedKeyword = keyword?.trim().toLowerCase();
  if (!normalizedKeyword) {
    return true;
  }

  return [
    payloadString(event, 'toolCode'),
    payloadString(event, 'reason'),
    event.message,
    String(event.taskId),
    String(event.runId),
    payloadString(event, 'agentVersionId'),
  ].some((value) => value?.toLowerCase().includes(normalizedKeyword));
}

export function filterToolAuditEvents(events: RuntimeEvent[], filters: ToolAuditFilters) {
  return events.filter(
    (event) =>
      matchesKeyword(event, filters.keyword) &&
      matchesExact(event.taskId, filters.taskId) &&
      matchesExact(event.runId, filters.runId) &&
      matchesExact(payloadString(event, 'agentVersionId'), filters.agentVersionId),
  );
}
