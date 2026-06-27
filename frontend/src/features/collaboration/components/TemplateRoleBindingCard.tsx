import { Button, Card, Select, Space, Table, Tag, Typography, message } from 'antd';
import { useEffect, useState } from 'react';
import { listAgentVersions, listAgents } from '../../../services/agent-api';
import {
  listCollaborationRoleBindings,
  updateCollaborationRoleBinding,
} from '../../../services/collaboration-api';
import type { Agent, AgentPageResponse, AgentVersionResponse } from '../../../types/agent';
import type {
  CollaborationRoleBinding,
  UpdateCollaborationRoleBindingRequest,
} from '../../../types/collaboration';

interface TemplateRoleBindingCardProps {
  templateId?: number;
  onListBindings?: (templateId: number) => Promise<CollaborationRoleBinding[]>;
  onListAgents?: () => Promise<AgentPageResponse>;
  onListVersions?: (agentId: number) => Promise<AgentVersionResponse[]>;
  onUpdateBinding?: (
    templateId: number,
    roleCode: string,
    request: UpdateCollaborationRoleBindingRequest,
  ) => Promise<CollaborationRoleBinding>;
}

interface DraftBinding {
  agentId?: number;
  versionId?: number;
}

function agentLabel(agent: Agent): string {
  return agent.agentName || agent.name || agent.agentCode || `Agent #${agent.id}`;
}

function roleLabel(binding: CollaborationRoleBinding): string {
  return binding.roleName ? `${binding.roleName} (${binding.roleCode})` : binding.roleCode;
}

function recommendedVersionId(agent?: Agent): number | undefined {
  return agent?.latestStableVersionId ?? agent?.currentVersionId;
}

export function TemplateRoleBindingCard({
  templateId,
  onListBindings = listCollaborationRoleBindings,
  onListAgents = () => listAgents({ status: 'published', pageNo: 1, pageSize: 100 }),
  onListVersions = listAgentVersions,
  onUpdateBinding = updateCollaborationRoleBinding,
}: TemplateRoleBindingCardProps) {
  const [bindings, setBindings] = useState<CollaborationRoleBinding[]>([]);
  const [agents, setAgents] = useState<Agent[]>([]);
  const [drafts, setDrafts] = useState<Record<string, DraftBinding>>({});
  const [versionsByAgent, setVersionsByAgent] = useState<Record<number, AgentVersionResponse[]>>({});
  const [loading, setLoading] = useState(false);
  const [savingRoleCode, setSavingRoleCode] = useState<string>();
  const [loadingVersionAgentId, setLoadingVersionAgentId] = useState<number>();
  const [messageApi, contextHolder] = message.useMessage();

  async function refresh() {
    if (!templateId) {
      setBindings([]);
      setDrafts({});
      return;
    }
    setLoading(true);
    try {
      const [nextBindings, agentPage] = await Promise.all([onListBindings(templateId), onListAgents()]);
      setBindings(nextBindings);
      setAgents(agentPage.records ?? []);
      setDrafts(
        nextBindings.reduce<Record<string, DraftBinding>>((next, binding) => {
          next[binding.roleCode] = {
            agentId: binding.agentId,
            versionId: binding.agentVersionId,
          };
          return next;
        }, {}),
      );
    } catch (error) {
      messageApi.error(error instanceof Error ? error.message : 'Template role bindings failed to load');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void refresh();
  }, [templateId]);

  async function ensureVersions(agentId: number) {
    if (versionsByAgent[agentId]) {
      return versionsByAgent[agentId];
    }
    setLoadingVersionAgentId(agentId);
    try {
      const versions = await onListVersions(agentId);
      setVersionsByAgent((current) => ({ ...current, [agentId]: versions }));
      return versions;
    } catch (error) {
      messageApi.error(error instanceof Error ? error.message : 'Agent versions failed to load');
      return [];
    } finally {
      setLoadingVersionAgentId(undefined);
    }
  }

  function updateDraft(roleCode: string, patch: DraftBinding) {
    setDrafts((current) => ({ ...current, [roleCode]: { ...current[roleCode], ...patch } }));
  }

  async function handleAgentChange(binding: CollaborationRoleBinding, agentId?: number) {
    if (!agentId) {
      updateDraft(binding.roleCode, { agentId: undefined, versionId: undefined });
      return;
    }
    const agent = agents.find((item) => item.id === agentId);
    const versions = await ensureVersions(agentId);
    const preferredVersionId =
      recommendedVersionId(agent) ??
      versions.find((version) => version.versionStatus === 'published')?.agentVersionId ??
      versions[0]?.agentVersionId;
    updateDraft(binding.roleCode, { agentId, versionId: preferredVersionId });
  }

  async function handleSave(binding: CollaborationRoleBinding) {
    if (!templateId) {
      return;
    }
    const draft = drafts[binding.roleCode] ?? {};
    if (!draft.agentId || !draft.versionId) {
      messageApi.warning('Please select both template Agent and AgentVersion');
      return;
    }
    setSavingRoleCode(binding.roleCode);
    try {
      const updated = await onUpdateBinding(templateId, binding.roleCode, {
        agentId: draft.agentId,
        agentVersionId: draft.versionId,
      });
      setBindings((current) => current.map((item) => (item.roleCode === binding.roleCode ? { ...item, ...updated } : item)));
      setDrafts((current) => ({
        ...current,
        [binding.roleCode]: { agentId: updated.agentId, versionId: updated.agentVersionId },
      }));
      messageApi.success('Template role binding saved');
    } catch (error) {
      messageApi.error(error instanceof Error ? error.message : 'Template role binding save failed');
    } finally {
      setSavingRoleCode(undefined);
    }
  }

  async function handleClear(binding: CollaborationRoleBinding) {
    if (!templateId) {
      return;
    }
    setSavingRoleCode(binding.roleCode);
    try {
      const updated = await onUpdateBinding(templateId, binding.roleCode, {
        agentId: null,
        agentVersionId: null,
      });
      setBindings((current) => current.map((item) => (item.roleCode === binding.roleCode ? { ...item, ...updated } : item)));
      setDrafts((current) => ({ ...current, [binding.roleCode]: {} }));
      messageApi.success('Template role binding cleared');
    } catch (error) {
      messageApi.error(error instanceof Error ? error.message : 'Template role binding clear failed');
    } finally {
      setSavingRoleCode(undefined);
    }
  }

  return (
    <Card title="Template role bindings" variant="borderless">
      {contextHolder}
      <Space direction="vertical" size={12} style={{ width: '100%' }}>
        <Typography.Text type="secondary">
          Override role-to-Agent bindings for the selected team template. Global role defaults stay as fallback.
        </Typography.Text>
        <Table<CollaborationRoleBinding>
          rowKey="roleCode"
          size="small"
          loading={loading}
          pagination={false}
          dataSource={bindings}
          columns={[
            {
              title: 'Role',
              dataIndex: 'roleCode',
              render: (_, binding) => (
                <Space direction="vertical" size={0}>
                  <Typography.Text>{roleLabel(binding)}</Typography.Text>
                  {binding.domainCode ? <Typography.Text type="secondary">{binding.domainCode}</Typography.Text> : null}
                </Space>
              ),
            },
            {
              title: 'Template Agent',
              dataIndex: 'agentId',
              width: 260,
              render: (_, binding) => (
                <Select
                  aria-label={`${binding.roleCode} template agent`}
                  allowClear
                  showSearch
                  placeholder="Use global default"
                  value={drafts[binding.roleCode]?.agentId}
                  optionFilterProp="label"
                  style={{ width: '100%' }}
                  onChange={(agentId) => void handleAgentChange(binding, agentId)}
                  options={agents.map((agent) => ({
                    label: `${agentLabel(agent)} #${agent.id}`,
                    value: agent.id,
                  }))}
                />
              ),
            },
            {
              title: 'Template Version',
              dataIndex: 'agentVersionId',
              width: 220,
              render: (_, binding) => {
                const agentId = drafts[binding.roleCode]?.agentId;
                const versions = agentId ? versionsByAgent[agentId] ?? [] : [];
                return (
                  <Select
                    aria-label={`${binding.roleCode} template version`}
                    allowClear
                    disabled={!agentId}
                    loading={loadingVersionAgentId === agentId}
                    placeholder="Select version"
                    value={drafts[binding.roleCode]?.versionId}
                    style={{ width: '100%' }}
                    onFocus={() => {
                      if (agentId) {
                        void ensureVersions(agentId);
                      }
                    }}
                    onChange={(versionId) => updateDraft(binding.roleCode, { versionId })}
                    options={versions.map((version) => ({
                      label: `${version.versionNo} (${version.versionStatus}) #${version.agentVersionId}`,
                      value: version.agentVersionId,
                    }))}
                  />
                );
              },
            },
            {
              title: 'Effective',
              width: 180,
              render: (_, binding) => (
                <Space direction="vertical" size={0}>
                  <Tag color={binding.source === 'template' ? 'blue' : binding.source === 'role_default' ? 'green' : undefined}>
                    {binding.source ?? 'unbound'}
                  </Tag>
                  {binding.effectiveAgentId && binding.effectiveAgentVersionId ? (
                    <Typography.Text type="secondary">
                      #{binding.effectiveAgentId} / v#{binding.effectiveAgentVersionId}
                    </Typography.Text>
                  ) : null}
                </Space>
              ),
            },
            {
              title: 'Action',
              width: 170,
              render: (_, binding) => (
                <Space>
                  <Button
                    size="small"
                    type="primary"
                    loading={savingRoleCode === binding.roleCode}
                    onClick={() => void handleSave(binding)}
                  >
                    Save
                  </Button>
                  <Button size="small" loading={savingRoleCode === binding.roleCode} onClick={() => void handleClear(binding)}>
                    Clear
                  </Button>
                </Space>
              ),
            },
          ]}
        />
        <Button onClick={() => void refresh()} loading={loading} disabled={!templateId}>
          Refresh template bindings
        </Button>
      </Space>
    </Card>
  );
}
