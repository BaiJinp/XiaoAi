import { Button, Card, Select, Space, Table, Tag, Typography, message } from 'antd';
import { useEffect, useState } from 'react';
import { listAgentVersions, listAgents } from '../../../services/agent-api';
import { listAgentRoles, updateAgentRoleDefaultAgent } from '../../../services/collaboration-api';
import type { Agent, AgentPageResponse, AgentVersionResponse } from '../../../types/agent';
import type { AgentRole, UpdateAgentRoleDefaultAgentRequest } from '../../../types/collaboration';

interface AgentRoleDefaultBindingCardProps {
  domainCode?: string;
  onListRoles?: (domainCode?: string) => Promise<AgentRole[]>;
  onListAgents?: () => Promise<AgentPageResponse>;
  onListVersions?: (agentId: number) => Promise<AgentVersionResponse[]>;
  onUpdateDefaultAgent?: (roleId: number, request: UpdateAgentRoleDefaultAgentRequest) => Promise<AgentRole>;
}

interface DraftBinding {
  agentId?: number;
  versionId?: number;
}

function agentLabel(agent: Agent): string {
  return agent.agentName || agent.name || agent.agentCode || `Agent #${agent.id}`;
}

function roleLabel(role: AgentRole): string {
  return role.roleName ? `${role.roleName} (${role.roleCode})` : role.roleCode;
}

function recommendedVersionId(agent?: Agent): number | undefined {
  return agent?.latestStableVersionId ?? agent?.currentVersionId;
}

export function AgentRoleDefaultBindingCard({
  domainCode = 'software_development',
  onListRoles = listAgentRoles,
  onListAgents = () => listAgents({ status: 'published', pageNo: 1, pageSize: 100 }),
  onListVersions = listAgentVersions,
  onUpdateDefaultAgent = updateAgentRoleDefaultAgent,
}: AgentRoleDefaultBindingCardProps) {
  const [roles, setRoles] = useState<AgentRole[]>([]);
  const [agents, setAgents] = useState<Agent[]>([]);
  const [drafts, setDrafts] = useState<Record<number, DraftBinding>>({});
  const [versionsByAgent, setVersionsByAgent] = useState<Record<number, AgentVersionResponse[]>>({});
  const [loading, setLoading] = useState(false);
  const [savingRoleId, setSavingRoleId] = useState<number>();
  const [loadingVersionAgentId, setLoadingVersionAgentId] = useState<number>();
  const [messageApi, contextHolder] = message.useMessage();

  async function refresh() {
    setLoading(true);
    try {
      const [nextRoles, agentPage] = await Promise.all([onListRoles(domainCode), onListAgents()]);
      setRoles(nextRoles);
      setAgents(agentPage.records ?? []);
      setDrafts(
        nextRoles.reduce<Record<number, DraftBinding>>((next, role) => {
          next[role.id] = {
            agentId: role.defaultAgentId,
            versionId: role.defaultAgentVersionId,
          };
          return next;
        }, {}),
      );
    } catch (error) {
      messageApi.error(error instanceof Error ? error.message : 'Agent role bindings failed to load');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void refresh();
  }, [domainCode]);

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

  function updateDraft(roleId: number, patch: DraftBinding) {
    setDrafts((current) => ({ ...current, [roleId]: { ...current[roleId], ...patch } }));
  }

  async function handleAgentChange(role: AgentRole, agentId?: number) {
    if (!agentId) {
      updateDraft(role.id, { agentId: undefined, versionId: undefined });
      return;
    }
    const agent = agents.find((item) => item.id === agentId);
    const versions = await ensureVersions(agentId);
    const preferredVersionId =
      recommendedVersionId(agent) ??
      versions.find((version) => version.versionStatus === 'published')?.agentVersionId ??
      versions[0]?.agentVersionId;
    updateDraft(role.id, { agentId, versionId: preferredVersionId });
  }

  async function handleSave(role: AgentRole) {
    const draft = drafts[role.id] ?? {};
    if (!draft.agentId || !draft.versionId) {
      messageApi.warning('Please select both default Agent and AgentVersion');
      return;
    }
    setSavingRoleId(role.id);
    try {
      const updated = await onUpdateDefaultAgent(role.id, {
        defaultAgentId: draft.agentId,
        defaultAgentVersionId: draft.versionId,
      });
      setRoles((current) => current.map((item) => (item.id === role.id ? { ...item, ...updated } : item)));
      setDrafts((current) => ({
        ...current,
        [role.id]: { agentId: updated.defaultAgentId, versionId: updated.defaultAgentVersionId },
      }));
      messageApi.success('Role default binding saved');
    } catch (error) {
      messageApi.error(error instanceof Error ? error.message : 'Role default binding save failed');
    } finally {
      setSavingRoleId(undefined);
    }
  }

  async function handleClear(role: AgentRole) {
    setSavingRoleId(role.id);
    try {
      const updated = await onUpdateDefaultAgent(role.id, {
        defaultAgentId: null,
        defaultAgentVersionId: null,
      });
      setRoles((current) => current.map((item) => (item.id === role.id ? { ...item, ...updated } : item)));
      setDrafts((current) => ({ ...current, [role.id]: {} }));
      messageApi.success('Role default binding cleared');
    } catch (error) {
      messageApi.error(error instanceof Error ? error.message : 'Role default binding clear failed');
    } finally {
      setSavingRoleId(undefined);
    }
  }

  return (
    <Card title="Agent role default bindings" variant="borderless">
      {contextHolder}
      <Space direction="vertical" size={12} style={{ width: '100%' }}>
        <Typography.Text type="secondary">
          Maintain explicit role-to-Agent bindings before importing an agile team template.
        </Typography.Text>
        <Table<AgentRole>
          rowKey="id"
          size="small"
          loading={loading}
          pagination={false}
          dataSource={roles}
          columns={[
            {
              title: 'Role',
              dataIndex: 'roleCode',
              render: (_, role) => (
                <Space direction="vertical" size={0}>
                  <Typography.Text>{roleLabel(role)}</Typography.Text>
                  {role.domainCode ? <Typography.Text type="secondary">{role.domainCode}</Typography.Text> : null}
                </Space>
              ),
            },
            {
              title: 'Default Agent',
              dataIndex: 'defaultAgentId',
              width: 260,
              render: (_, role) => (
                <Select
                  aria-label={`${role.roleCode} default agent`}
                  allowClear
                  showSearch
                  placeholder="Select Agent"
                  value={drafts[role.id]?.agentId}
                  optionFilterProp="label"
                  style={{ width: '100%' }}
                  onChange={(agentId) => void handleAgentChange(role, agentId)}
                  options={agents.map((agent) => ({
                    label: `${agentLabel(agent)} #${agent.id}`,
                    value: agent.id,
                  }))}
                />
              ),
            },
            {
              title: 'Default Version',
              dataIndex: 'defaultAgentVersionId',
              width: 220,
              render: (_, role) => {
                const agentId = drafts[role.id]?.agentId;
                const versions = agentId ? versionsByAgent[agentId] ?? [] : [];
                return (
                  <Select
                    aria-label={`${role.roleCode} default version`}
                    allowClear
                    disabled={!agentId}
                    loading={loadingVersionAgentId === agentId}
                    placeholder="Select version"
                    value={drafts[role.id]?.versionId}
                    style={{ width: '100%' }}
                    onFocus={() => {
                      if (agentId) {
                        void ensureVersions(agentId);
                      }
                    }}
                    onChange={(versionId) => updateDraft(role.id, { versionId })}
                    options={versions.map((version) => ({
                      label: `${version.versionNo} (${version.versionStatus}) #${version.agentVersionId}`,
                      value: version.agentVersionId,
                    }))}
                  />
                );
              },
            },
            {
              title: 'Status',
              width: 120,
              render: (_, role) =>
                role.defaultAgentId && role.defaultAgentVersionId ? <Tag color="success">bound</Tag> : <Tag>unbound</Tag>,
            },
            {
              title: 'Action',
              width: 170,
              render: (_, role) => (
                <Space>
                  <Button size="small" type="primary" loading={savingRoleId === role.id} onClick={() => void handleSave(role)}>
                    Save
                  </Button>
                  <Button size="small" loading={savingRoleId === role.id} onClick={() => void handleClear(role)}>
                    Clear
                  </Button>
                </Space>
              ),
            },
          ]}
        />
        <Button onClick={() => void refresh()} loading={loading}>
          Refresh role bindings
        </Button>
      </Space>
    </Card>
  );
}
