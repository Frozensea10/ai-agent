<template>
  <div class="chat-view">
    <div class="chat-layout">
      <div class="chat-sidebar" :class="{ open: sidebarOpen }">
        <div class="sidebar-header">
          <h3>会话列表</h3>
          <button class="btn-pill btn-primary-pill new-chat-btn" @click="createNewChat">
            <el-icon><Plus /></el-icon> 新对话
          </button>
        </div>
        <div class="chat-list">
          <div
            v-for="session in chatSessions"
            :key="session.id"
            class="chat-session-item"
            :class="{ active: currentSessionId === session.id }"
            @click="selectSession(session.id)"
          >
            <el-icon><ChatSquare /></el-icon>
            <span class="session-title">{{ session.title }}</span>
            <span class="session-time">{{ session.time }}</span>
          </div>
        </div>
      </div>
      <div class="chat-backdrop" :class="{ open: sidebarOpen }" @click="sidebarOpen = false"></div>

      <div class="chat-main">
        <div class="chat-main-header">
          <button class="sidebar-toggle" @click="sidebarOpen = !sidebarOpen">
            <el-icon><Menu /></el-icon>
          </button>
          <h3>{{ currentSession.title }}</h3>
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
            :key="msg.id"
            :class="['message', msg.role === 'user' ? 'user-message' : 'ai-message']"
          >
            <div class="message-content">
              <div class="message-avatar" :style="msg.role === 'user' ? { background: 'var(--coral)' } : { background: 'var(--teal)' }">
                <el-icon v-if="msg.role === 'user'"><User /></el-icon>
                <img v-else src="/assets/friendly-bot.jpg" alt="ai" />
              </div>
              <div class="message-bubble" v-html="msg.content"></div>
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
import { ref, nextTick, computed } from 'vue'
import { ChatDotRound, User, Promotion, Plus, ChatSquare, Menu } from '@element-plus/icons-vue'

interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
}

interface ChatSession {
  id: string
  title: string
  time: string
  messages: Message[]
}

const chatSessions = ref<ChatSession[]>([
  { id: '1', title: '新对话 1', time: '今天', messages: [] },
  { id: '2', title: '知识库问答', time: '昨天', messages: [] },
  { id: '3', title: '代码助手', time: '昨天', messages: [] },
])
const currentSessionId = ref('1')
const inputMessage = ref('')
const sending = ref(false)
const messagesRef = ref<HTMLDivElement>()
const sidebarOpen = ref(false)

const currentSession = computed(() => {
  return chatSessions.value.find((s) => s.id === currentSessionId.value) || chatSessions.value[0]
})

const messages = computed(() => currentSession.value?.messages || [])

const selectSession = (id: string) => {
  currentSessionId.value = id
  sidebarOpen.value = false
}

const createNewChat = () => {
  const id = Date.now().toString()
  chatSessions.value.unshift({ id, title: '新对话', time: '刚刚', messages: [] })
  currentSessionId.value = id
}

const sendMessage = async () => {
  if (!inputMessage.value.trim() || sending.value) return

  const userMsg: Message = {
    id: Date.now().toString(),
    role: 'user',
    content: inputMessage.value
  }

  currentSession.value.messages.push(userMsg)
  inputMessage.value = ''
  sending.value = true

  await nextTick()
  scrollToBottom()

  setTimeout(() => {
    const aiMsg: Message = {
      id: (Date.now() + 1).toString(),
      role: 'assistant',
      content: '这是一个模拟的 AI 回复。实际项目中会调用后端 SSE 接口实现流式对话。'
    }
    currentSession.value.messages.push(aiMsg)
    sending.value = false
    nextTick(scrollToBottom)
  }, 1000)
}

const scrollToBottom = () => {
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}
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

.new-chat-btn {
  width: 100%;
  justify-content: center;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
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
  padding: 12px 14px;
  border-radius: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 2px solid transparent;
  margin-bottom: 8px;
  color: var(--text-dark);
  font-weight: 600;
}

.chat-session-item:hover,
.chat-session-item.active {
  background: var(--cream);
  border-color: var(--text-dark);
  box-shadow: 3px 3px 0 var(--text-dark);
}

.chat-session-item.active {
  background: var(--coral);
  color: white;
}

.session-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-time {
  font-size: 12px;
  opacity: 0.8;
}

.chat-backdrop {
  display: none;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--cream);
  position: relative;
}

.chat-main-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  background: var(--card-bg);
  border-bottom: 3px solid var(--text-dark);
}

.chat-main-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 800;
  color: var(--text-dark);
}

.sidebar-toggle {
  display: none;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  border: 2px solid var(--text-dark);
  background: var(--card-bg);
  cursor: pointer;
  align-items: center;
  justify-content: center;
  color: var(--text-dark);
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-medium);
}

.empty-cloud {
  width: 100px;
  height: 100px;
  border-radius: 50%;
  background: var(--teal);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40px;
  border: 3px solid var(--text-dark);
  box-shadow: 4px 4px 0 var(--text-dark);
  margin-bottom: 20px;
  animation: float 4s ease-in-out infinite;
}

.empty-state p {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-dark);
  margin: 0 0 6px 0;
}

.empty-hint {
  font-size: 13px;
  color: var(--text-medium);
  font-weight: 600;
}

.message {
  margin-bottom: 20px;
}

.message-content {
  display: flex;
  gap: 12px;
  max-width: 80%;
  align-items: flex-start;
}

.user-message .message-content {
  margin-left: auto;
  flex-direction: row-reverse;
}

.message-avatar {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  border: 3px solid var(--text-dark);
  box-shadow: 3px 3px 0 var(--text-dark);
  flex-shrink: 0;
  overflow: hidden;
}

.message-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.message-bubble {
  padding: 14px 18px;
  border-radius: 20px;
  background: var(--card-bg);
  color: var(--text-dark);
  border: 3px solid var(--text-dark);
  box-shadow: 4px 4px 0 var(--text-dark);
  word-break: break-word;
  font-weight: 600;
  line-height: 1.5;
}

.user-message .message-bubble {
  background: var(--coral);
  color: white;
  border-radius: 20px 20px 4px 20px;
}

.ai-message .message-bubble {
  border-radius: 20px 20px 20px 4px;
}

.chat-input-area {
  padding: 16px 20px 24px;
  background: var(--card-bg);
  border-top: 3px solid var(--text-dark);
}

.chat-input-wrap {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.chat-input-wrap :deep(.el-textarea__inner) {
  background: transparent;
  border: none;
  box-shadow: none;
  font-family: 'Nunito', sans-serif;
  font-weight: 600;
  color: var(--text-dark);
}

.send-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 12px 22px;
  flex-shrink: 0;
  font-size: 14px;
}

.send-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
}

.hint {
  color: var(--text-medium);
  font-size: 12px;
  font-weight: 600;
}

.deco-img {
  position: absolute;
  width: 80px;
  height: 80px;
  object-fit: contain;
  pointer-events: none;
}

.deco-planet {
  bottom: 100px;
  right: 24px;
  animation: float 5s ease-in-out infinite;
}

.deco-moon {
  top: 20px;
  right: 100px;
  animation: twinkle 4s ease-in-out infinite;
}

@media (max-width: 1024px) {
  .chat-sidebar {
    position: fixed;
    left: 0;
    top: 72px;
    bottom: 0;
    z-index: 100;
    transform: translateX(-100%);
    transition: transform 0.3s ease;
    border-right: none;
    border-right: 3px solid var(--text-dark);
  }

  .chat-sidebar.open {
    transform: translateX(0);
  }

  .chat-backdrop {
    display: block;
    position: fixed;
    inset: 72px 0 0 0;
    background: rgba(45, 52, 54, 0.3);
    z-index: 99;
    opacity: 0;
    pointer-events: none;
    transition: opacity 0.3s ease;
  }

  .chat-backdrop.open {
    opacity: 1;
    pointer-events: auto;
  }

  .sidebar-toggle {
    display: flex;
  }

  .deco-planet,
  .deco-moon {
    display: none;
  }
}

@media (max-width: 767px) {
  .chat-main-header {
    padding: 12px 16px;
  }

  .chat-messages {
    padding: 16px;
  }

  .message-content {
    max-width: 90%;
  }

  .chat-input-area {
    padding: 12px 16px 16px;
  }

  .send-btn {
    padding: 10px 14px;
  }
}
</style>
