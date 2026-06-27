import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ComponentProps } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import type { AgentThread, CollaborationPlan, CollaborationSession, QualityGate } from '../../../types/collaboration';
import { CollaborationNextStep } from './CollaborationNextStep';

const session: CollaborationSession = {
  id: 12,
  sessionCode: 'CS001',
  strategyType: 'orchestrated_team',
  goalText: 'deliver',
  status: 'running',
};

const passedPlan: CollaborationPlan = {
  id: 20,
  sessionId: 12,
  planStatus: 'submitted',
  validationStatus: 'passed',
  planJson: '{}',
};

function renderNextStep(props: Partial<ComponentProps<typeof CollaborationNextStep>> = {}) {
  return render(
    <MemoryRouter>
      <CollaborationNextStep session={session} plans={[]} threads={[]} gates={[]} {...props} />
    </MemoryRouter>,
  );
}

describe('CollaborationNextStep', () => {
  it('asks for a plan before orchestration starts', () => {
    renderNextStep();

    expect(screen.getByText('Next step: submit a collaboration plan with stage toolCalls.')).toBeInTheDocument();
  });

  it('prevents start guidance when latest plan validation failed', () => {
    renderNextStep({
      plans: [{ ...passedPlan, validationStatus: 'failed' }],
    });

    expect(screen.getByText('Next step: fix the latest plan validation errors before start.')).toBeInTheDocument();
  });

  it('offers to start the next runnable thread', async () => {
    const user = userEvent.setup();
    const onStartThreadTask = vi.fn();
    const thread: AgentThread = {
      id: 30,
      sessionId: 12,
      taskId: 88,
      agentId: 7,
      threadCode: 'TH001',
      threadName: 'Product PRD',
      status: 'pending',
    };
    renderNextStep({
      plans: [passedPlan],
      threads: [thread],
      onStartThreadTask,
    });

    expect(screen.getByText('Next step: start Product PRD.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Task #88' })).toHaveAttribute('href', '/tasks/88');
    await user.click(screen.getByRole('button', { name: 'Start thread' }));
    expect(onStartThreadTask).toHaveBeenCalledWith(thread);
  });

  it('asks to accept a strict artifact handoff before starting the next thread', () => {
    const thread: AgentThread = {
      id: 30,
      sessionId: 12,
      taskId: 88,
      agentId: 7,
      threadCode: 'TH001',
      threadName: 'Product PRD',
      status: 'pending',
      inputArtifactId: 99,
      contextJson: '{"requireAcceptedInputHandoff":true}',
    };

    renderNextStep({
      plans: [passedPlan],
      threads: [thread],
      handoffs: [
        {
          id: 40,
          sessionId: 12,
          toThreadId: 30,
          artifactId: 99,
          handoffType: 'artifact',
          status: 'pending',
        },
      ],
    });

    expect(screen.getByText('Next step: accept the artifact handoff before starting Product PRD.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Start thread' })).not.toBeInTheDocument();
  });

  it('points to pending quality gate review', () => {
    const gate: QualityGate = {
      id: 50,
      sessionId: 12,
      gateCode: 'prd_confirmed',
      gateName: 'PRD confirmed',
      gateType: 'manual_confirmation',
      status: 'pending',
      required: true,
    };
    renderNextStep({
      plans: [passedPlan],
      threads: [{ id: 30, sessionId: 12, taskId: 88, agentId: 7, threadCode: 'TH001', status: 'completed' }],
      gates: [gate],
    });

    expect(
      screen.getByText('Next step: review quality gate prd_confirmed. Passing it can advance the next agent stage.'),
    ).toBeInTheDocument();
  });

  it('shows blocked guidance when a required quality gate failed', () => {
    const gate: QualityGate = {
      id: 51,
      sessionId: 12,
      gateCode: 'prd_confirmed',
      gateName: 'PRD confirmed',
      gateType: 'manual_confirmation',
      status: 'failed',
      required: true,
      failReason: 'Acceptance criteria missing',
    };
    renderNextStep({
      session: { ...session, status: 'blocked', currentStageCode: 'requirement_review' },
      plans: [passedPlan],
      threads: [{ id: 30, sessionId: 12, taskId: 88, agentId: 7, threadCode: 'TH001', status: 'completed' }],
      gates: [gate],
    });

    expect(screen.getByText('Next step: resolve failed quality gate prd_confirmed.')).toBeInTheDocument();
    expect(screen.getByText('Acceptance criteria missing')).toBeInTheDocument();
  });
});
