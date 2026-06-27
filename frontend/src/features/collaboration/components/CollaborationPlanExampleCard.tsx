import { Collapse, Space, Tag, Typography } from 'antd';

export const agileTeamToolChainExample = {
  goal: 'Deliver a requirement through a full agile development team',
  maxDepth: 1,
  maxThreads: 6,
  stages: [
    {
      stageCode: 'requirement_analysis',
      stageName: 'Requirement analysis',
      roleCode: 'software_product_manager',
      agentId: 101,
      agentVersionId: 1001,
      createTask: true,
      autoStartTask: true,
      inputText: 'Produce PRD, user stories, and acceptance criteria from the user requirement.',
      requiresGate: 'requirement_confirmed',
      gateName: 'Requirement confirmed',
      gateType: 'manual_confirmation',
    },
    {
      stageCode: 'technical_design',
      stageName: 'Technical design',
      roleCode: 'software_architect',
      agentId: 102,
      agentVersionId: 1002,
      createTask: true,
      autoStartTask: true,
      inputText: 'Produce technical design, API contract, and key risks from the confirmed requirement.',
      requiresGate: 'design_confirmed',
      gateName: 'Design confirmed',
      gateType: 'manual_confirmation',
    },
    {
      stageCode: 'backend_implementation',
      stageName: 'Backend implementation',
      roleCode: 'software_backend_developer',
      agentId: 103,
      agentVersionId: 1003,
      createTask: true,
      autoStartTask: true,
      inputText: 'Complete backend implementation from the technical design and output an implementation summary.',
      requiresGate: 'implementation_done',
      gateName: 'Implementation done',
      gateType: 'artifact_check',
    },
    {
      stageCode: 'frontend_implementation',
      stageName: 'Frontend implementation',
      roleCode: 'software_frontend_developer',
      agentId: 104,
      agentVersionId: 1004,
      createTask: true,
      autoStartTask: true,
      inputText: 'Complete frontend implementation from the technical design and output an implementation summary.',
      requiresGate: 'implementation_done',
      gateName: 'Implementation done',
      gateType: 'artifact_check',
    },
    {
      stageCode: 'testing',
      stageName: 'Test validation',
      roleCode: 'software_tester',
      agentId: 105,
      agentVersionId: 1005,
      createTask: true,
      autoStartTask: true,
      inputText: 'Produce the test plan, execution result, and remaining risks from acceptance criteria and implementation summaries.',
      requiresGate: 'tests_passed',
      gateName: 'Tests passed',
      gateType: 'test_result',
    },
    {
      stageCode: 'review_and_delivery',
      stageName: 'Review and delivery',
      roleCode: 'software_reviewer',
      agentId: 106,
      agentVersionId: 1006,
      createTask: true,
      autoStartTask: true,
      inputText: 'Produce review findings and delivery summary from design, implementation summaries, and test report.',
      requiresGate: 'delivery_confirmed',
      gateName: 'Delivery confirmed',
      gateType: 'manual_confirmation',
    },
  ],
};

export function CollaborationPlanExampleCard() {
  return (
    <Space orientation="vertical" size={8} style={{ width: '100%' }}>
      <Typography.Text type="secondary">
        stage.toolCalls lets each collaboration stage call approved CLI or HTTP business tools in order.
      </Typography.Text>
      <Space wrap>
        <Tag color="blue">agile team</Tag>
        <Tag color="green">six stages</Tag>
        <Tag color="purple">orchestrated_team</Tag>
      </Space>
      <Collapse
        size="small"
        defaultActiveKey={['plan-json']}
        items={[
          {
            key: 'plan-json',
            label: 'Plan JSON example',
            children: (
              <Typography.Paragraph style={{ marginBottom: 0 }}>
                <pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>
                  {JSON.stringify(agileTeamToolChainExample, null, 2)}
                </pre>
              </Typography.Paragraph>
            ),
          },
        ]}
      />
    </Space>
  );
}
