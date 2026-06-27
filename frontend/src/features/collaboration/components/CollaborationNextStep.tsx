import { Alert, Button, Space, Typography } from 'antd';
import { Link } from 'react-router-dom';
import type { AgentHandoff, AgentThread, CollaborationPlan, CollaborationSession, QualityGate } from '../../../types/collaboration';

interface CollaborationNextStepProps {
  session?: CollaborationSession;
  plans: CollaborationPlan[];
  threads: AgentThread[];
  handoffs?: AgentHandoff[];
  gates: QualityGate[];
  onStartThreadTask?: (thread: AgentThread) => void;
  startingThreadId?: number;
}

function latestPlan(plans: CollaborationPlan[]) {
  return plans[0];
}

function runnableThread(threads: AgentThread[], handoffs: AgentHandoff[]) {
  return threads.find((thread) => thread.taskId && !['running', 'completed'].includes(thread.status) && !isWaitingForHandoff(thread, handoffs));
}

function waitingHandoffThread(threads: AgentThread[], handoffs: AgentHandoff[]) {
  return threads.find((thread) => thread.taskId && !['running', 'completed'].includes(thread.status) && isWaitingForHandoff(thread, handoffs));
}

function pendingGate(gates: QualityGate[]) {
  return gates.find((gate) => gate.status === 'pending');
}

function failedGate(gates: QualityGate[]) {
  return gates.find((gate) => gate.status === 'failed');
}

export function CollaborationNextStep({
  session,
  plans,
  threads,
  handoffs = [],
  gates,
  onStartThreadTask,
  startingThreadId,
}: CollaborationNextStepProps) {
  const plan = latestPlan(plans);
  const thread = runnableThread(threads, handoffs);
  const waitingThread = waitingHandoffThread(threads, handoffs);
  const gate = pendingGate(gates);
  const failed = failedGate(gates);

  if (!plan) {
    return <Alert type="info" showIcon title="Next step: submit a collaboration plan with stage toolCalls." />;
  }
  if (plan.validationStatus !== 'passed') {
    return <Alert type="warning" showIcon title="Next step: fix the latest plan validation errors before start." />;
  }
  if (!threads.length && session?.status !== 'running') {
    return <Alert type="info" showIcon title="Next step: start the validated collaboration plan." />;
  }
  if (session?.status === 'blocked' || failed) {
    return (
      <Alert
        type="error"
        showIcon
        title={`Next step: resolve failed quality gate ${failed?.gateCode || session?.currentStageCode || ''}.`}
        description={failed?.failReason || 'The collaboration session is blocked until the rework stage or gate issue is handled.'}
      />
    );
  }
  if (thread) {
    return (
      <Alert
        type="info"
        showIcon
        title={
          <Space wrap>
            <Typography.Text>Next step: start {thread.threadName || thread.threadCode}.</Typography.Text>
            {onStartThreadTask ? (
              <Button size="small" loading={startingThreadId === thread.id} onClick={() => onStartThreadTask(thread)}>
                Start thread
              </Button>
            ) : null}
            {thread.taskId ? <Link to={`/tasks/${thread.taskId}`}>Task #{thread.taskId}</Link> : null}
          </Space>
        }
      />
    );
  }
  if (waitingThread) {
    return (
      <Alert
        type="warning"
        showIcon
        title={`Next step: accept the artifact handoff before starting ${waitingThread.threadName || waitingThread.threadCode}.`}
      />
    );
  }
  if (gate) {
    return (
      <Alert
        type="warning"
        showIcon
        title={`Next step: review quality gate ${gate.gateCode}. Passing it can advance the next agent stage.`}
      />
    );
  }
  if (threads.some((item) => item.taskId)) {
    return (
      <Alert
        type="success"
        showIcon
        title="Next step: inspect task events and artifacts from completed or running agent threads."
      />
    );
  }
  return <Alert type="success" showIcon title="Collaboration session has no pending operation." />;
}

function isWaitingForHandoff(thread: AgentThread, handoffs: AgentHandoff[]) {
  if (!requiresAcceptedInputHandoff(thread)) {
    return false;
  }
  return handoffs.some(
    (handoff) =>
      handoff.toThreadId === thread.id &&
      handoff.artifactId === thread.inputArtifactId &&
      handoff.handoffType === 'artifact' &&
      handoff.status !== 'accepted',
  );
}

function requiresAcceptedInputHandoff(thread: AgentThread) {
  if (!thread.contextJson?.trim()) {
    return false;
  }
  try {
    const context = JSON.parse(thread.contextJson);
    return Boolean(context?.requireAcceptedInputHandoff);
  } catch {
    return false;
  }
}
