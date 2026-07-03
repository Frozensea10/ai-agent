import request from '@/utils/request'

export interface LlmProviderConfig {
  providerName: string
  apiKey: string
  modelName?: string
  enabled: number
}

export const listModelProviders = () => {
  return request.get<LlmProviderConfig[]>('/v1/settings/model-providers').then(res => res.data)
}

export const saveModelProvider = (data: LlmProviderConfig) => {
  return request.post<void>('/v1/settings/model-providers', data).then(res => res.data)
}
