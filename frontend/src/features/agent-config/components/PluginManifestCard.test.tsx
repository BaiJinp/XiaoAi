import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { PluginManifestCard } from './PluginManifestCard';

describe('PluginManifestCard', () => {
  it('lists plugins and imports manifest json with optional agent version binding', async () => {
    const onList = vi.fn().mockResolvedValue([
      {
        pluginId: 1,
        pluginCode: 'project-cli',
        pluginName: 'Project CLI',
        pluginVersion: '1.0.0',
        status: 'active',
        manifestHash: 'abcdef1234567890',
        tools: [
          {
            toolId: 10,
            toolCode: 'controlled.cli.project-report',
            toolName: 'Project Report',
            toolType: 'cli',
            riskLevel: 'medium',
            status: 'active',
            bound: true,
            boundAgentId: 300,
            bindingId: 33,
            manifestHash: 'abcdef1234567890',
          },
        ],
      },
    ]);
    const onImport = vi.fn().mockResolvedValue({
      pluginCode: 'project-cli',
      pluginName: 'Project CLI',
      pluginVersion: '1.0.0',
      status: 'active',
      boundAgentVersionId: 13,
      agentVersionToolIds: [21, 22],
      tools: [],
    });
    const onDisable = vi.fn().mockResolvedValue({
      pluginId: 1,
      pluginCode: 'project-cli',
      pluginName: 'Project CLI',
      pluginVersion: '1.0.0',
      status: 'inactive',
      tools: [],
    });
    const onEnable = vi.fn();
    const user = userEvent.setup();

    render(<PluginManifestCard onList={onList} onImport={onImport} onDisable={onDisable} onEnable={onEnable} />);

    expect(await screen.findByText('Project CLI 1.0.0')).toBeInTheDocument();
    expect(screen.getByText('controlled.cli.project-report')).toBeInTheDocument();
    expect(screen.getByText('Agent 300')).toBeInTheDocument();
    expect(screen.getAllByText('sha256 abcdef123456').length).toBeGreaterThan(0);
    const manifestInput = screen.getByLabelText('Manifest JSON') as HTMLTextAreaElement;
    expect(manifestInput.value).toContain('controlled.http.project-query');
    expect(manifestInput.value).toContain('controlled.cli.project-update');

    await user.type(screen.getByLabelText('绑定 Agent ID'), '300');
    await user.type(screen.getByLabelText('Draft AgentVersion ID'), '13');
    fireEvent.change(screen.getByLabelText('Manifest JSON'), { target: { value: '{"pluginCode":"project-cli"}' } });
    await user.click(screen.getByRole('button', { name: '导入 Manifest' }));

    await waitFor(() =>
      expect(onImport).toHaveBeenCalledWith({
        manifestJson: '{"pluginCode":"project-cli"}',
        agentId: 300,
        agentVersionId: 13,
      }),
    );
    expect(await screen.findByText(/Draft Version #13 已同步 2 个工具/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Disable' }));

    await waitFor(() => expect(onDisable).toHaveBeenCalledWith(1));
    expect(onEnable).not.toHaveBeenCalled();
    expect(onList).toHaveBeenCalledTimes(3);
  });
});
