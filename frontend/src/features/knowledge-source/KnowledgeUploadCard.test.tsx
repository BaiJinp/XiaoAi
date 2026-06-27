import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { KnowledgeUploadCard } from './components/KnowledgeUploadCard';

const retrieveResult = {
  chunkId: 1,
  documentId: 2,
  chunkIndex: 0,
  content: '项目本周完成了前端工作台和审批卡片。',
  sourceTitle: '本周项目资料',
  sourceType: 'knowledge_base',
  snippet: '前端工作台和审批卡片',
  confidence: 'medium',
  accessChecked: true,
  score: 100,
};

describe('KnowledgeUploadCard', () => {
  it('submits text material and shows ingest result', async () => {
    const ingest = vi.fn().mockResolvedValue({ documentId: 2, chunkCount: 1 });
    const retrieve = vi.fn().mockResolvedValue([]);
    const user = userEvent.setup();

    render(<KnowledgeUploadCard onIngest={ingest} onRetrieve={retrieve} />);

    await user.clear(screen.getByLabelText('知识库 ID'));
    await user.type(screen.getByLabelText('知识库 ID'), '5');
    await user.type(screen.getByLabelText('文档标题'), '本周项目资料');
    await user.type(screen.getByLabelText('文档文本内容'), '项目本周完成了前端工作台。');
    await user.click(screen.getByRole('button', { name: '提交入库' }));

    await waitFor(() =>
      expect(ingest).toHaveBeenCalledWith({
        knowledgeBaseId: 5,
        title: '本周项目资料',
        content: '项目本周完成了前端工作台。',
      }),
    );
    expect(await screen.findByText(/已入库/)).toBeInTheDocument();
    expect(screen.getByText(/切分 1 个片段/)).toBeInTheDocument();
  }, 10000);

  it('retrieves project material and renders source snippets', async () => {
    const ingest = vi.fn().mockResolvedValue({ documentId: 2, chunkCount: 1 });
    const retrieve = vi.fn().mockResolvedValue([retrieveResult]);
    const user = userEvent.setup();

    render(<KnowledgeUploadCard onIngest={ingest} onRetrieve={retrieve} />);

    await user.clear(screen.getByLabelText('检索知识库 ID'));
    await user.type(screen.getByLabelText('检索知识库 ID'), '5');
    await user.type(screen.getByLabelText('检索关键词'), '审批卡片');
    await user.click(screen.getByRole('button', { name: '检索资料' }));

    await waitFor(() =>
      expect(retrieve).toHaveBeenCalledWith({
        knowledgeBaseId: 5,
        query: '审批卡片',
        topK: 5,
      }),
    );
    expect(await screen.findByText('本周项目资料')).toBeInTheDocument();
    expect(screen.getByText('前端工作台和审批卡片')).toBeInTheDocument();
    expect(screen.getByText('中可信')).toBeInTheDocument();
    expect(screen.getByText('knowledge_base')).toBeInTheDocument();
    expect(screen.getByText('已校验权限')).toBeInTheDocument();
  });
});
