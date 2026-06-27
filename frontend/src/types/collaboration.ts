export interface CollaborationSession {
  id: number;
  sessionCode: string;
  templateId?: number;
  rootTaskId?: number;
  strategyType: string;
  goalText: string;
  status: string;
  currentStageCode?: string;
  contextJson?: string;
}

export interface CollaborationSessionResponse {
  sessionId: number;
  sessionCode: string;
  status: string;
}

export interface CreateCollaborationSessionRequest {
  templateId?: number;
  rootTaskId?: number;
  strategyType: string;
  goalText: string;
  contextJson?: string;
}

export interface CollaborationTemplate {
  templateId: number;
  templateCode: string;
  templateName: string;
  domainCode?: string;
  strategyType: string;
  templateJson?: string;
  status: string;
}

export interface AgentRole {
  id: number;
  roleCode: string;
  roleName?: string;
  domainCode?: string;
  description?: string;
  defaultAgentId?: number;
  defaultAgentVersionId?: number;
  status?: string;
}

export interface CollaborationRoleBinding {
  roleId: number;
  roleCode: string;
  roleName?: string;
  domainCode?: string;
  globalDefaultAgentId?: number;
  globalDefaultAgentVersionId?: number;
  bindingId?: number;
  templateId: number;
  bindingScope?: string;
  bindingKey?: string;
  agentId?: number;
  agentVersionId?: number;
  effectiveAgentId?: number;
  effectiveAgentVersionId?: number;
  source?: 'template' | 'role_default' | 'unbound' | string;
  status?: string;
}

export interface UpdateCollaborationRoleBindingRequest {
  agentId?: number | null;
  agentVersionId?: number | null;
  bindingScope?: string;
  bindingKey?: string;
}

export interface UpdateAgentRoleDefaultAgentRequest {
  defaultAgentId?: number | null;
  defaultAgentVersionId?: number | null;
}

export interface SubmitCollaborationPlanRequest {
  generatedByThreadId?: number;
  planJson: string;
}

export interface CollaborationPlan {
  id: number;
  sessionId: number;
  generatedByThreadId?: number;
  planStatus: string;
  validationStatus: string;
  planJson: string;
  validationResultJson?: string;
}

export interface StartCollaborationSessionRequest {
  planId: number;
}

export interface CollaborationStrategyResult {
  status: string;
  message?: string;
  createdThreadCount?: number;
  createdGateCount?: number;
}

export interface AgentThread {
  id: number;
  sessionId: number;
  parentThreadId?: number;
  taskId?: number;
  agentId: number;
  roleId?: number;
  threadCode: string;
  threadName?: string;
  status: string;
  inputArtifactId?: number;
  outputArtifactId?: number;
  contextJson?: string;
}

export interface AgentHandoff {
  id: number;
  sessionId: number;
  fromThreadId?: number;
  toThreadId?: number;
  artifactId: number;
  handoffType: string;
  status: string;
  messageText?: string;
  metadataJson?: string;
}

export interface QualityGate {
  id: number;
  sessionId: number;
  gateCode: string;
  gateName: string;
  gateType: string;
  status: string;
  required: boolean;
  resultJson?: string;
  failReason?: string;
}

export interface UpdateQualityGateRequest {
  resultJson?: string;
  failReason?: string;
}

export interface StartAgentThreadTaskRequest {
  runtimeType?: string;
}

export interface AgentThreadRunResponse {
  taskId: number;
  runId: number;
  runCode?: string;
  status: string;
  runtimeType?: string;
}
