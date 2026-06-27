import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { QualityGate } from '../../../types/collaboration';
import { QualityGatePanel } from './QualityGatePanel';

const gate: QualityGate = {
  id: 50,
  sessionId: 12,
  gateCode: 'implementation_done',
  gateName: 'Implementation Done',
  gateType: 'manual_confirmation',
  status: 'pending',
  required: true,
};

describe('QualityGatePanel', () => {
  it('renders quality snapshot from gate result json', () => {
    render(
      <QualityGatePanel
        gates={[
          {
            ...gate,
            status: 'passed',
            resultJson: JSON.stringify({
              quality: {
                checked: true,
                status: 'warning',
                artifactCount: 2,
                warnings: ['artifact #702 missing metadata stageCode'],
              },
            }),
          },
        ]}
      />,
    );

    expect(screen.getByText('Quality: warning')).toBeInTheDocument();
    expect(screen.getByText('Artifacts: 2')).toBeInTheDocument();
    expect(screen.getByText('artifact #702 missing metadata stageCode')).toBeInTheDocument();
  });

  it('keeps pending gate actions available', async () => {
    const user = userEvent.setup();
    const onPassGate = vi.fn();
    const onFailGate = vi.fn();

    render(<QualityGatePanel gates={[gate]} onPassGate={onPassGate} onFailGate={onFailGate} />);

    await user.click(screen.getByRole('button', { name: 'Pass implementation_done' }));
    await user.click(screen.getByRole('button', { name: 'Fail implementation_done' }));

    expect(onPassGate).toHaveBeenCalledWith(gate);
    expect(onFailGate).toHaveBeenCalledWith(gate);
  });

  it('disables pass action when gate is not ready', async () => {
    const user = userEvent.setup();
    const onPassGate = vi.fn();

    render(
      <QualityGatePanel
        gates={[gate]}
        gateReadiness={{
          50: {
            canPass: false,
            reason: 'Wait for Backend implementation to complete before passing implementation_done.',
          },
        }}
        onPassGate={onPassGate}
      />,
    );

    const passButton = screen.getByRole('button', { name: 'Pass implementation_done' });
    expect(passButton).toBeDisabled();
    await user.click(passButton);
    expect(onPassGate).not.toHaveBeenCalled();
  });
});
