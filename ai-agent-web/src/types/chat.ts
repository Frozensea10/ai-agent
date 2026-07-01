export interface ChatSession {
  id: number
  sessionId: string
  agentId: number
  kbId?: number
  sessionTitle: string
  messageCount: number
  status: number
  createdAt: string
  updatedAt: string
}

export interface ChatMessage {
  id: number
  messageId: string
  role: 'user' | 'assistant'
  content: string
  contentType?: string
  tokensUsed?: number
  modelName?: string
  createdAt: string
}

export interface CreateSessionForm {
  agentId: number
  kbId?: number
  title?: string
}

export interface SendMessageForm {
  agentId: number
  kbId?: number
  content: string
  sessionId?: string
}
