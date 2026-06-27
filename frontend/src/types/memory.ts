export interface AgentMemory {
  id: number;
  tenantId?: number;
  memoryCode?: string;
  agentId: number;
  agentVersionId?: number;
  taskId?: number;
  runId?: number;
  sessionId?: number;
  userId?: number;
  memoryType?: string;
  memoryScope?: string;
  summaryText: string;
  sourceText?: string;
  confidence?: string;
  status: 'confirmed' | 'archived';
  policyJson?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateAgentMemoryRequest {
  agentId: number;
  agentVersionId?: number;
  taskId?: number;
  runId?: number;
  sessionId?: number;
  userId?: number;
  memoryType?: string;
  memoryScope?: string;
  summaryText: string;
  sourceText?: string;
  confidence?: string;
  policyJson?: string;
}

export interface AgentMemoryPageParams {
  pageNo?: number;
  pageSize?: number;
  agentId?: number;
  taskId?: number;
  sessionId?: number;
  userId?: number;
  status?: 'confirmed' | 'archived';
}
