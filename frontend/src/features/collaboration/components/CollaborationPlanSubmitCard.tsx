import { Alert, Button, Checkbox, Input, Select, Space, Tag, Typography } from 'antd';
import { useMemo, useState } from 'react';
import { listAgentVersions, listAgents } from '../../../services/agent-api';
import { listAgentRoles, listCollaborationRoleBindings } from '../../../services/collaboration-api';
import { listPluginManifests } from '../../../services/plugin-api';
import type { Agent, AgentPageResponse, AgentVersionResponse } from '../../../types/agent';
import type { AgentRole, CollaborationRoleBinding } from '../../../types/collaboration';
import type { PluginManifest, PluginTool } from '../../../types/plugin';

interface CollaborationPlanSubmitCardProps {
  defaultPlanJson: string;
  templateId?: number;
  templatePlanJson?: string;
  loading?: boolean;
  onSubmitAndStart: (planJson: string) => void;
  onListAgentRoles?: () => Promise<AgentRole[]>;
  onListRoleBindings?: (templateId: number) => Promise<CollaborationRoleBinding[]>;
  onListAgents?: () => Promise<AgentPageResponse>;
  onListAgentVersions?: (agentId: number) => Promise<AgentVersionResponse[]>;
  onListPluginManifests?: () => Promise<PluginManifest[]>;
}

interface PlanBuilderStage {
  stageCode: string;
  stageName: string;
  roleCode: string;
  agentId: string;
  agentVersionId: string;
  createTask: boolean;
  autoStartTask: boolean;
  waitForHandoffAcceptance: boolean;
  inputArtifactVersion: string;
  inputText: string;
  gateCode: string;
  gateName: string;
  reworkStageCode: string;
  toolCallsJson: string;
}

interface PlanBuilderResult {
  planJson?: string;
  error?: string;
}

type BuilderToolOption = PluginTool & { toolId: number };

interface SchemaField {
  name: string;
  type?: string;
  enumValues?: unknown[];
  required: boolean;
}

function agentLabel(agent: Agent): string {
  return agent.agentName || agent.name || agent.agentCode || `Agent #${agent.id}`;
}

function roleLabel(role: AgentRole): string {
  return role.roleName || role.roleCode;
}

function toBuilderToolOption(tool: PluginTool, plugin: PluginManifest): BuilderToolOption | undefined {
  if (!tool.toolId || tool.status !== 'active') {
    return undefined;
  }
  return {
    ...tool,
    toolId: tool.toolId,
    pluginCode: tool.pluginCode ?? plugin.pluginCode,
    pluginVersion: tool.pluginVersion ?? plugin.pluginVersion,
  };
}

function defaultValueForSchema(schema: Record<string, unknown>): unknown {
  if ('default' in schema) {
    return schema.default;
  }
  const enumValues = schema.enum;
  if (Array.isArray(enumValues) && enumValues.length > 0) {
    return enumValues[0];
  }

  const type = typeof schema.type === 'string' ? schema.type : undefined;
  if (type === 'string') {
    return '';
  }
  if (type === 'integer' || type === 'number') {
    return 0;
  }
  if (type === 'boolean') {
    return false;
  }
  if (type === 'array') {
    return [];
  }
  if (type === 'object') {
    return payloadTemplateFromSchemaObject(schema);
  }
  return null;
}

function payloadTemplateFromSchemaObject(schema: Record<string, unknown>): Record<string, unknown> {
  const properties = schema.properties;
  if (!properties || typeof properties !== 'object' || Array.isArray(properties)) {
    return {};
  }
  return Object.entries(properties as Record<string, unknown>).reduce<Record<string, unknown>>((template, [key, value]) => {
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      template[key] = defaultValueForSchema(value as Record<string, unknown>);
    }
    return template;
  }, {});
}

function payloadTemplateFromSchemaJson(schemaJson?: string): Record<string, unknown> {
  if (!schemaJson?.trim()) {
    return {};
  }
  try {
    const schema = JSON.parse(schemaJson);
    return schema && typeof schema === 'object' && !Array.isArray(schema)
      ? payloadTemplateFromSchemaObject(schema as Record<string, unknown>)
      : {};
  } catch {
    return {};
  }
}

function schemaFieldsFromSchemaJson(schemaJson?: string): SchemaField[] {
  if (!schemaJson?.trim()) {
    return [];
  }
  try {
    const schema = JSON.parse(schemaJson);
    if (!schema || typeof schema !== 'object' || Array.isArray(schema)) {
      return [];
    }
    const schemaRecord = schema as Record<string, unknown>;
    const properties = schemaRecord.properties;
    if (!properties || typeof properties !== 'object' || Array.isArray(properties)) {
      return [];
    }
    const requiredFields = new Set(Array.isArray(schemaRecord.required) ? schemaRecord.required.filter((item) => typeof item === 'string') : []);
    return Object.entries(properties as Record<string, unknown>).flatMap(([name, value]) => {
      if (!value || typeof value !== 'object' || Array.isArray(value)) {
        return [];
      }
      const fieldSchema = value as Record<string, unknown>;
      return [{
        name,
        type: typeof fieldSchema.type === 'string' ? fieldSchema.type : undefined,
        enumValues: Array.isArray(fieldSchema.enum) ? fieldSchema.enum : undefined,
        required: requiredFields.has(name),
      }];
    });
  } catch {
    return [];
  }
}

function createDefaultBuilderStages(): PlanBuilderStage[] {
  return [
    {
      stageCode: 'requirement_analysis',
      stageName: 'Requirement analysis',
      roleCode: 'software_product_manager',
      agentId: '101',
      agentVersionId: '1001',
      createTask: true,
      autoStartTask: true,
      waitForHandoffAcceptance: false,
      inputArtifactVersion: '',
      inputText: 'Produce PRD, user stories, and acceptance criteria from the user requirement.',
      gateCode: 'requirement_confirmed',
      gateName: 'Requirement confirmed',
      reworkStageCode: '',
      toolCallsJson: '[]',
    },
    {
      stageCode: 'technical_design',
      stageName: 'Technical design',
      roleCode: 'software_architect',
      agentId: '102',
      agentVersionId: '1002',
      createTask: true,
      autoStartTask: true,
      waitForHandoffAcceptance: false,
      inputArtifactVersion: '',
      inputText: 'Produce technical design, API contract, and key risks from the confirmed requirement.',
      gateCode: 'design_confirmed',
      gateName: 'Design confirmed',
      reworkStageCode: '',
      toolCallsJson: '[]',
    },
    {
      stageCode: 'backend_implementation',
      stageName: 'Backend implementation',
      roleCode: 'software_backend_developer',
      agentId: '103',
      agentVersionId: '1003',
      createTask: true,
      autoStartTask: true,
      waitForHandoffAcceptance: false,
      inputArtifactVersion: '',
      inputText: 'Complete backend implementation from the technical design and output an implementation summary.',
      gateCode: 'implementation_done',
      gateName: 'Implementation done',
      reworkStageCode: '',
      toolCallsJson: '[]',
    },
    {
      stageCode: 'frontend_implementation',
      stageName: 'Frontend implementation',
      roleCode: 'software_frontend_developer',
      agentId: '104',
      agentVersionId: '1004',
      createTask: true,
      autoStartTask: true,
      waitForHandoffAcceptance: false,
      inputArtifactVersion: '',
      inputText: 'Complete frontend implementation from the technical design and output an implementation summary.',
      gateCode: 'implementation_done',
      gateName: 'Implementation done',
      reworkStageCode: '',
      toolCallsJson: '[]',
    },
    {
      stageCode: 'testing',
      stageName: 'Test validation',
      roleCode: 'software_tester',
      agentId: '105',
      agentVersionId: '1005',
      createTask: true,
      autoStartTask: true,
      waitForHandoffAcceptance: false,
      inputArtifactVersion: '',
      inputText: 'Produce the test plan, execution result, and remaining risks from acceptance criteria and implementation summaries.',
      gateCode: 'tests_passed',
      gateName: 'Tests passed',
      reworkStageCode: '',
      toolCallsJson: '[]',
    },
    {
      stageCode: 'review_and_delivery',
      stageName: 'Review and delivery',
      roleCode: 'software_reviewer',
      agentId: '106',
      agentVersionId: '1006',
      createTask: true,
      autoStartTask: true,
      waitForHandoffAcceptance: false,
      inputArtifactVersion: '',
      inputText: 'Produce review findings and delivery summary from design, implementation summaries, and test report.',
      gateCode: 'delivery_confirmed',
      gateName: 'Delivery confirmed',
      reworkStageCode: '',
      toolCallsJson: '[]',
    },
  ];
}

function textFromRecord(record: Record<string, unknown>, fieldName: string): string {
  const value = record[fieldName];
  return typeof value === 'string' ? value : '';
}

function booleanFromRecord(record: Record<string, unknown>, fieldName: string, defaultValue: boolean): boolean {
  const value = record[fieldName];
  return typeof value === 'boolean' ? value : defaultValue;
}

function idFromRecord(record: Record<string, unknown>, fieldName: string): string {
  const value = record[fieldName];
  return typeof value === 'number' && Number.isFinite(value) ? String(value) : '';
}

function importTemplatePlan(
  templatePlanJson: string,
): { goal: string; maxThreads: string; handoffPolicyStrict: boolean; stages: PlanBuilderStage[] } | { error: string } {
  try {
    const root = JSON.parse(templatePlanJson);
    if (!root || typeof root !== 'object' || Array.isArray(root)) {
      return { error: 'Session template JSON must be an object' };
    }

    const plan = root as Record<string, unknown>;
    const rawStages = Array.isArray(plan.stages) ? plan.stages : [];
    if (!rawStages.length) {
      return { error: 'Session template has no stages to import' };
    }

    const stages = rawStages.map((rawStage, index) => {
      const stage = rawStage && typeof rawStage === 'object' && !Array.isArray(rawStage) ? (rawStage as Record<string, unknown>) : {};
      const stageCode = textFromRecord(stage, 'stageCode') || `stage_${index + 1}`;
      const toolCalls = Array.isArray(stage.toolCalls) ? stage.toolCalls : [];
      return {
        stageCode,
        stageName: textFromRecord(stage, 'stageName') || textFromRecord(stage, 'threadName') || stageCode,
        roleCode: textFromRecord(stage, 'roleCode'),
        agentId: idFromRecord(stage, 'agentId'),
        agentVersionId: idFromRecord(stage, 'agentVersionId'),
        createTask: booleanFromRecord(stage, 'createTask', true),
        autoStartTask: booleanFromRecord(stage, 'autoStartTask', false),
        waitForHandoffAcceptance: booleanFromRecord(stage, 'waitForHandoffAcceptance', false),
        inputArtifactVersion: idFromRecord(stage, 'inputArtifactVersion'),
        inputText: textFromRecord(stage, 'inputText'),
        gateCode: textFromRecord(stage, 'requiresGate'),
        gateName: textFromRecord(stage, 'gateName'),
        reworkStageCode: textFromRecord(stage, 'reworkStageCode'),
        toolCallsJson: JSON.stringify(toolCalls, null, 2),
      };
    });

    const maxThreads = typeof plan.maxThreads === 'number' && Number.isInteger(plan.maxThreads) && plan.maxThreads > 0
      ? String(plan.maxThreads)
      : String(Math.max(stages.length, 1));
    const handoffPolicy = plan.handoffPolicy && typeof plan.handoffPolicy === 'object' && !Array.isArray(plan.handoffPolicy)
      ? (plan.handoffPolicy as Record<string, unknown>)
      : {};
    const handoffPolicyStrict =
      handoffPolicy.mode === 'strict' || handoffPolicy.requireAcceptedBeforeConsume === true;

    return {
      goal: textFromRecord(plan, 'goal') || 'Deliver requirement through the selected collaboration template',
      maxThreads,
      handoffPolicyStrict,
      stages,
    };
  } catch {
    return { error: 'Session template JSON is invalid' };
  }
}

function formatJson(value: string) {
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

function jsonError(value: string) {
  if (!value.trim()) {
    return 'Plan JSON is required';
  }
  try {
    JSON.parse(value);
    return undefined;
  } catch {
    return 'Plan JSON is invalid';
  }
}

function parsePositiveInteger(value: string, fieldName: string): number | string {
  const normalized = value.trim();
  if (!normalized) {
    return `${fieldName} is required`;
  }
  const parsed = Number(normalized);
  if (!Number.isInteger(parsed) || parsed <= 0) {
    return `${fieldName} must be a positive integer`;
  }
  return parsed;
}

function buildPlanJson(
  goal: string,
  maxThreads: string,
  handoffPolicyStrict: boolean,
  stages: PlanBuilderStage[],
): PlanBuilderResult {
  const normalizedGoal = goal.trim();
  const errors: string[] = [];
  if (!normalizedGoal) {
    errors.push('Plan goal is required');
  }

  const parsedMaxThreads = parsePositiveInteger(maxThreads, 'Max threads');
  if (typeof parsedMaxThreads === 'string') {
    errors.push(parsedMaxThreads);
  }

  const builtStages = [];
  for (const [index, stage] of stages.entries()) {
    const rowName = `Stage ${index + 1}`;
    const stageErrors: string[] = [];
    const stageCode = stage.stageCode.trim();
    if (!stageCode) {
      stageErrors.push(`${rowName} code is required`);
    }

    const agentId = parsePositiveInteger(stage.agentId, `${rowName} agent ID`);
    if (typeof agentId === 'string') {
      stageErrors.push(agentId);
    }

    const agentVersionId = parsePositiveInteger(stage.agentVersionId, `${rowName} agent version ID`);
    if (typeof agentVersionId === 'string') {
      stageErrors.push(agentVersionId);
    }

    let toolCalls: unknown[] | undefined;
    if (stage.toolCallsJson.trim()) {
      try {
        const parsedToolCalls = JSON.parse(stage.toolCallsJson);
        if (!Array.isArray(parsedToolCalls)) {
          stageErrors.push(`${rowName} tool calls JSON must be an array`);
        }
        toolCalls = parsedToolCalls;
      } catch {
        stageErrors.push(`${rowName} tool calls JSON is invalid`);
      }
    }

    const gateCode = stage.gateCode.trim();
    const reworkStageCode = stage.reworkStageCode.trim();
    const inputArtifactVersion = stage.inputArtifactVersion.trim()
      ? parsePositiveInteger(stage.inputArtifactVersion, `${rowName} input artifact version`)
      : undefined;
    if (typeof inputArtifactVersion === 'string') {
      stageErrors.push(inputArtifactVersion);
    }
    if (!gateCode && reworkStageCode) {
      stageErrors.push(`${rowName} rework stage code requires a gate code`);
    }

    if (stageErrors.length > 0) {
      errors.push(...stageErrors);
      continue;
    }

    const builtStage: Record<string, unknown> = {
      stageCode,
      stageName: stage.stageName.trim() || stageCode,
      agentId,
      agentVersionId,
      createTask: stage.createTask,
      autoStartTask: stage.autoStartTask,
      waitForHandoffAcceptance: stage.waitForHandoffAcceptance,
      inputText: stage.inputText.trim(),
    };
    if (typeof inputArtifactVersion === 'number') {
      builtStage.inputArtifactVersion = inputArtifactVersion;
    }

    if (toolCalls && toolCalls.length > 0) {
      builtStage.toolCalls = toolCalls;
    }

    const roleCode = stage.roleCode.trim();
    if (roleCode) {
      builtStage.roleCode = roleCode;
    }

    if (gateCode) {
      builtStage.requiresGate = gateCode;
      builtStage.gateName = stage.gateName.trim() || gateCode;
      builtStage.gateType = 'manual_confirmation';
      if (reworkStageCode) {
        builtStage.reworkStageCode = reworkStageCode;
      }
    }

    builtStages.push(builtStage);
  }

  if (errors.length > 0) {
    return { error: errors.join('\n') };
  }

  const plan: Record<string, unknown> = {
    goal: normalizedGoal,
    maxDepth: 1,
    maxThreads: parsedMaxThreads,
    stages: builtStages,
  };
  if (handoffPolicyStrict) {
    plan.handoffPolicy = {
      mode: 'strict',
      requireAcceptedBeforeConsume: true,
    };
  }

  return {
    planJson: JSON.stringify(
      plan,
      null,
      2,
    ),
  };
}

export function CollaborationPlanSubmitCard({
  defaultPlanJson,
  templateId,
  templatePlanJson,
  loading,
  onSubmitAndStart,
  onListAgentRoles = () => listAgentRoles('software_development'),
  onListRoleBindings = (targetTemplateId: number) => listCollaborationRoleBindings(targetTemplateId),
  onListAgents = () => listAgents({ status: 'published', pageNo: 1, pageSize: 100 }),
  onListAgentVersions = listAgentVersions,
  onListPluginManifests = listPluginManifests,
}: CollaborationPlanSubmitCardProps) {
  const [planJson, setPlanJson] = useState(() => formatJson(defaultPlanJson));
  const [builderGoal, setBuilderGoal] = useState('Deliver requirement through a full agile development team');
  const [builderMaxThreads, setBuilderMaxThreads] = useState('6');
  const [builderHandoffPolicyStrict, setBuilderHandoffPolicyStrict] = useState(false);
  const [builderStages, setBuilderStages] = useState<PlanBuilderStage[]>(() => createDefaultBuilderStages());
  const [builderError, setBuilderError] = useState<string>();
  const [roleOptions, setRoleOptions] = useState<AgentRole[]>([]);
  const [roleBindingOptions, setRoleBindingOptions] = useState<CollaborationRoleBinding[]>([]);
  const [loadingRoles, setLoadingRoles] = useState(false);
  const [loadingRoleBindings, setLoadingRoleBindings] = useState(false);
  const [agentOptions, setAgentOptions] = useState<Agent[]>([]);
  const [loadingAgents, setLoadingAgents] = useState(false);
  const [stageToolOptions, setStageToolOptions] = useState<Record<number, BuilderToolOption[]>>({});
  const [loadingToolsStage, setLoadingToolsStage] = useState<number>();
  const error = useMemo(() => jsonError(planJson), [planJson]);

  function updateBuilderStage(index: number, patch: Partial<PlanBuilderStage>) {
    setBuilderStages((current) => current.map((stage, stageIndex) => (stageIndex === index ? { ...stage, ...patch } : stage)));
  }

  function addBuilderStage() {
    setBuilderStages((current) => [
      ...current,
      {
        stageCode: `stage_${current.length + 1}`,
        stageName: `Agent stage ${current.length + 1}`,
        roleCode: '',
        agentId: '',
        agentVersionId: '',
        createTask: true,
        autoStartTask: false,
        waitForHandoffAcceptance: false,
        inputArtifactVersion: '',
        inputText: '',
        gateCode: '',
        gateName: '',
        reworkStageCode: '',
        toolCallsJson: '[]',
      },
    ]);
  }

  function removeBuilderStage(index: number) {
    setBuilderStages((current) => current.filter((_, stageIndex) => stageIndex !== index));
  }

  async function loadRoles() {
    setLoadingRoles(true);
    try {
      const roles = await onListAgentRoles();
      setRoleOptions(roles);
      setBuilderError(undefined);
    } catch (loadError) {
      setBuilderError(loadError instanceof Error ? loadError.message : 'Agent roles failed to load');
    } finally {
      setLoadingRoles(false);
    }
  }

  async function loadRoleBindings() {
    if (!templateId) {
      setBuilderError('Session template is required before loading role bindings');
      return;
    }
    setLoadingRoleBindings(true);
    try {
      const bindings = await onListRoleBindings(templateId);
      setRoleBindingOptions(bindings);
      setBuilderError(undefined);
    } catch (loadError) {
      setBuilderError(loadError instanceof Error ? loadError.message : 'Collaboration role bindings failed to load');
    } finally {
      setLoadingRoleBindings(false);
    }
  }

  async function loadAgents() {
    setLoadingAgents(true);
    try {
      const page = await onListAgents();
      setAgentOptions(page.records ?? []);
      setBuilderError(undefined);
    } catch (loadError) {
      setBuilderError(loadError instanceof Error ? loadError.message : 'Agents failed to load');
    } finally {
      setLoadingAgents(false);
    }
  }

  function selectStageAgent(index: number, agentId: number) {
    const selectedAgent = agentOptions.find((agent) => agent.id === agentId);
    updateBuilderStage(index, {
      agentId: String(agentId),
      agentVersionId: selectedAgent?.latestStableVersionId
        ? String(selectedAgent.latestStableVersionId)
        : selectedAgent?.currentVersionId
          ? String(selectedAgent.currentVersionId)
          : builderStages[index].agentVersionId,
    });
    setStageToolOptions((current) => {
      const next = { ...current };
      delete next[index];
      return next;
    });
  }

  function applyRoleDefaults() {
    const roleByCode = new Map(roleOptions.map((role) => [role.roleCode, role]));
    let appliedCount = 0;
    setBuilderStages((current) =>
      current.map((stage) => {
        const role = roleByCode.get(stage.roleCode);
        if (!role?.defaultAgentId && !role?.defaultAgentVersionId) {
          return stage;
        }
        const patch: Partial<PlanBuilderStage> = {};
        if (!stage.agentId.trim() && role.defaultAgentId) {
          patch.agentId = String(role.defaultAgentId);
        }
        if (!stage.agentVersionId.trim() && role.defaultAgentVersionId) {
          patch.agentVersionId = String(role.defaultAgentVersionId);
        }
        if (Object.keys(patch).length === 0) {
          return stage;
        }
        appliedCount++;
        return { ...stage, ...patch };
      }),
    );
    setStageToolOptions({});
    setBuilderError(appliedCount > 0 ? undefined : 'No role default bindings were applied');
  }

  function applyRoleBindings() {
    const bindingByRoleCode = new Map(roleBindingOptions.map((binding) => [binding.roleCode, binding]));
    let appliedCount = 0;
    setBuilderStages((current) =>
      current.map((stage) => {
        const binding = bindingByRoleCode.get(stage.roleCode);
        if (!binding?.effectiveAgentId && !binding?.effectiveAgentVersionId) {
          return stage;
        }
        const patch: Partial<PlanBuilderStage> = {};
        if (!stage.agentId.trim() && binding.effectiveAgentId) {
          patch.agentId = String(binding.effectiveAgentId);
        }
        if (!stage.agentVersionId.trim() && binding.effectiveAgentVersionId) {
          patch.agentVersionId = String(binding.effectiveAgentVersionId);
        }
        if (Object.keys(patch).length === 0) {
          return stage;
        }
        appliedCount++;
        return { ...stage, ...patch };
      }),
    );
    setStageToolOptions({});
    setBuilderError(appliedCount > 0 ? undefined : 'No collaboration role bindings were applied');
  }

  function loadAgileTeamPreset() {
    setBuilderGoal('Deliver requirement through a full agile development team');
    setBuilderMaxThreads('6');
    setBuilderHandoffPolicyStrict(false);
    setBuilderStages(createDefaultBuilderStages());
    setStageToolOptions({});
    setBuilderError(undefined);
  }

  function importSessionTemplate() {
    if (!templatePlanJson) {
      return;
    }
    const result = importTemplatePlan(templatePlanJson);
    if ('error' in result) {
      setBuilderError(result.error);
      return;
    }
    setBuilderGoal(result.goal);
    setBuilderMaxThreads(result.maxThreads);
    setBuilderHandoffPolicyStrict(result.handoffPolicyStrict);
    setBuilderStages(result.stages);
    setStageToolOptions({});
    setBuilderError(undefined);
  }

  function generatePlanJson() {
    const result = buildPlanJson(builderGoal, builderMaxThreads, builderHandoffPolicyStrict, builderStages);
    if (result.error) {
      setBuilderError(result.error);
      return;
    }
    setBuilderError(undefined);
    setPlanJson(result.planJson ?? planJson);
  }

  async function loadStageTools(index: number) {
    const stage = builderStages[index];
    const parsedAgentId = parsePositiveInteger(stage.agentId, `Stage ${index + 1} agent ID`);
    if (typeof parsedAgentId === 'string') {
      setBuilderError(parsedAgentId);
      return;
    }

    setLoadingToolsStage(index);
    try {
      const [versions, plugins] = await Promise.all([onListAgentVersions(parsedAgentId), onListPluginManifests()]);
      const requestedVersionId = Number(stage.agentVersionId);
      const selectedVersion =
        versions.find((version) => version.agentVersionId === requestedVersionId) ??
        versions.find((version) => version.versionStatus === 'published') ??
        versions.find((version) => version.versionStatus === 'draft') ??
        versions[0];

      if (!selectedVersion) {
        setBuilderError(`Stage ${index + 1} has no AgentVersion`);
        return;
      }

      const allowedToolIds = new Set(selectedVersion.toolIds ?? []);
      const tools = plugins
        .flatMap((plugin) => plugin.tools.map((tool) => toBuilderToolOption(tool, plugin)))
        .filter((tool): tool is BuilderToolOption => Boolean(tool && allowedToolIds.has(tool.toolId)));

      updateBuilderStage(index, { agentVersionId: String(selectedVersion.agentVersionId) });
      setStageToolOptions((current) => ({ ...current, [index]: tools }));
      setBuilderError(undefined);
    } catch (loadError) {
      setBuilderError(loadError instanceof Error ? loadError.message : `Stage ${index + 1} tools failed to load`);
    } finally {
      setLoadingToolsStage(undefined);
    }
  }

  function appendStageToolCall(index: number, tool: BuilderToolOption) {
    const stage = builderStages[index];
    let toolCalls: unknown[];
    try {
      const parsedToolCalls = stage.toolCallsJson.trim() ? JSON.parse(stage.toolCallsJson) : [];
      if (!Array.isArray(parsedToolCalls)) {
        setBuilderError(`Stage ${index + 1} tool calls JSON must be an array`);
        return;
      }
      toolCalls = parsedToolCalls;
    } catch {
      setBuilderError(`Stage ${index + 1} tool calls JSON is invalid`);
      return;
    }

    toolCalls.push({
      toolId: tool.toolId,
      toolCode: tool.toolCode,
      toolType: tool.toolType,
      callPayloadJson: payloadTemplateFromSchemaJson(tool.schemaJson),
    });
    updateBuilderStage(index, { toolCallsJson: JSON.stringify(toolCalls, null, 2) });
    setBuilderError(undefined);
  }

  function updateStageToolCallPayload(index: number, toolCallIndex: number, field: SchemaField, value: unknown) {
    const stage = builderStages[index];
    let toolCalls: unknown[];
    try {
      const parsedToolCalls = stage.toolCallsJson.trim() ? JSON.parse(stage.toolCallsJson) : [];
      if (!Array.isArray(parsedToolCalls)) {
        setBuilderError(`Stage ${index + 1} tool calls JSON must be an array`);
        return;
      }
      toolCalls = parsedToolCalls;
    } catch {
      setBuilderError(`Stage ${index + 1} tool calls JSON is invalid`);
      return;
    }
    const current = toolCalls[toolCallIndex];
    if (!current || typeof current !== 'object' || Array.isArray(current)) {
      setBuilderError(`Stage ${index + 1} tool call ${toolCallIndex + 1} must be an object`);
      return;
    }
    const currentCall = current as Record<string, unknown>;
    const currentPayload = currentCall.callPayloadJson && typeof currentCall.callPayloadJson === 'object' && !Array.isArray(currentCall.callPayloadJson)
      ? (currentCall.callPayloadJson as Record<string, unknown>)
      : {};
    toolCalls[toolCallIndex] = {
      ...currentCall,
      callPayloadJson: {
        ...currentPayload,
        [field.name]: value,
      },
    };
    updateBuilderStage(index, { toolCallsJson: JSON.stringify(toolCalls, null, 2) });
    setBuilderError(undefined);
  }

  function renderPayloadInput(index: number, toolCallIndex: number, field: SchemaField, value: unknown) {
    if (field.enumValues?.length) {
      return (
        <select
          aria-label={`Stage ${index + 1} tool call ${toolCallIndex + 1} payload ${field.name}`}
          style={{ width: 180, height: 32, border: '1px solid #d9d9d9', borderRadius: 6, padding: '0 8px' }}
          value={value as string | number | undefined}
          onChange={(event) => updateStageToolCallPayload(index, toolCallIndex, field, event.target.value)}
        >
          {field.enumValues.map((item) => (
            <option key={String(item)} value={String(item)}>
              {String(item)}
            </option>
          ))}
        </select>
      );
    }
    if (field.type === 'boolean') {
      return (
        <Checkbox
          aria-label={`Stage ${index + 1} tool call ${toolCallIndex + 1} payload ${field.name}`}
          checked={Boolean(value)}
          onChange={(event) => updateStageToolCallPayload(index, toolCallIndex, field, event.target.checked)}
        >
          {field.name}
        </Checkbox>
      );
    }
    if (field.type === 'integer' || field.type === 'number') {
      return (
        <Input
          aria-label={`Stage ${index + 1} tool call ${toolCallIndex + 1} payload ${field.name}`}
          type="number"
          step={field.type === 'integer' ? 1 : 'any'}
          style={{ width: 160 }}
          value={value === undefined || value === null ? '' : String(value)}
          onChange={(event) => {
            const nextValue = event.target.value.trim();
            if (!nextValue) {
              updateStageToolCallPayload(index, toolCallIndex, field, undefined);
              return;
            }
            const parsedValue = Number(nextValue);
            if (field.type === 'integer' && !Number.isInteger(parsedValue)) {
              setBuilderError(`Stage ${index + 1} payload ${field.name} must be an integer`);
              return;
            }
            updateStageToolCallPayload(index, toolCallIndex, field, parsedValue);
          }}
        />
      );
    }
    if (field.type === 'object' || field.type === 'array') {
      return (
        <Input.TextArea
          aria-label={`Stage ${index + 1} tool call ${toolCallIndex + 1} payload ${field.name}`}
          rows={2}
          style={{ width: 260 }}
          value={value === undefined ? '' : JSON.stringify(value)}
          onChange={(event) => {
            try {
              updateStageToolCallPayload(index, toolCallIndex, field, event.target.value.trim() ? JSON.parse(event.target.value) : undefined);
            } catch {
              setBuilderError(`Stage ${index + 1} payload ${field.name} JSON is invalid`);
            }
          }}
        />
      );
    }
    return (
      <Input
        aria-label={`Stage ${index + 1} tool call ${toolCallIndex + 1} payload ${field.name}`}
        style={{ width: 180 }}
        value={value === undefined || value === null ? '' : String(value)}
        onChange={(event) => updateStageToolCallPayload(index, toolCallIndex, field, event.target.value)}
      />
    );
  }

  function renderStagePayloadEditors(stage: PlanBuilderStage, index: number) {
    let toolCalls: unknown[];
    try {
      toolCalls = stage.toolCallsJson.trim() ? JSON.parse(stage.toolCallsJson) : [];
    } catch {
      return null;
    }
    if (!Array.isArray(toolCalls) || !toolCalls.length || !stageToolOptions[index]?.length) {
      return null;
    }
    return (
      <Space orientation="vertical" size={8} style={{ width: '100%' }}>
        {toolCalls.map((toolCall, toolCallIndex) => {
          if (!toolCall || typeof toolCall !== 'object' || Array.isArray(toolCall)) {
            return null;
          }
          const callRecord = toolCall as Record<string, unknown>;
          const tool = stageToolOptions[index].find((item) => item.toolId === callRecord.toolId || item.toolCode === callRecord.toolCode);
          const fields = schemaFieldsFromSchemaJson(tool?.schemaJson);
          if (!tool || fields.length === 0) {
            return null;
          }
          const payload = callRecord.callPayloadJson && typeof callRecord.callPayloadJson === 'object' && !Array.isArray(callRecord.callPayloadJson)
            ? (callRecord.callPayloadJson as Record<string, unknown>)
            : {};
          return (
            <Space key={`${tool.toolId}-${toolCallIndex}`} orientation="vertical" size={6} style={{ width: '100%' }}>
              <Typography.Text type="secondary">
                Payload fields: {tool.toolCode}
              </Typography.Text>
              <Space wrap align="start">
                {fields.map((field) => (
                  <Space key={field.name} direction="vertical" size={2}>
                    <Typography.Text type={field.required ? undefined : 'secondary'}>
                      {field.name}{field.required ? ' *' : ''}
                    </Typography.Text>
                    {renderPayloadInput(index, toolCallIndex, field, payload[field.name])}
                  </Space>
                ))}
              </Space>
            </Space>
          );
        })}
      </Space>
    );
  }

  return (
    <Space orientation="vertical" size={12} style={{ width: '100%' }}>
      <Typography.Text type="secondary">
        Submit a validated plan, then start orchestrated_team when validation passes.
      </Typography.Text>
      <Space orientation="vertical" size={8} style={{ width: '100%' }}>
        <Typography.Text strong>Plan generator</Typography.Text>
        {builderError ? <Alert type="warning" showIcon message={builderError} /> : null}
        <Space wrap align="start">
          <Input
            aria-label="Plan goal"
            style={{ width: 360 }}
            value={builderGoal}
            onChange={(event) => setBuilderGoal(event.target.value)}
          />
          <Input
            aria-label="Max threads"
            style={{ width: 120 }}
            value={builderMaxThreads}
            onChange={(event) => setBuilderMaxThreads(event.target.value)}
          />
          <Checkbox
            checked={builderHandoffPolicyStrict}
            onChange={(event) => setBuilderHandoffPolicyStrict(event.target.checked)}
          >
            strict handoff policy
          </Checkbox>
          <Button onClick={addBuilderStage}>Add stage</Button>
          <Button loading={loadingRoles} onClick={() => void loadRoles()}>
            Load roles
          </Button>
          <Button disabled={roleOptions.length === 0} onClick={applyRoleDefaults}>
            Apply role defaults
          </Button>
          <Button loading={loadingRoleBindings} disabled={!templateId} onClick={() => void loadRoleBindings()}>
            Load role bindings
          </Button>
          <Button disabled={roleBindingOptions.length === 0} onClick={applyRoleBindings}>
            Apply role bindings
          </Button>
          <Button loading={loadingAgents} onClick={() => void loadAgents()}>
            Load agents
          </Button>
          <Button onClick={loadAgileTeamPreset}>Agile team preset</Button>
          <Button type="primary" onClick={generatePlanJson}>
            Generate JSON
          </Button>
        </Space>
        <Space orientation="vertical" size={8} style={{ width: '100%' }}>
          {builderStages.map((stage, index) => (
            <div
              key={`builder-stage-${index}`}
              style={{ border: '1px solid #f0f0f0', borderRadius: 6, padding: 12, width: '100%' }}
            >
              <Space orientation="vertical" size={8} style={{ width: '100%' }}>
                <Space wrap align="start">
                  <Input
                    aria-label={`Stage ${index + 1} code`}
                    style={{ width: 180 }}
                    value={stage.stageCode}
                    onChange={(event) => updateBuilderStage(index, { stageCode: event.target.value })}
                  />
                  <Input
                    aria-label={`Stage ${index + 1} stage name`}
                    style={{ width: 240 }}
                    value={stage.stageName}
                    onChange={(event) => updateBuilderStage(index, { stageName: event.target.value })}
                  />
                  <Input
                    aria-label={`Stage ${index + 1} role code`}
                    style={{ width: 210 }}
                    value={stage.roleCode}
                    onChange={(event) => updateBuilderStage(index, { roleCode: event.target.value })}
                  />
                  <Select
                    aria-label={`Stage ${index + 1} role`}
                    placeholder="Select role"
                    style={{ width: 240 }}
                    value={stage.roleCode || undefined}
                    options={roleOptions.map((role) => ({
                      value: role.roleCode,
                      label: `${roleLabel(role)} (${role.roleCode})`,
                    }))}
                    onChange={(value) => updateBuilderStage(index, { roleCode: value })}
                    disabled={roleOptions.length === 0}
                  />
                  <Input
                    aria-label={`Stage ${index + 1} agent ID`}
                    style={{ width: 120 }}
                    value={stage.agentId}
                    onChange={(event) => updateBuilderStage(index, { agentId: event.target.value })}
                  />
                  <Select
                    aria-label={`Stage ${index + 1} agent`}
                    placeholder="Select agent"
                    style={{ width: 220 }}
                    value={stage.agentId ? Number(stage.agentId) : undefined}
                    options={agentOptions.map((agent) => ({
                      value: agent.id,
                      label: `${agentLabel(agent)} #${agent.id}`,
                    }))}
                    onChange={(value) => selectStageAgent(index, value)}
                    disabled={agentOptions.length === 0}
                  />
                  <Input
                    aria-label={`Stage ${index + 1} agent version ID`}
                    style={{ width: 150 }}
                    value={stage.agentVersionId}
                    onChange={(event) => updateBuilderStage(index, { agentVersionId: event.target.value })}
                  />
                  <Button loading={loadingToolsStage === index} onClick={() => void loadStageTools(index)}>
                    Load version tools
                  </Button>
                  <Checkbox
                    checked={stage.createTask}
                    onChange={(event) => updateBuilderStage(index, { createTask: event.target.checked })}
                  >
                    createTask
                  </Checkbox>
                  <Checkbox
                    checked={stage.autoStartTask}
                    onChange={(event) => updateBuilderStage(index, { autoStartTask: event.target.checked })}
                  >
                    autoStartTask
                  </Checkbox>
                  <Checkbox
                    checked={stage.waitForHandoffAcceptance}
                    onChange={(event) => updateBuilderStage(index, { waitForHandoffAcceptance: event.target.checked })}
                  >
                    wait handoff
                  </Checkbox>
                  <Input
                    aria-label={`Stage ${index + 1} input artifact version`}
                    placeholder="artifact v"
                    style={{ width: 130 }}
                    value={stage.inputArtifactVersion}
                    onChange={(event) => updateBuilderStage(index, { inputArtifactVersion: event.target.value })}
                  />
                  <Button danger disabled={builderStages.length === 1} onClick={() => removeBuilderStage(index)}>
                    Remove stage
                  </Button>
                </Space>
                <Input.TextArea
                  aria-label={`Stage ${index + 1} input text`}
                  rows={2}
                  value={stage.inputText}
                  onChange={(event) => updateBuilderStage(index, { inputText: event.target.value })}
                />
                <Space wrap align="start">
                  <Input
                    aria-label={`Stage ${index + 1} gate code`}
                    style={{ width: 180 }}
                    value={stage.gateCode}
                    onChange={(event) => updateBuilderStage(index, { gateCode: event.target.value })}
                  />
                  <Input
                    aria-label={`Stage ${index + 1} gate name`}
                    style={{ width: 220 }}
                    value={stage.gateName}
                    onChange={(event) => updateBuilderStage(index, { gateName: event.target.value })}
                  />
                  <Input
                    aria-label={`Stage ${index + 1} rework stage code`}
                    style={{ width: 190 }}
                    value={stage.reworkStageCode}
                    onChange={(event) => updateBuilderStage(index, { reworkStageCode: event.target.value })}
                  />
                </Space>
                <Input.TextArea
                  aria-label={`Stage ${index + 1} tool calls JSON`}
                  rows={3}
                  value={stage.toolCallsJson}
                  onChange={(event) => updateBuilderStage(index, { toolCallsJson: event.target.value })}
                />
                {stageToolOptions[index]?.length ? (
                  <Space wrap>
                    {stageToolOptions[index].map((tool) => (
                      <Button key={tool.toolId} size="small" onClick={() => appendStageToolCall(index, tool)}>
                        <Space size={4}>
                          <span>{tool.toolCode}</span>
                          <Tag color={tool.riskLevel === 'high' ? 'red' : tool.riskLevel === 'medium' ? 'orange' : 'green'}>
                            {tool.toolType}
                          </Tag>
                        </Space>
                      </Button>
                    ))}
                  </Space>
                ) : null}
                {renderStagePayloadEditors(stage, index)}
              </Space>
            </div>
          ))}
        </Space>
      </Space>
      {error ? <Alert type="warning" showIcon message={error} /> : null}
      <Input.TextArea
        aria-label="Collaboration plan JSON"
        rows={12}
        value={planJson}
        onChange={(event) => setPlanJson(event.target.value)}
      />
      <Space wrap>
        <Button onClick={() => setPlanJson(formatJson(defaultPlanJson))}>Load example</Button>
        {templatePlanJson ? (
          <>
            <Button onClick={importSessionTemplate}>Import session template</Button>
            <Button onClick={() => setPlanJson(formatJson(templatePlanJson))}>Load session template</Button>
          </>
        ) : null}
        <Button onClick={() => setPlanJson(formatJson(planJson))} disabled={Boolean(error)}>
          Format JSON
        </Button>
        <Button type="primary" loading={loading} disabled={Boolean(error)} onClick={() => onSubmitAndStart(planJson)}>
          Submit and start
        </Button>
      </Space>
    </Space>
  );
}
