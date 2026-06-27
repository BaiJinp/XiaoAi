import { http, unwrap } from './http';
import type {
  AgentHandoff,
  AgentRole,
  AgentThread,
  CollaborationPlan,
  CollaborationRoleBinding,
  CollaborationSession,
  CollaborationSessionResponse,
  CollaborationStrategyResult,
  CollaborationTemplate,
  CreateCollaborationSessionRequest,
  QualityGate,
  AgentThreadRunResponse,
  StartAgentThreadTaskRequest,
  StartCollaborationSessionRequest,
  SubmitCollaborationPlanRequest,
  UpdateCollaborationRoleBindingRequest,
  UpdateAgentRoleDefaultAgentRequest,
  UpdateQualityGateRequest,
} from '../types/collaboration';

export function listAgentRoles(domainCode?: string) {
  return unwrap<AgentRole[]>(http.get('/v1/agent-roles', domainCode ? { params: { domainCode } } : undefined));
}

export function updateAgentRoleDefaultAgent(roleId: number, request: UpdateAgentRoleDefaultAgentRequest) {
  return unwrap<AgentRole>(http.put(`/v1/agent-roles/${roleId}/default-agent`, request));
}

export function listCollaborationRoleBindings(templateId: number, bindingScope?: string, bindingKey?: string) {
  const params: Record<string, string> = {};
  if (bindingScope) {
    params.bindingScope = bindingScope;
  }
  if (bindingKey) {
    params.bindingKey = bindingKey;
  }
  return unwrap<CollaborationRoleBinding[]>(
    http.get(
      `/v1/collaboration-templates/${templateId}/role-bindings`,
      Object.keys(params).length > 0 ? { params } : undefined,
    ),
  );
}

export function updateCollaborationRoleBinding(
  templateId: number,
  roleCode: string,
  request: UpdateCollaborationRoleBindingRequest,
) {
  return unwrap<CollaborationRoleBinding>(
    http.put(`/v1/collaboration-templates/${templateId}/role-bindings/${roleCode}`, request),
  );
}

export function listCollaborationTemplates(domainCode?: string) {
  return unwrap<CollaborationTemplate[]>(
    http.get('/v1/collaboration-templates', domainCode ? { params: { domainCode } } : undefined),
  );
}

export function getCollaborationTemplate(templateId: number) {
  return unwrap<CollaborationTemplate>(http.get(`/v1/collaboration-templates/${templateId}`));
}

export function createCollaborationSession(request: CreateCollaborationSessionRequest) {
  return unwrap<CollaborationSessionResponse>(http.post('/v1/collaboration-sessions', request));
}

export function getCollaborationSession(sessionId: number) {
  return unwrap<CollaborationSession>(http.get(`/v1/collaboration-sessions/${sessionId}`));
}

export function submitCollaborationPlan(sessionId: number, request: SubmitCollaborationPlanRequest) {
  return unwrap<CollaborationPlan>(http.post(`/v1/collaboration-sessions/${sessionId}/plans`, request));
}

export function listCollaborationPlans(sessionId: number) {
  return unwrap<CollaborationPlan[]>(http.get(`/v1/collaboration-sessions/${sessionId}/plans`));
}

export function startCollaborationSession(sessionId: number, request: StartCollaborationSessionRequest) {
  return unwrap<CollaborationStrategyResult>(http.post(`/v1/collaboration-sessions/${sessionId}/start`, request));
}

export function listAgentThreads(sessionId: number) {
  return unwrap<AgentThread[]>(http.get(`/v1/collaboration-sessions/${sessionId}/threads`));
}

export function startAgentThreadTask(sessionId: number, threadId: number, request: StartAgentThreadTaskRequest = {}) {
  return unwrap<AgentThreadRunResponse>(http.post(`/v1/collaboration-sessions/${sessionId}/threads/${threadId}/start`, request));
}

export function listAgentHandoffs(sessionId: number) {
  return unwrap<AgentHandoff[]>(http.get(`/v1/collaboration-sessions/${sessionId}/handoffs`));
}

export function acceptAgentHandoff(sessionId: number, handoffId: number) {
  return unwrap<void>(http.post(`/v1/collaboration-sessions/${sessionId}/handoffs/${handoffId}/accept`));
}

export function rejectAgentHandoff(sessionId: number, handoffId: number) {
  return unwrap<void>(http.post(`/v1/collaboration-sessions/${sessionId}/handoffs/${handoffId}/reject`));
}

export function listQualityGates(sessionId: number) {
  return unwrap<QualityGate[]>(http.get(`/v1/collaboration-sessions/${sessionId}/gates`));
}

export function passQualityGate(sessionId: number, gateId: number, request: UpdateQualityGateRequest) {
  return unwrap<QualityGate>(http.post(`/v1/collaboration-sessions/${sessionId}/gates/${gateId}/pass`, request));
}

export function failQualityGate(sessionId: number, gateId: number, request: UpdateQualityGateRequest) {
  return unwrap<QualityGate>(http.post(`/v1/collaboration-sessions/${sessionId}/gates/${gateId}/fail`, request));
}
