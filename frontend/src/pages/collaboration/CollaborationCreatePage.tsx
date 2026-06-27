import { useMutation, useQuery } from '@tanstack/react-query';
import { Button, Card, Form, Input, Select, Space, Typography, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import { createCollaborationSession, listCollaborationTemplates } from '../../services/collaboration-api';
import type { CreateCollaborationSessionRequest } from '../../types/collaboration';
import { AgentRoleDefaultBindingCard } from '../../features/collaboration/components/AgentRoleDefaultBindingCard';
import { TemplateRoleBindingCard } from '../../features/collaboration/components/TemplateRoleBindingCard';

const defaultContext = {
  source: 'collaboration-create-page',
};

export function CollaborationCreatePage() {
  const navigate = useNavigate();
  const [messageApi, contextHolder] = message.useMessage();
  const [form] = Form.useForm<CreateCollaborationSessionRequest>();
  const selectedTemplateId = Form.useWatch('templateId', form);
  const templatesQuery = useQuery({
    queryKey: ['collaboration-templates', 'software_development'],
    queryFn: () => listCollaborationTemplates('software_development'),
  });

  const createMutation = useMutation({
    mutationFn: (request: CreateCollaborationSessionRequest) => createCollaborationSession(request),
    onSuccess: (response) => {
      messageApi.success('Collaboration session created');
      navigate(`/collaboration/${response.sessionId}`);
    },
    onError: (error) => {
      messageApi.error(error instanceof Error ? error.message : 'Collaboration session create failed');
    },
  });

  return (
    <main className="agent-workbench-shell" style={{ padding: 24 }}>
      {contextHolder}
      <Space direction="vertical" size={20} style={{ width: '100%' }}>
        <div>
          <Typography.Title level={3} style={{ margin: 0 }}>
            Create collaboration session
          </Typography.Title>
          <Typography.Text type="secondary">
            Start an orchestrated Agent team from a requirement goal.
          </Typography.Text>
        </div>

        <Card variant="borderless">
          <Form
            form={form}
            layout="vertical"
            initialValues={{
              strategyType: 'orchestrated_team',
              contextJson: JSON.stringify(defaultContext, null, 2),
            }}
            onFinish={(values) => createMutation.mutate(values)}
          >
            <Form.Item
              label="Goal"
              name="goalText"
              rules={[{ required: true, message: 'Please enter the collaboration goal' }]}
            >
              <Input.TextArea rows={5} placeholder="Paste the requirement or business goal" />
            </Form.Item>
            <Form.Item
              label="Strategy"
              name="strategyType"
              rules={[{ required: true, message: 'Please select a strategy' }]}
            >
              <Select
                options={[
                  { label: 'Orchestrated team', value: 'orchestrated_team' },
                  { label: 'Self orchestrated', value: 'self_orchestrated' },
                ]}
              />
            </Form.Item>
            <Form.Item label="Team template" name="templateId">
              <Select
                allowClear
                loading={templatesQuery.isLoading}
                placeholder="Select a collaboration template"
                options={(templatesQuery.data ?? []).map((template) => ({
                  label: `${template.templateName} (${template.templateCode})`,
                  value: template.templateId,
                }))}
              />
            </Form.Item>
            <Form.Item label="Context JSON" name="contextJson">
              <Input.TextArea rows={6} />
            </Form.Item>
            <Button type="primary" htmlType="submit" loading={createMutation.isPending}>
              Create session
            </Button>
          </Form>
        </Card>
        <TemplateRoleBindingCard templateId={selectedTemplateId} />
        <AgentRoleDefaultBindingCard />
      </Space>
    </main>
  );
}
