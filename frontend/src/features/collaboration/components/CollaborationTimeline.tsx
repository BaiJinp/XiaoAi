import { Timeline } from 'antd';
import type { AgentThread, QualityGate } from '../../../types/collaboration';

interface CollaborationTimelineProps {
  threads: AgentThread[];
  gates: QualityGate[];
}

export function CollaborationTimeline({ threads, gates }: CollaborationTimelineProps) {
  const items = [
    ...threads.map((thread) => ({
      color: thread.status === 'completed' ? 'green' : 'blue',
      content: thread.threadName || thread.threadCode,
    })),
    ...gates.map((gate) => ({
      color: gate.status === 'passed' ? 'green' : gate.status === 'failed' ? 'red' : 'gray',
      content: gate.gateName,
    })),
  ];

  return <Timeline items={items} />;
}
