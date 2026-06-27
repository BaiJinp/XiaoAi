import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { CollaborationPlanSubmitCard } from './CollaborationPlanSubmitCard';

describe('CollaborationPlanSubmitCard', () => {
  it('generates plan JSON from the visual builder and keeps the submit flow', async () => {
    const user = userEvent.setup();
    const onSubmitAndStart = vi.fn();

    render(
      <CollaborationPlanSubmitCard
        defaultPlanJson='{"goal":"example","stages":[]}'
        onSubmitAndStart={onSubmitAndStart}
      />,
    );

    await user.clear(screen.getByLabelText('Plan goal'));
    await user.type(screen.getByLabelText('Plan goal'), 'Ship invoicing workflow');
    await user.click(screen.getByRole('checkbox', { name: 'strict handoff policy' }));
    fireEvent.change(screen.getByLabelText('Stage 1 tool calls JSON'), {
      target: {
        value: '[{"toolId":22,"toolCode":"controlled.http.project-query","callPayloadJson":{"query":"scope"}}]',
      },
    });
    await user.click(screen.getByRole('button', { name: 'Generate JSON' }));

    const planTextArea = screen.getByLabelText('Collaboration plan JSON') as HTMLTextAreaElement;
    const generatedPlan = JSON.parse(planTextArea.value);
    expect(generatedPlan.goal).toBe('Ship invoicing workflow');
    expect(generatedPlan.maxThreads).toBe(6);
    expect(generatedPlan.handoffPolicy).toEqual({
      mode: 'strict',
      requireAcceptedBeforeConsume: true,
    });
    expect(generatedPlan.stages[0]).toMatchObject({
      stageCode: 'requirement_analysis',
      stageName: 'Requirement analysis',
      roleCode: 'software_product_manager',
      agentId: 101,
      agentVersionId: 1001,
      createTask: true,
      autoStartTask: true,
      waitForHandoffAcceptance: false,
      requiresGate: 'requirement_confirmed',
      toolCalls: [
        {
          toolId: 22,
          toolCode: 'controlled.http.project-query',
          callPayloadJson: { query: 'scope' },
        },
      ],
    });

    await user.click(screen.getByRole('button', { name: 'Submit and start' }));
    expect(onSubmitAndStart).toHaveBeenCalledWith(planTextArea.value);
  });

  it('shows builder validation errors before writing invalid toolCalls', async () => {
    const user = userEvent.setup();

    render(
      <CollaborationPlanSubmitCard
        defaultPlanJson='{"goal":"example","stages":[]}'
        onSubmitAndStart={vi.fn()}
      />,
    );

    fireEvent.change(screen.getByLabelText('Stage 1 tool calls JSON'), { target: { value: '{"toolId":22}' } });
    await user.click(screen.getByRole('button', { name: 'Generate JSON' }));

    expect(screen.getByText('Stage 1 tool calls JSON must be an array')).toBeInTheDocument();
    expect((screen.getByLabelText('Collaboration plan JSON') as HTMLTextAreaElement).value).toContain('"example"');
  });

  it('restores the agile team preset before generating a plan', async () => {
    const user = userEvent.setup();

    render(
      <CollaborationPlanSubmitCard
        defaultPlanJson='{"goal":"example","stages":[]}'
        onSubmitAndStart={vi.fn()}
      />,
    );

    fireEvent.change(screen.getByLabelText('Stage 1 code'), { target: { value: 'custom_stage' } });
    await user.click(screen.getByRole('button', { name: 'Agile team preset' }));
    await user.click(screen.getByRole('button', { name: 'Generate JSON' }));

    const generatedPlan = JSON.parse((screen.getByLabelText('Collaboration plan JSON') as HTMLTextAreaElement).value);
    expect(generatedPlan.stages.map((stage: { stageCode: string }) => stage.stageCode)).toEqual([
      'requirement_analysis',
      'technical_design',
      'backend_implementation',
      'frontend_implementation',
      'testing',
      'review_and_delivery',
    ]);
    expect(generatedPlan.maxThreads).toBe(6);
    expect(generatedPlan.stages.map((stage: { autoStartTask: boolean }) => stage.autoStartTask)).toEqual([
      true,
      true,
      true,
      true,
      true,
      true,
    ]);
    expect(generatedPlan.stages.map((stage: { requiresGate: string }) => stage.requiresGate)).toEqual([
      'requirement_confirmed',
      'design_confirmed',
      'implementation_done',
      'implementation_done',
      'tests_passed',
      'delivery_confirmed',
    ]);
  });

  it('loads a session template plan into the raw JSON editor', async () => {
    const user = userEvent.setup();

    render(
      <CollaborationPlanSubmitCard
        defaultPlanJson='{"goal":"example","stages":[]}'
        templatePlanJson='{"goal":"from template","stages":[{"stageCode":"requirement_analysis"}]}'
        onSubmitAndStart={vi.fn()}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Load session template' }));

    const plan = JSON.parse((screen.getByLabelText('Collaboration plan JSON') as HTMLTextAreaElement).value);
    expect(plan.goal).toBe('from template');
    expect(plan.stages[0].stageCode).toBe('requirement_analysis');
  });

  it('imports a session template plan into the editable builder rows', async () => {
    const user = userEvent.setup();

    render(
      <CollaborationPlanSubmitCard
        defaultPlanJson='{"goal":"example","stages":[]}'
        templatePlanJson={JSON.stringify({
          goal: 'from template',
          maxThreads: 2,
          handoffPolicy: {
            mode: 'strict',
            requireAcceptedBeforeConsume: true,
          },
          stages: [
            {
              stageCode: 'requirement_analysis',
              stageName: 'Requirement analysis',
              roleCode: 'software_product_manager',
              agentId: 201,
              agentVersionId: 2001,
              createTask: true,
              autoStartTask: false,
              waitForHandoffAcceptance: true,
              inputArtifactVersion: 2,
              inputText: 'Analyze requirement',
              requiresGate: 'requirement_confirmed',
              gateName: 'Requirement confirmed',
              reworkStageCode: 'requirement_rework',
              toolCalls: [{ toolId: 22, toolCode: 'controlled.http.project-query' }],
            },
          ],
        })}
        onSubmitAndStart={vi.fn()}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Import session template' }));

    expect(screen.getByLabelText('Plan goal')).toHaveValue('from template');
    expect(screen.getByLabelText('Max threads')).toHaveValue('2');
    expect(screen.getByRole('checkbox', { name: 'strict handoff policy' })).toBeChecked();
    expect(screen.getByLabelText('Stage 1 code')).toHaveValue('requirement_analysis');
    expect(screen.getByLabelText('Stage 1 stage name')).toHaveValue('Requirement analysis');
    expect(screen.getByLabelText('Stage 1 role code')).toHaveValue('software_product_manager');
    expect(screen.getByLabelText('Stage 1 agent ID')).toHaveValue('201');
    expect(screen.getByLabelText('Stage 1 agent version ID')).toHaveValue('2001');
    expect(screen.getByLabelText('Stage 1 input artifact version')).toHaveValue('2');
    expect(screen.getByLabelText('Stage 1 input text')).toHaveValue('Analyze requirement');
    expect(screen.getByLabelText('Stage 1 gate code')).toHaveValue('requirement_confirmed');
    expect(screen.getByLabelText('Stage 1 gate name')).toHaveValue('Requirement confirmed');
    expect(screen.getByLabelText('Stage 1 rework stage code')).toHaveValue('requirement_rework');

    await user.click(screen.getByRole('button', { name: 'Generate JSON' }));

    const plan = JSON.parse((screen.getByLabelText('Collaboration plan JSON') as HTMLTextAreaElement).value);
    expect(plan.goal).toBe('from template');
    expect(plan.maxThreads).toBe(2);
    expect(plan.handoffPolicy).toEqual({
      mode: 'strict',
      requireAcceptedBeforeConsume: true,
    });
    expect(plan.stages[0]).toMatchObject({
      stageCode: 'requirement_analysis',
      stageName: 'Requirement analysis',
      roleCode: 'software_product_manager',
      agentId: 201,
      agentVersionId: 2001,
      waitForHandoffAcceptance: true,
      inputArtifactVersion: 2,
      requiresGate: 'requirement_confirmed',
      reworkStageCode: 'requirement_rework',
    });
    expect(plan.stages[0].toolCalls).toEqual([{ toolId: 22, toolCode: 'controlled.http.project-query' }]);
  });

  it('generates waitForHandoffAcceptance when selected in the builder', async () => {
    const user = userEvent.setup();

    render(
      <CollaborationPlanSubmitCard
        defaultPlanJson='{"goal":"example","stages":[]}'
        onSubmitAndStart={vi.fn()}
      />,
    );

    await user.click(screen.getAllByRole('checkbox', { name: 'wait handoff' })[0]);
    await user.click(screen.getByRole('button', { name: 'Generate JSON' }));

    const plan = JSON.parse((screen.getByLabelText('Collaboration plan JSON') as HTMLTextAreaElement).value);
    expect(plan.stages[0].waitForHandoffAcceptance).toBe(true);
  });

  it('validates input artifact version before generating JSON', async () => {
    const user = userEvent.setup();

    render(
      <CollaborationPlanSubmitCard
        defaultPlanJson='{"goal":"example","stages":[]}'
        onSubmitAndStart={vi.fn()}
      />,
    );

    await user.type(screen.getByLabelText('Stage 1 input artifact version'), '0');
    await user.click(screen.getByRole('button', { name: 'Generate JSON' }));

    expect(screen.getByText('Stage 1 input artifact version must be a positive integer')).toBeInTheDocument();
  });

  it('shows all missing stage bindings after importing an unbound session template', async () => {
    const user = userEvent.setup();

    render(
      <CollaborationPlanSubmitCard
        defaultPlanJson='{"goal":"example","stages":[]}'
        templatePlanJson={JSON.stringify({
          goal: 'from template',
          stages: [
            { stageCode: 'requirement_analysis', stageName: 'Requirement analysis', roleCode: 'software_product_manager' },
            { stageCode: 'technical_design', stageName: 'Technical design', roleCode: 'software_architect' },
          ],
        })}
        onSubmitAndStart={vi.fn()}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Import session template' }));
    await user.click(screen.getByRole('button', { name: 'Generate JSON' }));

    expect(screen.getByText(/Stage 1 agent ID is required/)).toBeInTheDocument();
    expect(screen.getByText(/Stage 1 agent version ID is required/)).toBeInTheDocument();
    expect(screen.getByText(/Stage 2 agent ID is required/)).toBeInTheDocument();
    expect(screen.getByText(/Stage 2 agent version ID is required/)).toBeInTheDocument();
  });

  it('loads agents and fills stage agent/version ids from selection', async () => {
    const user = userEvent.setup();
    const onListAgents = vi.fn().mockResolvedValue({
      pageNo: 1,
      pageSize: 100,
      total: 1,
      records: [
        {
          id: 301,
          agentName: 'Product Agent',
          status: 'published',
          latestStableVersionId: 3001,
        },
      ],
    });

    render(
      <CollaborationPlanSubmitCard
        defaultPlanJson='{"goal":"example","stages":[]}'
        onSubmitAndStart={vi.fn()}
        onListAgents={onListAgents}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Load agents' }));
    await waitFor(() => expect(onListAgents).toHaveBeenCalledTimes(1));
    await user.click(screen.getByLabelText('Stage 1 agent'));
    await user.click(await screen.findByText('Product Agent #301'));

    expect(screen.getByLabelText('Stage 1 agent ID')).toHaveValue('301');
    expect(screen.getByLabelText('Stage 1 agent version ID')).toHaveValue('3001');

    await user.click(screen.getByRole('button', { name: 'Generate JSON' }));

    const plan = JSON.parse((screen.getByLabelText('Collaboration plan JSON') as HTMLTextAreaElement).value);
    expect(plan.stages[0]).toMatchObject({
      agentId: 301,
      agentVersionId: 3001,
    });
  });

  it('loads roles and fills stage role code from selection', async () => {
    const user = userEvent.setup();
    const onListAgentRoles = vi.fn().mockResolvedValue([
      {
        id: 2,
        roleCode: 'software_architect',
        roleName: 'Software Architect',
        domainCode: 'software_development',
        status: 'active',
      },
    ]);

    render(
      <CollaborationPlanSubmitCard
        defaultPlanJson='{"goal":"example","stages":[]}'
        onSubmitAndStart={vi.fn()}
        onListAgentRoles={onListAgentRoles}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Load roles' }));
    await waitFor(() => expect(onListAgentRoles).toHaveBeenCalledTimes(1));
    await user.click(screen.getByLabelText('Stage 1 role'));
    const architectOptions = await screen.findAllByText('Software Architect (software_architect)');
    await user.click(architectOptions[architectOptions.length - 1]);

    expect(screen.getByLabelText('Stage 1 role code')).toHaveValue('software_architect');

    await user.click(screen.getByRole('button', { name: 'Generate JSON' }));

    const plan = JSON.parse((screen.getByLabelText('Collaboration plan JSON') as HTMLTextAreaElement).value);
    expect(plan.stages[0].roleCode).toBe('software_architect');
  });

  it('applies role default agent bindings to imported template stages', async () => {
    const user = userEvent.setup();
    const onListAgentRoles = vi.fn().mockResolvedValue([
      {
        id: 2,
        roleCode: 'software_product_manager',
        roleName: 'Product Manager',
        domainCode: 'software_development',
        defaultAgentId: 301,
        defaultAgentVersionId: 3001,
        status: 'active',
      },
      {
        id: 3,
        roleCode: 'software_architect',
        roleName: 'Software Architect',
        domainCode: 'software_development',
        defaultAgentId: 302,
        defaultAgentVersionId: 3002,
        status: 'active',
      },
    ]);

    render(
      <CollaborationPlanSubmitCard
        defaultPlanJson='{"goal":"example","stages":[]}'
        templatePlanJson={JSON.stringify({
          goal: 'from template',
          stages: [
            { stageCode: 'requirement_analysis', stageName: 'Requirement analysis', roleCode: 'software_product_manager' },
            { stageCode: 'technical_design', stageName: 'Technical design', roleCode: 'software_architect' },
          ],
        })}
        onSubmitAndStart={vi.fn()}
        onListAgentRoles={onListAgentRoles}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Import session template' }));
    await user.click(screen.getByRole('button', { name: 'Load roles' }));
    await waitFor(() => expect(onListAgentRoles).toHaveBeenCalledTimes(1));
    await user.click(screen.getByRole('button', { name: 'Apply role defaults' }));

    expect(screen.getByLabelText('Stage 1 agent ID')).toHaveValue('301');
    expect(screen.getByLabelText('Stage 1 agent version ID')).toHaveValue('3001');
    expect(screen.getByLabelText('Stage 2 agent ID')).toHaveValue('302');
    expect(screen.getByLabelText('Stage 2 agent version ID')).toHaveValue('3002');

    await user.click(screen.getByRole('button', { name: 'Generate JSON' }));

    const plan = JSON.parse((screen.getByLabelText('Collaboration plan JSON') as HTMLTextAreaElement).value);
    expect(plan.stages[0]).toMatchObject({ agentId: 301, agentVersionId: 3001 });
    expect(plan.stages[1]).toMatchObject({ agentId: 302, agentVersionId: 3002 });
  });

  it('applies template role bindings before global role defaults', async () => {
    const user = userEvent.setup();
    const onListRoleBindings = vi.fn().mockResolvedValue([
      {
        roleCode: 'software_product_manager',
        templateId: 2,
        effectiveAgentId: 401,
        effectiveAgentVersionId: 4001,
        source: 'template',
      },
      {
        roleCode: 'software_architect',
        templateId: 2,
        effectiveAgentId: 402,
        effectiveAgentVersionId: 4002,
        source: 'template',
      },
    ]);

    render(
      <CollaborationPlanSubmitCard
        defaultPlanJson='{"goal":"example","stages":[]}'
        templateId={2}
        templatePlanJson={JSON.stringify({
          goal: 'from template',
          stages: [
            { stageCode: 'requirement_analysis', stageName: 'Requirement analysis', roleCode: 'software_product_manager' },
            { stageCode: 'technical_design', stageName: 'Technical design', roleCode: 'software_architect' },
          ],
        })}
        onSubmitAndStart={vi.fn()}
        onListRoleBindings={onListRoleBindings}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Import session template' }));
    await user.click(screen.getByRole('button', { name: 'Load role bindings' }));
    await waitFor(() => expect(onListRoleBindings).toHaveBeenCalledWith(2));
    await user.click(screen.getByRole('button', { name: 'Apply role bindings' }));

    expect(screen.getByLabelText('Stage 1 agent ID')).toHaveValue('401');
    expect(screen.getByLabelText('Stage 1 agent version ID')).toHaveValue('4001');
    expect(screen.getByLabelText('Stage 2 agent ID')).toHaveValue('402');
    expect(screen.getByLabelText('Stage 2 agent version ID')).toHaveValue('4002');
  });

  it('does not overwrite manually selected agents when applying template role bindings', async () => {
    const user = userEvent.setup();
    const onListRoleBindings = vi.fn().mockResolvedValue([
      {
        roleCode: 'software_product_manager',
        templateId: 2,
        effectiveAgentId: 401,
        effectiveAgentVersionId: 4001,
        source: 'template',
      },
    ]);

    render(
      <CollaborationPlanSubmitCard
        defaultPlanJson='{"goal":"example","stages":[]}'
        templateId={2}
        onSubmitAndStart={vi.fn()}
        onListRoleBindings={onListRoleBindings}
      />,
    );

    fireEvent.change(screen.getByLabelText('Stage 1 agent ID'), { target: { value: '999' } });
    fireEvent.change(screen.getByLabelText('Stage 1 agent version ID'), { target: { value: '9999' } });
    await user.click(screen.getByRole('button', { name: 'Load role bindings' }));
    await user.click(screen.getByRole('button', { name: 'Apply role bindings' }));

    expect(screen.getByLabelText('Stage 1 agent ID')).toHaveValue('999');
    expect(screen.getByLabelText('Stage 1 agent version ID')).toHaveValue('9999');
  });

  it('can fallback to global role defaults when template role bindings miss a role', async () => {
    const user = userEvent.setup();
    const onListRoleBindings = vi.fn().mockResolvedValue([
      {
        roleCode: 'software_product_manager',
        templateId: 2,
        effectiveAgentId: 401,
        effectiveAgentVersionId: 4001,
        source: 'template',
      },
    ]);
    const onListAgentRoles = vi.fn().mockResolvedValue([
      {
        id: 3,
        roleCode: 'software_architect',
        roleName: 'Software Architect',
        domainCode: 'software_development',
        defaultAgentId: 302,
        defaultAgentVersionId: 3002,
        status: 'active',
      },
    ]);

    render(
      <CollaborationPlanSubmitCard
        defaultPlanJson='{"goal":"example","stages":[]}'
        templateId={2}
        templatePlanJson={JSON.stringify({
          goal: 'from template',
          stages: [
            { stageCode: 'requirement_analysis', stageName: 'Requirement analysis', roleCode: 'software_product_manager' },
            { stageCode: 'technical_design', stageName: 'Technical design', roleCode: 'software_architect' },
          ],
        })}
        onSubmitAndStart={vi.fn()}
        onListRoleBindings={onListRoleBindings}
        onListAgentRoles={onListAgentRoles}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Import session template' }));
    await user.click(screen.getByRole('button', { name: 'Load role bindings' }));
    await user.click(screen.getByRole('button', { name: 'Apply role bindings' }));
    await user.click(screen.getByRole('button', { name: 'Load roles' }));
    await waitFor(() => expect(onListAgentRoles).toHaveBeenCalledTimes(1));
    await user.click(screen.getByRole('button', { name: 'Apply role defaults' }));

    expect(screen.getByLabelText('Stage 1 agent ID')).toHaveValue('401');
    expect(screen.getByLabelText('Stage 1 agent version ID')).toHaveValue('4001');
    expect(screen.getByLabelText('Stage 2 agent ID')).toHaveValue('302');
    expect(screen.getByLabelText('Stage 2 agent version ID')).toHaveValue('3002');
  });

  it('loads AgentVersion tool scope and appends a tool call template', async () => {
    const user = userEvent.setup();
    const onListAgentVersions = vi.fn().mockResolvedValue([
      {
        agentVersionId: 1001,
        versionNo: 'v1',
        versionStatus: 'published',
        toolIds: [22],
      },
    ]);
    const onListPluginManifests = vi.fn().mockResolvedValue([
      {
        pluginCode: 'project-http',
        pluginName: 'Project HTTP',
        pluginVersion: '1.0.0',
        status: 'active',
        tools: [
          {
            toolId: 22,
            toolCode: 'controlled.http.project-query',
            toolName: 'Project Query',
            toolType: 'http',
            riskLevel: 'low',
            status: 'active',
            schemaJson:
              '{"type":"object","properties":{"query":{"type":"string","default":"scope"},"limit":{"type":"integer","default":10},"dryRun":{"type":"boolean"}}}',
          },
          {
            toolId: 23,
            toolCode: 'controlled.cli.project-update',
            toolName: 'Project Update',
            toolType: 'cli',
            riskLevel: 'high',
            status: 'active',
          },
        ],
      },
    ]);

    render(
      <CollaborationPlanSubmitCard
        defaultPlanJson='{"goal":"example","stages":[]}'
        onSubmitAndStart={vi.fn()}
        onListAgentVersions={onListAgentVersions}
        onListPluginManifests={onListPluginManifests}
      />,
    );

    await user.click(screen.getAllByRole('button', { name: 'Load version tools' })[0]);

    await waitFor(() => expect(onListAgentVersions).toHaveBeenCalledWith(101));
    expect(onListPluginManifests).toHaveBeenCalledTimes(1);
    await user.click(await screen.findByRole('button', { name: /controlled\.http\.project-query/ }));

    const toolCalls = JSON.parse((screen.getByLabelText('Stage 1 tool calls JSON') as HTMLTextAreaElement).value);
    expect(toolCalls).toEqual([
      {
        toolId: 22,
        toolCode: 'controlled.http.project-query',
        toolType: 'http',
        callPayloadJson: {
          query: 'scope',
          limit: 10,
          dryRun: false,
        },
      },
    ]);
    expect(screen.queryByRole('button', { name: /controlled\.cli\.project-update/ })).not.toBeInTheDocument();
  });

  it('edits appended tool payload through schema-driven fields', async () => {
    const user = userEvent.setup();
    const onListAgentVersions = vi.fn().mockResolvedValue([
      {
        agentVersionId: 1001,
        versionNo: 'v1',
        versionStatus: 'published',
        toolIds: [22],
      },
    ]);
    const onListPluginManifests = vi.fn().mockResolvedValue([
      {
        pluginCode: 'project-http',
        pluginName: 'Project HTTP',
        pluginVersion: '1.0.0',
        status: 'active',
        tools: [
          {
            toolId: 22,
            toolCode: 'controlled.http.project-query',
            toolName: 'Project Query',
            toolType: 'http',
            riskLevel: 'low',
            status: 'active',
            schemaJson: JSON.stringify({
              type: 'object',
              required: ['query'],
              properties: {
                query: { type: 'string', default: 'scope' },
                limit: { type: 'integer', default: 10 },
                dryRun: { type: 'boolean' },
                status: { type: 'string', enum: ['open', 'closed'] },
              },
            }),
          },
        ],
      },
    ]);

    render(
      <CollaborationPlanSubmitCard
        defaultPlanJson='{"goal":"example","stages":[]}'
        onSubmitAndStart={vi.fn()}
        onListAgentVersions={onListAgentVersions}
        onListPluginManifests={onListPluginManifests}
      />,
    );

    await user.click(screen.getAllByRole('button', { name: 'Load version tools' })[0]);
    await user.click(await screen.findByRole('button', { name: /controlled\.http\.project-query/ }));

    await user.clear(screen.getByLabelText('Stage 1 tool call 1 payload query'));
    await user.type(screen.getByLabelText('Stage 1 tool call 1 payload query'), 'customer overdue');
    await user.clear(screen.getByLabelText('Stage 1 tool call 1 payload limit'));
    await user.type(screen.getByLabelText('Stage 1 tool call 1 payload limit'), '25');
    await user.click(screen.getByLabelText('Stage 1 tool call 1 payload dryRun'));
    await user.selectOptions(screen.getByLabelText('Stage 1 tool call 1 payload status'), 'closed');
    await user.click(screen.getByRole('button', { name: 'Generate JSON' }));

    const plan = JSON.parse((screen.getByLabelText('Collaboration plan JSON') as HTMLTextAreaElement).value);
    expect(plan.stages[0].toolCalls[0].callPayloadJson).toEqual({
      query: 'customer overdue',
      limit: 25,
      dryRun: true,
      status: 'closed',
    });
  });

  it('keeps the previous integer payload value when integer input is fractional', async () => {
    const user = userEvent.setup();
    const onListAgentVersions = vi.fn().mockResolvedValue([
      {
        agentVersionId: 1001,
        versionNo: 'v1',
        versionStatus: 'published',
        toolIds: [22],
      },
    ]);
    const onListPluginManifests = vi.fn().mockResolvedValue([
      {
        pluginCode: 'project-http',
        pluginName: 'Project HTTP',
        pluginVersion: '1.0.0',
        status: 'active',
        tools: [
          {
            toolId: 22,
            toolCode: 'controlled.http.project-query',
            toolName: 'Project Query',
            toolType: 'http',
            riskLevel: 'low',
            status: 'active',
            schemaJson: JSON.stringify({
              type: 'object',
              properties: {
                limit: { type: 'integer', default: 10 },
              },
            }),
          },
        ],
      },
    ]);

    render(
      <CollaborationPlanSubmitCard
        defaultPlanJson='{"goal":"example","stages":[]}'
        onSubmitAndStart={vi.fn()}
        onListAgentVersions={onListAgentVersions}
        onListPluginManifests={onListPluginManifests}
      />,
    );

    await user.click(screen.getAllByRole('button', { name: 'Load version tools' })[0]);
    await user.click(await screen.findByRole('button', { name: /controlled\.http\.project-query/ }));

    await user.clear(screen.getByLabelText('Stage 1 tool call 1 payload limit'));
    await user.type(screen.getByLabelText('Stage 1 tool call 1 payload limit'), '1.5');

    expect(screen.getByText('Stage 1 payload limit must be an integer')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Generate JSON' }));

    const plan = JSON.parse((screen.getByLabelText('Collaboration plan JSON') as HTMLTextAreaElement).value);
    expect(plan.stages[0].toolCalls[0].callPayloadJson).toEqual({
      limit: 1,
    });
  });
});
