export interface LoginForm {
  username: string
  password: string
}

export interface RegisterForm {
  username: string
  password: string
  email?: string
  phone?: string
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  tokenType: string
}

export interface UserInfo {
  id: number
  username: string
  email: string
  phone: string
  avatarUrl: string
  status: number
  createdAt: string
}
