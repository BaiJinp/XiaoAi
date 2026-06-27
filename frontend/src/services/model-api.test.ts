import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createModelConfig, createModelProvider } from './model-api';
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

describe('model-api', () => {
  beforeEach(() => {
    vi.mocked(http.post).mockReset();
  });

  it('maps model provider URL and API key to backend command fields', async () => {
    vi.mocked(http.post).mockResolvedValue({
      data: {
        code: 0,
        message: '成功',
        data: {
          id: 10,
          providerCode: 'openai-prod',
          providerName: 'OpenAI Production',
          providerType: 'openai_compatible',
          baseUrl: 'https://api.openai.com/v1',
          status: 'active',
        },
      },
    });

    const response = await createModelProvider({
      providerCode: 'openai-prod',
      providerName: 'OpenAI Production',
      providerType: 'openai_compatible',
      baseUrl: 'https://api.openai.com/v1',
      apiKey: 'sk-live-secret',
    });

    expect(http.post).toHaveBeenCalledWith('/v1/model-providers', {
      providerCode: 'openai-prod',
      providerName: 'OpenAI Production',
      providerType: 'openai_compatible',
      baseUrl: 'https://api.openai.com/v1',
      apiKey: 'sk-live-secret',
    });
    expect(response).toMatchObject({ id: 10, providerCode: 'openai-prod' });
  });

  it('maps model config to backend command fields', async () => {
    vi.mocked(http.post).mockResolvedValue({
      data: {
        code: 0,
        message: '成功',
        data: {
          id: 11,
          providerId: 10,
          modelCode: 'gpt-4o-mini',
          modelName: 'GPT-4o Mini',
          modelType: 'chat',
          contextWindow: 128000,
          configJson: '{"temperature":0.2}',
          status: 'active',
        },
      },
    });

    const response = await createModelConfig({
      providerId: 10,
      modelCode: 'gpt-4o-mini',
      modelName: 'GPT-4o Mini',
      modelType: 'chat',
      contextWindow: 128000,
      configJson: '{"temperature":0.2}',
    });

    expect(http.post).toHaveBeenCalledWith('/v1/model-configs', {
      providerId: 10,
      modelCode: 'gpt-4o-mini',
      modelName: 'GPT-4o Mini',
      modelType: 'chat',
      contextWindow: 128000,
      configJson: '{"temperature":0.2}',
    });
    expect(response).toMatchObject({ id: 11, modelCode: 'gpt-4o-mini' });
  });
});
