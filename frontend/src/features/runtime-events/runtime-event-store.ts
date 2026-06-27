import { create } from 'zustand';
import type { RuntimeEvent } from '../../types/runtime-event';

interface RuntimeEventState {
  eventsByTaskId: Record<number, RuntimeEvent[]>;
  lastEventIdByTaskId: Record<number, string>;
  appendEvent: (taskId: number, event: RuntimeEvent) => void;
  clearEvents: (taskId?: number) => void;
}

export const useRuntimeEventStore = create<RuntimeEventState>((set) => ({
  eventsByTaskId: {},
  lastEventIdByTaskId: {},
  appendEvent: (taskId, event) =>
    set((state) => {
      const currentEvents = state.eventsByTaskId[taskId] || [];
      if (
        currentEvents.some((item) =>
          event.sequence !== undefined && item.sequence !== undefined ? item.sequence === event.sequence : item.id === event.id,
        )
      ) {
        return state;
      }
      const nextEvents = [...currentEvents, event].sort((left, right) => {
        if (left.sequence !== undefined && right.sequence !== undefined) {
          return left.sequence - right.sequence;
        }
        if (left.sequence !== undefined) {
          return -1;
        }
        if (right.sequence !== undefined) {
          return 1;
        }
        return 0;
      });
      const lastSequencedEvent = [...nextEvents].reverse().find((item) => item.lastEventId || item.sequence !== undefined);
      const lastEventId =
        lastSequencedEvent?.lastEventId ||
        (lastSequencedEvent?.sequence === undefined ? state.lastEventIdByTaskId[taskId] : String(lastSequencedEvent.sequence));
      return {
        eventsByTaskId: {
          ...state.eventsByTaskId,
          [taskId]: nextEvents,
        },
        lastEventIdByTaskId: {
          ...state.lastEventIdByTaskId,
          ...(lastEventId ? { [taskId]: lastEventId } : {}),
        },
      };
    }),
  clearEvents: (taskId) =>
    set((state) => {
      if (taskId === undefined) {
        return { eventsByTaskId: {}, lastEventIdByTaskId: {} };
      }
      const { [taskId]: _removed, ...remainingEvents } = state.eventsByTaskId;
      const { [taskId]: _removedLastEventId, ...remainingLastEventIds } = state.lastEventIdByTaskId;
      return { eventsByTaskId: remainingEvents, lastEventIdByTaskId: remainingLastEventIds };
    }),
}));
