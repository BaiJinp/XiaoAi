import axios from 'axios';
import type { ApiResponse } from '../types/api';

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
  headers: {
    'X-Tenant-Id': import.meta.env.VITE_DEV_TENANT_ID || '100',
    'X-User-Id': import.meta.env.VITE_DEV_USER_ID || '1000',
  },
});

export async function unwrap<T>(request: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  const response = await request;
  if (String(response.data.code) !== '0') {
    throw new Error(response.data.message || '请求失败');
  }
  return response.data.data;
}
