import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ExistingAgentVersionToolsCard } from './ExistingAgentVersionToolsCard';

function renderCard(element: React.ReactNode, initialEntry = '/agent-config') {
  return render(<MemoryRouter initialEntries={[initialEntry]}>{element}</MemoryRouter>);
}

describe('ExistingAgentVersionToolsCard', () => {
  const plugins = [
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
          toolCode: 'controlled.cli.project-query',
          toolName: 'Project Query',
          toolType: 'cli',
          riskLevel: 'low',
          status: 'active',
        },
      ],
    },
  ];

  it('loads existing versions and syncs selected draft tool scope', async () => {
    const onListVersions = vi.fn().mockResolvedValue([
      {
        agentVersionId: 13,
        versionNo: 'v20260614000100',
        versionStatus: 'draft',
        toolIds: [22],
      },
      {
        agentVersionId: 14,
        versionNo: 'v20260613000100',
        versionStatus: 'published',
        toolIds: [23],
      },
    ]);
    const onReplaceVersionTools = vi.fn().mockResolvedValue({
      agentVersionId: 13,
      versionNo: 'v20260614000100',
      versionStatus: 'draft',
      toolIds: [22, 23],
    });
    const user = userEvent.setup();

    renderCard(
      <ExistingAgentVersionToolsCard
        onListPlugins={vi.fn().mockResolvedValue(plugins)}
        onListVersions={onListVersions}
        onReplaceVersionTools={onReplaceVersionTools}
      />,
    );

    await user.type(screen.getByLabelText('Agent ID'), '12');
    await user.click(screen.getByRole('button', { name: '加载版本' }));

    expect(await screen.findByText('v20260614000100')).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: /Project Report/ })).toBeChecked();
    await user.click(screen.getByRole('checkbox', { name: /Project Query/ }));
    await user.click(screen.getByRole('button', { name: '同步 draft 工具范围' }));

    await waitFor(() =>
      expect(onReplaceVersionTools).toHaveBeenCalledWith(12, 13, {
        toolIds: [22, 23],
      }),
    );
  });

  it('keeps published versions read only', async () => {
    const user = userEvent.setup();

    renderCard(
      <ExistingAgentVersionToolsCard
        onListPlugins={vi.fn().mockResolvedValue(plugins)}
        onListVersions={vi.fn().mockResolvedValue([
          {
            agentVersionId: 14,
            versionNo: 'v20260613000100',
            versionStatus: 'published',
            toolIds: [23],
          },
        ])}
        onReplaceVersionTools={vi.fn()}
      />,
    );

    await user.type(screen.getByLabelText('Agent ID'), '12');
    await user.click(screen.getByRole('button', { name: '加载版本' }));

    expect(await screen.findByText('v20260613000100')).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: /Project Query/ })).toBeDisabled();
    expect(screen.getByRole('button', { name: '同步 draft 工具范围' })).toBeDisabled();
  });

  it('prefills version from query params before loading agent versions', async () => {
    const onListVersions = vi.fn().mockResolvedValue([
      {
        agentVersionId: 13,
        versionNo: 'v20260614000100',
        versionStatus: 'draft',
        toolIds: [22],
      },
      {
        agentVersionId: 14,
        versionNo: 'v20260613000100',
        versionStatus: 'draft',
        toolIds: [23],
      },
    ]);
    const user = userEvent.setup();

    renderCard(
      <ExistingAgentVersionToolsCard
        onListPlugins={vi.fn().mockResolvedValue(plugins)}
        onListVersions={onListVersions}
        onReplaceVersionTools={vi.fn()}
      />,
      '/agent-config?agentId=12&versionId=14',
    );

    await user.click(screen.getByRole('button', { name: '加载版本' }));

    await waitFor(() => expect(onListVersions).toHaveBeenCalledWith(12));
    expect(await screen.findByText('v20260613000100')).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /v20260613000100/ })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: /Project Query/ })).toBeChecked();
  });
});
