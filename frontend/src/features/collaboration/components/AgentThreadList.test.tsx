import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import type { AgentThread } from '../../../types/collaboration';
import { AgentThreadList } from './AgentThreadList';

const thread: AgentThread = {
  id: 30,
  sessionId: 12,
  taskId: 88,
  agentId: 7,
  threadCode: 'TH001',
  threadName: 'Product PRD',
  status: 'pending',
};

describe('AgentThreadList', () => {
  it('renders runtime summary for bound task thread', async () => {
    const user = userEvent.setup();
    const onStartThreadTask = vi.fn();
    const onHandleApproval = vi.fn();

    render(
      <MemoryRouter>
        <AgentThreadList
          threads={[thread]}
          runtimeSummaries={{
            88: {
              eventType: 'TOOL_BLOCKED',
              label: 'Tool blocked',
              status: 'warning',
              toolCode: 'controlled.cli.project-update',
              toolCallIndex: 1,
              riskLevel: 'high',
              approvalRequestId: 101,
              reason: 'requires_human_approval',
            },
          }}
          onStartThreadTask={onStartThreadTask}
          onHandleApproval={onHandleApproval}
        />
      </MemoryRouter>,
    );

    expect(screen.getByText('Tool blocked')).toBeInTheDocument();
    expect(screen.getByText('call #1')).toBeInTheDocument();
    expect(screen.getByText('controlled.cli.project-update')).toBeInTheDocument();
    expect(screen.getByText('approval #101')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Approve' }));
    expect(onHandleApproval).toHaveBeenCalledWith(101, 'approve');
    await user.click(screen.getByRole('button', { name: 'Start' }));
    expect(onStartThreadTask).toHaveBeenCalledWith(thread);
  });

  it('waits for strict input artifact handoff before starting a thread', () => {
    const onStartThreadTask = vi.fn();
    render(
      <MemoryRouter>
        <AgentThreadList
          threads={[
            {
              ...thread,
              inputArtifactId: 99,
              contextJson: '{"requireAcceptedInputHandoff":true}',
            },
          ]}
          handoffs={[
            {
              id: 40,
              sessionId: 12,
              toThreadId: 30,
              artifactId: 99,
              handoffType: 'artifact',
              status: 'pending',
            },
          ]}
          onStartThreadTask={onStartThreadTask}
        />
      </MemoryRouter>,
    );

    expect(screen.getByText('waiting handoff')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Start' })).not.toBeInTheDocument();
  });

  it('renders multiple input artifact references from collaboration context', () => {
    render(
      <MemoryRouter>
        <AgentThreadList
          threads={[
            {
              ...thread,
              threadName: 'Test validation',
              inputArtifactId: 702,
              contextJson: '{"inputArtifactIds":[702,703],"stageCode":"testing"}',
            },
          ]}
        />
      </MemoryRouter>,
    );

    expect(screen.getByText('Input artifacts #702, #703')).toBeInTheDocument();
  });
});
