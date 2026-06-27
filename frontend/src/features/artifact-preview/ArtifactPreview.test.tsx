import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { TaskArtifact } from '../../types/artifact';
import { ArtifactPreview } from './components/ArtifactPreview';

const markdownArtifact: TaskArtifact = {
  id: 1,
  taskId: 21,
  artifactType: 'markdown',
  title: '项目周报',
  content: '# 项目周报\n\n- 已完成需求分析',
};

const actionItemsArtifact: TaskArtifact = {
  id: 2,
  taskId: 21,
  artifactType: 'action_items',
  title: '行动项',
  content: JSON.stringify([
    { title: '确认上线范围', owner: '张三', dueDate: '2026-06-12', status: '待处理' },
  ]),
};

const riskListArtifact: TaskArtifact = {
  id: 3,
  taskId: 21,
  artifactType: 'risk_list',
  title: '风险清单',
  content: JSON.stringify([
    { risk: '接口延期', impact: '影响联调', mitigation: '提前 Mock', level: 'high' },
  ]),
};

const weeklyReportArtifact: TaskArtifact = {
  ...markdownArtifact,
  id: 4,
  artifactType: 'weekly_report',
  metadata: {
    knowledgeConfidence: 'low',
    lowConfidence: true,
    sourceRefs: [{ title: '项目资料' }],
  },
};

const meetingActionItemsArtifact: TaskArtifact = {
  ...actionItemsArtifact,
  id: 5,
  artifactType: 'meeting_action_items',
};

const riskAnalysisArtifact: TaskArtifact = {
  ...riskListArtifact,
  id: 6,
  artifactType: 'risk_analysis',
};

describe('ArtifactPreview', () => {
  const writeText = vi.fn();

  beforeEach(() => {
    writeText.mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText },
    });
  });

  afterEach(() => {
    writeText.mockReset();
  });

  it('renders action items and risk list tables', () => {
    render(<ArtifactPreview artifacts={[actionItemsArtifact, riskListArtifact]} />);

    expect(screen.getByText('确认上线范围')).toBeInTheDocument();
    expect(screen.getByText('张三')).toBeInTheDocument();
    expect(screen.getByText('接口延期')).toBeInTheDocument();
    expect(screen.getByText('提前 Mock')).toBeInTheDocument();
  });

  it('renders markdown artifact heading and copies content', async () => {
    render(<ArtifactPreview artifacts={[markdownArtifact]} />);

    expect(screen.getAllByText('项目周报')).toHaveLength(2);
    expect(screen.getByRole('heading', { name: '项目周报' })).toBeInTheDocument();
    expect(screen.getByText('已完成需求分析')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /复制/ }));

    await waitFor(() => expect(writeText).toHaveBeenCalledWith(markdownArtifact.content));
  });

  it('renders design artifact types with existing previews', () => {
    render(<ArtifactPreview artifacts={[weeklyReportArtifact, meetingActionItemsArtifact, riskAnalysisArtifact]} />);

    expect(screen.getByRole('heading', { name: '项目周报' })).toBeInTheDocument();
    expect(screen.getByText('确认上线范围')).toBeInTheDocument();
    expect(screen.getByText('接口延期')).toBeInTheDocument();
  });

  it('renders artifact evidence summary from metadata', () => {
    render(<ArtifactPreview artifacts={[weeklyReportArtifact]} />);

    expect(screen.getByText('低可信')).toBeInTheDocument();
    expect(screen.getByText('1 个来源')).toBeInTheDocument();
    expect(screen.getByText('依据有限，内容需要人工复核')).toBeInTheDocument();
  });
});
