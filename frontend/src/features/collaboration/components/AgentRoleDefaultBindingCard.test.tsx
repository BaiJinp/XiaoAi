import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { AgentRoleDefaultBindingCard } from './AgentRoleDefaultBindingCard';

describe('AgentRoleDefaultBindingCard', () => {
  it('saves a selected default Agent and AgentVersion for a role', async () => {
    const user = userEvent.setup();
    const onListRoles = vi.fn().mockResolvedValue([
      {
        id: 2,
        roleCode: 'software_product_manager',
        roleName: 'Product Manager',
        domainCode: 'software_development',
        status: 'active',
      },
    ]);
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
    const onListVersions = vi.fn().mockResolvedValue([
      {
        agentVersionId: 3001,
        versionNo: 'v1',
        versionStatus: 'published',
      },
    ]);
    const onUpdateDefaultAgent = vi.fn().mockResolvedValue({
      id: 2,
      roleCode: 'software_product_manager',
      defaultAgentId: 301,
      defaultAgentVersionId: 3001,
    });

    render(
      <AgentRoleDefaultBindingCard
        onListRoles={onListRoles}
        onListAgents={onListAgents}
        onListVersions={onListVersions}
        onUpdateDefaultAgent={onUpdateDefaultAgent}
      />,
    );

    expect(await screen.findByText('Product Manager (software_product_manager)')).toBeInTheDocument();
    await user.click(screen.getByLabelText('software_product_manager default agent'));
    await user.click(await screen.findByText('Product Agent #301'));
    await waitFor(() => expect(onListVersions).toHaveBeenCalledWith(301));
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(onUpdateDefaultAgent).toHaveBeenCalledWith(2, {
        defaultAgentId: 301,
        defaultAgentVersionId: 3001,
      }),
    );
  });

  it('clears a role default binding', async () => {
    const user = userEvent.setup();
    const onUpdateDefaultAgent = vi.fn().mockResolvedValue({
      id: 2,
      roleCode: 'software_product_manager',
      defaultAgentId: undefined,
      defaultAgentVersionId: undefined,
    });

    render(
      <AgentRoleDefaultBindingCard
        onListRoles={vi.fn().mockResolvedValue([
          {
            id: 2,
            roleCode: 'software_product_manager',
            roleName: 'Product Manager',
            domainCode: 'software_development',
            defaultAgentId: 301,
            defaultAgentVersionId: 3001,
            status: 'active',
          },
        ])}
        onListAgents={vi.fn().mockResolvedValue({
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
        })}
        onListVersions={vi.fn().mockResolvedValue([])}
        onUpdateDefaultAgent={onUpdateDefaultAgent}
      />,
    );

    expect(await screen.findByText('Product Manager (software_product_manager)')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Clear' }));

    await waitFor(() =>
      expect(onUpdateDefaultAgent).toHaveBeenCalledWith(2, {
        defaultAgentId: null,
        defaultAgentVersionId: null,
      }),
    );
  });
});
