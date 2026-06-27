import { afterEach, describe, expect, it, vi } from 'vitest';
import type { RuntimeEvent } from '../types/runtime-event';
import { subscribeRuntimeEvents } from './runtime-sse';

class EventSourceMock {
  static instances: EventSourceMock[] = [];

  onmessage: ((event: MessageEvent<string>) => void) | null = null;
  onerror: (() => void) | null = null;
  eventListeners = new Map<string, Array<(event: MessageEvent<string>) => void>>();
  addEventListener = vi.fn((eventType: string, listener: EventListener) => {
    const listeners = this.eventListeners.get(eventType) || [];
    listeners.push(listener as (event: MessageEvent<string>) => void);
    this.eventListeners.set(eventType, listeners);
  });
  close = vi.fn();

  constructor(public readonly url: string) {
    EventSourceMock.instances.push(this);
  }

  emit(eventType: string, data: unknown) {
    this.eventListeners.get(eventType)?.forEach((listener) => {
      listener({ data: JSON.stringify(data) } as MessageEvent<string>);
    });
  }
}

describe('subscribeRuntimeEvents', () => {
  afterEach(() => {
    EventSourceMock.instances = [];
    vi.unstubAllGlobals();
  });

  it('normalizes backend task event payload from SSE message', () => {
    vi.stubGlobal('EventSource', EventSourceMock);
    const onEvent = vi.fn();

    subscribeRuntimeEvents(12, { onEvent });

    EventSourceMock.instances[0].onmessage?.({
      data: JSON.stringify({
        id: 88,
        tenantId: 100,
        taskId: 12,
        runId: 22,
        userId: 1000,
        agentId: 7,
        traceId: 'trace-1',
        sequence: 12,
        eventType: 'APPROVAL_REQUIRED',
        eventSummary: 'Approval required',
        payloadJson: '{"approvalRequestId":99}',
        occurredAt: '2026-06-09T10:00:00Z',
      }),
    } as MessageEvent<string>);

    expect(onEvent).toHaveBeenCalledWith({
      id: '88',
      tenantId: 100,
      taskId: 12,
      runId: 22,
      userId: 1000,
      agentId: 7,
      traceId: 'trace-1',
      sequence: 12,
      lastEventId: '12',
      eventType: 'APPROVAL_REQUIRED',
      message: 'Approval required',
      payload: { approvalRequestId: 99 },
      createTime: '2026-06-09T10:00:00Z',
    });
  });

  it('subscribes runtime event stream and closes EventSource', () => {
    vi.stubGlobal('EventSource', EventSourceMock);
    const onEvent = vi.fn();
    const onError = vi.fn();

    const unsubscribe = subscribeRuntimeEvents(12, { onEvent, onError });

    expect(EventSourceMock.instances[0].url).toBe('/api/v1/tasks/12/events/stream?tenantId=100&userId=1000');

    const event: RuntimeEvent = {
      id: 'event-1',
      tenantId: 100,
      taskId: 12,
      runId: 22,
      eventType: 'RUN_STARTED',
    };
    EventSourceMock.instances[0].onmessage?.({ data: JSON.stringify(event) } as MessageEvent<string>);
    EventSourceMock.instances[0].onerror?.();
    unsubscribe();

    expect(onEvent).toHaveBeenCalledWith(event);
    expect(onError).not.toHaveBeenCalled();
    expect(EventSourceMock.instances[0].close).toHaveBeenCalled();
  });

  it('receives backend named SSE events', () => {
    vi.stubGlobal('EventSource', EventSourceMock);
    const onEvent = vi.fn();

    subscribeRuntimeEvents(12, { onEvent });

    EventSourceMock.instances[0].emit('ASSISTANT_ARTIFACT', {
      id: 89,
      tenantId: 100,
      taskId: 12,
      runId: 22,
      eventType: 'ASSISTANT_ARTIFACT',
      eventSummary: 'Artifact generated',
      payloadJson: '{"artifactType":"markdown"}',
    });

    expect(EventSourceMock.instances[0].addEventListener).toHaveBeenCalledWith('ASSISTANT_ARTIFACT', expect.any(Function));
    expect(onEvent).toHaveBeenCalledWith(
      expect.objectContaining({
        id: '89',
        eventType: 'ASSISTANT_ARTIFACT',
        message: 'Artifact generated',
        payload: { artifactType: 'markdown' },
      }),
    );
  });

  it('subscribes runtime stream with lastEventId for reconnect', () => {
    vi.stubGlobal('EventSource', EventSourceMock);

    subscribeRuntimeEvents(12, { onEvent: vi.fn() }, '12');

    expect(EventSourceMock.instances[0].url).toBe('/api/v1/tasks/12/events/stream?tenantId=100&userId=1000&lastEventId=12');
  });

  it('receives tool denied named SSE events', () => {
    vi.stubGlobal('EventSource', EventSourceMock);
    const onEvent = vi.fn();

    subscribeRuntimeEvents(12, { onEvent });

    EventSourceMock.instances[0].emit('TOOL_DENIED', {
      id: 91,
      tenantId: 100,
      taskId: 12,
      runId: 22,
      eventType: 'TOOL_DENIED',
      eventSummary: 'Tool denied',
      payloadJson: '{"toolId":10,"agentVersionId":11,"reason":"tool_not_in_agent_version_scope"}',
    });

    expect(EventSourceMock.instances[0].addEventListener).toHaveBeenCalledWith('TOOL_DENIED', expect.any(Function));
    expect(onEvent).toHaveBeenCalledWith(
      expect.objectContaining({
        id: '91',
        eventType: 'TOOL_DENIED',
        message: 'Tool denied',
        payload: { toolId: 10, agentVersionId: 11, reason: 'tool_not_in_agent_version_scope' },
      }),
    );
  });

  it('closes stream without reporting error after receiving replayed events', () => {
    vi.stubGlobal('EventSource', EventSourceMock);
    const onEvent = vi.fn();
    const onError = vi.fn();

    subscribeRuntimeEvents(12, { onEvent, onError });

    EventSourceMock.instances[0].emit('RUN_STARTED', {
      id: 90,
      tenantId: 100,
      taskId: 12,
      runId: 22,
      eventType: 'RUN_STARTED',
    });
    EventSourceMock.instances[0].onerror?.();

    expect(onEvent).toHaveBeenCalled();
    expect(onError).not.toHaveBeenCalled();
    expect(EventSourceMock.instances[0].close).toHaveBeenCalled();
  });
});
