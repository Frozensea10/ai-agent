import request from '@/utils/request'
import type { FileRecord } from '@/types/file'

export const listFiles = () => {
  return request.get<FileRecord[]>('/v1/files')
}

export const uploadFile = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<string>('/v1/files/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

export const deleteFile = (objectName: string) => {
  return request.delete(`/v1/files/${encodeURIComponent(objectName)}`)
}

export const getPreviewUrl = (objectName: string) => {
  return request.get<string>(`/v1/files/${encodeURIComponent(objectName)}/url`)
}

export const downloadFile = (objectName: string) => {
  const token = localStorage.getItem('token') || ''
  return fetch(`/api/v1/files/${encodeURIComponent(objectName)}?disposition=attachment`, {
    headers: {
      Authorization: `Bearer ${token}`
    }
  })
}

export const previewFile = (objectName: string) => {
  const token = localStorage.getItem('token') || ''
  return fetch(`/api/v1/files/${encodeURIComponent(objectName)}?disposition=inline`, {
    headers: {
      Authorization: `Bearer ${token}`
    }
  })
}
