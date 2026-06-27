import { http, unwrap } from './http';
import type { ApprovalRequest, HandleApprovalRequest } from '../types/approval';

export function getApprovalRequest(requestId: number) {
  return unwrap<ApprovalRequest>(http.get(`/v1/approval-requests/${requestId}`));
}

export function handleApproval(requestId: number, request: HandleApprovalRequest) {
  return unwrap<void>(
    http.post(`/v1/approval-requests/${requestId}/handle`, {
      operatorUserId: Number(import.meta.env.VITE_DEV_USER_ID || '1000'),
      action: request.action,
      commentText: request.comment,
    }),
  );
}
