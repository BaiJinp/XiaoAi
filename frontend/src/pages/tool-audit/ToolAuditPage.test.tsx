import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ToolAuditPage } from './ToolAuditPage';
import { listTaskEventsByType } from '../../services/task-api';

vi.mock('../../services/task-api', () => ({
  listTaskEventsByType: vi.fn(),
}));

function createWrapper() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <QueryClientProvider client={client}>
        <MemoryRouter>{children}</MemoryRouter>
      </QueryClientProvider>
    );
  };
}

describe('ToolAuditPage', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it('renders denied tool events for audit review', async () => {
    vi.mocked(listTaskEventsByType).mockResolvedValue({
      records: [
        {
          id: '91',
          tenantId: 100,
          taskId: 12,
          runId: 22,
          sequence: 91,
          eventType: 'TOOL_DENIED',
          message: 'Tool denied',
          payload: {
            toolId: 10,
            toolCode: 'controlled.cli.create-task',
            agentVersionId: 11,
            reason: 'tool_not_in_agent_version_scope',
          },
          createTime: '2026-06-12T10:00:00Z',
        },
      ],
      total: 86,
    });

    render(<ToolAuditPage />, { wrapper: createWrapper() });

    await waitFor(() => expect(listTaskEventsByType).toHaveBeenCalledWith('TOOL_DENIED', { pageNo: 1, pageSize: 50 }));
    expect(screen.getByText('工具越权审计')).toBeInTheDocument();
    expect(await screen.findByText('Task 12')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Task 12' })).toHaveAttribute('href', '/tasks/12');
    expect(screen.getByText('Run 22')).toBeInTheDocument();
    expect(screen.getByText('controlled.cli.create-task')).toBeInTheDocument();
    expect(screen.getByText('11')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Fix scope' })).toHaveAttribute('href', '/agent-config?versionId=11');
    expect(screen.getByText('tool_not_in_agent_version_scope')).toBeInTheDocument();
  });

  it('requests denied tool events with applied backend filters', async () => {
    vi.mocked(listTaskEventsByType).mockResolvedValue({
      records: [
        {
          id: '91',
          tenantId: 100,
          taskId: 12,
          runId: 22,
          eventType: 'TOOL_DENIED',
          message: 'Tool denied',
          payload: {
            toolId: 10,
            toolCode: 'controlled.cli.create-task',
            agentVersionId: 11,
            reason: 'tool_not_in_agent_version_scope',
          },
        },
        {
          id: '92',
          tenantId: 100,
          taskId: 13,
          runId: 23,
          eventType: 'TOOL_DENIED',
          message: 'Other tool denied',
          payload: {
            toolId: 20,
            toolCode: 'controlled.cli.other',
            agentVersionId: 12,
            reason: 'tool_not_in_agent_version_scope_on_resume',
          },
        },
      ],
      total: 2,
    });
    render(<ToolAuditPage />, { wrapper: createWrapper() });

    expect(await screen.findByText('controlled.cli.create-task')).toBeInTheDocument();
    expect(screen.getByText('controlled.cli.other')).toBeInTheDocument();

    fireEvent.change(screen.getByPlaceholderText('Search tool, reason, task, run, version'), {
      target: { value: 'create-task' },
    });

    await waitFor(() =>
      expect(listTaskEventsByType).toHaveBeenLastCalledWith('TOOL_DENIED', {
        pageNo: 1,
        pageSize: 50,
        keyword: 'create-task',
      }),
    );

    expect(screen.getByText('controlled.cli.create-task')).toBeInTheDocument();
    expect(screen.queryByText('controlled.cli.other')).not.toBeInTheDocument();

    fireEvent.change(screen.getByPlaceholderText('AgentVersion ID'), {
      target: { value: '11' },
    });

    await waitFor(() =>
      expect(listTaskEventsByType).toHaveBeenLastCalledWith('TOOL_DENIED', {
        pageNo: 1,
        pageSize: 50,
        keyword: 'create-task',
        agentVersionId: '11',
      }),
    );
  });

  it('exports the currently filtered events as CSV', async () => {
    vi.mocked(listTaskEventsByType).mockResolvedValue({
      records: [
        {
          id: '91',
          tenantId: 100,
          taskId: 12,
          runId: 22,
          eventType: 'TOOL_DENIED',
          message: 'Tool denied',
          payload: {
            toolId: 10,
            toolCode: 'controlled.cli.create-task',
            agentVersionId: 11,
            reason: 'tool_not_in_agent_version_scope',
          },
        },
      ],
      total: 1,
    });
    render(<ToolAuditPage />, { wrapper: createWrapper() });

    await screen.findByText('controlled.cli.create-task');
    const createObjectUrl = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:tool-audit');
    const revokeObjectUrl = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    const link = document.createElement('a');
    const click = vi.spyOn(link, 'click').mockImplementation(() => undefined);
    vi.spyOn(document, 'createElement').mockReturnValue(link);
    fireEvent.click(screen.getByRole('button', { name: /Export CSV/ }));

    expect(createObjectUrl).toHaveBeenCalledWith(expect.any(Blob));
    expect(link.download).toBe('tool-audit-denied-events.csv');
    expect(click).toHaveBeenCalled();
    expect(revokeObjectUrl).toHaveBeenCalledWith('blob:tool-audit');
  });
});
