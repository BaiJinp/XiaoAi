import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { PropsWithChildren } from 'react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  acceptAgentHandoff,
  getCollaborationTemplate,
  getCollaborationSession,
  failQualityGate,
  listCollaborationPlans,
  listAgentHandoffs,
  listAgentThreads,
  listQualityGates,
  passQualityGate,
  rejectAgentHandoff,
  startAgentThreadTask,
  startCollaborationSession,
  submitCollaborationPlan,
} from '../../services/collaboration-api';
import { handleApproval } from '../../services/approval-api';
import { listTaskEvents } from '../../services/task-api';
import { CollaborationSessionPage } from './CollaborationSessionPage';

vi.mock('../../services/collaboration-api', () => ({
  acceptAgentHandoff: vi.fn(),
  getCollaborationTemplate: vi.fn(),
  getCollaborationSession: vi.fn(),
  listCollaborationPlans: vi.fn(),
  listAgentThreads: vi.fn(),
  listAgentHandoffs: vi.fn(),
  listQualityGates: vi.fn(),
  passQualityGate: vi.fn(),
  failQualityGate: vi.fn(),
  rejectAgentHandoff: vi.fn(),
  startAgentThreadTask: vi.fn(),
  startCollaborationSession: vi.fn(),
  submitCollaborationPlan: vi.fn(),
}));

vi.mock('../../services/task-api', () => ({
  listTaskEvents: vi.fn(),
}));

vi.mock('../../services/approval-api', () => ({
  handleApproval: vi.fn(),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return function Wrapper({ children }: PropsWithChildren) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

function renderCollaborationSession(sessionId = 12) {
  return render(
    <MemoryRouter initialEntries={[`/collaboration/${sessionId}`]}>
      <Routes>
        <Route path="/collaboration/:sessionId" element={<CollaborationSessionPage />} />
      </Routes>
    </MemoryRouter>,
    { wrapper: createWrapper() },
  );
}

describe('CollaborationSessionPage', () => {
  beforeEach(() => {
    vi.mocked(acceptAgentHandoff).mockReset();
    vi.mocked(getCollaborationTemplate).mockReset();
    vi.mocked(getCollaborationSession).mockReset();
    vi.mocked(listCollaborationPlans).mockReset();
    vi.mocked(listAgentThreads).mockReset();
    vi.mocked(listAgentHandoffs).mockReset();
    vi.mocked(listQualityGates).mockReset();
    vi.mocked(passQualityGate).mockReset();
    vi.mocked(failQualityGate).mockReset();
    vi.mocked(rejectAgentHandoff).mockReset();
    vi.mocked(startAgentThreadTask).mockReset();
    vi.mocked(startCollaborationSession).mockReset();
    vi.mocked(submitCollaborationPlan).mockReset();
    vi.mocked(handleApproval).mockReset();
    vi.mocked(listTaskEvents).mockReset();
  });

  it('renders generic collaboration session details, threads, handoffs, and gates', async () => {
    const user = userEvent.setup();
    vi.mocked(getCollaborationSession).mockResolvedValue({
      id: 12,
      templateId: 2,
      sessionCode: 'CS202606110001',
      strategyType: 'orchestrated_team',
      goalText: '完成一次通用协作交付',
      status: 'planning',
      currentStageCode: 'requirement_analysis',
    });
    vi.mocked(getCollaborationTemplate).mockResolvedValue({
      templateId: 2,
      templateCode: 'software_requirement_to_delivery',
      templateName: 'Software Requirement To Delivery',
      strategyType: 'orchestrated_team',
      templateJson: '{"goal":"from template","stages":[{"stageCode":"requirement_analysis"}]}',
      status: 'active',
    });
    vi.mocked(listAgentThreads).mockResolvedValue([
      {
        id: 30,
        sessionId: 12,
        taskId: 88,
        agentId: 7,
        threadCode: 'TH001',
        threadName: '需求分析',
        status: 'completed',
        outputArtifactId: 99,
        contextJson: '{"stageCode":"requirement_analysis"}',
      },
      {
        id: 31,
        sessionId: 12,
        taskId: 89,
        agentId: 8,
        threadCode: 'TH002',
        threadName: 'Technical Design',
        status: 'pending',
        contextJson: '{"stageCode":"technical_design"}',
      },
    ]);
    vi.mocked(listCollaborationPlans).mockResolvedValue([
      {
        id: 20,
        sessionId: 12,
        planStatus: 'submitted',
        validationStatus: 'passed',
        planJson: JSON.stringify({
          stages: [
            { stageCode: 'requirement_analysis' },
            { stageCode: 'technical_design' },
            { stageCode: 'backend_implementation' },
            { stageCode: 'frontend_implementation' },
            { stageCode: 'testing' },
            {
              stageCode: 'review_and_delivery',
              toolCalls: [
                {
                  toolId: 22,
                  toolCode: 'controlled.http.project-query',
                  callPayloadJson: { query: 'scope' },
                },
              ],
            },
          ],
        }),
      },
    ]);
    vi.mocked(listAgentHandoffs).mockResolvedValue([
      {
        id: 40,
        sessionId: 12,
        artifactId: 99,
        handoffType: 'artifact',
        status: 'pending',
        messageText: '请基于交付物继续处理',
      },
      {
        id: 41,
        sessionId: 12,
        artifactId: 100,
        handoffType: 'artifact',
        status: 'pending',
        messageText: '请复核交付物',
      },
    ]);
    vi.mocked(listQualityGates).mockResolvedValue([
      {
        id: 50,
        sessionId: 12,
        gateCode: 'requirement_confirmed',
        gateName: '需求确认',
        gateType: 'manual_confirmation',
        status: 'pending',
        required: true,
      },
      {
        id: 51,
        sessionId: 12,
        gateCode: 'design_confirmed',
        gateName: '设计确认',
        gateType: 'manual_confirmation',
        status: 'pending',
        required: false,
      },
    ]);
    vi.mocked(startAgentThreadTask).mockResolvedValue({
      taskId: 88,
      runId: 99,
      runCode: 'RUN-99',
      status: 'running',
      runtimeType: 'java-in-process',
    });
    vi.mocked(submitCollaborationPlan).mockResolvedValue({
      id: 21,
      sessionId: 12,
      planStatus: 'submitted',
      validationStatus: 'passed',
      planJson: '{}',
    });
    vi.mocked(startCollaborationSession).mockResolvedValue({
      status: 'started',
      createdThreadCount: 1,
      createdGateCount: 1,
    });
    vi.mocked(passQualityGate).mockResolvedValue({
      id: 50,
      sessionId: 12,
      gateCode: 'requirement_confirmed',
      gateName: '需求确认',
      gateType: 'manual_confirmation',
      status: 'passed',
      required: true,
    });
    vi.mocked(failQualityGate).mockResolvedValue({
      id: 51,
      sessionId: 12,
      gateCode: 'design_confirmed',
      gateName: '设计确认',
      gateType: 'manual_confirmation',
      status: 'failed',
      required: false,
    });
    vi.mocked(acceptAgentHandoff).mockResolvedValue(undefined);
    vi.mocked(rejectAgentHandoff).mockResolvedValue(undefined);
    vi.mocked(handleApproval).mockResolvedValue(undefined);
    vi.mocked(listTaskEvents).mockResolvedValue([
      {
        id: 'event-1',
        tenantId: 100,
        taskId: 88,
        runId: 99,
        eventType: 'TOOL_BLOCKED',
        payload: {
          toolCode: 'controlled.cli.project-update',
          toolCallIndex: 1,
          riskLevel: 'high',
          approvalRequestId: 101,
          reason: 'requires_human_approval',
        },
      },
    ]);

    renderCollaborationSession();

    expect(await screen.findByText('完成一次通用协作交付')).toBeInTheDocument();
    expect(screen.getAllByText('orchestrated_team').length).toBeGreaterThan(0);
    expect(screen.getByText('Agile team MVP progress')).toBeInTheDocument();
    expect(screen.getByText('1/6 stages completed')).toBeInTheDocument();
    expect(screen.getByText('Current stage: Requirement analysis')).toBeInTheDocument();
    expect(screen.getByText('Next MVP action: Accept artifact handoff #40.')).toBeInTheDocument();
    expect(screen.getByText('协作计划工具链')).toBeInTheDocument();
    expect(screen.getByText('six stages')).toBeInTheDocument();
    expect(screen.getAllByText(/review_and_delivery/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/controlled\.http\.project-query/).length).toBeGreaterThan(0);
    expect(screen.getByText('已提交协作计划')).toBeInTheDocument();
    expect(screen.getByText('Plan #20')).toBeInTheDocument();
    expect(screen.getByText(/"query": "scope"/)).toBeInTheDocument();
    expect(screen.getByText(/Next step: start/)).toBeInTheDocument();
    expect((await screen.findAllByText('Tool blocked')).length).toBeGreaterThan(0);
    expect(screen.getAllByText('controlled.cli.project-update').length).toBeGreaterThan(0);
    expect(screen.getAllByText('approval #101').length).toBeGreaterThan(0);
    await user.click(screen.getAllByRole('button', { name: 'Approve' })[0]);
    expect(handleApproval).toHaveBeenCalledWith(101, { action: 'approve' });
    await user.click(screen.getByRole('button', { name: 'Submit and start' }));
    await waitFor(() =>
      expect(submitCollaborationPlan).toHaveBeenCalledWith(
        12,
        expect.objectContaining({ planJson: expect.stringContaining('requirement_analysis') }),
      ),
    );
    await waitFor(() => expect(startCollaborationSession).toHaveBeenCalledWith(12, { planId: 21 }));
    expect(screen.getAllByText('需求分析')).toHaveLength(2);
    expect(screen.getAllByRole('link', { name: 'Task #88' })[0]).toHaveAttribute('href', '/tasks/88');
    await user.click(screen.getByRole('button', { name: 'Start thread' }));
    expect(startAgentThreadTask).toHaveBeenCalledWith(12, 31);
    await user.click(screen.getByRole('button', { name: 'Pass requirement_confirmed' }));
    await user.click(screen.getByRole('button', { name: 'Fail design_confirmed' }));
    expect(passQualityGate).toHaveBeenCalledWith(12, 50, { resultJson: '{}' });
    expect(failQualityGate).toHaveBeenCalledWith(12, 51, { failReason: 'Rejected from collaboration session page' });
    expect(screen.getByText('Artifact #99')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Accept handoff 40' }));
    await user.click(screen.getByRole('button', { name: 'Reject handoff 41' }));
    expect(acceptAgentHandoff).toHaveBeenCalledWith(12, 40);
    expect(rejectAgentHandoff).toHaveBeenCalledWith(12, 41);
    await waitFor(() => expect(vi.mocked(listAgentThreads).mock.calls.length).toBeGreaterThan(1));
    expect(screen.getAllByText('需求确认')).toHaveLength(2);
    expect(screen.getByText('requirement_confirmed')).toBeInTheDocument();
    expect(getCollaborationSession).toHaveBeenCalledWith(12);
    expect(listAgentThreads).toHaveBeenCalledWith(12);
    expect(listCollaborationPlans).toHaveBeenCalledWith(12);
    expect(listAgentHandoffs).toHaveBeenCalledWith(12);
    expect(listQualityGates).toHaveBeenCalledWith(12);
    expect(listTaskEvents).toHaveBeenCalledWith(88);
    expect(getCollaborationTemplate).toHaveBeenCalledWith(2);
  }, 30000);

  it('refreshes collaboration state after thread start and gate decisions', async () => {
    const user = userEvent.setup();
    vi.mocked(getCollaborationSession).mockResolvedValue({
      id: 12,
      sessionCode: 'CS202606110001',
      strategyType: 'orchestrated_team',
      goalText: '推进敏捷闭环',
      status: 'running',
      currentStageCode: 'requirement_analysis',
    });
    vi.mocked(listAgentThreads).mockResolvedValue([
      {
        id: 30,
        sessionId: 12,
        taskId: 88,
        agentId: 7,
        threadCode: 'TH001',
        threadName: '需求分析',
        status: 'completed',
        outputArtifactId: 99,
        contextJson: '{"stageCode":"requirement_analysis"}',
      },
      {
        id: 31,
        sessionId: 12,
        taskId: 89,
        agentId: 8,
        threadCode: 'TH002',
        threadName: 'Technical Design',
        status: 'pending',
        contextJson: '{"stageCode":"technical_design"}',
      },
    ]);
    vi.mocked(listCollaborationPlans).mockResolvedValue([
      {
        id: 20,
        sessionId: 12,
        planStatus: 'submitted',
        validationStatus: 'passed',
        planJson: JSON.stringify({
          stages: [
            { stageCode: 'requirement_analysis' },
            { stageCode: 'technical_design' },
            { stageCode: 'backend_implementation' },
            { stageCode: 'frontend_implementation' },
            { stageCode: 'testing' },
            { stageCode: 'review_and_delivery' },
          ],
        }),
      },
    ]);
    vi.mocked(listAgentHandoffs).mockResolvedValue([]);
    vi.mocked(listQualityGates).mockResolvedValue([
      {
        id: 50,
        sessionId: 12,
        gateCode: 'requirement_confirmed',
        gateName: '需求确认',
        gateType: 'manual_confirmation',
        status: 'pending',
        required: true,
      },
      {
        id: 51,
        sessionId: 12,
        gateCode: 'design_confirmed',
        gateName: '设计确认',
        gateType: 'manual_confirmation',
        status: 'pending',
        required: false,
      },
    ]);
    vi.mocked(startAgentThreadTask).mockResolvedValue({
      taskId: 88,
      runId: 99,
      runCode: 'RUN-99',
      status: 'running',
      runtimeType: 'java-in-process',
    });
    vi.mocked(passQualityGate).mockResolvedValue({
      id: 50,
      sessionId: 12,
      gateCode: 'requirement_confirmed',
      gateName: '需求确认',
      gateType: 'manual_confirmation',
      status: 'passed',
      required: true,
    });
    vi.mocked(failQualityGate).mockResolvedValue({
      id: 51,
      sessionId: 12,
      gateCode: 'design_confirmed',
      gateName: '设计确认',
      gateType: 'manual_confirmation',
      status: 'failed',
      required: false,
    });
    vi.mocked(listTaskEvents).mockResolvedValue([]);

    renderCollaborationSession();

    await screen.findByText('推进敏捷闭环');
    expect(getCollaborationSession).toHaveBeenCalledTimes(1);
    expect(listAgentThreads).toHaveBeenCalledTimes(1);
    expect(listCollaborationPlans).toHaveBeenCalledTimes(1);
    expect(listAgentHandoffs).toHaveBeenCalledTimes(1);
    expect(listQualityGates).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole('button', { name: 'Start' }));

    await waitFor(() => expect(getCollaborationSession).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(listAgentThreads).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(listCollaborationPlans).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(listAgentHandoffs).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(listQualityGates).toHaveBeenCalledTimes(2));

    await user.click(screen.getByRole('button', { name: 'Pass requirement_confirmed' }));

    await waitFor(() => expect(getCollaborationSession).toHaveBeenCalledTimes(3));
    await waitFor(() => expect(listAgentThreads).toHaveBeenCalledTimes(3));
    await waitFor(() => expect(listCollaborationPlans).toHaveBeenCalledTimes(3));
    await waitFor(() => expect(listAgentHandoffs).toHaveBeenCalledTimes(3));
    await waitFor(() => expect(listQualityGates).toHaveBeenCalledTimes(3));

    await user.click(screen.getByRole('button', { name: 'Fail design_confirmed' }));

    await waitFor(() => expect(getCollaborationSession).toHaveBeenCalledTimes(4));
    await waitFor(() => expect(listAgentThreads).toHaveBeenCalledTimes(4));
    await waitFor(() => expect(listCollaborationPlans).toHaveBeenCalledTimes(4));
    await waitFor(() => expect(listAgentHandoffs).toHaveBeenCalledTimes(4));
    await waitFor(() => expect(listQualityGates).toHaveBeenCalledTimes(4));
  });

  it('shows agile delivery completed when session is completed', async () => {
    vi.mocked(getCollaborationSession).mockResolvedValue({
      id: 12,
      sessionCode: 'CS202606110001',
      strategyType: 'orchestrated_team',
      goalText: 'deliver agile team mvp',
      status: 'completed',
      currentStageCode: 'review_and_delivery',
    });
    vi.mocked(listAgentThreads).mockResolvedValue([
      {
        id: 30,
        sessionId: 12,
        taskId: 88,
        agentId: 7,
        threadCode: 'TH001',
        threadName: 'Review and delivery',
        status: 'completed',
        contextJson: '{"stageCode":"review_and_delivery"}',
      },
    ]);
    vi.mocked(listCollaborationPlans).mockResolvedValue([
      {
        id: 20,
        sessionId: 12,
        planStatus: 'submitted',
        validationStatus: 'passed',
        planJson: JSON.stringify({
          stages: [
            { stageCode: 'requirement_analysis' },
            { stageCode: 'technical_design' },
            { stageCode: 'backend_implementation' },
            { stageCode: 'frontend_implementation' },
            { stageCode: 'testing' },
            { stageCode: 'review_and_delivery' },
          ],
        }),
      },
    ]);
    vi.mocked(listAgentHandoffs).mockResolvedValue([]);
    vi.mocked(listQualityGates).mockResolvedValue([
      {
        id: 50,
        sessionId: 12,
        gateCode: 'delivery_confirmed',
        gateName: 'Delivery confirmed',
        gateType: 'manual_confirmation',
        status: 'passed',
        required: true,
      },
    ]);
    vi.mocked(listTaskEvents).mockResolvedValue([]);

    renderCollaborationSession();

    expect(await screen.findByText('deliver agile team mvp')).toBeInTheDocument();
    expect(screen.getByText('Next MVP action: Agile team delivery completed.')).toBeInTheDocument();
  });

  it('disables agile gate pass until all target stage threads are completed', async () => {
    vi.mocked(getCollaborationSession).mockResolvedValue({
      id: 12,
      sessionCode: 'CS202606110001',
      strategyType: 'orchestrated_team',
      goalText: 'wait for implementation fan-out',
      status: 'running',
      currentStageCode: 'backend_implementation',
    });
    vi.mocked(listAgentThreads).mockResolvedValue([
      {
        id: 30,
        sessionId: 12,
        taskId: 88,
        agentId: 7,
        threadCode: 'TH001',
        threadName: 'Backend implementation',
        status: 'completed',
        contextJson: '{"stageCode":"backend_implementation"}',
      },
      {
        id: 31,
        sessionId: 12,
        taskId: 89,
        agentId: 8,
        threadCode: 'TH002',
        threadName: 'Frontend implementation',
        status: 'running',
        contextJson: '{"stageCode":"frontend_implementation"}',
      },
    ]);
    vi.mocked(listCollaborationPlans).mockResolvedValue([
      {
        id: 20,
        sessionId: 12,
        planStatus: 'submitted',
        validationStatus: 'passed',
        planJson: JSON.stringify({
          stages: [
            { stageCode: 'requirement_analysis' },
            { stageCode: 'technical_design' },
            { stageCode: 'backend_implementation' },
            { stageCode: 'frontend_implementation' },
            { stageCode: 'testing' },
            { stageCode: 'review_and_delivery' },
          ],
        }),
      },
    ]);
    vi.mocked(listAgentHandoffs).mockResolvedValue([]);
    vi.mocked(listQualityGates).mockResolvedValue([
      {
        id: 50,
        sessionId: 12,
        gateCode: 'implementation_done',
        gateName: 'Implementation done',
        gateType: 'artifact_check',
        status: 'pending',
        required: true,
      },
    ]);
    vi.mocked(listTaskEvents).mockResolvedValue([]);

    renderCollaborationSession();

    expect(await screen.findByText('wait for implementation fan-out')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Pass implementation_done' })).toBeDisabled();
  });

  it('keeps agile gate disabled until the completed target stage publishes an output artifact', async () => {
    vi.mocked(getCollaborationSession).mockResolvedValue({
      id: 12,
      sessionCode: 'CS202606110001',
      strategyType: 'orchestrated_team',
      goalText: 'wait for requirement artifact',
      status: 'running',
      currentStageCode: 'requirement_analysis',
    });
    vi.mocked(listAgentThreads).mockResolvedValue([
      {
        id: 30,
        sessionId: 12,
        taskId: 88,
        agentId: 7,
        threadCode: 'TH001',
        threadName: 'Requirement analysis',
        status: 'completed',
        contextJson: '{"stageCode":"requirement_analysis"}',
      },
    ]);
    vi.mocked(listCollaborationPlans).mockResolvedValue([
      {
        id: 20,
        sessionId: 12,
        planStatus: 'submitted',
        validationStatus: 'passed',
        planJson: JSON.stringify({
          stages: [
            { stageCode: 'requirement_analysis' },
            { stageCode: 'technical_design' },
            { stageCode: 'backend_implementation' },
            { stageCode: 'frontend_implementation' },
            { stageCode: 'testing' },
            { stageCode: 'review_and_delivery' },
          ],
        }),
      },
    ]);
    vi.mocked(listAgentHandoffs).mockResolvedValue([]);
    vi.mocked(listQualityGates).mockResolvedValue([
      {
        id: 50,
        sessionId: 12,
        gateCode: 'requirement_confirmed',
        gateName: 'Requirement confirmed',
        gateType: 'manual_confirmation',
        status: 'pending',
        required: true,
      },
    ]);
    vi.mocked(listTaskEvents).mockResolvedValue([]);

    renderCollaborationSession();

    expect(await screen.findByText('wait for requirement artifact')).toBeInTheDocument();
    expect(screen.getByText('Next MVP action: Wait for Requirement analysis to publish an output artifact before passing requirement_confirmed.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Pass requirement_confirmed' })).toBeDisabled();
  });

  it('shows pass gate as the next agile action when stage completion and artifact are ready', async () => {
    vi.mocked(getCollaborationSession).mockResolvedValue({
      id: 12,
      sessionCode: 'CS202606110001',
      strategyType: 'orchestrated_team',
      goalText: 'requirement gate ready',
      status: 'running',
      currentStageCode: 'requirement_analysis',
    });
    vi.mocked(listAgentThreads).mockResolvedValue([
      {
        id: 30,
        sessionId: 12,
        taskId: 88,
        agentId: 7,
        threadCode: 'TH001',
        threadName: 'Requirement analysis',
        status: 'completed',
        outputArtifactId: 99,
        contextJson: '{"stageCode":"requirement_analysis"}',
      },
    ]);
    vi.mocked(listCollaborationPlans).mockResolvedValue([
      {
        id: 20,
        sessionId: 12,
        planStatus: 'submitted',
        validationStatus: 'passed',
        planJson: JSON.stringify({
          stages: [
            { stageCode: 'requirement_analysis' },
            { stageCode: 'technical_design' },
            { stageCode: 'backend_implementation' },
            { stageCode: 'frontend_implementation' },
            { stageCode: 'testing' },
            { stageCode: 'review_and_delivery' },
          ],
        }),
      },
    ]);
    vi.mocked(listAgentHandoffs).mockResolvedValue([]);
    vi.mocked(listQualityGates).mockResolvedValue([
      {
        id: 50,
        sessionId: 12,
        gateCode: 'requirement_confirmed',
        gateName: 'Requirement confirmed',
        gateType: 'manual_confirmation',
        status: 'pending',
        required: true,
      },
    ]);
    vi.mocked(listTaskEvents).mockResolvedValue([]);

    renderCollaborationSession();

    expect(await screen.findByText('requirement gate ready')).toBeInTheDocument();
    expect(screen.getByText('Next MVP action: Pass quality gate requirement_confirmed.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Pass requirement_confirmed' })).toBeEnabled();
  });

  it('refreshes thread runtime summary after tool approval is handled', async () => {
    const user = userEvent.setup();
    vi.mocked(getCollaborationSession).mockResolvedValue({
      id: 12,
      sessionCode: 'CS202606110001',
      strategyType: 'orchestrated_team',
      goalText: 'approve tool call',
      status: 'running',
      currentStageCode: 'implementation',
    });
    vi.mocked(listAgentThreads)
      .mockResolvedValueOnce([
        {
          id: 30,
          sessionId: 12,
          taskId: 88,
          agentId: 7,
          threadCode: 'TH001',
          threadName: 'Implementation',
          status: 'running',
        },
      ])
      .mockResolvedValue([
        {
          id: 30,
          sessionId: 12,
          taskId: 88,
          agentId: 7,
          threadCode: 'TH001',
          threadName: 'Implementation',
          status: 'completed',
        },
      ]);
    vi.mocked(listCollaborationPlans).mockResolvedValue([]);
    vi.mocked(listAgentHandoffs).mockResolvedValue([]);
    vi.mocked(listQualityGates).mockResolvedValue([]);
    vi.mocked(handleApproval).mockResolvedValue(undefined);
    vi.mocked(listTaskEvents)
      .mockResolvedValueOnce([
        {
          id: 'event-tool-blocked',
          tenantId: 100,
          taskId: 88,
          runId: 99,
          eventType: 'TOOL_BLOCKED',
          payload: {
            toolCode: 'controlled.cli.project-update',
            toolCallIndex: 1,
            riskLevel: 'high',
            approvalRequestId: 101,
            reason: 'requires_human_approval',
          },
        },
      ])
      .mockResolvedValue([
        {
          id: 'event-tool-blocked',
          tenantId: 100,
          taskId: 88,
          runId: 99,
          eventType: 'TOOL_BLOCKED',
          payload: {
            toolCode: 'controlled.cli.project-update',
            toolCallIndex: 1,
            riskLevel: 'high',
            approvalRequestId: 101,
            reason: 'requires_human_approval',
          },
        },
        {
          id: 'event-tool-result',
          tenantId: 100,
          taskId: 88,
          runId: 99,
          eventType: 'TOOL_RESULT',
          payload: {
            toolCode: 'controlled.cli.project-update',
            toolCallIndex: 1,
            riskLevel: 'high',
          },
        },
      ]);

    renderCollaborationSession();

    expect(await screen.findByText('Tool blocked')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Approve' }));

    expect(handleApproval).toHaveBeenCalledWith(101, { action: 'approve' });
    await waitFor(() => expect(listTaskEvents).toHaveBeenCalledTimes(2));
    expect(await screen.findByText('Tool completed')).toBeInTheDocument();
    expect(await screen.findByText('completed')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument();
  });

  it('does not start collaboration session when submitted plan validation fails', async () => {
    const user = userEvent.setup();
    vi.mocked(getCollaborationSession).mockResolvedValue({
      id: 12,
      sessionCode: 'CS202606110001',
      strategyType: 'orchestrated_team',
      goalText: 'validate before start',
      status: 'planning',
    });
    vi.mocked(listAgentThreads).mockResolvedValue([]);
    const failedPlan = {
      id: 22,
      sessionId: 12,
      planStatus: 'submitted',
      validationStatus: 'failed',
      planJson: '{}',
      validationResultJson:
        '{"passed":false,"errors":["stage toolCalls requires createTask","stage toolCalls requires agentVersionId","stage toolCalls[0] tool not in agent version scope: controlled.http.project-query","stage toolCalls[1] toolCode mismatch: controlled.cli.wrong"]}',
    };
    vi.mocked(listCollaborationPlans).mockResolvedValueOnce([]).mockResolvedValueOnce([failedPlan]);
    vi.mocked(listAgentHandoffs).mockResolvedValue([]);
    vi.mocked(listQualityGates).mockResolvedValue([]);
    vi.mocked(listTaskEvents).mockResolvedValue([]);
    vi.mocked(submitCollaborationPlan).mockResolvedValue(failedPlan);

    renderCollaborationSession();

    await screen.findByText('validate before start');
    await user.click(screen.getByRole('button', { name: 'Submit and start' }));

    await waitFor(() => expect(submitCollaborationPlan).toHaveBeenCalledWith(12, expect.any(Object)));
    expect(startCollaborationSession).not.toHaveBeenCalled();
    expect(await screen.findByText('Plan validation failed')).toBeInTheDocument();
    expect(screen.getByText('stage toolCalls requires createTask')).toBeInTheDocument();
    expect(screen.getByText('stage toolCalls requires agentVersionId')).toBeInTheDocument();
    expect(screen.getByText('stage toolCalls[0] tool not in agent version scope: controlled.http.project-query')).toBeInTheDocument();
    expect(screen.getByText('stage toolCalls[1] toolCode mismatch: controlled.cli.wrong')).toBeInTheDocument();
  });
});
