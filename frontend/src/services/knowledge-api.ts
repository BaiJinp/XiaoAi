import { http, unwrap } from './http';

export interface IngestKnowledgeTextRequest {
  knowledgeBaseId: number;
  title: string;
  content: string;
}

export interface KnowledgeIngestResponse {
  documentId: number;
  documentCode?: string;
  chunkCount: number;
}

export interface RetrieveKnowledgeRequest {
  knowledgeBaseId: number;
  query: string;
  topK?: number;
}

interface BackendKnowledgeRetrieveResult {
  chunkId: number;
  documentId: number;
  chunkIndex?: number;
  chunkText?: string;
  sourceJson?: string;
  score?: number;
  sourceTitle?: string;
  sourceType?: string;
  snippet?: string;
  confidence?: 'high' | 'medium' | 'low' | 'conflicting' | 'insufficient' | string;
  accessChecked?: boolean;
}

export interface KnowledgeRetrieveResult {
  chunkId: number;
  documentId: number;
  chunkIndex?: number;
  content: string;
  sourceTitle?: string;
  sourceType?: string;
  snippet?: string;
  confidence?: string;
  accessChecked?: boolean;
  score?: number;
}

function parseSourceTitle(sourceJson?: string) {
  if (!sourceJson) {
    return undefined;
  }
  try {
    const source = JSON.parse(sourceJson) as { documentName?: string; title?: string };
    return source.documentName || source.title;
  } catch {
    return undefined;
  }
}

export function ingestKnowledgeText(request: IngestKnowledgeTextRequest) {
  return unwrap<KnowledgeIngestResponse>(
    http.post('/v1/knowledge-documents/ingest-text', {
      knowledgeBaseId: request.knowledgeBaseId,
      documentName: request.title,
      text: request.content,
    }),
  );
}

export async function retrieveKnowledge(request: RetrieveKnowledgeRequest) {
  const results = await unwrap<BackendKnowledgeRetrieveResult[]>(
    http.post('/v1/knowledge-documents/retrieve', request),
  );

  return results.map((result) => ({
    chunkId: result.chunkId,
    documentId: result.documentId,
    chunkIndex: result.chunkIndex,
    content: result.chunkText || '',
    sourceTitle: result.sourceTitle || parseSourceTitle(result.sourceJson),
    sourceType: result.sourceType,
    snippet: result.snippet,
    confidence: result.confidence,
    accessChecked: result.accessChecked,
    score: result.score,
  }));
}
