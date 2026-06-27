import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Card, Col, Descriptions, Flex, Progress, Result, Row, Skeleton, Space, Tag, Typography, message } from 'antd';
import { useParams } from 'react-router-dom';
import { AgentThreadList } from '../../features/collaboration/components/AgentThreadList';
import {
  CollaborationPlanExampleCard,
  agileTeamToolChainExample,
} from '../../features/collaboration/components/CollaborationPlanExampleCard';
import { CollaborationPlanList } from '../../features/collaboration/components/CollaborationPlanList';
import { CollaborationPlanSubmitCard } from '../../features/collaboration/components/CollaborationPlanSubmitCard';
import { CollaborationNextStep } from '../../features/collaboration/components/CollaborationNextStep';
import { CollaborationTimeline } from '../../features/collaboration/components/CollaborationTimeline';
import { HandoffList } from '../../features/collaboration/components/HandoffList';
import { QualityGatePanel } from '../../features/collaboration/components/QualityGatePanel';
import { summarizeThreadRuntime } from '../../features/collaboration/components/threadRuntimeSummary';
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
import { listTaskEvents } from '../../services/task-api';
import { handleApproval } from '../../services/approval-api';
import type { ApprovalAction } from '../../types/approval';
import type { AgentHandoff, AgentThread, CollaborationPlan, CollaborationSession, QualityGate } from '../../types/collaboration';

const AGILE_MVP_STAGES = [
  { stageCode: 'requirement_analysis', stageName: 'Requirement analysis', gateCode: 'requirement_confirmed' },
  { stageCode: 'technical_design', stageName: 'Technical design', gateCode: 'design_confirmed' },
  { stageCode: 'backend_implementation', stageName: 'Backend implementation', gateCode: 'implementation_done' },
  { stageCode: 'frontend_implementation', stageName: 'Frontend implementation', gateCode: 'implementation_done' },
  { stageCode: 'testing', stageName: 'Test validation', gateCode: 'tests_passed' },
  { stageCode: 'review_and_delivery', stageName: 'Review and delivery', gateCode: 'delivery_confirmed' },
];

function parseSessionId(sessionId?: string) {
  const value = Number(sessionId);
  return Number.isFinite(value) && value > 0 ? value : undefined;
}

function latestPlan(plans: CollaborationPlan[]) {
  return plans[0];
}

function isAgileMvpPlan(plan?: CollaborationPlan) {
  if (!plan?.planJson?.trim()) {
    return false;
  }
  try {
    const parsed = JSON.parse(plan.planJson);
    const stages = Array.isArray(parsed?.stages) ? parsed.stages : [];
    const stageCodes = new Set(
      stages
        .map((stage: unknown) => (stage && typeof stage === 'object' && !Array.isArray(stage) ? (stage as { stageCode?: unknown }).stageCode : undefined))
        .filter((stageCode: unknown): stageCode is string => typeof stageCode === 'string'),
    );
    return AGILE_MVP_STAGES.every((stage) => stageCodes.has(stage.stageCode));
  } catch {
    return false;
  }
}

function findStageThread(threads: AgentThread[], stageCode: string) {
  return threads.find((thread) => {
    if (thread.contextJson?.includes(`"stageCode":"${stageCode}"`)) {
      return true;
    }
    return (thread.threadName || '').toLowerCase() === stageCode.split('_').join(' ');
  });
}

function agileGateStageReadiness(threads: AgentThread[], gate: QualityGate) {
  const gateStages = AGILE_MVP_STAGES.filter((stage) => stage.gateCode === gate.gateCode);
  if (gateStages.length === 0) {
    return { canPass: true };
  }
  for (const stage of gateStages) {
    const thread = findStageThread(threads, stage.stageCode);
    if (!thread) {
      return {
        canPass: false,
        reason: `Wait for ${stage.stageName} to be created before passing ${gate.gateCode}.`,
      };
    }
    if (thread.status !== 'completed') {
      return {
        canPass: false,
        reason: `Wait for ${stage.stageName} to complete before passing ${gate.gateCode}.`,
      };
    }
    if (!thread.outputArtifactId) {
      return {
        canPass: false,
        reason: `Wait for ${stage.stageName} to publish an output artifact before passing ${gate.gateCode}.`,
      };
    }
  }
  return { canPass: true };
}

function agileGateReadiness(plans: CollaborationPlan[], threads: AgentThread[], gates: QualityGate[]) {
  if (!isAgileMvpPlan(latestPlan(plans))) {
    return undefined;
  }
  return Object.fromEntries(
    gates.map((gate) => [gate.id, agileGateStageReadiness(threads, gate)]),
  );
}

function nextAgileAction(
  session: CollaborationSession | undefined,
  plans: CollaborationPlan[],
  threads: AgentThread[],
  handoffs: AgentHandoff[],
  gates: QualityGate[],
) {
  if (session?.status === 'completed') {
    return 'Agile team delivery completed.';
  }
  const plan = latestPlan(plans);
  if (!plan) {
    return 'Submit and start the agile team plan.';
  }
  if (plan.validationStatus !== 'passed') {
    return 'Fix plan validation errors before starting the team.';
  }
  const pendingHandoff = handoffs.find((handoff) => handoff.handoffType === 'artifact' && handoff.status === 'pending');
  if (pendingHandoff) {
    return `Accept artifact handoff #${pendingHandoff.id}.`;
  }
  const runnableThread = threads.find((thread) => thread.taskId && !['running', 'completed'].includes(thread.status));
  if (runnableThread) {
    return `Start ${runnableThread.threadName || runnableThread.threadCode}.`;
  }
  const failedGate = gates.find((gate) => gate.status === 'failed');
  if (session?.status === 'blocked' || failedGate) {
    return `Resolve failed gate ${failedGate?.gateCode || session?.currentStageCode || ''}.`;
  }
  const pendingGate = gates.find((gate) => gate.status === 'pending');
  if (pendingGate) {
    if (isAgileMvpPlan(plan)) {
      const readiness = agileGateStageReadiness(threads, pendingGate);
      return readiness.canPass ? `Pass quality gate ${pendingGate.gateCode}.` : readiness.reason || `Review quality gate ${pendingGate.gateCode}.`;
    }
    return `Review quality gate ${pendingGate.gateCode}.`;
  }
  return 'Review final artifacts and delivery summary.';
}

function AgileTeamMvpProgress({
  session,
  plans,
  threads,
  handoffs,
  gates,
}: {
  session?: CollaborationSession;
  plans: CollaborationPlan[];
  threads: AgentThread[];
  handoffs: AgentHandoff[];
  gates: QualityGate[];
}) {
  const agilePlan = isAgileMvpPlan(latestPlan(plans));
  const completedStages = AGILE_MVP_STAGES.filter((stage) => {
    const thread = findStageThread(threads, stage.stageCode);
    const gate = gates.find((item) => item.gateCode === stage.gateCode);
    return thread?.status === 'completed' || gate?.status === 'passed';
  }).length;
  const currentStage = AGILE_MVP_STAGES.find((stage) => stage.stageCode === session?.currentStageCode);
  const nextAction = nextAgileAction(session, plans, threads, handoffs, gates);

  return (
    <Card title="Agile team MVP progress" variant="borderless">
      <Space orientation="vertical" size={12} style={{ width: '100%' }}>
        {!agilePlan ? (
          <Alert type="info" showIcon title="Use the Agile team preset or software-development template to run the MVP team." />
        ) : null}
        <Flex justify="space-between" align="center" gap={16}>
          <Space orientation="vertical" size={2}>
            <Typography.Text strong>{completedStages}/6 stages completed</Typography.Text>
            <Typography.Text type="secondary">
              Current stage: {currentStage?.stageName || session?.currentStageCode || 'not started'}
            </Typography.Text>
          </Space>
          <Progress type="circle" size={72} percent={Math.round((completedStages / AGILE_MVP_STAGES.length) * 100)} />
        </Flex>
        <Space wrap>
          {AGILE_MVP_STAGES.map((stage) => {
            const thread = findStageThread(threads, stage.stageCode);
            const gate = gates.find((item) => item.gateCode === stage.gateCode);
            const status = gate?.status === 'passed' || thread?.status === 'completed'
              ? 'done'
              : thread?.status || gate?.status || 'waiting';
            const color = status === 'done' ? 'green' : status === 'running' ? 'blue' : status === 'failed' ? 'red' : undefined;
            return (
              <Tag key={stage.stageCode} color={color}>
                {stage.stageName}: {status}
              </Tag>
            );
          })}
        </Space>
        <Alert type="success" showIcon title={`Next MVP action: ${nextAction}`} />
      </Space>
    </Card>
  );
}

export function CollaborationSessionPage() {
  const params = useParams();
  const sessionId = parseSessionId(params.sessionId);
  const queryClient = useQueryClient();
  const [messageApi, contextHolder] = message.useMessage();

  const sessionQuery = useQuery({
    queryKey: ['collaboration-session', sessionId],
    queryFn: () => getCollaborationSession(sessionId!),
    enabled: sessionId !== undefined,
  });
  const sessionTemplateQuery = useQuery({
    queryKey: ['collaboration-template', sessionQuery.data?.templateId],
    queryFn: () => getCollaborationTemplate(sessionQuery.data!.templateId!),
    enabled: sessionQuery.data?.templateId !== undefined,
  });
  const threadsQuery = useQuery({
    queryKey: ['collaboration-threads', sessionId],
    queryFn: () => listAgentThreads(sessionId!),
    enabled: sessionId !== undefined,
  });
  const plansQuery = useQuery({
    queryKey: ['collaboration-plans', sessionId],
    queryFn: () => listCollaborationPlans(sessionId!),
    enabled: sessionId !== undefined,
  });
  const handoffsQuery = useQuery({
    queryKey: ['collaboration-handoffs', sessionId],
    queryFn: () => listAgentHandoffs(sessionId!),
    enabled: sessionId !== undefined,
  });
  const gatesQuery = useQuery({
    queryKey: ['collaboration-gates', sessionId],
    queryFn: () => listQualityGates(sessionId!),
    enabled: sessionId !== undefined,
  });
  const threadTaskIds = (threadsQuery.data || [])
    .map((thread) => thread.taskId)
    .filter((taskId): taskId is number => taskId !== undefined);
  const threadEventsQuery = useQuery({
    queryKey: ['collaboration-thread-events', sessionId, threadTaskIds.join(',')],
    queryFn: async () => {
      const entries = await Promise.all(
        threadTaskIds.map(async (taskId) => [taskId, await listTaskEvents(taskId)] as const),
      );
      return Object.fromEntries(entries);
    },
    enabled: sessionId !== undefined && threadTaskIds.length > 0,
  });
  const invalidateCollaborationState = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['collaboration-session', sessionId] }),
      queryClient.invalidateQueries({ queryKey: ['collaboration-gates', sessionId] }),
      queryClient.invalidateQueries({ queryKey: ['collaboration-threads', sessionId] }),
      queryClient.invalidateQueries({ queryKey: ['collaboration-handoffs', sessionId] }),
      queryClient.invalidateQueries({ queryKey: ['collaboration-plans', sessionId] }),
      queryClient.invalidateQueries({ queryKey: ['collaboration-thread-events', sessionId] }),
    ]);
  };
  const startThreadMutation = useMutation({
    mutationFn: (threadId: number) => startAgentThreadTask(sessionId!, threadId),
    onSuccess: async () => {
      messageApi.success('Thread task started');
      await invalidateCollaborationState();
    },
    onError: (error) => {
      messageApi.error(error instanceof Error ? error.message : 'Thread task start failed');
    },
  });
  const handleApprovalMutation = useMutation({
    mutationFn: ({ approvalRequestId, action }: { approvalRequestId: number; action: ApprovalAction }) =>
      handleApproval(approvalRequestId, { action }),
    onSuccess: async () => {
      messageApi.success('Approval handled');
      await invalidateCollaborationState();
    },
    onError: (error) => {
      messageApi.error(error instanceof Error ? error.message : 'Approval handle failed');
    },
  });
  const submitAndStartPlanMutation = useMutation({
    mutationFn: async (planJson: string) => {
      const plan = await submitCollaborationPlan(sessionId!, { planJson });
      if (plan.validationStatus !== 'passed') {
        return { plan, started: false };
      }
      const result = await startCollaborationSession(sessionId!, { planId: plan.id });
      return { plan, result, started: true };
    },
    onSuccess: async ({ started }) => {
      messageApi.success(started ? 'Collaboration session started' : 'Collaboration plan validation failed');
      await invalidateCollaborationState();
    },
    onError: (error) => {
      messageApi.error(error instanceof Error ? error.message : 'Collaboration plan submit failed');
    },
  });
  const passGateMutation = useMutation({
    mutationFn: (gateId: number) => passQualityGate(sessionId!, gateId, { resultJson: '{}' }),
    onSuccess: async () => {
      messageApi.success('Quality gate passed');
      await invalidateCollaborationState();
    },
    onError: (error) => {
      messageApi.error(error instanceof Error ? error.message : 'Quality gate pass failed');
    },
  });
  const failGateMutation = useMutation({
    mutationFn: (gateId: number) => failQualityGate(sessionId!, gateId, { failReason: 'Rejected from collaboration session page' }),
    onSuccess: async () => {
      messageApi.success('Quality gate failed');
      await invalidateCollaborationState();
    },
    onError: (error) => {
      messageApi.error(error instanceof Error ? error.message : 'Quality gate fail failed');
    },
  });
  const acceptHandoffMutation = useMutation({
    mutationFn: (handoffId: number) => acceptAgentHandoff(sessionId!, handoffId),
    onSuccess: async () => {
      messageApi.success('Handoff accepted');
      await invalidateCollaborationState();
    },
    onError: (error) => {
      messageApi.error(error instanceof Error ? error.message : 'Handoff accept failed');
    },
  });
  const rejectHandoffMutation = useMutation({
    mutationFn: (handoffId: number) => rejectAgentHandoff(sessionId!, handoffId),
    onSuccess: async () => {
      messageApi.success('Handoff rejected');
      await invalidateCollaborationState();
    },
    onError: (error) => {
      messageApi.error(error instanceof Error ? error.message : 'Handoff reject failed');
    },
  });

  if (!sessionId) {
    return <Result status="404" title="协作会话不存在" subTitle="请检查协作会话 ID 是否正确。" />;
  }

  const threads = threadsQuery.data || [];
  const plans = plansQuery.data || [];
  const handoffs = handoffsQuery.data || [];
  const gates = gatesQuery.data || [];
  const gateReadiness = agileGateReadiness(plans, threads, gates);
  const runtimeSummaries = Object.fromEntries(
    Object.entries(threadEventsQuery.data || {}).map(([taskId, events]) => [Number(taskId), summarizeThreadRuntime(events)]),
  );

  return (
    <main className="agent-workbench-shell" style={{ padding: 24 }}>
      {contextHolder}
      <Space orientation="vertical" size={20} style={{ width: '100%' }}>
        <div>
          <Typography.Title level={3} style={{ margin: 0 }}>
            通用协作会话
          </Typography.Title>
          <Typography.Text type="secondary">查看协作目标、Agent 线程、结构化交接物和质量门禁。</Typography.Text>
        </div>

        <Card variant="borderless">
          {sessionQuery.isLoading ? (
            <Skeleton active />
          ) : sessionQuery.data ? (
            <Descriptions column={2} bordered size="small">
              <Descriptions.Item label="协作目标" span={2}>
                {sessionQuery.data.goalText}
              </Descriptions.Item>
              <Descriptions.Item label="会话编号">{sessionQuery.data.sessionCode}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag>{sessionQuery.data.status}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="协作策略">{sessionQuery.data.strategyType}</Descriptions.Item>
              <Descriptions.Item label="当前阶段">{sessionQuery.data.currentStageCode || '-'}</Descriptions.Item>
            </Descriptions>
          ) : (
            <Result status="error" title="协作会话加载失败" subTitle={sessionQuery.error?.message || '请稍后重试'} />
          )}
        </Card>

        <AgileTeamMvpProgress
          session={sessionQuery.data}
          plans={plans}
          threads={threads}
          handoffs={handoffs}
          gates={gates}
        />

        <CollaborationNextStep
          session={sessionQuery.data}
          plans={plans}
          threads={threads}
          handoffs={handoffs}
          gates={gates}
          startingThreadId={startThreadMutation.isPending ? startThreadMutation.variables : undefined}
          onStartThreadTask={(thread) => startThreadMutation.mutate(thread.id)}
        />

        <Row gutter={16} align="top">
          <Col span={16}>
            <Space orientation="vertical" size={16} style={{ width: '100%' }}>
              <Card title="协作时间线" variant="borderless">
                <CollaborationTimeline threads={threads} gates={gates} />
              </Card>
              <Card title="Agent 线程" variant="borderless">
                <AgentThreadList
                  threads={threads}
                  handoffs={handoffs}
                  runtimeSummaries={runtimeSummaries}
                  startingThreadId={startThreadMutation.isPending ? startThreadMutation.variables : undefined}
                  handlingApprovalId={
                    handleApprovalMutation.isPending ? handleApprovalMutation.variables?.approvalRequestId : undefined
                  }
                  onStartThreadTask={(thread) => startThreadMutation.mutate(thread.id)}
                  onHandleApproval={(approvalRequestId, action) =>
                    handleApprovalMutation.mutate({ approvalRequestId, action })
                  }
                />
              </Card>
            </Space>
          </Col>
          <Col span={8}>
            <Space orientation="vertical" size={16} style={{ width: '100%' }}>
              <Card title="协作计划工具链" variant="borderless">
                <CollaborationPlanExampleCard />
              </Card>
              <Card title="提交并启动计划" variant="borderless">
                <CollaborationPlanSubmitCard
                  defaultPlanJson={JSON.stringify(agileTeamToolChainExample, null, 2)}
                  templateId={sessionQuery.data?.templateId}
                  templatePlanJson={sessionTemplateQuery.data?.templateJson}
                  loading={submitAndStartPlanMutation.isPending}
                  onSubmitAndStart={(planJson) => submitAndStartPlanMutation.mutate(planJson)}
                />
              </Card>
              <Card title="已提交协作计划" variant="borderless">
                <CollaborationPlanList plans={plans} />
              </Card>
              <Card title="交接物" variant="borderless">
                <HandoffList
                  handoffs={handoffs}
                  actingHandoff={
                    acceptHandoffMutation.isPending && acceptHandoffMutation.variables !== undefined
                      ? { handoffId: acceptHandoffMutation.variables, action: 'accept' }
                      : rejectHandoffMutation.isPending && rejectHandoffMutation.variables !== undefined
                        ? { handoffId: rejectHandoffMutation.variables, action: 'reject' }
                        : undefined
                  }
                  onAcceptHandoff={(handoff) => acceptHandoffMutation.mutate(handoff.id)}
                  onRejectHandoff={(handoff) => rejectHandoffMutation.mutate(handoff.id)}
                />
              </Card>
              <Card title="质量门禁" variant="borderless">
                <QualityGatePanel
                  gates={gates}
                  gateReadiness={gateReadiness}
                  actingGate={
                    passGateMutation.isPending && passGateMutation.variables !== undefined
                      ? { gateId: passGateMutation.variables, action: 'pass' }
                      : failGateMutation.isPending && failGateMutation.variables !== undefined
                        ? { gateId: failGateMutation.variables, action: 'fail' }
                        : undefined
                  }
                  onPassGate={(gate) => passGateMutation.mutate(gate.id)}
                  onFailGate={(gate) => failGateMutation.mutate(gate.id)}
                />
              </Card>
            </Space>
          </Col>
        </Row>
      </Space>
    </main>
  );
}
