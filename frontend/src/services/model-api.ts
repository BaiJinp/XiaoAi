import { http, unwrap } from './http';
import type { CreateModelConfigRequest, CreateModelProviderRequest, ModelConfig, ModelProvider } from '../types/model';

export function createModelProvider(request: CreateModelProviderRequest) {
  return unwrap<ModelProvider>(http.post('/v1/model-providers', request));
}

export function createModelConfig(request: CreateModelConfigRequest) {
  return unwrap<ModelConfig>(http.post('/v1/model-configs', request));
}
