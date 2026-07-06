import request from '@/utils/request'

export interface ToolConfigSchemaProperty {
  type: string
  description?: string
}

export interface ToolConfigSchema {
  properties: Record<string, ToolConfigSchemaProperty>
  required?: string[]
}

export interface ToolInfo {
  toolCode: string
  toolName: string
  description: string
  toolType: string
  source: string
  configSchema?: ToolConfigSchema
}

export interface McpServer {
  id?: number
  serverName: string
  serverType: string
  transportConfig: string
  status: string
}

export interface ToolExecuteResult {
  success: boolean
  toolCode: string
  data: unknown
  errorMessage: string
  executeTimeMs: number
}

export function listTools() {
  return request.get<ToolInfo[]>('/v1/mcp/tools').then(res => res.data)
}

export function listMcpServers() {
  return request.get<McpServer[]>('/v1/mcp/servers').then(res => res.data)
}

export function createMcpServer(data: McpServer) {
  return request.post<McpServer>('/v1/mcp/servers', data).then(res => res.data)
}

export function updateMcpServer(id: number, data: McpServer) {
  return request.put<McpServer>(`/v1/mcp/servers/${id}`, data).then(res => res.data)
}

export function deleteMcpServer(id: number) {
  return request.delete(`/v1/mcp/servers/${id}`).then(res => res.data)
}

export function updateMcpServerStatus(id: number, status: string) {
  return request.put(`/v1/mcp/servers/${id}/status`, { status }).then(res => res.data)
}

export function getServerConnectionStatus() {
  return request.get<Record<string, string>>('/v1/mcp/servers/status').then(res => res.data)
}

export function executeTool(toolCode: string, parameters: Record<string, unknown>) {
  return request.post<ToolExecuteResult>(`/v1/mcp/tools/${toolCode}/execute`, parameters).then(res => res.data)
}
