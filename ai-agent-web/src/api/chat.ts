import request from '@/utils/request'
import type { ChatSession, ChatMessage, CreateSessionForm, SendMessageForm } from '@/types/chat'

export const listSessions = () => {
  return request.get<ChatSession[]>('/v1/sessions').then(res => res.data)
}

export const createSession = (data: CreateSessionForm) => {
  return request.post<ChatSession>('/v1/sessions', data).then(res => res.data)
}

export const deleteSession = (sessionId: string) => {
  return request.delete(`/v1/sessions/${sessionId}`).then(res => res.data)
}

export const getMessages = (sessionId: string) => {
  return request.get<ChatMessage[]>(`/v1/sessions/${sessionId}/messages`).then(res => res.data)
}

export const sendMessage = (sessionId: string, data: SendMessageForm) => {
  return request.post<ChatMessage>(`/v1/sessions/${sessionId}/messages`, data).then(res => res.data)
}

export function streamChat(
  sessionId: string,
  content: string,
  agentId: number,
  onChunk: (chunk: string) => void,
  onDone: () => void,
  onError: (error: string) => void
): () => void {
  const token = localStorage.getItem('token') || ''
  const url = `/api/v1/sessions/${sessionId}/stream?content=${encodeURIComponent(content)}&agentId=${agentId}`

  const controller = new AbortController()

  fetch(url, {
    headers: {
      Authorization: `Bearer ${token}`
    },
    signal: controller.signal
  })
    .then(async (response) => {
      if (!response.ok || !response.body) {
        onError(`请求失败: ${response.status}`)
        return
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          const trimmed = line.trim()
          if (!trimmed.startsWith('data:')) continue
          const data = trimmed.slice(5).trim()
          if (!data) continue

          try {
            const parsed = JSON.parse(data)
            if (parsed.type === 'content' && parsed.delta) {
              onChunk(parsed.delta)
            } else if (parsed.type === 'error') {
              onError(parsed.delta || '流式响应错误')
            } else if (parsed.type === 'end') {
              onDone()
            }
          } catch {
            onChunk(data)
          }
        }
      }

      onDone()
    })
    .catch((err) => {
      if (err.name !== 'AbortError') {
        onError(err.message || 'SSE 连接异常')
      }
    })

  return () => controller.abort()
}
