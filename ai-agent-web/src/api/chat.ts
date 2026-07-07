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
  onStart: (messageId: string) => void,
  onChunk: (chunk: string) => void,
  onDone: () => void,
  onError: (error: string) => void,
  kbCode?: string,
  modelProvider?: string,
  modelName?: string
): () => void {
  const token = localStorage.getItem('token') || ''
  // 注意：SSE 基于 GET + query 传递参数。浏览器原生 fetch 流式读取对 POST 支持有限，
  // 且服务端 SSE 端点通常要求 GET。此处保留 GET 方案，但需注意 URL 长度限制
  // （浏览器通常约 2KB~8KB），超长 content 应考虑改用 EventSource + POST 端点或先发送再订阅。
  let url = `/api/v1/sessions/${sessionId}/stream?content=${encodeURIComponent(content)}&agentId=${agentId}`
  if (kbCode) {
    url += `&kbCode=${encodeURIComponent(kbCode)}`
  }
  if (modelProvider) {
    url += `&modelProvider=${encodeURIComponent(modelProvider)}`
  }
  if (modelName) {
    url += `&modelName=${encodeURIComponent(modelName)}`
  }

  const controller = new AbortController()
  let completed = false

  fetch(url, {
    headers: {
      Authorization: `Bearer ${token}`
    },
    signal: controller.signal
  })
    .then(async (response) => {
      if (!response.ok || !response.body) {
        let message = `请求失败: ${response.status}`
        try {
          const body = await response.json()
          if (body?.message) message = body.message
        } catch {
          // ignore
        }
        if (!completed) {
          completed = true
          onError(message)
        }
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
            if (parsed.type === 'start' && parsed.messageId) {
              onStart(parsed.messageId)
            } else if (parsed.type === 'content' && parsed.delta) {
              onChunk(parsed.delta)
            } else if (parsed.type === 'tool_call') {
              onChunk(`\n[调用工具 ${parsed.toolName}]\n`)
            } else if (parsed.type === 'tool_result') {
              onChunk(`[工具 ${parsed.toolName} 执行结果]\n${parsed.result}\n`)
            } else if (parsed.type === 'error') {
              if (!completed) {
                completed = true
                onError(parsed.delta || '流式响应错误')
              }
            } else if (parsed.type === 'end') {
              if (!completed) {
                completed = true
                onDone()
              }
            }
          } catch {
            onChunk(data)
          }
        }
      }

      if (!completed) {
        completed = true
        onDone()
      }
    })
    .catch((err) => {
      if (err.name !== 'AbortError' && !completed) {
        completed = true
        onError(err.message || 'SSE 连接异常')
      }
    })

  return () => controller.abort()
}
