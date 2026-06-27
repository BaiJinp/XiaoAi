import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ProjectAssistantForm } from './components/ProjectAssistantForm';

describe('ProjectAssistantForm', () => {
  it('creates a project assistant draft and draft version', async () => {
    const onCreateProvider = vi.fn().mockResolvedValue({ id: 10, providerCode: 'openai-prod', status: 'active' });
    const onCreateModelConfig = vi.fn().mockResolvedValue({ id: 11, modelCode: 'gpt-4o-mini', status: 'active' });
    const onCreateDraft = vi.fn().mockResolvedValue({ agentId: 9, agentCode: 'AGENT-9', status: 'draft' });
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
      agentVersionId: 19,
      versionNo: 'v20260612230100',
      versionStatus: 'draft',
      toolIds: [22],
    });
    const user = userEvent.setup();

    const { container } = render(
      <ProjectAssistantForm
        onCreateProvider={onCreateProvider}
        onCreateModelConfig={onCreateModelConfig}
        onCreateDraft={onCreateDraft}
        onCreateVersion={onCreateVersion}
        onListPlugins={onListPlugins}
      />,
    );

    const projectReportTool = await screen.findByRole('checkbox', { name: /Project Report/ });
    expect(screen.getByText('Tool scope is saved into a new AgentVersion snapshot')).toBeInTheDocument();
    expect(screen.getByText('Published versions are not edited in place. To change allowed tools, create and publish a new AgentVersion.')).toBeInTheDocument();
    await user.click(projectReportTool);
    expect(projectReportTool).toBeChecked();
    fireEvent.change(screen.getByLabelText('API Key'), { target: { value: 'sk-live-secret' } });
    fireEvent.submit(container.querySelector('form') as HTMLFormElement);

    await waitFor(() =>
      expect(onCreateDraft).toHaveBeenCalledWith(
        expect.objectContaining({
          modelProviderId: 10,
          modelConfigId: 11,
        }),
      ),
    );
    expect(onCreateVersion).toHaveBeenCalledWith(
      9,
      expect.objectContaining({
        rolePrompt: expect.any(String),
        responsibilityText: expect.any(String),
        boundaryText: expect.any(String),
        toolIds: [22],
      }),
    );
    expect(await screen.findByText(/v20260612230100/)).toBeInTheDocument();
    expect(screen.getByText(/AGENT-9/)).toBeInTheDocument();
  });

  it('shows draft tool sync action disabled before a draft version exists', () => {
    render(<ProjectAssistantForm onCreateDraft={vi.fn()} onListPlugins={vi.fn().mockResolvedValue([])} />);

    expect(screen.getByRole('button', { name: 'Sync draft tool scope' })).toBeDisabled();
  });
});
