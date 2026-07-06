import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login, register, getProfile } from '@/api/user'
import type { LoginForm, RegisterForm, UserInfo } from '@/types/user'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const userInfo = ref<UserInfo | null>(null)
  let fetchUserInfoPromise: Promise<unknown> | null = null

  const isLoggedIn = computed(() => !!token.value)

  const setToken = (newToken: string) => {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  const clearToken = () => {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
  }

  const loginAction = async (form: LoginForm) => {
    const res = await login(form)
    setToken(res.data.accessToken)
    localStorage.setItem('refreshToken', res.data.refreshToken)
    await fetchUserInfo()
    return res
  }

  const registerAction = async (form: RegisterForm) => {
    return await register(form)
  }

  const fetchUserInfo = async () => {
    const res = await getProfile()
    userInfo.value = res.data
    localStorage.setItem('userId', String(res.data.id))
    return res
  }

  // 确保 userInfo 已加载：若 token 存在但 userInfo 为空则拉取，避免重复请求
  const ensureUserInfo = async () => {
    if (userInfo.value || !token.value) return
    if (fetchUserInfoPromise) return fetchUserInfoPromise
    fetchUserInfoPromise = fetchUserInfo().finally(() => {
      fetchUserInfoPromise = null
    })
    return fetchUserInfoPromise
  }

  const logout = () => {
    clearToken()
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('userId')
  }

  // store 初始化时，如果 token 存在但 userInfo 为空，尝试恢复用户信息
  if (token.value && !userInfo.value) {
    ensureUserInfo().catch(() => {
      // 拉取失败时静默处理，路由守卫会再次尝试并处理错误
    })
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    setToken,
    loginAction,
    registerAction,
    fetchUserInfo,
    ensureUserInfo,
    logout
  }
})
