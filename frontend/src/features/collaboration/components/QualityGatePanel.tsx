import { Button, Empty, Flex, Space, Tag, Tooltip, Typography } from 'antd';
import type { QualityGate } from '../../../types/collaboration';

interface QualityGatePanelProps {
  gates: QualityGate[];
  gateReadiness?: Record<number, { canPass: boolean; reason?: string }>;
  actingGate?: { gateId: number; action: 'pass' | 'fail' };
  onPassGate?: (gate: QualityGate) => void;
  onFailGate?: (gate: QualityGate) => void;
}

interface QualitySnapshot {
  checked?: boolean;
  status?: string;
  artifactCount?: number;
  warnings?: string[];
}

export function QualityGatePanel({ gates, gateReadiness, actingGate, onPassGate, onFailGate }: QualityGatePanelProps) {
  if (gates.length === 0) {
    return <Empty description="暂无质量门禁" />;
  }

  return (
    <Space orientation="vertical" size={12} style={{ width: '100%' }}>
      {gates.map((gate) => {
        const quality = parseQualitySnapshot(gate.resultJson);
        const readiness = gateReadiness?.[gate.id];
        const passDisabled = readiness ? !readiness.canPass : false;
        return (
          <Flex key={gate.id} justify="space-between" align="center" gap={12}>
            <Space orientation="vertical" size={2}>
              <Typography.Text strong>{gate.gateName}</Typography.Text>
              <Typography.Text type="secondary">{gate.gateCode}</Typography.Text>
              <Typography.Text type="secondary">{gate.gateType}</Typography.Text>
              {quality ? (
                <Space size={6} wrap>
                  <Tag color={quality.status === 'passed' ? 'green' : 'gold'}>Quality: {quality.status}</Tag>
                  <Tag>Artifacts: {quality.artifactCount ?? 0}</Tag>
                  {quality.warnings?.[0] ? (
                    <Typography.Text type="warning">{quality.warnings[0]}</Typography.Text>
                  ) : null}
                </Space>
              ) : null}
            </Space>
            <Space>
              {gate.required ? <Tag color="red">必需</Tag> : <Tag>可选</Tag>}
              <Tag>{gate.status}</Tag>
              {gate.status === 'pending' ? (
                <>
                  <Tooltip title={passDisabled ? readiness?.reason : undefined}>
                    <Button
                      size="small"
                      type="primary"
                      aria-label={`Pass ${gate.gateCode}`}
                      disabled={passDisabled}
                      loading={actingGate?.gateId === gate.id && actingGate.action === 'pass'}
                      onClick={() => onPassGate?.(gate)}
                    >
                      Pass
                    </Button>
                  </Tooltip>
                  <Button
                    size="small"
                    danger
                    aria-label={`Fail ${gate.gateCode}`}
                    loading={actingGate?.gateId === gate.id && actingGate.action === 'fail'}
                    onClick={() => onFailGate?.(gate)}
                  >
                    Fail
                  </Button>
                </>
              ) : null}
            </Space>
          </Flex>
        );
      })}
    </Space>
  );
}

function parseQualitySnapshot(resultJson?: string): QualitySnapshot | undefined {
  if (!resultJson?.trim()) {
    return undefined;
  }
  try {
    const result = JSON.parse(resultJson) as { quality?: QualitySnapshot };
    if (!result.quality?.checked) {
      return undefined;
    }
    return result.quality;
  } catch {
    return undefined;
  }
}
