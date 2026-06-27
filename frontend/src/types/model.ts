export interface ModelProvider {
  id: number;
  providerCode: string;
  providerName: string;
  providerType: string;
  baseUrl: string;
  status: string;
}

export interface CreateModelProviderRequest {
  providerCode: string;
  providerName: string;
  providerType: string;
  baseUrl: string;
  apiKey: string;
}

export interface ModelConfig {
  id: number;
  providerId: number;
  modelCode: string;
  modelName: string;
  modelType: string;
  contextWindow?: number;
  configJson?: string;
  status: string;
}

export interface CreateModelConfigRequest {
  providerId: number;
  modelCode: string;
  modelName: string;
  modelType: string;
  contextWindow?: number;
  configJson?: string;
}
