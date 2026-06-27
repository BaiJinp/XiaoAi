import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { TemplateRoleBindingCard } from './TemplateRoleBindingCard';

describe('TemplateRoleBindingCard', () => {
  it('saves a selected template Agent and AgentVersion for a role', async () => {
    const user = userEvent.setup();
    const onListBindings = vi.fn().mockResolvedValue([
      {
        roleCode: 'software_product_manager',
        roleName: 'Product Manager',
        domainCode: 'software_development',
        templateId: 2,
        source: 'role_default',
        effectiveAgentId: 101,
        effectiveAgentVersionId: 1001,
      },
    ]);
    const onListAgents = vi.fn().mockResolvedValue({
      pageNo: 1,
      pageSize: 100,
      total: 1,
      records: [
        {
          id: 401,
          agentName: 'Team Product Agent',
          status: 'published',
          latestStableVersionId: 4001,
        },
      ],
    });
    const onListVersions = vi.fn().mockResolvedValue([
      {
        agentVersionId: 4001,
        versionNo: 'v1',
        versionStatus: 'published',
      },
    ]);
    const onUpdateBinding = vi.fn().mockResolvedValue({
      roleCode: 'software_product_manager',
      roleName: 'Product Manager',
      templateId: 2,
      agentId: 401,
      agentVersionId: 4001,
      effectiveAgentId: 401,
      effectiveAgentVersionId: 4001,
      source: 'template',
    });

    render(
      <TemplateRoleBindingCard
        templateId={2}
        onListBindings={onListBindings}
        onListAgents={onListAgents}
        onListVersions={onListVersions}
        onUpdateBinding={onUpdateBinding}
      />,
    );

    expect(await screen.findByText('Product Manager (software_product_manager)')).toBeInTheDocument();
    await user.click(screen.getByLabelText('software_product_manager template agent'));
    await user.click(await screen.findByText('Team Product Agent #401'));
    await waitFor(() => expect(onListVersions).toHaveBeenCalledWith(401));
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(onUpdateBinding).toHaveBeenCalledWith(2, 'software_product_manager', {
        agentId: 401,
        agentVersionId: 4001,
      }),
    );
  });

  it('clears a template binding without deleting the effective global fallback', async () => {
    const user = userEvent.setup();
    const onUpdateBinding = vi.fn().mockResolvedValue({
      roleCode: 'software_product_manager',
      templateId: 2,
      agentId: undefined,
      agentVersionId: undefined,
      effectiveAgentId: 101,
      effectiveAgentVersionId: 1001,
      source: 'role_default',
    });

    render(
      <TemplateRoleBindingCard
        templateId={2}
        onListBindings={vi.fn().mockResolvedValue([
          {
            roleCode: 'software_product_manager',
            roleName: 'Product Manager',
            templateId: 2,
            agentId: 401,
            agentVersionId: 4001,
            effectiveAgentId: 401,
            effectiveAgentVersionId: 4001,
            source: 'template',
          },
        ])}
        onListAgents={vi.fn().mockResolvedValue({
          pageNo: 1,
          pageSize: 100,
          total: 0,
          records: [],
        })}
        onListVersions={vi.fn().mockResolvedValue([])}
        onUpdateBinding={onUpdateBinding}
      />,
    );

    expect(await screen.findByText('Product Manager (software_product_manager)')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Clear' }));

    await waitFor(() =>
      expect(onUpdateBinding).toHaveBeenCalledWith(2, 'software_product_manager', {
        agentId: null,
        agentVersionId: null,
      }),
    );
  });
});
