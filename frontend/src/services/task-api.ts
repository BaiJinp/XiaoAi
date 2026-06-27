import { http, unwrap } from './http';
import type {
  BackendTaskCreateResponse,
  CreateTaskRequest,
  StartTaskRequest,
  Task,
  TaskCreateResponse,
  TaskRunResponse,
} from '../types/task';
import type { RuntimeEvent, RuntimeEventType } from '../types/runtime-event';
import type { ArtifactType, TaskArtifact } from '../types/artifact';

interface BackendTask {
  id: number;
  tenantId?: number;
  taskCode?: string;
  agentId?: number;
  agentVersionId?: number;
  title?: string;
  inputText?: string;
  status: Task['status'];
  currentRunId?: number;
  resultSummary?: string;
  createdAt?: string;
  updatedAt?: string;
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

interface BackendTaskArtifact {
  id: number;
  taskId: number;
  artifactType: ArtifactType;
  artifactName?: string;
  contentText?: string;
  metadata?: TaskArtifact['metadata'];
  metadataJson?: string;
  createdAt?: string;
}

export interface ListTaskEventsByTypeParams {
  limit?: number;
  pageNo?: number;
  pageSize?: number;
  taskId?: string | number;
  runId?: string | number;
  agentVersionId?: string | number;
  keyword?: string;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  pageNo?: number;
  pageSize?: number;
}

function compactParams(params: ListTaskEventsByTypeParams) {
  return Object.fromEntries(Object.entries(params).filter(([, value]) => value !== undefined && value !== ''));
}

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

function normalizeTask(task: BackendTask): Task {
  return {
    id: task.id,
    tenantId: task.tenantId,
    taskCode: task.taskCode,
    agentId: task.agentId,
    agentVersionId: task.agentVersionId,
    title: task.title,
    input: task.inputText,
    status: task.status,
    currentRunId: task.currentRunId,
    resultSummary: task.resultSummary,
    createTime: task.createdAt,
    updateTime: task.updatedAt,
  };
}

function isBackendTaskEvent(event: RuntimeEvent | BackendTaskEvent): event is BackendTaskEvent {
  return typeof event.id !== 'string' || 'eventSummary' in event || 'payloadJson' in event || 'occurredAt' in event;
}

function normalizeTaskEvent(event: RuntimeEvent | BackendTaskEvent): RuntimeEvent {
  if (!isBackendTaskEvent(event)) {
    return event;
  }
  return {
    id: String(event.id),
    tenantId: event.tenantId,
    userId: event.userId,
    agentId: event.agentId,
    taskId: event.taskId,
    runId: event.runId,
    stepId: event.stepId,
    traceId: event.traceId,
    sequence: event.sequence ?? Number(event.id),
    lastEventId: String(event.sequence ?? event.id),
    eventType: event.eventType,
    message: event.eventSummary,
    payload: parsePayload(event.payloadJson),
    createTime: event.occurredAt,
  };
}

function normalizeTaskEventPage(
  eventsResult: Array<RuntimeEvent | BackendTaskEvent> | PageResult<RuntimeEvent | BackendTaskEvent>,
): PageResult<RuntimeEvent> {
  if (Array.isArray(eventsResult)) {
    const records = eventsResult.map(normalizeTaskEvent);
    return {
      records,
      total: records.length,
    };
  }

  return {
    records: eventsResult.records.map(normalizeTaskEvent),
    total: eventsResult.total,
    pageNo: eventsResult.pageNo,
    pageSize: eventsResult.pageSize,
  };
}

function isBackendTaskArtifact(artifact: TaskArtifact | BackendTaskArtifact): artifact is BackendTaskArtifact {
  return !('title' in artifact) || !('content' in artifact) || 'artifactName' in artifact || 'contentText' in artifact;
}

function normalizeArtifact(artifact: TaskArtifact | BackendTaskArtifact): TaskArtifact {
  if (!isBackendTaskArtifact(artifact)) {
    return artifact;
  }
  const metadata =
    artifact.metadata || (artifact.metadataJson ? parsePayload(artifact.metadataJson) : undefined);
  return {
    id: artifact.id,
    taskId: artifact.taskId,
    artifactType: artifact.artifactType,
    title: artifact.artifactName || '任务交付物',
    content: artifact.contentText || '',
    metadata,
    createTime: artifact.createdAt,
  };
}

export async function getTask(taskId: number) {
  const task = await unwrap<BackendTask>(http.get(`/v1/tasks/${taskId}`));
  return normalizeTask(task);
}

export async function createTask(request: CreateTaskRequest): Promise<TaskCreateResponse> {
  const response = await unwrap<BackendTaskCreateResponse>(
    http.post('/v1/tasks', {
      agentId: request.agentId,
      userId: Number(import.meta.env.VITE_DEV_USER_ID || '1000'),
      channelType: request.channel,
      inputText: request.input,
      title: request.input.slice(0, 60),
    }),
  );

  return {
    taskId: response.taskId,
    taskCode: response.taskCode,
    taskStatus: response.status,
  } satisfies TaskCreateResponse;
}

export function startTask(taskId: number, request: StartTaskRequest) {
  return unwrap<TaskRunResponse>(http.post(`/v1/tasks/${taskId}/runs`, request));
}

export async function listTaskEvents(taskId: number, lastEventId?: string) {
  const events = await unwrap<Array<RuntimeEvent | BackendTaskEvent>>(
    http.get(`/v1/tasks/${taskId}/events`, {
      params: lastEventId ? { lastEventId } : undefined,
    }),
  );
  return events.map(normalizeTaskEvent);
}

export async function listTaskEventsByType(
  eventType: RuntimeEventType,
  paramsOrLimit?: number | ListTaskEventsByTypeParams,
) {
  const params = typeof paramsOrLimit === 'number' ? { limit: paramsOrLimit } : compactParams(paramsOrLimit || {});
  const eventsResult = await unwrap<Array<RuntimeEvent | BackendTaskEvent> | PageResult<RuntimeEvent | BackendTaskEvent>>(
    http.get('/v1/task-events', {
      params: {
        eventType,
        ...params,
      },
    }),
  );
  return normalizeTaskEventPage(eventsResult);
}

export async function listTaskArtifacts(taskId: number) {
  const artifacts = await unwrap<Array<TaskArtifact | BackendTaskArtifact>>(http.get(`/v1/tasks/${taskId}/artifacts`));
  return artifacts.map(normalizeArtifact);
}
