import { http, unwrap } from './http';
import type {
  Agent,
  AgentDraftResponse,
  AgentPageResponse,
  AgentVersionResponse,
  CreateAgentDraftRequest,
  CreateAgentVersionRequest,
  CreateMainAgentRequest,
  MainAgentPreviewResponse,
  MainAgentSetupResponse,
  MainAgentTrialRunRequest,
  MainAgentTrialRunResponse,
  ReplaceAgentVersionToolsRequest,
} from '../types/agent';

export function getAgent(agentId: number) {
  return unwrap<Agent>(http.get(`/v1/agents/${agentId}`));
}

export function listAgents(params?: { agentName?: string; status?: string; pageNo?: number; pageSize?: number }) {
  return unwrap<AgentPageResponse>(http.get('/v1/agents', { params }));
}

function joinDescription(request: CreateAgentDraftRequest) {
  return [
    request.roleDescription ? `角色：${request.roleDescription}` : undefined,
    request.responsibilities ? `职责：${request.responsibilities}` : undefined,
    request.boundaries ? `边界：${request.boundaries}` : undefined,
    request.description,
  ]
    .filter(Boolean)
    .join('\n');
}

export function createAgentDraft(request: CreateAgentDraftRequest) {
  return unwrap<AgentDraftResponse>(
    http.post('/v1/agents/drafts', {
      agentName: request.name,
      description: joinDescription(request),
      ownerUserId: Number(import.meta.env.VITE_DEV_USER_ID || '1000'),
      modelProviderId: request.modelProviderId,
      modelConfigId: request.modelConfigId,
    }),
  );
}

export function createAgentVersion(agentId: number, request: CreateAgentVersionRequest) {
  return unwrap<AgentVersionResponse>(http.post(`/v1/agent-versions/agents/${agentId}/versions`, request));
}

export function listAgentVersions(agentId: number) {
  return unwrap<AgentVersionResponse[]>(http.get(`/v1/agent-versions/agents/${agentId}/versions`));
}

export function replaceAgentVersionTools(agentId: number, versionId: number, request: ReplaceAgentVersionToolsRequest) {
  return unwrap<AgentVersionResponse>(http.put(`/v1/agent-versions/agents/${agentId}/versions/${versionId}/tools`, request));
}

export function previewMainAgent() {
  return unwrap<MainAgentPreviewResponse>(http.get('/v1/main-agent/preview'));
}

export function createMainAgentDraft(request: CreateMainAgentRequest) {
  return unwrap<MainAgentSetupResponse>(http.post('/v1/main-agent/draft', request));
}

export function trialRunMainAgent(request: MainAgentTrialRunRequest) {
  return unwrap<MainAgentTrialRunResponse>(http.post('/v1/main-agent/trial-run', request));
}
