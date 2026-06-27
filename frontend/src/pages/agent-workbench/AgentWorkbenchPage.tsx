import { RocketOutlined } from '@ant-design/icons';
import { Button, Divider, Empty, Space, Typography } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { ChatComposer } from '../../features/chat/components/ChatComposer';
import { ChatTimeline } from '../../features/chat/components/ChatTimeline';
import { DemoScenarioPanel } from '../../features/demo/DemoScenarioPanel';
import { useChatStore } from '../../features/chat/chat-store';
import { useRuntimeEventStore } from '../../features/runtime-events/runtime-event-store';
import { useCreateTask } from '../../features/task-ledger/hooks/useCreateTask';
import { demoScenarios, type DemoScenario } from '../../mocks/demo-scenarios';
import { createCollaborationSession } from '../../services/collaboration-api';
import { subscribeRuntimeEvents } from '../../services/runtime-sse';
import { listTaskEvents, startTask } from '../../services/task-api';
import type { ApprovalRequest, ApprovalStatus } from '../../types/approval';
import type { RuntimeEvent } from '../../types/runtime-event';
import { ConversationSidebar } from './components/ConversationSidebar';
import { ExecutionPanel } from './components/ExecutionPanel';
import { WorkbenchLayout } from './components/WorkbenchLayout';

const starterPrompts = [
  '根据这段会议纪要生成行动项并创建任务',
  '根据本周资料生成项目周报',
  '分析当前项目延期风险',
];

const defaultProjectAssistantAgentId = 1;
const defaultMockChatModelId = 1;
const defaultHighRiskToolId = 10;
const emptyRuntimeEvents: RuntimeEvent[] = [];

function inferAssistantTaskType(content: string) {
  if (content.includes('风险')) {
    return 'risk_analysis';
  }
  if (content.includes('会议') || content.includes('行动项') || content.includes('任务')) {
    return 'meeting_minutes';
  }
  return 'weekly_report';
}

function buildRuntimeInput(content: string) {
  const assistantTaskType = inferAssistantTaskType(content);
  const runtimeInput: Record<string, unknown> = {
    assistantTaskType,
    modelId: defaultMockChatModelId,
    prompt: content,
    query: content,
  };
  if (assistantTaskType === 'meeting_minutes') {
    runtimeInput.toolId = defaultHighRiskToolId;
    runtimeInput.fallbackApproverUserId = Number(import.meta.env.VITE_DEV_USER_ID || '1000');
    runtimeInput.callPayloadJson = {
      source: 'agent-workbench',
      action: 'create_project_task',
      content,
    };
  }
  return JSON.stringify(runtimeInput);
}

function toApprovalRequest(event: RuntimeEvent): ApprovalRequest | undefined {
  const approvalRequestId = event.payload?.approvalRequestId;
  if (event.eventType !== 'APPROVAL_REQUIRED' || typeof approvalRequestId !== 'number') {
    return undefined;
  }
  return {
    id: approvalRequestId,
    taskId: event.taskId,
    runId: event.runId,
    title: typeof event.payload?.title === 'string' ? event.payload.title : '需要审批',
    reason: event.message,
    riskLevel:
      event.payload?.riskLevel === 'low' || event.payload?.riskLevel === 'medium' || event.payload?.riskLevel === 'high'
        ? event.payload.riskLevel
        : undefined,
    executorType:
      typeof event.payload?.executorType === 'string'
        ? event.payload.executorType
        : typeof event.payload?.toolType === 'string'
          ? event.payload.toolType
          : undefined,
    status: 'pending',
    approverUserId: typeof event.payload?.approverUserId === 'number' ? event.payload.approverUserId : undefined,
  };
}

function getApprovalStatus(events: RuntimeEvent[], approvalRequestId: number): ApprovalStatus {
  for (let index = events.length - 1; index >= 0; index -= 1) {
    const event = events[index];
    if (
      (event.eventType === 'APPROVAL_APPROVED' || event.eventType === 'APPROVAL_REJECTED') &&
      event.payload?.approvalRequestId === approvalRequestId
    ) {
      return event.eventType === 'APPROVAL_APPROVED' ? 'approved' : 'rejected';
    }
  }
  return 'pending';
}

export function AgentWorkbenchPage() {
  const [currentTaskId, setCurrentTaskId] = useState<number>();
  const [composerDraft, setComposerDraft] = useState('');
  const [selectedScenario, setSelectedScenario] = useState<DemoScenario>();
  const [isStartingTask, setIsStartingTask] = useState(false);
  const unsubscribeRuntimeEventsRef = useRef<(() => void) | null>(null);
  const messages = useChatStore((state) => state.messages);
  const currentTaskEvents = useRuntimeEventStore((state) =>
    currentTaskId ? state.eventsByTaskId[currentTaskId] || emptyRuntimeEvents : emptyRuntimeEvents,
  );
  const currentLastEventId = useRuntimeEventStore((state) =>
    currentTaskId ? state.lastEventIdByTaskId[currentTaskId] : undefined,
  );
  const addMessage = useChatStore((state) => state.addMessage);
  const appendRuntimeEvent = useRuntimeEventStore((state) => state.appendEvent);
  const createTaskMutation = useCreateTask();

  useEffect(() => {
    return () => {
      unsubscribeRuntimeEventsRef.current?.();
    };
  }, []);

  const refreshCurrentTaskEvents = async () => {
    if (currentTaskId === undefined) {
      return;
    }
    const events = await listTaskEvents(currentTaskId, currentLastEventId);
    events.forEach((event) => appendRuntimeEvent(currentTaskId, event));
  };

  const handleScenarioSelect = (scenario: DemoScenario) => {
    setSelectedScenario(scenario);
    setComposerDraft(scenario.prompt);
  };

  const handleSubmit = async (content: string) => {
    addMessage({ role: 'user', content });
    try {
      if (selectedScenario?.templateCode) {
        const response = await createCollaborationSession({
          strategyType: selectedScenario.strategyType || 'orchestrated_team',
          goalText: content,
          contextJson: JSON.stringify({
            source: 'agent-workbench',
            templateCode: selectedScenario.templateCode,
            scenarioTitle: selectedScenario.title,
          }),
        });
        addMessage({
          role: 'assistant',
          content: `协作会话已创建：${response.sessionCode || `#${response.sessionId}`}，请进入协作会话查看多 Agent 交付进度。`,
          link: {
            label: '查看协作会话',
            href: `/collaboration/${response.sessionId}`,
          },
        });
        setSelectedScenario(undefined);
        return;
      }

      const response = await createTaskMutation.mutateAsync({
        agentId: defaultProjectAssistantAgentId,
        input: buildRuntimeInput(content),
        channel: 'web',
      });
      setCurrentTaskId(response.taskId);
      setIsStartingTask(true);
      await startTask(response.taskId, { agentId: defaultProjectAssistantAgentId });
      unsubscribeRuntimeEventsRef.current?.();
      const handleRuntimeEvent = (event: RuntimeEvent) => {
        appendRuntimeEvent(response.taskId, event);
        const approval = toApprovalRequest(event);
        if (approval) {
          addMessage({
            role: 'assistant',
            content: '需要审批',
            approval,
          });
        }
      };
      unsubscribeRuntimeEventsRef.current = subscribeRuntimeEvents(response.taskId, {
        onEvent: handleRuntimeEvent,
        onError: async () => {
          const lastEventId = useRuntimeEventStore.getState().lastEventIdByTaskId[response.taskId];
          try {
            const missedEvents = await listTaskEvents(response.taskId, lastEventId);
            missedEvents.forEach(handleRuntimeEvent);
          } catch {
            addMessage({
              role: 'system',
              content: '运行事件连接异常，请稍后刷新任务进度。',
            });
          }
        },
      }, useRuntimeEventStore.getState().lastEventIdByTaskId[response.taskId]);
      addMessage({
        role: 'assistant',
        content: `任务已创建：${response.taskCode || `#${response.taskId}`}，正在准备启动运行。`,
        link: {
          label: '查看任务详情',
          href: `/tasks/${response.taskId}`,
        },
      });
    } catch (error) {
      addMessage({
        role: 'system',
        content: error instanceof Error ? `任务创建失败：${error.message}` : '任务创建失败，请稍后重试。',
      });
    } finally {
      setIsStartingTask(false);
    }
  };

  return (
    <main className="agent-workbench-shell">
      <header style={{ padding: '20px 24px 16px' }}>
        <Space align="center" size={12}>
          <RocketOutlined style={{ color: '#1677ff', fontSize: 28 }} />
          <div>
            <Typography.Title level={3} style={{ margin: 0 }}>
              项目助理 Agent 工作台
            </Typography.Title>
            <Typography.Text type="secondary">
              从会议、资料和任务状态中生成行动项、周报和风险，并在权限边界内推进后续动作。
            </Typography.Text>
          </div>
        </Space>
      </header>

      <WorkbenchLayout
        sidebar={<ConversationSidebar />}
        executionPanel={<ExecutionPanel taskId={currentTaskId} onApprovalHandled={refreshCurrentTaskEvents} />}
      >
        <section className="agent-workbench-scroll" style={{ padding: 24 }}>
          <Space direction="vertical" size={24} style={{ width: '100%', minHeight: '100%' }}>
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={
                <Space direction="vertical" size={8}>
                  <Typography.Title level={4} style={{ margin: 0 }}>
                    让项目助理开始处理真实任务
                  </Typography.Title>
                  <Typography.Text type="secondary">
                    贴入会议纪要、项目资料或项目问题，Agent 会展示计划、执行过程、审批和最终交付物。
                  </Typography.Text>
                </Space>
              }
            />

            <div>
              <Typography.Text strong>试试：</Typography.Text>
              <Space wrap style={{ marginTop: 12 }}>
                {starterPrompts.map((prompt) => (
                  <Button
                    key={prompt}
                    onClick={() => {
                      setSelectedScenario(undefined);
                      setComposerDraft(prompt);
                    }}
                  >
                    {prompt}
                  </Button>
                ))}
              </Space>
            </div>

            <DemoScenarioPanel scenarios={demoScenarios} onSelect={handleScenarioSelect} />

            <Divider />

            <ChatTimeline
              messages={messages}
              getApprovalStatus={(approval) => getApprovalStatus(currentTaskEvents, approval.id)}
              onApprovalHandled={refreshCurrentTaskEvents}
            />

            <ChatComposer
              disabled={createTaskMutation.isPending || isStartingTask}
              draft={composerDraft}
              onDraftChange={(draft) => {
                setSelectedScenario(undefined);
                setComposerDraft(draft);
              }}
              onSubmit={handleSubmit}
            />
          </Space>
        </section>
      </WorkbenchLayout>
    </main>
  );
}
