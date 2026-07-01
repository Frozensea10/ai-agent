import request from '@/utils/request'
import type { AgentConfig, AgentForm } from '@/types/agent'

export const listAgents = () => {
  return request.get<AgentConfig[]>('/v1/agents').then(res => res.data)
}

export const getAgent = (id: number) => {
  return request.get<AgentConfig>(`/v1/agents/${id}`).then(res => res.data)
}

export const createAgent = (data: AgentForm) => {
  return request.post<AgentConfig>('/v1/agents', data).then(res => res.data)
}

export const updateAgent = (id: number, data: Partial<AgentForm> & { status?: number }) => {
  return request.put<AgentConfig>(`/v1/agents/${id}`, data).then(res => res.data)
}

export const deleteAgent = (id: number) => {
  return request.delete(`/v1/agents/${id}`).then(res => res.data)
}
