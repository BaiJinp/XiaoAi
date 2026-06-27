import type { RuntimeEvent } from './runtime-event';

export type TaskStatus = 'pending' | 'running' | 'suspended' | 'completed' | 'failed' | 'cancelled';

export interface Task {
  id: number;
  tenantId?: number;
  taskCode?: string;
  agentId?: number;
  agentVersionId?: number;
  title?: string;
  input?: string;
  status: TaskStatus;
  currentRunId?: number;
  resultSummary?: string;
  createTime?: string;
  updateTime?: string;
}

export interface CreateTaskRequest {
  agentId: number;
  input: string;
  channel: 'web';
}

export interface TaskCreateResponse {
  taskId: number;
  taskCode?: string;
  taskStatus: TaskStatus;
}

export interface BackendTaskCreateResponse {
  taskId: number;
  taskCode?: string;
  status: TaskStatus;
}

export interface StartTaskRequest {
  agentId?: number;
  assistantTaskType?: string;
}

export interface TaskRunResponse {
  taskId: number;
  runId: number;
  status: TaskStatus;
  events?: RuntimeEvent[];
}
