import { unwrap, http } from './http';
import type { ImportPluginManifestRequest, PluginManifest } from '../types/plugin';

export function importPluginManifest(request: ImportPluginManifestRequest) {
  return unwrap<PluginManifest>(http.post('/v1/plugin-manifests/import', request));
}

export function listPluginManifests() {
  return unwrap<PluginManifest[]>(http.get('/v1/plugin-manifests'));
}

export function enablePluginManifest(pluginId: number) {
  return unwrap<PluginManifest>(http.post(`/v1/plugin-manifests/${pluginId}/enable`, {}));
}

export function disablePluginManifest(pluginId: number) {
  return unwrap<PluginManifest>(http.post(`/v1/plugin-manifests/${pluginId}/disable`, {}));
}
