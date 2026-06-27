import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ProjectAssistantForm } from './ProjectAssistantForm';

describe('ProjectAssistantForm', () => {
  it('groups active tools by plugin and filters tools locally', async () => {
    const onListPlugins = vi.fn().mockResolvedValue([
      {
        pluginCode: 'project-cli',
        pluginName: 'Project CLI',
        pluginVersion: '1.0.0',
        status: 'active',
        tools: [
          {
            toolId: 22,
            toolCode: 'controlled.cli.project-report',
            toolName: 'Project Report',
            toolType: 'cli',
            riskLevel: 'medium',
            status: 'active',
          },
          {
            toolId: 23,
            toolCode: 'controlled.cli.delete-project',
            toolName: 'Delete Project',
            toolType: 'cli',
            riskLevel: 'high',
            status: 'inactive',
          },
        ],
      },
      {
        pluginCode: 'calendar-tools',
        pluginName: 'Calendar Tools',
        pluginVersion: '1.0.0',
        status: 'active',
        tools: [
          {
            toolId: 24,
            toolCode: 'calendar.meeting.summary',
            toolName: 'Meeting Summary',
            toolType: 'http',
            riskLevel: 'low',
            status: 'active',
          },
        ],
      },
    ]);
    const user = userEvent.setup();

    render(<ProjectAssistantForm onListPlugins={onListPlugins} />);

    expect(await screen.findByText('Project CLI (project-cli)')).toBeInTheDocument();
    expect(screen.getByText('Calendar Tools (calendar-tools)')).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: /Project Report/ })).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: /Meeting Summary/ })).toBeInTheDocument();
    expect(screen.queryByRole('checkbox', { name: /Delete Project/ })).not.toBeInTheDocument();

    const searchInput = screen.getByPlaceholderText('Search tools');
    await user.type(searchInput, 'calendar');

    expect(screen.queryByText('Project CLI (project-cli)')).not.toBeInTheDocument();
    expect(screen.getByText('Calendar Tools (calendar-tools)')).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: /Meeting Summary/ })).toBeInTheDocument();
    expect(screen.queryByRole('checkbox', { name: /Project Report/ })).not.toBeInTheDocument();

    await user.clear(searchInput);
    await user.type(searchInput, 'medium');

    expect(screen.getByText('Project CLI (project-cli)')).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: /Project Report/ })).toBeInTheDocument();
    expect(screen.queryByRole('checkbox', { name: /Meeting Summary/ })).not.toBeInTheDocument();

    await user.clear(searchInput);
    await user.type(searchInput, 'http');

    expect(screen.getByText('Calendar Tools (calendar-tools)')).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: /Meeting Summary/ })).toBeInTheDocument();
    expect(screen.queryByRole('checkbox', { name: /Project Report/ })).not.toBeInTheDocument();
  });

  it('creates provider, model config, draft, and draft version', async () => {
    const onCreateProvider = vi.fn().mockResolvedValue({ id: 10, providerCode: 'openai-prod', status: 'active' });
    const onCreateModelConfig = vi.fn().mockResolvedValue({ id: 11, modelCode: 'gpt-4o-mini', status: 'active' });
    const onCreateDraft = vi.fn().mockResolvedValue({ agentId: 12, agentCode: 'AGENT-12', status: 'draft' });
    const onListPlugins = vi.fn().mockResolvedValue([
      {
        pluginCode: 'project-cli',
        pluginName: 'Project CLI',
        pluginVersion: '1.0.0',
        status: 'active',
        tools: [
          {
            toolId: 22,
            toolCode: 'controlled.cli.project-report',
            toolName: 'Project Report',
            toolType: 'cli',
            riskLevel: 'medium',
            status: 'active',
          },
        ],
      },
    ]);
    const onCreateVersion = vi.fn().mockResolvedValue({
      agentVersionId: 13,
      versionNo: 'v20260612230000',
      versionStatus: 'draft',
      toolIds: [22],
    });
    const onReplaceVersionTools = vi.fn().mockResolvedValue({
      agentVersionId: 13,
      versionNo: 'v20260612230000',
      versionStatus: 'draft',
      toolIds: [],
    });
    const user = userEvent.setup();

    const { container } = render(
      <ProjectAssistantForm
        onCreateProvider={onCreateProvider}
        onCreateModelConfig={onCreateModelConfig}
        onCreateDraft={onCreateDraft}
        onCreateVersion={onCreateVersion}
        onReplaceVersionTools={onReplaceVersionTools}
        onListPlugins={onListPlugins}
      />,
    );

    const projectReportTool = await screen.findByRole('checkbox', { name: /Project Report/ });
    expect(screen.getByLabelText('模型策略 JSON')).toBeInTheDocument();
    expect(screen.getByLabelText('工具策略 JSON')).toBeInTheDocument();
    expect(screen.getByLabelText('上下文策略 JSON')).toBeInTheDocument();
    expect(screen.getByLabelText('记忆策略 JSON')).toBeInTheDocument();
    expect(screen.getByLabelText('编排策略 JSON')).toBeInTheDocument();
    expect(screen.getByText('Tool scope is saved into a new AgentVersion snapshot')).toBeInTheDocument();
    expect(screen.getByText('Published versions are not edited in place. To change allowed tools, create and publish a new AgentVersion.')).toBeInTheDocument();
    await user.click(projectReportTool);
    expect(projectReportTool).toBeChecked();
    fireEvent.change(screen.getByLabelText('API Key'), { target: { value: 'sk-live-secret' } });
    fireEvent.change(screen.getByLabelText('编排策略 JSON'), { target: { value: '{"executionMode":"single_agent"}' } });
    fireEvent.submit(container.querySelector('form') as HTMLFormElement);

    await waitFor(() =>
      expect(onCreateProvider).toHaveBeenCalledWith({
        providerCode: 'openai-prod',
        providerName: 'OpenAI Production',
        providerType: 'openai_compatible',
        baseUrl: 'https://api.openai.com/v1',
        apiKey: 'sk-live-secret',
      }),
    );
    expect(onCreateModelConfig).toHaveBeenCalledWith({
      providerId: 10,
      modelCode: 'gpt-4o-mini',
      modelName: 'GPT-4o Mini',
      modelType: 'chat',
      contextWindow: 128000,
      configJson: '{"temperature":0.2}',
    });
    expect(onCreateDraft).toHaveBeenCalledWith(
      expect.objectContaining({
        modelProviderId: 10,
        modelConfigId: 11,
      }),
    );
    expect(onCreateVersion).toHaveBeenCalledWith(
      12,
      expect.objectContaining({
        rolePrompt: expect.any(String),
        responsibilityText: expect.any(String),
        boundaryText: expect.any(String),
        modelPolicyJson: '{"routing":"agent_default"}',
        toolPolicyJson: '{"approval":"risk_based"}',
        contextPolicyJson: '{"maxItems":8}',
        memoryPolicyJson: '{"write":"confirmed_only","enabled":true,"maxItems":5,"scopes":["task"]}',
        orchestrationPolicyJson: '{"executionMode":"single_agent"}',
        toolIds: [22],
      }),
    );
    expect(await screen.findByText(/v20260612230000/)).toBeInTheDocument();

    await user.click(projectReportTool);
    await user.click(screen.getByRole('button', { name: 'Sync draft tool scope' }));

    await waitFor(() =>
      expect(onReplaceVersionTools).toHaveBeenCalledWith(12, 13, {
        toolIds: [],
      }),
    );
  });
});
