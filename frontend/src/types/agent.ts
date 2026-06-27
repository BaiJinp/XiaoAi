export type AgentStatus = 'draft' | 'published' | 'archived';

export interface Agent {
  id: number;
  tenantId?: number;
  agentCode?: string;
  agentName?: string;
  agentType?: string;
  name?: string;
  roleDescription?: string;
  description?: string;
  status?: AgentStatus;
  currentVersionId?: number;
  latestStableVersionId?: number;
  createTime?: string;
  updateTime?: string;
}

export interface AgentPageResponse {
  pageNo: number;
  pageSize: number;
  total: number;
  records: Agent[];
}

export interface CreateAgentDraftRequest {
  name: string;
  description?: string;
  roleDescription?: string;
  responsibilities?: string;
  boundaries?: string;
  modelProviderId?: number;
  modelConfigId?: number;
}

export interface AgentDraftResponse {
  agentId: number;
  agentCode?: string;
  status: AgentStatus;
}

export interface CreateAgentVersionRequest {
  rolePrompt?: string;
  responsibilityText?: string;
  boundaryText?: string;
  configJson?: string;
  knowledgeScopeJson?: string;
  toolScopeJson?: string;
  modelPolicyJson?: string;
  toolPolicyJson?: string;
  contextPolicyJson?: string;
  memoryPolicyJson?: string;
  orchestrationPolicyJson?: string;
  permissionPolicyJson?: string;
  budgetPolicyJson?: string;
  toolIds?: number[];
}

export interface ReplaceAgentVersionToolsRequest {
  toolIds?: number[];
}

export interface AgentVersionResponse {
  agentVersionId: number;
  versionNo: string;
  versionStatus: string;
  runtimeSnapshotJson?: string;
  modelPolicyJson?: string;
  toolPolicyJson?: string;
  contextPolicyJson?: string;
  memoryPolicyJson?: string;
  orchestrationPolicyJson?: string;
  toolIds?: number[];
}

export interface MainAgentPreviewResponse {
  name: string;
  role: string;
  responsibilities: string[];
  scenarios: string[];
  requiredTools: string[];
  requiredKnowledge: string[];
}

export interface CreateMainAgentRequest {
  name?: string;
  ownerUserId?: number;
}

export interface MainAgentSetupResponse {
  agentId: number;
  agentName: string;
  status: string;
}

export interface MainAgentTrialRunRequest {
  agentId?: number;
  input: string;
  assistantTaskType?: 'meeting_minutes' | 'weekly_report' | 'risk_analysis';
}

export interface MainAgentTrialRunResponse {
  taskId: number;
  runId: number;
  status: string;
  events: unknown[];
}
