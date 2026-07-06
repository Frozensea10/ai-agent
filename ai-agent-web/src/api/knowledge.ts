import request from '@/utils/request'
import type { KnowledgeBase, KnowledgeDocument } from '@/types/knowledge'

export const getKnowledgeBases = () => {
  return request.get<KnowledgeBase[]>('/v1/knowledge-bases').then(res => res.data)
}

export const createKnowledgeBase = (data: Partial<KnowledgeBase>) => {
  return request.post<KnowledgeBase>('/v1/knowledge-bases', data).then(res => res.data)
}

export const getKnowledgeBaseById = (id: number) => {
  return request.get<KnowledgeBase>(`/v1/knowledge-bases/${id}`).then(res => res.data)
}

export const updateKnowledgeBase = (id: number, data: Partial<KnowledgeBase>) => {
  return request.put<KnowledgeBase>(`/v1/knowledge-bases/${id}`, data).then(res => res.data)
}

export const deleteKnowledgeBase = (id: number) => {
  return request.delete(`/v1/knowledge-bases/${id}`).then(res => res.data)
}

export const getDocuments = (kbId: number) => {
  return request.get<KnowledgeDocument[]>(`/v1/knowledge-bases/${kbId}/documents`).then(res => res.data)
}

export const uploadDocument = (kbId: number, file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<KnowledgeDocument>(`/v1/knowledge-bases/${kbId}/documents`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    timeout: 120000
  }).then(res => res.data)
}

export const retrieve = (kbCode: string, query: string, topK?: number) => {
  return request.post(`/v1/knowledge-bases/${kbCode}/retrieve`, { query, topK }).then(res => res.data)
}

export const ragQuery = (kbCode: string, query: string, systemPrompt?: string, topK?: number) => {
  return request.post(`/v1/knowledge-bases/${kbCode}/rag`, { query, systemPrompt, topK }).then(res => res.data)
}
