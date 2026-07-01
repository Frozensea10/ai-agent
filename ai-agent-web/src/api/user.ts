import request from '@/utils/request'
import type { LoginForm, RegisterForm, TokenResponse, UserInfo } from '@/types/user'

export const login = (data: LoginForm) => {
  return request.post<TokenResponse>('/v1/auth/login', data)
}

export const register = (data: RegisterForm) => {
  return request.post<UserInfo>('/v1/auth/register', data)
}

export const getProfile = () => {
  return request.get<UserInfo>('/v1/user/profile')
}
