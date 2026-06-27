import type { RuntimeEvent } from '../../types/runtime-event';

const TOOL_AUDIT_CSV_HEADERS = [
  'eventId',
  'taskId',
  'runId',
  'toolId',
  'toolCode',
  'agentVersionId',
  'reason',
  'occurredAt',
  'message',
];

function csvValue(value: unknown): string {
  if (value === null || value === undefined) {
    return '';
  }

  const text = String(value);
  if (/[",\r\n]/.test(text)) {
    return `"${text.replace(/"/g, '""')}"`;
  }
  return text;
}

export function buildToolAuditCsv(events: RuntimeEvent[]): string {
  const rows = events.map((event) =>
    [
      event.id,
      event.taskId,
      event.runId,
      event.payload?.toolId,
      event.payload?.toolCode,
      event.payload?.agentVersionId,
      event.payload?.reason,
      event.createTime,
      event.message,
    ]
      .map(csvValue)
      .join(','),
  );

  return [TOOL_AUDIT_CSV_HEADERS.join(','), ...rows].join('\n');
}
