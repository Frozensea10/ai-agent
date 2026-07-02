<template>
  <div class="chat-view">
    <div class="chat-layout">
      <div class="chat-sidebar" :class="{ open: sidebarOpen }">
        <div class="sidebar-header">
          <h3>会话列表</h3>
          <button class="btn-pill btn-primary-pill new-chat-btn" :disabled="loading" @click="createNewChat">
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
              <div class="message-bubble" v-html="renderMarkdown(msg.content)"></div>
            </div>
          </div>
        </div>

        <div class="chat-input-area">
          <div class="hand-input chat-input-wrap">
            <el-input
              v-model="inputMessage"
              type="textarea"
              :rows="3"
              placeholder="输入消息..."
              @keyup.enter.ctrl="sendMessage"
              resize="none"
            />
            <button class="btn-pill btn-primary-pill send-btn" :disabled="sending || !inputMessage.trim()" @click="sendMessage">
              <el-icon v-if="!sending"><Promotion /></el-icon>
              <span v-else>发送中</span>
            </button>
          </div>
          <div class="input-actions">
            <span class="hint">Ctrl + Enter 发送</span>
          </div>
        </div>
      </div>
    </div>

    <img src="/assets/cute-planet.jpg" class="deco-img deco-planet" alt="planet" />
    <img src="/assets/cute-moon.jpg" class="deco-img deco-moon" alt="moon" />
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, computed, onMounted } from 'vue'
import { ChatDotRound, User, Promotion, Plus, ChatSquare, Menu, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listSessions, createSession, deleteSession, getMessages, streamChat } from '@/api/chat'
import type { ChatSession, ChatMessage } from '@/types/chat'

const DEFAULT_AGENT_ID = 1

const chatSessions = ref<ChatSession[]>([])
const currentSessionId = ref<string>('')
const inputMessage = ref('')
const sending = ref(false)
const loading = ref(false)
const messagesRef = ref<HTMLDivElement>()
const sidebarOpen = ref(false)
const abortFn = ref<(() => void) | null>(null)

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

const renderMarkdown = (content: string) => {
  return content.replace(/\n/g, '<br>')
}

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

const selectSession = async (sessionId: string) => {
  currentSessionId.value = sessionId
  sidebarOpen.value = false
  await loadMessages(sessionId)
}

const createNewChat = async () => {
  try {
    const data = await createSession({ agentId: DEFAULT_AGENT_ID, title: '新对话' })
    chatSessions.value.unshift(data)
    currentSessionId.value = data.sessionId
    messages.value = []
    sidebarOpen.value = false
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
    await createNewChat()
    if (!currentSessionId.value) return
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

  abortFn.value = streamChat(
    currentSessionId.value,
    content,
    DEFAULT_AGENT_ID,
    (messageId) => {
      aiMsg.messageId = messageId
    },
    (chunk) => {
      aiMsg.content += chunk
      nextTick(scrollToBottom)
    },
    () => {
      sending.value = false
      abortFn.value = null
      nextTick(scrollToBottom)
    },
    (error) => {
      sending.value = false
      abortFn.value = null
      aiMsg.content += `\n[错误: ${error}]`
      ElMessage.error(error)
      nextTick(scrollToBottom)
    },
    currentSession.value?.kbCode
  )
}

onMounted(() => {
  loadSessions()
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
}

.user-message .message-bubble {
  background: var(--coral);
  color: white;
}

.chat-input-area {
  padding: 16px 24px;
  border-top: 3px solid var(--text-dark);
  background: var(--card-bg);
}

.chat-input-wrap {
  display: flex;
  gap: 12px;
}

.send-btn {
  align-self: flex-end;
  white-space: nowrap;
}

.input-actions {
  margin-top: 8px;
  text-align: right;
}

.hint {
  font-size: 12px;
  color: #888;
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
