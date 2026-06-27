import { useMutation } from '@tanstack/react-query';
import { createTask } from '../../../services/task-api';

export function useCreateTask() {
  return useMutation({
    mutationFn: (request: Parameters<typeof createTask>[0]) => createTask(request),
  });
}
