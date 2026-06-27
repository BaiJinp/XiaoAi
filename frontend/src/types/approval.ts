export type ApprovalStatus = 'pending' | 'approved' | 'rejected' | 'timeout';
export type ApprovalAction = 'approve' | 'reject';

export interface ApprovalRequest {
  id: number;
  tenantId?: number;
  taskId?: number;
  runId?: number;
  title?: string;
  reason?: string;
  riskLevel?: 'low' | 'medium' | 'high';
  executorType?: string;
  status: ApprovalStatus;
  approverUserId?: number;
  createTime?: string;
}

export interface HandleApprovalRequest {
  action: ApprovalAction;
  comment?: string;
}
