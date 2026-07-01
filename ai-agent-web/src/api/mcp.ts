import request from '@/utils/request'
import type { Result } from '@/types'

export interface ToolInfo {
  toolCode: string
  toolName: string
  description: string
  toolType: string
  source: string
  configSchema?: any
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
  data: any
  errorMessage: string
  executeTimeMs: number
}

export function listTools(): Promise<Result<ToolInfo[]>> {
  return request.get('/api/v1/mcp/tools') as Promise<Result<ToolInfo[]>>
}

export function listMcpServers(): Promise<Result<McpServer[]>> {
  return request.get('/api/v1/mcp/servers') as Promise<Result<McpServer[]>>
}

export function createMcpServer(data: McpServer): Promise<Result<McpServer>> {
  return request.post('/api/v1/mcp/servers', data) as Promise<Result<McpServer>>
}

export function updateMcpServer(id: number, data: McpServer): Promise<Result<void>> {
  return request.put(`/api/v1/mcp/servers/${id}`, data) as Promise<Result<void>>
}

export function deleteMcpServer(id: number): Promise<Result<void>> {
  return request.delete(`/api/v1/mcp/servers/${id}`) as Promise<Result<void>>
}

export function updateMcpServerStatus(id: number, status: string): Promise<Result<void>> {
  return request.put(`/api/v1/mcp/servers/${id}/status`, { status }) as Promise<Result<void>>
}

export function getServerConnectionStatus(): Promise<Result<Record<string, string>>> {
  return request.get('/api/v1/mcp/servers/status') as Promise<Result<Record<string, string>>>
}

export function executeTool(toolCode: string, parameters: Record<string, any>): Promise<Result<ToolExecuteResult>> {
  return request.post(`/api/v1/mcp/tools/${toolCode}/execute`, parameters) as Promise<Result<ToolExecuteResult>>
}
