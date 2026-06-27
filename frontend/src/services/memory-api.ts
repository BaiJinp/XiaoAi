import { http, unwrap } from './http';
import type { PageResult } from './task-api';
import type { AgentMemory, AgentMemoryPageParams, CreateAgentMemoryRequest } from '../types/memory';

function compactParams(params: AgentMemoryPageParams) {
  return Object.fromEntries(Object.entries(params).filter(([, value]) => value !== undefined && value !== ''));
}

export function listAgentMemories(params: AgentMemoryPageParams = {}) {
  return unwrap<PageResult<AgentMemory>>(
    http.get('/v1/agent-memories', {
      params: compactParams(params),
    }),
  );
}

export function createAgentMemory(request: CreateAgentMemoryRequest) {
  return unwrap<AgentMemory>(http.post('/v1/agent-memories', request));
}

export function archiveAgentMemory(memoryId: number) {
  return unwrap<AgentMemory>(http.post(`/v1/agent-memories/${memoryId}/archive`));
}
