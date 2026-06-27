import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { HandoffList } from './HandoffList';

describe('HandoffList', () => {
  it('renders artifact handoff audit metadata when present', () => {
    render(
      <HandoffList
        handoffs={[
          {
            id: 9,
            sessionId: 1,
            artifactId: 30,
            handoffType: 'artifact',
            status: 'pending',
            messageText: 'Continue from PRD',
            metadataJson: JSON.stringify({
              fromThreadName: 'Requirement analysis',
              toThreadName: 'Technical design',
              artifactMetadata: {
                artifactVersion: 2,
                producerAgentId: 501,
                producerAgentVersionId: 601,
                stageCode: 'requirement_analysis',
              },
            }),
          },
        ]}
      />,
    );

    expect(screen.getByText('Artifact #30')).toBeInTheDocument();
    expect(screen.getByText('Requirement analysis -> Technical design')).toBeInTheDocument();
    expect(screen.getByText('artifact v2')).toBeInTheDocument();
    expect(screen.getByText('producer Agent 501')).toBeInTheDocument();
    expect(screen.getByText('version 601')).toBeInTheDocument();
    expect(screen.getByText('requirement_analysis')).toBeInTheDocument();
  });
});
