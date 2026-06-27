import { useMutation } from '@tanstack/react-query';
import { handleApproval } from '../../../services/approval-api';
import type { ApprovalAction } from '../../../types/approval';

interface HandleApprovalVariables {
  requestId: number;
  action: ApprovalAction;
  comment?: string;
}

export function useHandleApproval() {
  return useMutation({
    mutationFn: ({ requestId, action, comment }: HandleApprovalVariables) =>
      handleApproval(requestId, { action, comment }),
  });
}
