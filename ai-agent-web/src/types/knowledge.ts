export interface KnowledgeBase {
  id: number
  kbName: string
  kbCode: string
  description: string
  embeddingProvider: string
  embeddingModel: string
  documentCount: number
  status: number
  createdAt: string
  updatedAt: string
}

export interface KnowledgeDocument {
  id: number
  kbId: number
  docName: string
  docType: string
  fileUrl: string
  fileSize: number
  chunkCount: number
  status: number
  createdAt: string
}

export interface RetrievalResult {
  chunkId: string
  content: string
  docId: string
  score: number
}

export interface RAGResponse {
  prompt: string
  context: string
  references: RetrievalResult[]
}
