import { DownloadOutlined, ReloadOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Card, Flex, Input, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { toUserEvent } from '../../features/runtime-events/runtime-event-presenter';
import { listTaskEventsByType } from '../../services/task-api';
import type { RuntimeEvent } from '../../types/runtime-event';
import { buildToolAuditCsv } from './toolAuditExport';
import { filterToolAuditEvents, type ToolAuditFilters } from './toolAuditFilters';

const DEFAULT_PAGE = 1;
const DEFAULT_PAGE_SIZE = 50;

function textPayload(event: RuntimeEvent, fieldName: string) {
  const value = event.payload?.[fieldName];
  if (typeof value === 'number') {
    return String(value);
  }
  return typeof value === 'string' ? value : '-';
}

function normalizeFilterValue(value: string | number | undefined) {
  const normalizedValue = value === undefined ? undefined : String(value).trim();
  return normalizedValue || undefined;
}

function toBackendFilters(filters: ToolAuditFilters) {
  return {
    keyword: normalizeFilterValue(filters.keyword),
    taskId: normalizeFilterValue(filters.taskId),
    runId: normalizeFilterValue(filters.runId),
    agentVersionId: normalizeFilterValue(filters.agentVersionId),
  };
}

export function ToolAuditPage() {
  const [filters, setFilters] = useState<ToolAuditFilters>({});
  const [backendFilters, setBackendFilters] = useState(() => toBackendFilters(filters));
  const [page, setPage] = useState(DEFAULT_PAGE);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setBackendFilters(toBackendFilters(filters));
    }, 300);
    return () => window.clearTimeout(timer);
  }, [filters]);
  const deniedEventsQuery = useQuery({
    queryKey: ['task-events', 'TOOL_DENIED', backendFilters, page, pageSize],
    queryFn: () =>
      listTaskEventsByType('TOOL_DENIED', {
        pageNo: page,
        pageSize,
        ...backendFilters,
      }),
  });
  const deniedEvents = deniedEventsQuery.data?.records || [];
  const filteredEvents = useMemo(
    () => filterToolAuditEvents(deniedEvents, filters),
    [deniedEvents, filters],
  );

  const updateFilter = (fieldName: keyof ToolAuditFilters, value: string) => {
    setPage(DEFAULT_PAGE);
    setFilters((current) => ({
      ...current,
      [fieldName]: value,
    }));
  };

  const exportFilteredEvents = () => {
    const csv = buildToolAuditCsv(filteredEvents);
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'tool-audit-denied-events.csv';
    link.click();
    URL.revokeObjectURL(url);
  };

  const columns: ColumnsType<RuntimeEvent> = [
    {
      title: 'Event',
      dataIndex: 'sequence',
      width: 96,
      render: (_, event) => <Typography.Text strong>#{event.sequence ?? event.id}</Typography.Text>,
    },
    {
      title: 'Task / Run',
      width: 140,
      render: (_, event) => (
        <Space orientation="vertical" size={0}>
          <Link to={`/tasks/${event.taskId}`}>Task {event.taskId}</Link>
          <Typography.Text type="secondary">Run {event.runId}</Typography.Text>
        </Space>
      ),
    },
    {
      title: 'Tool',
      render: (_, event) => (
        <Space orientation="vertical" size={0}>
          <Typography.Text>{textPayload(event, 'toolCode')}</Typography.Text>
          <Typography.Text type="secondary">ID {textPayload(event, 'toolId')}</Typography.Text>
        </Space>
      ),
    },
    {
      title: 'AgentVersion',
      width: 132,
      render: (_, event) => {
        const agentVersionId = textPayload(event, 'agentVersionId');
        return (
          <Space direction="vertical" size={4}>
            <Tag color="red">{agentVersionId}</Tag>
            {agentVersionId !== '-' ? (
              <Link to={`/agent-config?versionId=${agentVersionId}`}>Fix scope</Link>
            ) : null}
          </Space>
        );
      },
    },
    {
      title: 'Reason',
      dataIndex: 'payload',
      render: (_, event) => {
        const userEvent = toUserEvent(event);
        return (
          <Space orientation="vertical" size={0}>
            <Typography.Text>{userEvent.description || event.message || '-'}</Typography.Text>
            <Typography.Text type="secondary">{textPayload(event, 'reason')}</Typography.Text>
          </Space>
        );
      },
    },
    {
      title: 'Time',
      dataIndex: 'createTime',
      width: 190,
      render: (value?: string) => value || '-',
    },
  ];

  return (
    <main className="agent-workbench-shell" style={{ padding: 24 }}>
      <Space orientation="vertical" size={20} style={{ width: '100%' }}>
        <Space align="center" style={{ justifyContent: 'space-between', width: '100%' }}>
          <div>
            <Typography.Title level={3} style={{ margin: 0 }}>
              工具越权审计
            </Typography.Title>
            <Typography.Text type="secondary">查看最近被 AgentVersion 工具范围拦截的调用事件。</Typography.Text>
          </div>
          <Button icon={<ReloadOutlined />} onClick={() => deniedEventsQuery.refetch()}>
            刷新
          </Button>
        </Space>
        {deniedEventsQuery.isError ? (
          <Alert
            type="error"
            showIcon
            message="审计事件加载失败"
            description={deniedEventsQuery.error.message}
          />
        ) : null}
        <Card variant="borderless">
          <Flex gap={12} wrap="wrap" style={{ marginBottom: 16 }}>
            <Input
              allowClear
              placeholder="Search tool, reason, task, run, version"
              style={{ width: 320 }}
              value={filters.keyword}
              onChange={(event) => updateFilter('keyword', event.target.value)}
            />
            <Input
              allowClear
              placeholder="Task ID"
              style={{ width: 140 }}
              value={filters.taskId}
              onChange={(event) => updateFilter('taskId', event.target.value)}
            />
            <Input
              allowClear
              placeholder="Run ID"
              style={{ width: 140 }}
              value={filters.runId}
              onChange={(event) => updateFilter('runId', event.target.value)}
            />
            <Input
              allowClear
              placeholder="AgentVersion ID"
              style={{ width: 180 }}
              value={filters.agentVersionId}
              onChange={(event) => updateFilter('agentVersionId', event.target.value)}
            />
            <Button icon={<DownloadOutlined />} onClick={exportFilteredEvents} disabled={filteredEvents.length === 0}>
              Export CSV
            </Button>
          </Flex>
          <Table
            rowKey="id"
            columns={columns}
            dataSource={filteredEvents}
            loading={deniedEventsQuery.isLoading}
            pagination={{
              current: page,
              pageSize,
              total: deniedEventsQuery.data?.total || filteredEvents.length,
              showSizeChanger: true,
              showTotal: (total) => `Total ${total}`,
            }}
            onChange={(pagination) => {
              setPage(pagination.current || DEFAULT_PAGE);
              setPageSize(pagination.pageSize || DEFAULT_PAGE_SIZE);
            }}
          />
        </Card>
      </Space>
    </main>
  );
}
