export interface AgentConfig {
  id: number
  agentName: string
  agentCode: string
  description: string
  modelProvider: string
  modelName: string
  systemPrompt: string
  temperature: number
  maxTokens: number
  memoryType: string
  memoryMaxMessages: number
  status: number
  createdAt: string
  updatedAt: string
}

export interface AgentForm {
  agentName: string
  agentCode: string
  description: string
  modelProvider: string
  modelName: string
  systemPrompt: string
  temperature: number
  maxTokens: number
  memoryType: string
  memoryMaxMessages: number
}
