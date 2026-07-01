import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login, register, getProfile } from '@/api/user'
import type { LoginForm, RegisterForm, UserInfo } from '@/types/user'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const userInfo = ref<UserInfo | null>(null)

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
    return res
  }

  const logout = () => {
    clearToken()
    localStorage.removeItem('refreshToken')
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    setToken,
    loginAction,
    registerAction,
    fetchUserInfo,
    logout
  }
})
