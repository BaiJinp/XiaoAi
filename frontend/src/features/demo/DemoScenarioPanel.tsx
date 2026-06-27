import { Card, Space, Typography } from 'antd';
import type { DemoScenario } from '../../mocks/demo-scenarios';

interface DemoScenarioPanelProps {
  scenarios: DemoScenario[];
  onSelect: (scenario: DemoScenario) => void;
}

export function DemoScenarioPanel({ scenarios, onSelect }: DemoScenarioPanelProps) {
  return (
    <section>
      <Typography.Text strong>Demo 场景：</Typography.Text>
      <Space orientation="vertical" size={12} style={{ width: '100%', marginTop: 12 }}>
        {scenarios.map((scenario) => (
          <Card
            aria-label={scenario.title}
            hoverable
            key={scenario.title}
            onClick={() => onSelect(scenario)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                onSelect(scenario);
              }
            }}
            role="button"
            size="small"
            tabIndex={0}
            title={scenario.title}
          >
            <Typography.Text type="secondary">{scenario.description}</Typography.Text>
          </Card>
        ))}
      </Space>
    </section>
  );
}
