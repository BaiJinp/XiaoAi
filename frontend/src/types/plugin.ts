export interface PluginTool {
  toolId?: number;
  toolCode: string;
  toolName: string;
  toolType: string;
  riskLevel: 'low' | 'medium' | 'high' | string;
  status: string;
  schemaJson?: string;
  bound?: boolean;
  boundAgentId?: number;
  bindingId?: number;
  pluginCode?: string;
  pluginVersion?: string;
  manifestHash?: string;
}

export interface PluginManifest {
  pluginId?: number;
  pluginCode: string;
  pluginName: string;
  pluginVersion: string;
  status: string;
  manifestHash?: string;
  boundAgentVersionId?: number;
  agentVersionToolIds?: number[];
  tools: PluginTool[];
}

export interface ImportPluginManifestRequest {
  manifestJson: string;
  agentId?: number;
  agentVersionId?: number;
}
