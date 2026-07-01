<template>
  <div class="agent-view">
    <div class="page-header">
      <div class="header-text">
        <h1 class="page-title">Agent 管理</h1>
        <p class="page-subtitle">创建并管理你的专属 AI 助手</p>
      </div>
      <button class="btn-pill btn-primary-pill create-btn" :disabled="loading" @click="openCreateAgent">
        <el-icon><Plus /></el-icon> 创建 Agent
      </button>
    </div>

    <div class="search-bar">
      <div class="hand-input search-input-wrap">
        <el-icon><Search /></el-icon>
        <el-input v-model="searchKeyword" placeholder="搜索 Agent..." clearable />
      </div>
    </div>

    <div v-if="loading" class="loading-tip">加载中...</div>

    <el-row :gutter="20" class="agent-list">
      <el-col :xs="24" :sm="12" :lg="8" v-for="agent in filteredAgents" :key="agent.id">
        <div class="hand-card agent-card" :style="{ '--accent': getAgentColor(agent.modelProvider) }">
          <div class="agent-card-header">
            <div class="agent-avatar" :style="{ background: getAgentColor(agent.modelProvider) }">
              <el-icon><Cpu /></el-icon>
            </div>
            <div class="agent-info">
              <div class="agent-name">{{ agent.agentName }}</div>
              <div class="agent-tag">
                <span class="hand-tag small" :style="{ background: getAgentColor(agent.modelProvider) }">{{ agent.modelProvider }}</span>
              </div>
            </div>
            <el-switch
              :model-value="agent.status === 1"
              active-color="#FF6B6B"
              @change="(val: boolean) => toggleStatus(agent, val)"
            />
          </div>

          <div class="agent-desc">{{ agent.description || '暂无描述' }}</div>

          <div class="agent-meta">
            <span class="meta-item">
              <el-icon><Cpu /></el-icon> {{ agent.modelName }}
            </span>
            <span class="meta-item">
              <el-icon><Timer /></el-icon> {{ agent.maxTokens }} tokens
            </span>
          </div>

          <div class="agent-actions">
            <button class="btn-pill btn-primary-pill" style="padding: 8px 16px; font-size: 13px" @click="openEditAgent(agent)">
              <el-icon><Edit /></el-icon> 编辑
            </button>
            <button class="btn-pill btn-primary-pill" style="padding: 8px 16px; font-size: 13px; background: var(--teal); border-color: var(--teal)" @click="goChat(agent)">
              <el-icon><ChatDotRound /></el-icon> 对话
            </button>
            <button class="btn-pill btn-primary-pill" style="padding: 8px 16px; font-size: 13px; background: #ff6b6b; border-color: #ff6b6b" @click="handleDeleteAgent(agent)">
              <el-icon><Delete /></el-icon> 删除
            </button>
          </div>
        </div>
      </el-col>
    </el-row>

    <div v-if="!loading && filteredAgents.length === 0" class="empty-state">
      <div class="empty-icon">
        <el-icon><Star /></el-icon>
      </div>
      <p>还没有 Agent</p>
      <span>创建一个，开启你的智能助手之旅</span>
    </div>

    <img src="/assets/cute-planet.jpg" class="deco-img deco-planet" alt="planet" />
    <img src="/assets/happy-star.jpg" class="deco-img deco-star" alt="star" />

    <!-- 创建/编辑 Agent 对话框 -->
    <el-dialog v-model="showAgentDialog" :title="isEditing ? '编辑 Agent' : '创建 Agent'" width="600px" class="cute-dialog">
      <el-form :model="agentForm" label-width="120px">
        <el-form-item label="名称" required>
          <el-input v-model="agentForm.agentName" placeholder="Agent 名称" />
        </el-form-item>
        <el-form-item label="编码" required>
          <el-input v-model="agentForm.agentCode" placeholder="唯一编码，如 code-assistant" />
        </el-form-item>
        <el-form-item label="模型提供商" required>
          <el-select v-model="agentForm.modelProvider" placeholder="选择模型提供商" style="width: 100%">
            <el-option label="OpenAI" value="openai" />
            <el-option label="DeepSeek" value="deepseek" />
            <el-option label="通义千问" value="qwen" />
            <el-option label="Anthropic" value="anthropic" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型名称" required>
          <el-input v-model="agentForm.modelName" placeholder="如 gpt-3.5-turbo / deepseek-chat" />
        </el-form-item>
        <el-form-item label="描述" required>
          <el-input v-model="agentForm.description" type="textarea" :rows="3" placeholder="描述这个 Agent 的用途" />
        </el-form-item>
        <el-form-item label="系统提示" required>
          <el-input v-model="agentForm.systemPrompt" type="textarea" :rows="4" placeholder="定义 Agent 的行为和角色" />
        </el-form-item>
        <el-form-item label="温度">
          <el-slider v-model="agentForm.temperature" :min="0" :max="1" :step="0.1" />
        </el-form-item>
        <el-form-item label="最大 Token">
          <el-input-number v-model="agentForm.maxTokens" :min="256" :max="8192" :step="256" />
        </el-form-item>
        <el-form-item label="记忆类型">
          <el-select v-model="agentForm.memoryType" placeholder="选择记忆类型" style="width: 100%">
            <el-option label="窗口记忆" value="window" />
            <el-option label="摘要记忆" value="summary" />
          </el-select>
        </el-form-item>
        <el-form-item label="记忆消息数">
          <el-input-number v-model="agentForm.memoryMaxMessages" :min="1" :max="50" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAgentDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveAgent" :loading="saving">{{ isEditing ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Edit, ChatDotRound, Star, Cpu, Timer, Delete } from '@element-plus/icons-vue'
import { listAgents, createAgent, updateAgent, deleteAgent } from '@/api/agent'
import type { AgentConfig, AgentForm } from '@/types/agent'

const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const searchKeyword = ref('')
const showAgentDialog = ref(false)
const isEditing = ref(false)
const editAgentId = ref<number | null>(null)
const agents = ref<AgentConfig[]>([])

const providerColorMap: Record<string, string> = {
  openai: '#E0F7FA',
  deepseek: '#F8BBD0',
  qwen: '#FFF9C4',
  anthropic: '#E8F5E9'
}

const getAgentColor = (provider?: string) => {
  return providerColorMap[provider || ''] || '#F3E5F5'
}

const filteredAgents = computed(() => {
  let result = agents.value
  if (searchKeyword.value.trim()) {
    const keyword = searchKeyword.value.toLowerCase()
    result = result.filter(a =>
      a.agentName.toLowerCase().includes(keyword) ||
      a.description.toLowerCase().includes(keyword) ||
      a.agentCode.toLowerCase().includes(keyword)
    )
  }
  return result
})

const agentForm = reactive<AgentForm>({
  agentName: '',
  agentCode: '',
  description: '',
  modelProvider: 'openai',
  modelName: 'gpt-3.5-turbo',
  systemPrompt: '',
  temperature: 0.7,
  maxTokens: 2048,
  memoryType: 'window',
  memoryMaxMessages: 10
})

const resetForm = () => {
  agentForm.agentName = ''
  agentForm.agentCode = ''
  agentForm.description = ''
  agentForm.modelProvider = 'openai'
  agentForm.modelName = 'gpt-3.5-turbo'
  agentForm.systemPrompt = ''
  agentForm.temperature = 0.7
  agentForm.maxTokens = 2048
  agentForm.memoryType = 'window'
  agentForm.memoryMaxMessages = 10
}

const loadAgents = async () => {
  loading.value = true
  try {
    const data = await listAgents()
    agents.value = data || []
  } catch {
    ElMessage.error('加载 Agent 列表失败')
  } finally {
    loading.value = false
  }
}

const openCreateAgent = () => {
  isEditing.value = false
  editAgentId.value = null
  resetForm()
  showAgentDialog.value = true
}

const openEditAgent = (agent: AgentConfig) => {
  isEditing.value = true
  editAgentId.value = agent.id
  agentForm.agentName = agent.agentName
  agentForm.agentCode = agent.agentCode
  agentForm.description = agent.description
  agentForm.modelProvider = agent.modelProvider
  agentForm.modelName = agent.modelName
  agentForm.systemPrompt = agent.systemPrompt
  agentForm.temperature = agent.temperature
  agentForm.maxTokens = agent.maxTokens
  agentForm.memoryType = agent.memoryType
  agentForm.memoryMaxMessages = agent.memoryMaxMessages
  showAgentDialog.value = true
}

const handleSaveAgent = async () => {
  if (!agentForm.agentName || !agentForm.agentCode || !agentForm.description || !agentForm.systemPrompt || !agentForm.modelName) {
    ElMessage.error('请填写必填项')
    return
  }

  saving.value = true
  try {
    if (isEditing.value && editAgentId.value) {
      await updateAgent(editAgentId.value, { ...agentForm })
      ElMessage.success('更新成功')
    } else {
      await createAgent({ ...agentForm })
      ElMessage.success('创建成功')
    }
    showAgentDialog.value = false
    await loadAgents()
  } catch {
    ElMessage.error(isEditing.value ? '更新失败' : '创建失败')
  } finally {
    saving.value = false
  }
}

const toggleStatus = async (agent: AgentConfig, enabled: boolean) => {
  try {
    await updateAgent(agent.id, { status: enabled ? 1 : 0 })
    agent.status = enabled ? 1 : 0
    ElMessage.success('状态更新成功')
  } catch {
    ElMessage.error('状态更新失败')
  }
}

const handleDeleteAgent = async (agent: AgentConfig) => {
  try {
    await ElMessageBox.confirm(`确定删除 Agent「${agent.agentName}」吗？`, '提示', { type: 'warning' })
    await deleteAgent(agent.id)
    ElMessage.success('删除成功')
    await loadAgents()
  } catch {
    // cancel
  }
}

const goChat = (agent: AgentConfig) => {
  router.push({ path: '/chat', query: { agentId: agent.id } })
}

onMounted(() => {
  loadAgents()
})
</script>

<style scoped>
.agent-view {
  padding: 24px;
  position: relative;
  min-height: calc(100vh - 72px);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-title {
  font-size: 28px;
  font-weight: 800;
  margin: 0 0 8px 0;
}

.page-subtitle {
  color: #666;
  margin: 0;
}

.search-bar {
  display: flex;
  gap: 16px;
  align-items: center;
  margin-bottom: 24px;
}

.search-input-wrap {
  width: 320px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 12px;
}

.loading-tip {
  padding: 20px 0;
  color: #888;
}

.agent-list {
  margin-bottom: 24px;
}

.agent-card {
  padding: 20px;
  margin-bottom: 20px;
  border-radius: 16px;
  transition: transform 0.2s;
}

.agent-card:hover {
  transform: translateY(-4px);
}

.agent-card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.agent-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid var(--text-dark);
  font-size: 24px;
}

.agent-info {
  flex: 1;
  min-width: 0;
}

.agent-name {
  font-weight: 800;
  font-size: 16px;
  margin-bottom: 4px;
}

.agent-desc {
  color: #555;
  font-size: 14px;
  line-height: 1.5;
  margin-bottom: 12px;
  min-height: 42px;
}

.agent-meta {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
  font-size: 13px;
  color: #666;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.agent-actions {
  display: flex;
  gap: 8px;
}

.empty-state {
  text-align: center;
  padding: 60px 0;
  color: #888;
}

.empty-icon {
  font-size: 64px;
  color: var(--teal);
  margin-bottom: 16px;
}
</style>
