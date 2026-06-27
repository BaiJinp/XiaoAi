import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Card, Col, Descriptions, Input, List, Result, Row, Skeleton, Space, Tag, Typography, message } from 'antd';
import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { ArtifactPreview } from '../../features/artifact-preview/components/ArtifactPreview';
import { ExecutionTimeline } from '../../features/runtime-events/components/ExecutionTimeline';
import { createAgentMemory, listAgentMemories } from '../../services/memory-api';
import { getTask, listTaskArtifacts, listTaskEvents } from '../../services/task-api';
import type { Task } from '../../types/task';

function parseTaskId(taskId?: string) {
  const value = Number(taskId);
  return Number.isFinite(value) && value > 0 ? value : undefined;
}

export function TaskDetailPage() {
  const params = useParams();
  const taskId = parseTaskId(params.taskId);
  const queryClient = useQueryClient();
  const [memorySummary, setMemorySummary] = useState('');
  const [messageApi, contextHolder] = message.useMessage();

  const taskQuery = useQuery({
    queryKey: ['task', taskId],
    queryFn: () => getTask(taskId!),
    enabled: taskId !== undefined,
  });
  const eventsQuery = useQuery({
    queryKey: ['task-events', taskId],
    queryFn: () => listTaskEvents(taskId!),
    enabled: taskId !== undefined,
  });
  const artifactsQuery = useQuery({
    queryKey: ['task-artifacts', taskId],
    queryFn: () => listTaskArtifacts(taskId!),
    enabled: taskId !== undefined,
  });
  const memoriesQuery = useQuery({
    queryKey: ['agent-memories', 'task', taskId],
    queryFn: () => listAgentMemories({ taskId: taskId!, status: 'confirmed' }),
    enabled: taskId !== undefined,
  });
  const createMemoryMutation = useMutation({
    mutationFn: (task: Task) =>
      createAgentMemory({
        agentId: task.agentId!,
        agentVersionId: task.agentVersionId,
        taskId: task.id,
        runId: task.currentRunId,
        memoryType: 'session_summary',
        memoryScope: 'task',
        summaryText: memorySummary,
        sourceText: task.resultSummary || task.input,
      }),
    onSuccess: async () => {
      setMemorySummary('');
      await queryClient.invalidateQueries({ queryKey: ['agent-memories', 'task', taskId] });
      messageApi.success('记忆已确认保存');
    },
    onError: (error) => {
      messageApi.error(error instanceof Error ? error.message : '记忆保存失败');
    },
  });

  if (!taskId) {
    return <Result status="404" title="任务不存在" subTitle="请检查任务 ID 是否正确。" />;
  }

  return (
    <main className="agent-workbench-shell" style={{ padding: 24 }}>
      {contextHolder}
      <Space direction="vertical" size={20} style={{ width: '100%' }}>
        <div>
          <Typography.Title level={3} style={{ margin: 0 }}>
            任务详情
          </Typography.Title>
          <Typography.Text type="secondary">查看任务事实、Runtime 执行过程、审批和交付物。</Typography.Text>
        </div>

        <Card variant="borderless">
          {taskQuery.isLoading ? (
            <Skeleton active />
          ) : taskQuery.data ? (
            <Descriptions column={2} bordered size="small">
              <Descriptions.Item label="任务标题">{taskQuery.data.title || `任务 #${taskQuery.data.id}`}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag>{taskQuery.data.status}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="任务编号">{taskQuery.data.taskCode || '-'}</Descriptions.Item>
              <Descriptions.Item label="当前 runId">{taskQuery.data.currentRunId || '-'}</Descriptions.Item>
              <Descriptions.Item label="创建时间">{taskQuery.data.createTime || '-'}</Descriptions.Item>
              <Descriptions.Item label="Agent ID">{taskQuery.data.agentId || '-'}</Descriptions.Item>
              <Descriptions.Item label="任务输入" span={2}>
                {taskQuery.data.input || '-'}
              </Descriptions.Item>
              {taskQuery.data.resultSummary ? (
                <Descriptions.Item label="结果摘要" span={2}>
                  {taskQuery.data.resultSummary}
                </Descriptions.Item>
              ) : null}
            </Descriptions>
          ) : (
            <Result status="error" title="任务加载失败" subTitle={taskQuery.error?.message || '请稍后重试'} />
          )}
        </Card>

        <Row gutter={16} align="top">
          <Col span={14}>
            <Card title="执行事件" variant="borderless">
              <ExecutionTimeline events={eventsQuery.data || []} />
            </Card>
          </Col>
          <Col span={10}>
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              <Card title="交付物" variant="borderless">
                <ArtifactPreview artifacts={artifactsQuery.data || []} />
              </Card>
              <Card title="已确认记忆" variant="borderless">
                <Space direction="vertical" size={12} style={{ width: '100%' }}>
                  {memoriesQuery.isLoading ? (
                    <Skeleton active paragraph={{ rows: 2 }} />
                  ) : memoriesQuery.data?.records.length ? (
                    <List
                      size="small"
                      dataSource={memoriesQuery.data.records}
                      renderItem={(memory) => (
                        <List.Item>
                          <Space direction="vertical" size={4}>
                            <Typography.Text>{memory.summaryText}</Typography.Text>
                            <Typography.Text type="secondary">
                              {memory.memoryType || 'session_summary'} / {memory.confidence || 'confirmed'}
                            </Typography.Text>
                          </Space>
                        </List.Item>
                      )}
                    />
                  ) : (
                    <Typography.Text type="secondary">暂无已确认记忆</Typography.Text>
                  )}
                  <Input.TextArea
                    aria-label="确认记忆摘要"
                    rows={3}
                    value={memorySummary}
                    onChange={(event) => setMemorySummary(event.target.value)}
                  />
                  <Button
                    type="primary"
                    disabled={!taskQuery.data?.agentId || !memorySummary.trim()}
                    loading={createMemoryMutation.isPending}
                    onClick={() => taskQuery.data && createMemoryMutation.mutate(taskQuery.data)}
                  >
                    确认保存
                  </Button>
                </Space>
              </Card>
            </Space>
          </Col>
        </Row>
      </Space>
    </main>
  );
}
