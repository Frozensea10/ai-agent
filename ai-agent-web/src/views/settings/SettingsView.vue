<template>
  <div class="settings-view">
    <div class="page-header">
      <div class="header-text">
        <h1 class="page-title">系统设置</h1>
        <p class="page-subtitle">自定义你的 AI Agent 体验</p>
      </div>
    </div>

    <div class="settings-layout">
      <div class="settings-sidebar">
        <div
          v-for="tab in settingTabs"
          :key="tab.key"
          class="settings-tab"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key"
        >
          <el-icon><component :is="tab.icon" /></el-icon>
          <span>{{ tab.label }}</span>
        </div>
      </div>

      <div class="settings-content hand-card">
        <div v-if="activeTab === 'profile'" class="settings-section">
          <div class="section-title">
            <span class="title-dot" style="background: var(--coral)"></span>
            <h3>个人资料</h3>
          </div>

          <div class="profile-header">
            <div class="profile-avatar">{{ profileForm.username.charAt(0).toUpperCase() }}</div>
            <div class="profile-info">
              <div class="profile-name">{{ profileForm.username }}</div>
              <div class="profile-role">管理员</div>
            </div>
          </div>

          <el-form :model="profileForm" label-width="120px" class="settings-form">
            <el-form-item label="用户名">
              <el-input v-model="profileForm.username" />
            </el-form-item>
            <el-form-item label="昵称">
              <el-input v-model="profileForm.nickname" />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="profileForm.email" />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="profileForm.phone" />
            </el-form-item>
            <el-form-item>
              <button class="btn-pill btn-primary-pill" type="button" @click="saveProfile">
                保存资料
              </button>
            </el-form-item>
          </el-form>
        </div>

        <div v-if="activeTab === 'account'" class="settings-section">
          <div class="section-title">
            <span class="title-dot" style="background: var(--teal)"></span>
            <h3>账号安全</h3>
          </div>

          <el-form :model="passwordForm" label-width="120px" class="settings-form">
            <el-form-item label="当前密码">
              <el-input v-model="passwordForm.oldPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="passwordForm.newPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="确认新密码">
              <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
            </el-form-item>
            <el-form-item>
              <button class="btn-pill btn-primary-pill" type="button" @click="savePassword">
                修改密码
              </button>
            </el-form-item>
          </el-form>
        </div>

        <div v-if="activeTab === 'notifications'" class="settings-section">
          <div class="section-title">
            <span class="title-dot" style="background: var(--lavender)"></span>
            <h3>通知设置</h3>
          </div>

          <div class="settings-list">
            <div class="setting-item">
              <div class="setting-text">
                <div class="setting-label">系统通知</div>
                <div class="setting-desc">接收系统更新、维护等重要通知</div>
              </div>
              <el-switch v-model="notificationForm.systemNotify" active-color="#FF6B6B" />
            </div>
            <div class="setting-item">
              <div class="setting-text">
                <div class="setting-label">对话消息</div>
                <div class="setting-desc">AI 回复完成时发送提醒</div>
              </div>
              <el-switch v-model="notificationForm.messageNotify" active-color="#FF6B6B" />
            </div>
            <div class="setting-item">
              <div class="setting-text">
                <div class="setting-label">邮件订阅</div>
                <div class="setting-desc">接收每周使用报告和技巧分享</div>
              </div>
              <el-switch v-model="notificationForm.emailSubscribe" active-color="#FF6B6B" />
            </div>
          </div>
        </div>

        <div v-if="activeTab === 'preferences'" class="settings-section">
          <div class="section-title">
            <span class="title-dot" style="background: var(--golden)"></span>
            <h3>系统偏好</h3>
          </div>

          <el-form :model="preferenceForm" label-width="120px" class="settings-form">
            <el-form-item label="默认模型">
              <el-select v-model="preferenceForm.defaultModel" style="width: 100%">
                <el-option label="GPT-4o" value="gpt-4o" />
                <el-option label="GPT-4o-mini" value="gpt-4o-mini" />
                <el-option label="Claude 3.5 Sonnet" value="claude-3.5-sonnet" />
              </el-select>
            </el-form-item>
            <el-form-item label="主题">
              <el-select v-model="preferenceForm.theme" style="width: 100%">
                <el-option label="可爱手绘" value="cute" />
                <el-option label="明亮" value="light" />
                <el-option label="暗色" value="dark" />
              </el-select>
            </el-form-item>
            <el-form-item label="语言">
              <el-select v-model="preferenceForm.language" style="width: 100%">
                <el-option label="简体中文" value="zh-CN" />
                <el-option label="English" value="en" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <button class="btn-pill btn-primary-pill" type="button" @click="savePreferences">
                保存偏好
              </button>
            </el-form-item>
          </el-form>
        </div>

        <div v-if="activeTab === 'models'" class="settings-section">
          <div class="section-title">
            <span class="title-dot" style="background: var(--mint)"></span>
            <h3>模型配置</h3>
          </div>

          <el-alert
            type="info"
            :closable="false"
            show-icon
            style="margin-bottom: 20px;"
          >
            配置各模型提供商的 API Key 后，即可在对话中使用对应模型。
          </el-alert>

          <div class="provider-cards">
            <div
              v-for="provider in modelProviders"
              :key="provider.providerName"
              class="provider-card"
            >
              <div class="provider-header">
                <div class="provider-name">{{ providerLabel(provider.providerName) }}</div>
                <el-switch
                  v-model="provider.enabled"
                  :active-value="1"
                  :inactive-value="0"
                  active-color="#FF6B6B"
                />
              </div>

              <el-form label-width="90px" class="settings-form">
                <el-form-item label="API Key">
                  <el-input
                    v-model="provider.apiKey"
                    type="password"
                    show-password
                    placeholder="sk-..."
                  />
                </el-form-item>
                <el-form-item label="模型名称">
                  <el-input
                    v-model="provider.modelName"
                    placeholder="可选，留空使用默认模型"
                  />
                </el-form-item>
                <el-form-item>
                  <button class="btn-pill btn-primary-pill" type="button" @click="saveProvider(provider)">
                    保存
                  </button>
                </el-form-item>
              </el-form>
            </div>
          </div>
        </div>

        <div v-if="activeTab === 'about'" class="settings-section">
          <div class="section-title">
            <span class="title-dot" style="background: var(--mint)"></span>
            <h3>关于</h3>
          </div>

          <div class="about-card">
            <div class="about-logo">
              <el-icon><Star /></el-icon>
            </div>
            <div class="about-name">AI Agent</div>
            <div class="about-version">Version 1.0.0</div>
            <p class="about-desc">
              一个可爱又强大的 AI 智能助手平台，帮助你管理知识、连接工具、创造智能 Agent。
            </p>
            <div class="about-links">
              <a href="#" class="action-link">使用文档</a>
              <a href="#" class="action-link">隐私协议</a>
              <a href="#" class="action-link">联系我们</a>
            </div>
          </div>
        </div>
      </div>
    </div>

    <img src="/assets/cute-moon.jpg" class="deco-img deco-moon" alt="moon" />
    <img src="/assets/cute-cloud.jpg" class="deco-img deco-cloud" alt="cloud" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { User, Lock, Bell, Setting, Star, InfoFilled, Cpu } from '@element-plus/icons-vue'
import { listModelProviders, saveModelProvider, type LlmProviderConfig } from '@/api/settings'

const activeTab = ref('profile')

const settingTabs = [
  { key: 'profile', label: '个人资料', icon: User },
  { key: 'account', label: '账号安全', icon: Lock },
  { key: 'models', label: '模型配置', icon: Cpu },
  { key: 'notifications', label: '通知设置', icon: Bell },
  { key: 'preferences', label: '系统偏好', icon: Setting },
  { key: 'about', label: '关于', icon: InfoFilled }
]

const modelProviderNames = ['openai', 'deepseek', 'qwen', 'anthropic']

const modelProviders = reactive<LlmProviderConfig[]>([])

const loadModelProviders = async () => {
  try {
    const list = await listModelProviders()
    const map = new Map(list.map(item => [item.providerName, item]))
    modelProviders.splice(0, modelProviders.length)
    for (const name of modelProviderNames) {
      const existing = map.get(name)
      modelProviders.push({
        providerName: name,
        apiKey: existing?.apiKey || '',
        modelName: existing?.modelName || '',
        enabled: existing?.enabled ?? 1
      })
    }
  } catch (error) {
    ElMessage.error('加载模型配置失败')
  }
}

const providerLabel = (name: string) => {
  const labels: Record<string, string> = {
    openai: 'OpenAI',
    deepseek: 'DeepSeek',
    qwen: '通义千问',
    anthropic: 'Anthropic'
  }
  return labels[name] || name
}

const saveProvider = async (provider: LlmProviderConfig) => {
  if (!provider.apiKey || !provider.apiKey.trim()) {
    ElMessage.warning('请输入 API Key')
    return
  }
  try {
    await saveModelProvider({ ...provider, apiKey: provider.apiKey.trim() })
    ElMessage.success(`${providerLabel(provider.providerName)} 配置已保存`)
  } catch (error) {
    ElMessage.error('保存失败')
  }
}

onMounted(() => {
  loadModelProviders()
})

const profileForm = reactive({
  username: 'Admin',
  nickname: 'AI 小管家',
  email: 'admin@example.com',
  phone: '13800000000'
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const notificationForm = reactive({
  systemNotify: true,
  messageNotify: true,
  emailSubscribe: false
})

const preferenceForm = reactive({
  defaultModel: 'gpt-4o',
  theme: 'cute',
  language: 'zh-CN'
})

const saveProfile = () => {
  ElMessage.success('个人资料已保存')
}

const savePassword = () => {
  if (!passwordForm.oldPassword || !passwordForm.newPassword || !passwordForm.confirmPassword) {
    ElMessage.warning('请填写完整密码信息')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  ElMessage.success('密码修改成功')
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
}

const savePreferences = () => {
  ElMessage.success('偏好设置已保存')
}
</script>

<style scoped>
.settings-view {
  padding: 24px;
  position: relative;
  min-height: calc(100vh - 112px);
}

.page-header {
  margin-bottom: 24px;
}

.page-title {
  font-size: 28px;
  font-weight: 900;
  color: var(--text-dark);
  margin: 0 0 6px 0;
}

.page-subtitle {
  font-size: 15px;
  color: var(--text-medium);
  margin: 0;
  font-weight: 600;
}

.settings-layout {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.settings-sidebar {
  width: 220px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.settings-tab {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 2px solid transparent;
  color: var(--text-dark);
  font-weight: 700;
  background: var(--card-bg);
}

.settings-tab:hover {
  border-color: var(--text-dark);
  box-shadow: 3px 3px 0 var(--text-dark);
  transform: translateX(4px);
}

.settings-tab.active {
  background: var(--coral);
  color: white;
  border-color: var(--text-dark);
  box-shadow: 3px 3px 0 var(--text-dark);
}

.settings-content {
  flex: 1;
  padding: 28px;
  min-height: 480px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 24px;
}

.section-title h3 {
  margin: 0;
  font-size: 20px;
  font-weight: 800;
  color: var(--text-dark);
}

.title-dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid var(--text-dark);
}

.profile-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
  padding: 16px;
  background: var(--cream);
  border-radius: 20px;
  border: 2px solid var(--text-dark);
}

.profile-avatar {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: var(--coral);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  font-weight: 900;
  border: 3px solid var(--text-dark);
  box-shadow: 4px 4px 0 var(--text-dark);
  flex-shrink: 0;
}

.profile-info {
  flex: 1;
}

.profile-name {
  font-size: 20px;
  font-weight: 800;
  color: var(--text-dark);
}

.profile-role {
  font-size: 14px;
  color: var(--text-medium);
  font-weight: 600;
  margin-top: 4px;
}

.settings-form :deep(.el-form-item__label) {
  font-weight: 700;
  color: var(--text-dark);
}

.settings-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.setting-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-radius: 16px;
  background: var(--cream);
  border: 2px solid var(--text-dark);
  transition: transform 0.2s ease;
}

.setting-item:hover {
  transform: translateX(4px);
}

.setting-label {
  font-weight: 800;
  color: var(--text-dark);
  font-size: 15px;
}

.setting-desc {
  font-size: 13px;
  color: var(--text-medium);
  font-weight: 600;
  margin-top: 4px;
}

.about-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 40px 20px;
}

.about-logo {
  width: 90px;
  height: 90px;
  border-radius: 50%;
  background: var(--golden);
  color: var(--text-dark);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40px;
  border: 3px solid var(--text-dark);
  box-shadow: 4px 4px 0 var(--text-dark);
  margin-bottom: 20px;
  animation: float 4s ease-in-out infinite;
}

.about-name {
  font-size: 22px;
  font-weight: 900;
  color: var(--text-dark);
}

.about-version {
  font-size: 13px;
  color: var(--text-medium);
  font-weight: 700;
  margin-bottom: 12px;
}

.about-desc {
  max-width: 400px;
  color: var(--text-medium);
  font-weight: 600;
  line-height: 1.6;
  margin-bottom: 20px;
}

.about-links {
  display: flex;
  gap: 16px;
}

.action-link {
  background: none;
  border: none;
  padding: 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--coral);
  cursor: pointer;
  text-decoration: underline;
  text-decoration-style: wavy;
  text-underline-offset: 3px;
  font-family: 'Nunito', sans-serif;
}

.deco-moon {
  top: 20px;
  right: 24px;
  animation: twinkle 4s ease-in-out infinite;
}

.deco-cloud {
  bottom: 24px;
  right: 120px;
  animation: drift 6s ease-in-out infinite;
}

.provider-cards {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.provider-card {
  padding: 20px;
  border-radius: 20px;
  background: var(--cream);
  border: 2px solid var(--text-dark);
  transition: transform 0.2s ease;
}

.provider-card:hover {
  transform: translateY(-2px);
}

.provider-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.provider-name {
  font-size: 16px;
  font-weight: 800;
  color: var(--text-dark);
}

@media (max-width: 1024px) {
  .provider-cards {
    grid-template-columns: 1fr;
  }

  .settings-layout {
    flex-direction: column;
  }

  .settings-sidebar {
    width: 100%;
    flex-direction: row;
    overflow-x: auto;
  }

  .settings-tab {
    white-space: nowrap;
  }

  .settings-tab:hover {
    transform: translateY(-2px);
  }
}

@media (max-width: 767px) {
  .settings-view {
    padding: 16px;
  }

  .page-title {
    font-size: 24px;
  }

  .settings-content {
    padding: 20px;
  }

  .deco-img {
    display: none;
  }
}
</style>
