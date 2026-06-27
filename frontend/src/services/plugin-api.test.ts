import { beforeEach, describe, expect, it, vi } from 'vitest';
import { http } from './http';
import { disablePluginManifest, enablePluginManifest, importPluginManifest, listPluginManifests } from './plugin-api';

vi.mock('./http', () => ({
  http: {
    get: vi.fn(),
    post: vi.fn(),
  },
  unwrap: vi.fn(async (request) => {
    const response = await request;
    return response.data.data;
  }),
}));

describe('plugin-api', () => {
  beforeEach(() => {
    vi.mocked(http.get).mockReset();
    vi.mocked(http.post).mockReset();
  });

  it('imports plugin manifest', async () => {
    vi.mocked(http.post).mockResolvedValue({
      data: { code: '0', message: 'success', data: { pluginCode: 'project-cli', tools: [] } },
    });

    const response = await importPluginManifest({ manifestJson: '{}', agentId: 300, agentVersionId: 13 });

    expect(http.post).toHaveBeenCalledWith('/v1/plugin-manifests/import', { manifestJson: '{}', agentId: 300, agentVersionId: 13 });
    expect(response.pluginCode).toBe('project-cli');
  });

  it('lists plugin manifests', async () => {
    vi.mocked(http.get).mockResolvedValue({
      data: { code: '0', message: 'success', data: [{ pluginCode: 'project-cli', tools: [] }] },
    });

    const response = await listPluginManifests();

    expect(http.get).toHaveBeenCalledWith('/v1/plugin-manifests');
    expect(response).toHaveLength(1);
  });

  it('enables and disables plugin manifests', async () => {
    vi.mocked(http.post)
      .mockResolvedValueOnce({ data: { code: '0', message: 'success', data: { pluginId: 1, status: 'active' } } })
      .mockResolvedValueOnce({ data: { code: '0', message: 'success', data: { pluginId: 1, status: 'inactive' } } });

    await enablePluginManifest(1);
    await disablePluginManifest(1);

    expect(http.post).toHaveBeenNthCalledWith(1, '/v1/plugin-manifests/1/enable', {});
    expect(http.post).toHaveBeenNthCalledWith(2, '/v1/plugin-manifests/1/disable', {});
  });
});
