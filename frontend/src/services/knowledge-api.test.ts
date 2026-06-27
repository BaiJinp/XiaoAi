import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ingestKnowledgeText, retrieveKnowledge } from './knowledge-api';
import { http } from './http';

vi.mock('./http', async () => {
  const actual = await vi.importActual<typeof import('./http')>('./http');
  return {
    ...actual,
    http: {
      post: vi.fn(),
    },
  };
});

describe('knowledge-api', () => {
  beforeEach(() => {
    vi.mocked(http.post).mockReset();
  });

  it('maps text ingest request to backend command fields', async () => {
    vi.mocked(http.post).mockResolvedValue({
      data: {
        code: 0,
        message: '成功',
        data: {
          documentId: 31,
          documentCode: 'DOC-31',
          chunkCount: 3,
        },
      },
    });

    const response = await ingestKnowledgeText({
      knowledgeBaseId: 5,
      title: '项目资料',
      content: '项目背景和目标',
    });

    expect(http.post).toHaveBeenCalledWith('/v1/knowledge-documents/ingest-text', {
      knowledgeBaseId: 5,
      documentName: '项目资料',
      text: '项目背景和目标',
    });
    expect(response).toEqual({
      documentId: 31,
      documentCode: 'DOC-31',
      chunkCount: 3,
    });
  });

  it('normalizes retrieve results for frontend source preview', async () => {
    vi.mocked(http.post).mockResolvedValue({
      data: {
        code: 0,
        message: '成功',
        data: [
          {
            chunkId: 11,
            documentId: 31,
            chunkIndex: 0,
            chunkText: '项目目标是完成 MVP',
            sourceJson: '{"documentName":"项目资料"}',
            score: 100,
            sourceType: 'knowledge_base',
            snippet: 'MVP',
            confidence: 'high',
            accessChecked: true,
          },
        ],
      },
    });

    const response = await retrieveKnowledge({ knowledgeBaseId: 5, query: 'MVP', topK: 3 });

    expect(http.post).toHaveBeenCalledWith('/v1/knowledge-documents/retrieve', {
      knowledgeBaseId: 5,
      query: 'MVP',
      topK: 3,
    });
    expect(response).toEqual([
      {
        chunkId: 11,
        documentId: 31,
        chunkIndex: 0,
        content: '项目目标是完成 MVP',
        sourceTitle: '项目资料',
        sourceType: 'knowledge_base',
        snippet: 'MVP',
        confidence: 'high',
        accessChecked: true,
        score: 100,
      },
    ]);
  });
});
