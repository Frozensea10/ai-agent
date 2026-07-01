import request from '@/utils/request'
import type { KnowledgeBase, KnowledgeDocument } from '@/types/knowledge'

export const getKnowledgeBases = () => {
  return request.get<KnowledgeBase[]>('/v1/knowledge-bases')
}

export const createKnowledgeBase = (data: Partial<KnowledgeBase>) => {
  return request.post<KnowledgeBase>('/v1/knowledge-bases', data)
}

export const deleteKnowledgeBase = (id: number) => {
  return request.delete(`/v1/knowledge-bases/${id}`)
}

export const getDocuments = (kbId: number) => {
  return request.get<KnowledgeDocument[]>(`/v1/knowledge-bases/${kbId}/documents`)
}

export const uploadDocument = (kbId: number, file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<KnowledgeDocument>(`/v1/knowledge-bases/${kbId}/documents`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

export const retrieve = (kbCode: string, query: string, topK?: number) => {
  return request.post(`/v1/knowledge-bases/${kbCode}/retrieve`, { query, topK })
}

export const ragQuery = (kbCode: string, query: string, systemPrompt?: string, topK?: number) => {
  return request.post(`/v1/knowledge-bases/${kbCode}/rag`, { query, systemPrompt, topK })
}
