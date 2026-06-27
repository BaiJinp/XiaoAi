export type ArtifactType =
  | 'markdown'
  | 'action_items'
  | 'risk_list'
  | 'task_creation_result'
  | 'text'
  | 'json'
  | 'meeting_action_items'
  | 'weekly_report'
  | 'risk_analysis'
  | 'knowledge_answer'
  | 'handoff'
  | 'file';

export interface TaskArtifact {
  id: number;
  taskId: number;
  artifactType: ArtifactType;
  title: string;
  content: string;
  metadata?: {
    assistantTaskType?: string;
    knowledgeConfidence?: string;
    lowConfidence?: boolean;
    sources?: Array<Record<string, unknown>>;
    sourceRefs?: Array<Record<string, unknown>>;
    [key: string]: unknown;
  };
  createTime?: string;
}
