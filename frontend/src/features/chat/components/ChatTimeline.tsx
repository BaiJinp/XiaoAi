import { RobotOutlined, UserOutlined } from '@ant-design/icons';
import { Avatar, Card, Empty, Space, Typography } from 'antd';
import { Link } from 'react-router-dom';
import ReactMarkdown from 'react-markdown';
import { ApprovalCard } from '../../approval-card/components/ApprovalCard';
import { useHandleApproval } from '../../approval-card/hooks/useHandleApproval';
import type { ApprovalRequest, ApprovalStatus } from '../../../types/approval';
import type { ChatMessage } from '../chat-store';

interface ChatTimelineProps {
  messages: ChatMessage[];
  getApprovalStatus?: (approval: ApprovalRequest) => ApprovalStatus | undefined;
  onApprovalHandled?: (approval: ApprovalRequest) => void | Promise<void>;
}

const roleLabel: Record<ChatMessage['role'], string> = {
  user: '你',
  assistant: '项目助理 Agent',
  system: '系统',
};

export function ChatTimeline({ messages, getApprovalStatus, onApprovalHandled }: ChatTimelineProps) {
  const handleApprovalMutation = useHandleApproval();

  if (messages.length === 0) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有消息，输入任务后 Agent 会在这里回复" />;
  }

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      {messages.map((message) => (
        <div
          key={message.id}
          style={{
            display: 'flex',
            justifyContent: message.role === 'user' ? 'flex-end' : 'flex-start',
          }}
        >
          <Space align="start" style={{ maxWidth: '82%' }}>
            {message.role !== 'user' && <Avatar icon={<RobotOutlined />} style={{ background: '#1677ff' }} />}
            <Card
              size="small"
              style={{
                background: message.role === 'user' ? '#e6f4ff' : '#ffffff',
                borderColor: message.role === 'user' ? '#91caff' : '#f0f0f0',
              }}
            >
              <Space direction="vertical" size={8} style={{ width: '100%' }}>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {roleLabel[message.role]}
                </Typography.Text>
                {message.approval ? (
                  <ApprovalCard
                    approval={{
                      ...message.approval,
                      status: getApprovalStatus?.(message.approval) || message.approval.status,
                    }}
                    loading={handleApprovalMutation.isPending}
                    onApprove={(approvalRequest) =>
                      handleApprovalMutation.mutate(
                        { requestId: approvalRequest.id, action: 'approve' },
                        { onSuccess: () => void onApprovalHandled?.(approvalRequest) },
                      )
                    }
                    onReject={(approvalRequest) =>
                      handleApprovalMutation.mutate(
                        { requestId: approvalRequest.id, action: 'reject' },
                        { onSuccess: () => void onApprovalHandled?.(approvalRequest) },
                      )
                    }
                  />
                ) : message.role === 'assistant' ? (
                  <div className="chat-markdown-content">
                    <ReactMarkdown>{message.content}</ReactMarkdown>
                  </div>
                ) : (
                  <Typography.Paragraph style={{ margin: 0, whiteSpace: 'pre-wrap' }}>
                    {message.content}
                  </Typography.Paragraph>
                )}
                {message.link ? <Link to={message.link.href}>{message.link.label}</Link> : null}
              </Space>
            </Card>
            {message.role === 'user' && <Avatar icon={<UserOutlined />} style={{ background: '#52c41a' }} />}
          </Space>
        </div>
      ))}
    </Space>
  );
}
