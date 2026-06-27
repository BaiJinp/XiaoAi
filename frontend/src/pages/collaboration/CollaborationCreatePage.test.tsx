import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { PropsWithChildren } from 'react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createCollaborationSession, listCollaborationTemplates } from '../../services/collaboration-api';
import { CollaborationCreatePage } from './CollaborationCreatePage';

vi.mock('../../services/collaboration-api', () => ({
  createCollaborationSession: vi.fn(),
  listCollaborationTemplates: vi.fn(),
}));

vi.mock('../../features/collaboration/components/AgentRoleDefaultBindingCard', () => ({
  AgentRoleDefaultBindingCard: () => <div>Agent role default bindings</div>,
}));

vi.mock('../../features/collaboration/components/TemplateRoleBindingCard', () => ({
  TemplateRoleBindingCard: ({ templateId }: { templateId?: number }) => <div>Template role bindings {templateId ?? 'none'}</div>,
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return function Wrapper({ children }: PropsWithChildren) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/collaboration']}>
          <Routes>
            <Route path="/collaboration" element={children} />
            <Route path="/collaboration/:sessionId" element={<div>Session detail page</div>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );
  };
}

describe('CollaborationCreatePage', () => {
  beforeEach(() => {
    vi.mocked(createCollaborationSession).mockReset();
    vi.mocked(listCollaborationTemplates).mockReset();
    vi.mocked(listCollaborationTemplates).mockResolvedValue([]);
  });

  it('creates collaboration session and navigates to detail page', async () => {
    vi.mocked(createCollaborationSession).mockResolvedValue({
      sessionId: 66,
      sessionCode: 'CS-66',
      status: 'planning',
    });
    const user = userEvent.setup();

    render(<CollaborationCreatePage />, { wrapper: createWrapper() });

    await user.type(screen.getByPlaceholderText('Paste the requirement or business goal'), 'Build a payment workflow');
    await user.click(screen.getByRole('button', { name: 'Create session' }));

    await waitFor(() =>
      expect(createCollaborationSession).toHaveBeenCalledWith({
        goalText: 'Build a payment workflow',
        strategyType: 'orchestrated_team',
        contextJson: JSON.stringify({ source: 'collaboration-create-page' }, null, 2),
      }),
    );
    expect(await screen.findByText('Session detail page')).toBeInTheDocument();
  });

  it('creates collaboration session with selected team template', async () => {
    vi.mocked(listCollaborationTemplates).mockResolvedValue([
      {
        templateId: 2,
        templateCode: 'software_requirement_to_delivery',
        templateName: 'Software Requirement To Delivery',
        domainCode: 'software_development',
        strategyType: 'orchestrated_team',
        status: 'active',
      },
    ]);
    vi.mocked(createCollaborationSession).mockResolvedValue({
      sessionId: 67,
      sessionCode: 'CS-67',
      status: 'planning',
    });
    const user = userEvent.setup();

    render(<CollaborationCreatePage />, { wrapper: createWrapper() });

    await user.type(screen.getByPlaceholderText('Paste the requirement or business goal'), 'Deliver customer portal');
    await user.click(await screen.findByRole('combobox', { name: 'Team template' }));
    await user.click(await screen.findByText('Software Requirement To Delivery (software_requirement_to_delivery)'));
    expect(screen.getByText('Template role bindings 2')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Create session' }));

    await waitFor(() =>
      expect(createCollaborationSession).toHaveBeenCalledWith(
        expect.objectContaining({
          goalText: 'Deliver customer portal',
          strategyType: 'orchestrated_team',
          templateId: 2,
        }),
      ),
    );
    expect(listCollaborationTemplates).toHaveBeenCalledWith('software_development');
  });
});
