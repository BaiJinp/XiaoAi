import { Alert, Collapse, Empty, Space, Tag, Typography } from 'antd';
import type { CollaborationPlan } from '../../../types/collaboration';

interface CollaborationPlanListProps {
  plans: CollaborationPlan[];
}

function formatPlanJson(planJson: string) {
  try {
    return JSON.stringify(JSON.parse(planJson), null, 2);
  } catch {
    return planJson;
  }
}

function validationErrors(validationResultJson?: string) {
  if (!validationResultJson) {
    return [];
  }
  try {
    const parsed = JSON.parse(validationResultJson) as { errors?: unknown };
    return Array.isArray(parsed.errors) ? parsed.errors.filter((error): error is string => typeof error === 'string') : [];
  } catch {
    return [];
  }
}

export function CollaborationPlanList({ plans }: CollaborationPlanListProps) {
  if (plans.length === 0) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无已提交协作计划" />;
  }

  return (
    <Space orientation="vertical" size={12} style={{ width: '100%' }}>
      {plans.map((plan, index) => {
        const errors = validationErrors(plan.validationResultJson);
        return (
          <Collapse
            key={plan.id}
            size="small"
            defaultActiveKey={index === 0 ? [`plan-${plan.id}`] : undefined}
            items={[
              {
                key: `plan-${plan.id}`,
                label: (
                  <Space wrap>
                    <Typography.Text strong>Plan #{plan.id}</Typography.Text>
                    <Tag>{plan.planStatus}</Tag>
                    <Tag color={plan.validationStatus === 'passed' ? 'green' : 'orange'}>{plan.validationStatus}</Tag>
                  </Space>
                ),
                children: (
                  <Space orientation="vertical" size={8} style={{ width: '100%' }}>
                    {errors.length > 0 ? (
                      <Alert
                        type="warning"
                        showIcon
                        title="Plan validation failed"
                        description={
                          <Space orientation="vertical" size={2}>
                            {errors.map((error) => (
                              <Typography.Text key={error}>{error}</Typography.Text>
                            ))}
                          </Space>
                        }
                      />
                    ) : null}
                    <Typography.Paragraph style={{ marginBottom: 0 }}>
                      <pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>{formatPlanJson(plan.planJson)}</pre>
                    </Typography.Paragraph>
                  </Space>
                ),
              },
            ]}
          />
        );
      })}
    </Space>
  );
}
