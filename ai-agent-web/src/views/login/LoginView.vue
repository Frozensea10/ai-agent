
<template>
  <div class="login-container">
    <!-- Floating background image -->
    <div class="login-bg-image"></div>

    <!-- Floating illustrations -->
    <img src="/assets/cute-cloud.jpg" alt="" class="deco-img animate-float" style="width: 140px; height: 140px; top: 8%; left: 6%; animation-delay: 0.5s;">
    <img src="/assets/happy-star.jpg" alt="" class="deco-img animate-float" style="width: 120px; height: 120px; top: 12%; right: 8%; animation-delay: 1.5s;">
    <img src="/assets/friendly-bot.jpg" alt="" class="deco-img animate-float" style="width: 100px; height: 100px; bottom: 18%; left: 10%; animation-delay: 3s;">
    <img src="/assets/cute-moon.jpg" alt="" class="deco-img animate-float" style="width: 70px; height: 70px; top: 28%; left: 4%; animation-delay: 0.8s;">
    <img src="/assets/cute-rocket.jpg" alt="" class="deco-img animate-float" style="width: 80px; height: 80px; top: 6%; right: 22%; animation-delay: 2.2s;">
    <img src="/assets/cute-sun.jpg" alt="" class="deco-img animate-float" style="width: 65px; height: 65px; bottom: 12%; right: 6%; animation-delay: 1.2s;">
    <img src="/assets/cute-planet.jpg" alt="" class="deco-img animate-float" style="width: 75px; height: 75px; bottom: 30%; right: 18%; animation-delay: 2.8s;">

    <!-- Login Card -->
    <div class="login-card-wrapper animate-slide-up-bounce">
      <div class="hand-card login-card">
        <!-- Logo / Header -->
        <div class="login-header">
          <div class="logo-bubbles">
            <div class="icon-bubble" style="background: var(--golden); color: var(--text-dark);">
              <el-icon><Star /></el-icon>
            </div>
            <div class="icon-bubble" style="background: var(--coral); color: white;">
              <el-icon><ChatDotRound /></el-icon>
            </div>
            <div class="icon-bubble" style="background: var(--teal); color: white;">
              <el-icon><Lightning /></el-icon>
            </div>
          </div>
          <h1 class="login-title">AI Agent 智能助手</h1>
          <p class="login-subtitle">登录您的账户，开始奇妙之旅</p>
        </div>

        <!-- Decorative divider -->
        <div class="login-divider">
          <div class="divider-line"></div>
          <div class="divider-dot"></div>
          <div class="divider-line" style="background: var(--teal);"></div>
        </div>

        <!-- Login Form -->
        <el-tabs v-model="activeTab" class="login-tabs">
          <el-tab-pane label="登录" name="login">
            <el-form :model="loginForm" :rules="loginRules" ref="loginFormRef">
              <el-form-item prop="username">
                <el-input
                  v-model="loginForm.username"
                  placeholder="用户名"
                  size="large"
                  :prefix-icon="User"
                />
              </el-form-item>
              <el-form-item prop="password">
                <el-input
                  v-model="loginForm.password"
                  type="password"
                  placeholder="密码"
                  size="large"
                  :prefix-icon="Lock"
                  show-password
                  @keyup.enter="handleLogin"
                />
              </el-form-item>
              <el-form-item>
                <button
                  type="button"
                  class="btn-pill btn-primary-pill w-full justify-center"
                  :disabled="loading"
                  @click="handleLogin"
                >
                  <el-icon v-if="loading" class="is-loading"><Loading /></el-icon>
                  <el-icon v-else><Right /></el-icon>
                  登录
                </button>
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="注册" name="register">
            <el-form :model="registerForm" :rules="registerRules" ref="registerFormRef">
              <el-form-item prop="username">
                <el-input
                  v-model="registerForm.username"
                  placeholder="用户名"
                  size="large"
                  :prefix-icon="User"
                />
              </el-form-item>
              <el-form-item prop="password">
                <el-input
                  v-model="registerForm.password"
                  type="password"
                  placeholder="密码"
                  size="large"
                  :prefix-icon="Lock"
                  show-password
                />
              </el-form-item>
              <el-form-item prop="email">
                <el-input
                  v-model="registerForm.email"
                  placeholder="邮箱（可选）"
                  size="large"
                  :prefix-icon="Message"
                />
              </el-form-item>
              <el-form-item>
                <button
                  type="button"
                  class="btn-pill btn-primary-pill w-full justify-center"
                  :disabled="loading"
                  @click="handleRegister"
                >
                  <el-icon v-if="loading" class="is-loading"><Loading /></el-icon>
                  <el-icon v-else><Plus /></el-icon>
                  注册
                </button>
              </el-form-item>
            </el-form>
          </el-tab-pane>
        </el-tabs>

        <!-- Bottom divider -->
        <div class="login-divider">
          <div class="divider-line" style="background: var(--lavender);"></div>
          <div class="divider-square"></div>
          <div class="divider-line" style="background: var(--golden);"></div>
        </div>

        <div class="login-footer">
          <p>还没有账户？<span class="login-link" @click="activeTab = 'register'">立即注册</span></p>
        </div>
      </div>

      <div class="login-deco-dots">
        <div class="deco-dot" style="background: var(--coral); opacity: 0.4; width: 12px; height: 12px;"></div>
        <div class="deco-dot" style="background: var(--teal); opacity: 0.4; width: 12px; height: 12px;"></div>
        <div class="deco-dot" style="background: var(--golden); opacity: 0.4; width: 12px; height: 12px;"></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import {
  User,
  Lock,
  Message,
  Right,
  Plus,
  Loading,
  Star,
  ChatDotRound,
  Lightning
} from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()

const activeTab = ref('login')
const loading = ref(false)
const loginFormRef = ref<FormInstance>()
const registerFormRef = ref<FormInstance>()

const loginForm = reactive({
  username: '',
  password: ''
})

const registerForm = reactive({
  username: '',
  password: '',
  email: ''
})

const loginRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const registerRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  if (!loginFormRef.value) return
  const valid = await loginFormRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.loginAction(loginForm)
    ElMessage.success('登录成功')
    router.push('/')
  } catch (error: unknown) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  if (!registerFormRef.value) return
  const valid = await registerFormRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.registerAction(registerForm)
    ElMessage.success('注册成功，请登录')
    activeTab.value = 'login'
  } catch (error: unknown) {
    console.error(error)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  padding: 20px;
  overflow-x: hidden;
}

.login-bg-image {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-image: url('/assets/floating-shapes.jpg');
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  opacity: 0.18;
  pointer-events: none;
  z-index: 0;
}

.deco-img {
  position: absolute;
  pointer-events: none;
  z-index: 1;
  border-radius: 50%;
  object-fit: cover;
  border: 3px solid var(--text-dark);
  box-shadow: 3px 3px 0px rgba(45, 52, 54, 0.15);
}

.login-card-wrapper {
  width: 90%;
  min-width: 320px;
  max-width: 400px;
  margin: 0 auto;
  position: relative;
  z-index: 10;
}

.login-card {
  padding: 32px;
  border-radius: 28px;
  box-shadow: 8px 8px 0px rgba(45, 52, 54, 0.15), 0 4px 20px rgba(0,0,0,0.05);
}

.login-header {
  text-align: center;
  margin-bottom: 24px;
}

.logo-bubbles {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: 16px;
}

.logo-bubbles .icon-bubble {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border-width: 3px;
}

.logo-bubbles .icon-bubble :deep(.el-icon) {
  font-size: 20px;
}

.login-title {
  font-size: 28px;
  font-weight: 900;
  color: var(--text-dark);
  letter-spacing: -0.5px;
  line-height: 1.2;
  margin-bottom: 8px;
}

.login-subtitle {
  color: var(--text-medium);
  font-size: 15px;
  font-weight: 700;
  line-height: 1.5;
}

.login-divider {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 16px 0;
}

.divider-line {
  flex: 1;
  height: 4px;
  border-radius: 999px;
  background: var(--coral);
  opacity: 0.3;
}

.divider-dot {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--golden);
  flex-shrink: 0;
}

.divider-square {
  width: 14px;
  height: 14px;
  border-radius: 4px;
  background: var(--coral-light);
  transform: rotate(15deg);
  flex-shrink: 0;
}

.login-tabs :deep(.el-tabs__nav-wrap) {
  display: flex;
  justify-content: center;
  margin-bottom: 8px;
}

.login-tabs :deep(.el-tabs__item) {
  font-size: 16px;
  font-weight: 800;
  color: var(--text-medium);
  padding: 0 20px;
}

.login-tabs :deep(.el-tabs__item.is-active) {
  color: var(--coral);
}

.login-tabs :deep(.el-tabs__active-bar) {
  background: var(--coral);
  height: 3px;
  border-radius: 999px;
}

.w-full {
  width: 100%;
}

.justify-center {
  justify-content: center;
}

.login-footer {
  text-align: center;
  margin-top: 8px;
  color: var(--text-medium);
  font-size: 14px;
  font-weight: 700;
}

.login-link {
  color: var(--teal-dark);
  font-weight: 800;
  cursor: pointer;
  margin-left: 4px;
  transition: color 0.2s ease;
}

.login-link:hover {
  color: var(--coral);
}

.login-deco-dots {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-top: 16px;
}

@media (max-width: 767px) {
  .deco-img {
    display: none;
  }
  .login-card-wrapper {
    width: 100%;
    min-width: auto;
    max-width: 380px;
  }
  .login-card {
    padding: 24px 22px;
  }
  .login-title {
    font-size: 24px;
  }
  .login-subtitle {
    font-size: 14px;
  }
}

@media (min-width: 768px) and (max-width: 1024px) {
  .deco-img {
    transform: scale(0.85);
  }
}
</style>
