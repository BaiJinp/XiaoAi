import { beforeEach, describe, expect, it } from 'vitest';
import type { RuntimeEvent } from '../../types/runtime-event';
import { useRuntimeEventStore } from './runtime-event-store';

function createRuntimeEvent(id: string, taskId: number): RuntimeEvent {
  return {
    id,
    tenantId: 100,
    taskId,
    runId: 200,
    eventType: 'RUN_STARTED',
  };
}

describe('useRuntimeEventStore', () => {
  beforeEach(() => {
    useRuntimeEventStore.getState().clearEvents();
  });

  it('stores runtime events by task id and deduplicates by event id', () => {
    const first = createRuntimeEvent('event-1', 10);
    const duplicate = createRuntimeEvent('event-1', 10);
    const second = createRuntimeEvent('event-2', 10);
    const otherTask = createRuntimeEvent('event-3', 11);

    useRuntimeEventStore.getState().appendEvent(10, first);
    useRuntimeEventStore.getState().appendEvent(10, duplicate);
    useRuntimeEventStore.getState().appendEvent(10, second);
    useRuntimeEventStore.getState().appendEvent(11, otherTask);

    expect(useRuntimeEventStore.getState().eventsByTaskId[10]).toEqual([first, second]);
    expect(useRuntimeEventStore.getState().eventsByTaskId[11]).toEqual([otherTask]);
  });

  it('orders events by sequence and keeps last event id per task', () => {
    const second = { ...createRuntimeEvent('event-2', 10), sequence: 2, lastEventId: '2' };
    const first = { ...createRuntimeEvent('event-1', 10), sequence: 1, lastEventId: '1' };
    const duplicate = { ...createRuntimeEvent('event-duplicate', 10), sequence: 2, lastEventId: '2' };

    useRuntimeEventStore.getState().appendEvent(10, second);
    useRuntimeEventStore.getState().appendEvent(10, first);
    useRuntimeEventStore.getState().appendEvent(10, duplicate);

    expect(useRuntimeEventStore.getState().eventsByTaskId[10]).toEqual([first, second]);
    expect(useRuntimeEventStore.getState().lastEventIdByTaskId[10]).toBe('2');
  });
});
