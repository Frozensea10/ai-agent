<template>
  <div class="chat-view">
    <div class="chat-layout">
      <div class="chat-sidebar" :class="{ open: sidebarOpen }">
        <div class="sidebar-header">
          <h3>会话列表</h3>
          <button class="btn-pill btn-primary-pill new-chat-btn" :disabled="loading" @click="openNewChatDialog">
            <el-icon><Plus /></el-icon> 新对话
          </button>
        </div>
        <div v-if="loading" class="loading-tip">加载中...</div>
        <div class="chat-list">
          <div
            v-for="session in chatSessions"
            :key="session.sessionId"
            class="chat-session-item"
            :class="{ active: currentSessionId === session.sessionId }"
            @click="selectSession(session.sessionId)"
          >
            <el-icon><ChatSquare /></el-icon>
            <span class="session-title">{{ session.sessionTitle || '未命名会话' }}</span>
            <span v-if="session.kbCode" class="session-kb-tag">KB</span>
            <span class="session-time">{{ formatTime(session.updatedAt) }}</span>
            <el-icon class="delete-icon" @click.stop="handleDeleteSession(session.sessionId)"><Delete /></el-icon>
          </div>
        </div>
      </div>
      <div class="chat-backdrop" :class="{ open: sidebarOpen }" @click="sidebarOpen = false"></div>

      <div class="chat-main">
        <div class="chat-main-header">
          <button class="sidebar-toggle" @click="sidebarOpen = !sidebarOpen">
            <el-icon><Menu /></el-icon>
          </button>
          <div class="header-title">
            <h3>{{ currentSession?.sessionTitle || '新对话' }}</h3>
            <span v-if="currentSession?.kbCode" class="kb-tag">KB: {{ currentSession.kbCode }}</span>
          </div>
          <div class="model-select-wrap">
            <el-select v-model="selectedAgentId" placeholder="选择 Agent" size="small" style="width: 140px" @change="handleAgentChange">
              <el-option
                v-for="opt in agentOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </div>
        </div>

        <div class="chat-messages" ref="messagesRef">
          <div v-if="messages.length === 0" class="empty-state">
            <div class="empty-cloud">
              <el-icon><ChatDotRound /></el-icon>
            </div>
            <p>开始一段新的对话吧</p>
            <span class="empty-hint">输入你的问题，AI 会尽力帮你解答</span>
          </div>

          <div
            v-for="msg in messages"
            :key="msg.messageId"
            :class="['message', msg.role === 'user' ? 'user-message' : 'ai-message']"
          >
            <div class="message-content">
              <div class="message-avatar" :style="msg.role === 'user' ? { background: 'var(--coral)' } : { background: 'var(--teal)' }">
                <el-icon v-if="msg.role === 'user'"><User /></el-icon>
                <img v-else src="/assets/friendly-bot.jpg" alt="ai" />
              </div>
              <div class="message-bubble">{{ msg.content }}</div>
            </div>
          </div>
        </div>

        <div class="chat-input-area">
          <div class="modern-input-card">
            <el-input
              v-model="inputMessage"
              type="textarea"
              :rows="4"
              placeholder="帮你编写代码、调试 Bug、优化性能等开发工作，交付生产级代码产物。"
              @keyup.enter.ctrl="sendMessage"
              resize="none"
              class="modern-textarea"
            />
            <div class="input-toolbar">
              <div class="toolbar-left">
                <button class="toolbar-btn" title="上传文件">
                  <el-icon><Paperclip /></el-icon>
                </button>
                <button class="toolbar-btn" title="上传图片">
                  <el-icon><Picture /></el-icon>
                </button>
                <button class="toolbar-btn quick-btn" title="速通模式">
                  <el-icon><MagicStick /></el-icon>
                  <span>速通</span>
                </button>
              </div>
              <div class="toolbar-right">
                <el-select v-model="selectedModel" placeholder="选择模型" clearable size="small" class="model-select-inline">
                  <el-option
                    v-for="opt in modelOptions"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>
                <button class="toolbar-btn" title="语音输入">
                  <el-icon><Microphone /></el-icon>
                </button>
                <button class="toolbar-btn" title="更多">
                  <el-icon><More /></el-icon>
                </button>
                <button class="send-circle-btn" :disabled="sending || !inputMessage.trim()" @click="sendMessage">
                  <el-icon v-if="!sending"><Top /></el-icon>
                  <span v-else>发送中</span>
                </button>
              </div>
            </div>
          </div>
          <div class="input-actions">
            <span class="hint">Ctrl + Enter 发送</span>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="newChatDialogVisible" title="新建对话" width="420px">
      <div class="new-chat-form">
        <el-input v-model="newChatTitle" placeholder="请输入对话标题" />
        <el-select v-model="newChatAgentId" placeholder="选择 Agent" clearable>
          <el-option
            v-for="opt in agentOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <el-select v-model="newChatKbId" placeholder="选择知识库" clearable>
          <el-option label="不使用知识库" :value="undefined" />
          <el-option
            v-for="kb in knowledgeBases"
            :key="kb.id"
            :label="kb.kbName"
            :value="kb.id"
          />
        </el-select>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="newChatDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="confirmCreateNewChat">确认</el-button>
        </div>
      </template>
    </el-dialog>


  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChatDotRound, User, Plus, ChatSquare, Menu, Delete, Paperclip, Picture, MagicStick, Microphone, More, Top } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listSessions, createSession, deleteSession, getMessages, streamChat } from '@/api/chat'
import { getKnowledgeBases } from '@/api/knowledge'
import { listModelProviders } from '@/api/settings'
import { getAgent, listAgents } from '@/api/agent'
import { providerLabelMap } from '@/utils/provider'
import type { ChatSession, ChatMessage } from '@/types/chat'
import type { KnowledgeBase } from '@/types/knowledge'
import type { AgentConfig } from '@/types/agent'

const route = useRoute()
const router = useRouter()

const DEFAULT_AGENT_ID = 1

const chatSessions = ref<ChatSession[]>([])
const currentSessionId = ref<string>('')
const inputMessage = ref('')
const sending = ref(false)
const loading = ref(false)
const messagesRef = ref<HTMLDivElement>()
const sidebarOpen = ref(false)
const knowledgeBases = ref<KnowledgeBase[]>([])
const newChatDialogVisible = ref(false)
const newChatTitle = ref('新对话')
const newChatAgentId = ref<number>(DEFAULT_AGENT_ID)
const newChatKbId = ref<number | undefined>(undefined)
const selectedModel = ref<string>('')
const selectedAgentId = ref<number>(DEFAULT_AGENT_ID)
const providerConfigs = ref<{ providerName: string; apiKey?: string; modelName?: string; enabled?: number }[]>([])
const defaultAgent = ref<AgentConfig | null>(null)
const agents = ref<AgentConfig[]>([])

const agentOptions = computed(() => {
  return agents.value.map((agent) => ({
    label: agent.agentName || agent.agentCode,
    value: agent.id
  }))
})

const currentAgent = computed(() => {
  return agents.value.find((a) => a.id === selectedAgentId.value) || defaultAgent.value
})

const modelOptions = computed(() => {
  const options = providerConfigs.value
    .filter(item => item.enabled !== 0 && item.modelName)
    .map(item => ({
      label: `${providerLabelMap[item.providerName] || item.providerName} · ${item.modelName}`,
      value: `${item.providerName}:${item.modelName}`
    }))
  const agent = currentAgent.value
  if (agent?.modelProvider && agent?.modelName) {
    const defaultValue = `${agent.modelProvider}:${agent.modelName}`
    if (!options.some(opt => opt.value === defaultValue)) {
      options.unshift({
        label: `${providerLabelMap[agent.modelProvider] || agent.modelProvider} · ${agent.modelName}（Agent 默认）`,
        value: defaultValue
      })
    }
  }
  return options
})

const parsedSelectedModel = computed(() => {
  if (!selectedModel.value) return { modelProvider: undefined, modelName: undefined }
  const [provider, ...modelParts] = selectedModel.value.split(':')
  return {
    modelProvider: provider,
    modelName: modelParts.join(':')
  }
})

const currentSession = computed(() => {
  return chatSessions.value.find((s) => s.sessionId === currentSessionId.value)
})

const messages = ref<ChatMessage[]>([])

const formatTime = (time?: string) => {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const isToday = date.toDateString() === now.toDateString()
  return isToday ? date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : date.toLocaleDateString()
}

const currentAbortController = ref<(() => void) | null>(null)

const scrollToBottom = () => {
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

const loadSessions = async () => {
  loading.value = true
  try {
    const data = await listSessions()
    chatSessions.value = data || []
    if (chatSessions.value.length > 0 && !currentSessionId.value) {
      currentSessionId.value = chatSessions.value[0].sessionId
      await loadMessages(chatSessions.value[0].sessionId)
    }
  } catch (e) {
    ElMessage.error('加载会话列表失败')
  } finally {
    loading.value = false
  }
}

const loadMessages = async (sessionId: string) => {
  try {
    const data = await getMessages(sessionId)
    messages.value = data || []
    await nextTick()
    scrollToBottom()
  } catch (e) {
    ElMessage.error('加载消息历史失败')
  }
}

const loadKnowledgeBases = async () => {
  try {
    const res = await getKnowledgeBases()
    knowledgeBases.value = res || []
  } catch (e) {
    ElMessage.error('加载知识库列表失败')
  }
}

const loadModelProviders = async () => {
  try {
    providerConfigs.value = await listModelProviders()
  } catch (e) {
    ElMessage.error('加载模型配置失败')
  }
}

const loadDefaultAgent = async () => {
  try {
    const agent = await getAgent(DEFAULT_AGENT_ID)
    defaultAgent.value = agent
    if (!route.query.agentId && agent.modelProvider && agent.modelName) {
      selectedModel.value = `${agent.modelProvider}:${agent.modelName}`
    }
  } catch (e) {
    ElMessage.error('加载默认 Agent 失败')
  }
}

const loadAgents = async () => {
  try {
    const data = await listAgents()
    agents.value = data || []
  } catch (e) {
    ElMessage.error('加载 Agent 列表失败')
  }
}

const initSelectedAgent = () => {
  const queryAgentId = route.query.agentId ? Number(route.query.agentId) : undefined
  if (queryAgentId && agents.value.some(a => a.id === queryAgentId)) {
    selectedAgentId.value = queryAgentId
  } else if (agents.value.length > 0) {
    selectedAgentId.value = agents.value[0].id || DEFAULT_AGENT_ID
  } else {
    selectedAgentId.value = DEFAULT_AGENT_ID
  }
  const agent = currentAgent.value
  if (agent?.modelProvider && agent?.modelName) {
    selectedModel.value = `${agent.modelProvider}:${agent.modelName}`
  }
}

const selectSession = async (sessionId: string) => {
  currentSessionId.value = sessionId
  sidebarOpen.value = false
  await loadMessages(sessionId)
}

const openNewChatDialog = () => {
  newChatTitle.value = '新对话'
  newChatAgentId.value = selectedAgentId.value
  newChatKbId.value = undefined
  newChatDialogVisible.value = true
}

const confirmCreateNewChat = async () => {
  const title = newChatTitle.value.trim() || '新对话'
  const kbId = newChatKbId.value
  const agentId = newChatAgentId.value || selectedAgentId.value
  try {
    const data = await createSession({ agentId, title, kbId })
    chatSessions.value.unshift(data)
    currentSessionId.value = data.sessionId
    messages.value = []
    selectedAgentId.value = agentId
    sidebarOpen.value = false
    newChatDialogVisible.value = false
  } catch (e) {
    ElMessage.error('创建会话失败')
  }
}

const handleDeleteSession = async (sessionId: string) => {
  try {
    await ElMessageBox.confirm('确定删除该会话吗？', '提示', { type: 'warning' })
    await deleteSession(sessionId)
    chatSessions.value = chatSessions.value.filter((s) => s.sessionId !== sessionId)
    if (currentSessionId.value === sessionId) {
      currentSessionId.value = chatSessions.value[0]?.sessionId || ''
      messages.value = []
    }
    ElMessage.success('删除成功')
  } catch {
    // cancel
  }
}

const sendMessage = async () => {
  if (!inputMessage.value.trim() || sending.value) return
  if (!currentSessionId.value) {
    const session = await createSession({ agentId: selectedAgentId.value, title: '新对话' })
    if (!session?.sessionId) return
    chatSessions.value.unshift(session)
    currentSessionId.value = session.sessionId
    messages.value = []
  }

  const content = inputMessage.value.trim()
  const userMsg: ChatMessage = {
    id: Date.now(),
    messageId: `msg_${Date.now()}`,
    role: 'user',
    content,
    createdAt: new Date().toISOString()
  }

  messages.value.push(userMsg)
  inputMessage.value = ''
  sending.value = true

  await nextTick()
  scrollToBottom()

  const aiMsg: ChatMessage = {
    id: Date.now() + 1,
    messageId: `msg_${Date.now() + 1}`,
    role: 'assistant',
    content: '',
    createdAt: new Date().toISOString()
  }
  messages.value.push(aiMsg)

  let fullContent = ''

  currentAbortController.value = streamChat(
    currentSessionId.value,
    content,
    selectedAgentId.value,
    (messageId) => {
      aiMsg.messageId = messageId
    },
    (chunk) => {
      fullContent += chunk
      aiMsg.content = fullContent
    },
    () => {
      aiMsg.content = fullContent
      sending.value = false
      currentAbortController.value = null
      nextTick(scrollToBottom)
    },
    (error) => {
      aiMsg.content = fullContent ? fullContent + `\n\n[错误: ${error}]` : `[错误: ${error}]`
      sending.value = false
      currentAbortController.value = null
      ElMessage.error(error)
      nextTick(scrollToBottom)
    },
    currentSession.value?.kbCode,
    parsedSelectedModel.value.modelProvider,
    parsedSelectedModel.value.modelName
  )
}

const handleAgentChange = (agentId: number) => {
  selectedAgentId.value = agentId
  const agent = currentAgent.value
  if (agent?.modelProvider && agent?.modelName) {
    selectedModel.value = `${agent.modelProvider}:${agent.modelName}`
  }
  if (route.query.agentId) {
    router.replace({ path: route.path })
  }
}

watch(
  () => agents.value,
  () => {
    if (agents.value.length > 0 && selectedAgentId.value === DEFAULT_AGENT_ID) {
      initSelectedAgent()
    }
  },
  { once: true }
)

onMounted(async () => {
  await loadAgents()
  initSelectedAgent()
  loadSessions()
  loadKnowledgeBases()
  loadModelProviders()
  loadDefaultAgent()
})

onUnmounted(() => {
  if (currentAbortController.value) {
    currentAbortController.value()
    currentAbortController.value = null
  }
})
</script>

<style scoped>
.chat-view {
  height: calc(100vh - 72px);
  display: flex;
  position: relative;
  overflow: hidden;
}

.chat-layout {
  display: flex;
  width: 100%;
  height: 100%;
}

.chat-sidebar {
  width: 260px;
  background: var(--card-bg);
  border-right: 3px solid var(--text-dark);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.sidebar-header {
  padding: 20px;
  border-bottom: 3px solid var(--text-dark);
}

.sidebar-header h3 {
  margin: 0 0 14px 0;
  font-size: 18px;
  font-weight: 800;
  color: var(--text-dark);
}

.loading-tip {
  padding: 10px 20px;
  color: #888;
  font-size: 14px;
}

.chat-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.chat-session-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.2s;
  position: relative;
}

.chat-session-item:hover {
  background: rgba(0, 0, 0, 0.04);
}

.chat-session-item.active {
  background: var(--teal);
  color: var(--text-dark);
}

.session-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
  font-size: 14px;
}

.session-kb-tag {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 8px;
  background: var(--coral);
  color: white;
  font-weight: 700;
}

.session-time {
  font-size: 12px;
  opacity: 0.6;
}

.delete-icon {
  opacity: 0;
  transition: opacity 0.2s;
}

.chat-session-item:hover .delete-icon {
  opacity: 1;
}

.chat-backdrop {
  display: none;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  position: relative;
}

.chat-main-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 24px;
  border-bottom: 3px solid var(--text-dark);
  background: var(--card-bg);
}

.model-select-wrap {
  margin-left: auto;
}

.chat-main-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 800;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.kb-tag {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 12px;
  background: var(--teal);
  color: var(--text-dark);
  font-weight: 600;
}

.sidebar-toggle {
  display: none;
  background: none;
  border: none;
  font-size: 20px;
  cursor: pointer;
  color: var(--text-dark);
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.empty-state {
  margin: auto;
  text-align: center;
  color: #888;
}

.empty-cloud {
  font-size: 64px;
  color: var(--teal);
  margin-bottom: 16px;
}

.empty-hint {
  display: block;
  margin-top: 8px;
  font-size: 14px;
}

.message {
  display: flex;
}

.message-content {
  display: flex;
  gap: 12px;
  max-width: 80%;
}

.user-message {
  justify-content: flex-end;
}

.user-message .message-content {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
  border: 2px solid var(--text-dark);
}

.message-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.message-bubble {
  background: var(--card-bg);
  border: 2px solid var(--text-dark);
  border-radius: 16px;
  padding: 12px 16px;
  box-shadow: 4px 4px 0 var(--text-dark);
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
}

.user-message .message-bubble {
  background: var(--coral);
  color: white;
}

.chat-input-area {
  padding: 16px 24px 24px;
  background: var(--card-bg);
}

.modern-input-card {
  background: #fff;
  border: 2px solid var(--text-dark);
  border-radius: 24px;
  padding: 16px 16px 12px;
  box-shadow: 6px 6px 0 var(--text-dark);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.modern-textarea :deep(.el-textarea__inner) {
  border: none !important;
  box-shadow: none !important;
  background: transparent !important;
  resize: none !important;
  font-size: 15px;
  line-height: 1.6;
  padding: 4px 8px;
  color: var(--text-dark);
}

.modern-textarea :deep(.el-textarea__inner::placeholder) {
  color: #999;
  font-size: 15px;
}

.modern-textarea :deep(.el-textarea__inner:focus) {
  outline: none;
}

.input-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 4px;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-btn {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  border: none;
  background: transparent;
  color: #666;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 18px;
}

.toolbar-btn:hover {
  background: #f2f2f2;
  color: var(--text-dark);
}

.quick-btn {
  width: auto;
  padding: 0 12px;
  gap: 6px;
  font-size: 14px;
  font-weight: 500;
  color: #666;
}

.quick-btn span {
  font-size: 14px;
}

.model-select-inline {
  width: 160px;
}

.model-select-inline :deep(.el-input__wrapper) {
  border-radius: 10px;
  background: #f7f7f7;
  box-shadow: none !important;
  border: 1px solid transparent;
}

.model-select-inline :deep(.el-input__inner) {
  font-size: 13px;
  color: var(--text-dark);
}

.send-circle-btn {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: none;
  background: var(--coral);
  color: white;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 18px;
  box-shadow: 2px 2px 0 var(--text-dark);
}

.send-circle-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 3px 3px 0 var(--text-dark);
}

.send-circle-btn:disabled {
  background: #ccc;
  cursor: not-allowed;
  box-shadow: none;
}

.input-actions {
  margin-top: 10px;
  text-align: center;
}

.hint {
  font-size: 12px;
  color: #888;
}

.new-chat-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 768px) {
  .chat-sidebar {
    position: fixed;
    left: 0;
    top: 72px;
    bottom: 0;
    z-index: 100;
    transform: translateX(-100%);
    transition: transform 0.3s;
  }

  .chat-sidebar.open {
    transform: translateX(0);
  }

  .chat-backdrop {
    display: block;
    position: fixed;
    inset: 72px 0 0 0;
    background: rgba(0, 0, 0, 0.3);
    z-index: 99;
    opacity: 0;
    pointer-events: none;
    transition: opacity 0.3s;
  }

  .chat-backdrop.open {
    opacity: 1;
    pointer-events: auto;
  }

  .sidebar-toggle {
    display: block;
  }

  .message-content {
    max-width: 90%;
  }
}
</style>
