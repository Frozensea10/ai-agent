import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const request = axios.create({
  baseURL: '/api',
  timeout: 120000
})

let isRefreshing = false
let refreshSubscribers: Array<(token: string) => void> = []

function subscribeTokenRefresh(callback: (token: string) => void) {
  refreshSubscribers.push(callback)
}

function onTokenRefreshed(newToken: string) {
  refreshSubscribers.forEach(callback => callback(newToken))
  refreshSubscribers = []
}

function extractMessage(error: unknown): string {
  if (typeof error === 'object' && error !== null) {
    const err = error as { response?: { data?: { message?: string } }; message?: string }
    if (err.response?.data?.message) {
      return err.response.data.message
    }
    if (err.message) {
      return err.message
    }
  }
  return '网络错误，请稍后重试'
}

request.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

request.interceptors.response.use(
  (response) => {
    const data = response.data
    if (data.code !== 200) {
      const message = data.message || '请求失败'
      ElMessage.error(message)
      return Promise.reject(new Error(message))
    }
    return data
  },
  async (error: AxiosError) => {
    const { response, config } = error
    const userStore = useUserStore()

    if (response?.status === 401 && config) {
      const originalRequest = config as InternalAxiosRequestConfig & { _retry?: boolean }

      if (!originalRequest._retry) {
        if (isRefreshing) {
          return new Promise((resolve) => {
            subscribeTokenRefresh((newToken: string) => {
              originalRequest.headers.Authorization = `Bearer ${newToken}`
              resolve(request(originalRequest))
            })
          })
        }

        originalRequest._retry = true
        isRefreshing = true

        try {
          const refreshToken = localStorage.getItem('refreshToken')
          if (!refreshToken) {
            throw new Error('登录已过期，请重新登录')
          }

          const res = await axios.post('/api/v1/auth/refresh', {}, {
            headers: { 'X-Refresh-Token': refreshToken }
          })

          if (res.data.code === 200) {
            const { accessToken, refreshToken: newRefreshToken } = res.data.data
            userStore.setToken(accessToken)
            localStorage.setItem('refreshToken', newRefreshToken)
            onTokenRefreshed(accessToken)
            originalRequest.headers.Authorization = `Bearer ${accessToken}`
            return request(originalRequest)
          }

          throw new Error(res.data?.message || '登录已过期，请重新登录')
        } catch (refreshError) {
          // 刷新失败时，需释放所有等待中的订阅者，避免它们永久挂起
          refreshSubscribers.forEach(callback => callback(''))
          refreshSubscribers = []
          userStore.logout()
          ElMessage.error(extractMessage(refreshError))
          window.location.href = '/login'
          return Promise.reject(refreshError)
        } finally {
          isRefreshing = false
        }
      }
    }

    const message = extractMessage(error)
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default request
