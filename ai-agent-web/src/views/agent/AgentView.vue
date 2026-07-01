<template>
  <div class="agent-view">
    <div class="page-header">
      <div class="header-text">
        <h1 class="page-title">Agent 管理</h1>
        <p class="page-subtitle">创建并管理你的专属 AI 助手</p>
      </div>
      <button class="btn-pill btn-primary-pill create-btn" @click="openCreateAgent">
        <el-icon><Plus /></el-icon> 创建 Agent
      </button>
    </div>

    <div class="search-bar">
      <div class="hand-input search-input-wrap">
        <el-icon><Search /></el-icon>
        <el-input v-model="searchKeyword" placeholder="搜索 Agent..." clearable />
      </div>
      <div class="filter-tags">
        <button
          v-for="tag in agentTags"
          :key="tag"
          class="hand-tag"
          :class="{ active: activeTag === tag }"
          @click="activeTag = tag"
        >
          {{ tag }}
        </button>
      </div>
    </div>

    <el-row :gutter="20" class="agent-list">
      <el-col :xs="24" :sm="12" :lg="8" v-for="agent in filteredAgents" :key="agent.id">
        <div class="hand-card agent-card" :style="{ '--accent': agent.color }">
          <div class="agent-card-header">
            <div class="agent-avatar" :style="{ background: agent.color }">
              <el-icon><component :is="agent.icon" /></el-icon>
            </div>
            <div class="agent-info">
              <div class="agent-name">{{ agent.name }}</div>
              <div class="agent-tag">
                <span class="hand-tag small" :style="{ background: agent.color }">{{ agent.tag }}</span>
              </div>
            </div>
            <el-switch v-model="agent.enabled" active-color="#FF6B6B" />
          </div>

          <div class="agent-desc">{{ agent.description }}</div>

          <div class="agent-meta">
            <span class="meta-item">
              <el-icon><Tools /></el-icon> {{ agent.tools }} 工具
            </span>
            <span class="meta-item">
              <el-icon><Document /></el-icon> {{ agent.knowledgeBases }} 知识库
            </span>
          </div>

          <div class="agent-actions">
            <button class="btn-pill btn-primary-pill" style="padding: 8px 16px; font-size: 13px" @click="openEditAgent(agent)">
              <el-icon><Edit /></el-icon> 编辑
            </button>
            <button class="btn-pill btn-primary-pill" style="padding: 8px 16px; font-size: 13px; background: var(--teal); border-color: var(--teal)" @click="goChat(agent)">
              <el-icon><ChatDotRound /></el-icon> 对话
            </button>
          </div>
        </div>
      </el-col>
    </el-row>

    <div v-if="filteredAgents.length === 0" class="empty-state">
      <div class="empty-icon">
              <el-icon><Star /></el-icon>
            </div>
      <p>还没有 Agent</p>
      <span>创建一个，开启你的智能助手之旅</span>
    </div>

    <img src="/assets/cute-planet.jpg" class="deco-img deco-planet" alt="planet" />
    <img src="/assets/happy-star.jpg" class="deco-img deco-star" alt="star" />

    <!-- 创建/编辑 Agent 对话框 -->
    <el-dialog v-model="showAgentDialog" :title="isEditing ? '编辑 Agent' : '创建 Agent'" width="560px" class="cute-dialog">
      <el-form :model="agentForm" label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="agentForm.name" placeholder="Agent 名称" />
        </el-form-item>
        <el-form-item label="标签" required>
          <el-select v-model="agentForm.tag" placeholder="选择标签" style="width: 100%">
            <el-option v-for="tag in agentTags.slice(1)" :key="tag" :label="tag" :value="tag" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述" required>
          <el-input v-model="agentForm.description" type="textarea" :rows="3" placeholder="描述这个 Agent 的用途" />
        </el-form-item>
        <el-form-item label="系统提示" required>
          <el-input v-model="agentForm.systemPrompt" type="textarea" :rows="4" placeholder="定义 Agent 的行为和角色" />
        </el-form-item>
        <el-form-item label="绑定工具">
          <el-select v-model="agentForm.tools" multiple placeholder="选择工具" style="width: 100%">
            <el-option label="搜索引擎" value="search" />
            <el-option label="代码执行器" value="code" />
            <el-option label="文件读取" value="file" />
            <el-option label="邮件发送" value="email" />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定知识库">
          <el-select v-model="agentForm.knowledgeBases" multiple placeholder="选择知识库" style="width: 100%">
            <el-option label="产品文档" value="1" />
            <el-option label="技术规范" value="2" />
            <el-option label="用户手册" value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="agentForm.enabled" active-color="#FF6B6B" />
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
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Search, Edit, ChatDotRound, Tools, Document, Star, ChatSquare, Lightning, Brush } from '@element-plus/icons-vue'

interface Agent {
  id: string
  name: string
  tag: string
  description: string
  systemPrompt: string
  tools: number
  knowledgeBases: number
  enabled: boolean
  color: string
  icon: any
}

const router = useRouter()

const agentTags = ['全部', '客服', '编程', '写作', '数据分析', '创意']
const activeTag = ref('全部')
const searchKeyword = ref('')
const showAgentDialog = ref(false)
const isEditing = ref(false)
const saving = ref(false)

const pastelColors = ['#E0F7FA', '#F8BBD0', '#FFF9C4', '#E8F5E9', '#F3E5F5', '#FFE0B2']
const tagColorMap: Record<string, string> = {
  '客服': '#E0F7FA',
  '编程': '#F8BBD0',
  '写作': '#FFF9C4',
  '数据分析': '#E8F5E9',
  '创意': '#F3E5F5'
}

const agentIconMap: Record<string, any> = {
  '客服': ChatSquare,
  '编程': Lightning,
  '写作': Brush,
  '数据分析': Tools,
  '创意': Star
}

const agents = ref<Agent[]>([
  {
    id: '1',
    name: '智能客服助手',
    tag: '客服',
    description: '擅长回答产品使用问题，处理常见咨询。',
    systemPrompt: '你是一个友好专业的客服助手。',
    tools: 3,
    knowledgeBases: 2,
    enabled: true,
    color: '#E0F7FA',
    icon: ChatSquare
  },
  {
    id: '2',
    name: '代码小精灵',
    tag: '编程',
    description: '帮你写代码、改 Bug、解释技术概念。',
    systemPrompt: '你是一个精通多种编程语言的助手。',
    tools: 4,
    knowledgeBases: 1,
    enabled: true,
    color: '#F8BBD0',
    icon: Lightning
  },
  {
    id: '3',
    name: '创意写手',
    tag: '写作',
    description: '擅长撰写文案、故事、营销内容。',
    systemPrompt: '你是一个富有创意的中文写作助手。',
    tools: 1,
    knowledgeBases: 0,
    enabled: false,
    color: '#FFF9C4',
    icon: Brush
  }
])

const agentForm = reactive({
  id: '',
  name: '',
  tag: '客服',
  description: '',
  systemPrompt: '',
  tools: [] as string[],
  knowledgeBases: [] as string[],
  enabled: true
})

const filteredAgents = computed(() => {
  let result = agents.value
  if (activeTag.value !== '全部') {
    result = result.filter(a => a.tag === activeTag.value)
  }
  if (searchKeyword.value.trim()) {
    const keyword = searchKeyword.value.toLowerCase()
    result = result.filter(a =>
      a.name.toLowerCase().includes(keyword) ||
      a.description.toLowerCase().includes(keyword)
    )
  }
  return result
})

const resetForm = () => {
  agentForm.id = ''
  agentForm.name = ''
  agentForm.tag = '客服'
  agentForm.description = ''
  agentForm.systemPrompt = ''
  agentForm.tools = []
  agentForm.knowledgeBases = []
  agentForm.enabled = true
}

const openCreateAgent = () => {
  isEditing.value = false
  resetForm()
  showAgentDialog.value = true
}

const openEditAgent = (agent: Agent) => {
  isEditing.value = true
  agentForm.id = agent.id
  agentForm.name = agent.name
  agentForm.tag = agent.tag
  agentForm.description = agent.description
  agentForm.systemPrompt = agent.systemPrompt
  agentForm.tools = []
  agentForm.knowledgeBases = []
  agentForm.enabled = agent.enabled
  showAgentDialog.value = true
}

const handleSaveAgent = async () => {
  if (!agentForm.name || !agentForm.description || !agentForm.systemPrompt) {
    ElMessage.warning('请填写完整信息')
    return
  }

  saving.value = true
  await new Promise(resolve => setTimeout(resolve, 500))

  const color = tagColorMap[agentForm.tag] || pastelColors[Math.floor(Math.random() * pastelColors.length)]
  const icon = agentIconMap[agentForm.tag] || Star

  if (isEditing.value) {
    const idx = agents.value.findIndex(a => a.id === agentForm.id)
    if (idx !== -1) {
      agents.value[idx] = {
        ...agents.value[idx],
        name: agentForm.name,
        tag: agentForm.tag,
        description: agentForm.description,
        systemPrompt: agentForm.systemPrompt,
        enabled: agentForm.enabled,
        color,
        icon,
        tools: agentForm.tools.length || agents.value[idx].tools,
        knowledgeBases: agentForm.knowledgeBases.length || agents.value[idx].knowledgeBases
      }
    }
    ElMessage.success('保存成功')
  } else {
    agents.value.push({
      id: Date.now().toString(),
      name: agentForm.name,
      tag: agentForm.tag,
      description: agentForm.description,
      systemPrompt: agentForm.systemPrompt,
      tools: agentForm.tools.length,
      knowledgeBases: agentForm.knowledgeBases.length,
      enabled: agentForm.enabled,
      color,
      icon
    })
    ElMessage.success('创建成功')
  }

  saving.value = false
  showAgentDialog.value = false
}

const goChat = (_agent: Agent) => {
  router.push('/chat')
}
</script>

<style scoped>
.agent-view {
  padding: 24px;
  position: relative;
  min-height: 100%;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
  flex-wrap: wrap;
  gap: 16px;
}

.header-text {
  flex: 1;
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

.create-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 22px;
  font-size: 15px;
}

.search-bar {
  margin-bottom: 24px;
  display: flex;
  gap: 16px;
  align-items: flex-start;
  flex-wrap: wrap;
}

.search-input-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 16px;
  max-width: 360px;
  flex: 1;
  min-width: 240px;
}

.search-input-wrap :deep(.el-input__wrapper) {
  background: transparent;
  box-shadow: none;
  border: none;
}

.search-input-wrap :deep(.el-input__inner) {
  font-family: 'Nunito', sans-serif;
  font-weight: 600;
  color: var(--text-dark);
}

.filter-tags {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.hand-tag {
  padding: 6px 14px;
  border-radius: 999px;
  border: 2px solid var(--text-dark);
  background: white;
  color: var(--text-dark);
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s ease;
  font-family: 'Nunito', sans-serif;
  font-size: 13px;
}

.hand-tag:hover,
.hand-tag.active {
  background: var(--coral);
  color: white;
  transform: translateY(-2px);
  box-shadow: 3px 3px 0 var(--text-dark);
}

.hand-tag.small {
  padding: 4px 10px;
  font-size: 12px;
  border-width: 2px;
}

.agent-list {
  margin-bottom: 20px;
}

.agent-card {
  padding: 20px;
  margin-bottom: 20px;
  border-left: 6px solid var(--accent, var(--coral));
  transition: transform 0.2s ease;
}

.agent-card:hover {
  transform: translateY(-4px);
}

.agent-card-header {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 14px;
}

.agent-avatar {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-dark);
  font-size: 24px;
  border: 3px solid var(--text-dark);
  box-shadow: 3px 3px 0 var(--text-dark);
  flex-shrink: 0;
}

.agent-info {
  flex: 1;
  min-width: 0;
}

.agent-name {
  font-weight: 800;
  font-size: 17px;
  color: var(--text-dark);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-tag {
  margin-top: 6px;
}

.agent-desc {
  color: var(--text-medium);
  font-size: 14px;
  line-height: 1.5;
  margin-bottom: 14px;
  font-weight: 600;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.agent-meta {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-medium);
  font-weight: 700;
}

.agent-actions {
  display: flex;
  gap: 10px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: var(--text-medium);
  text-align: center;
}

.empty-icon {
  width: 90px;
  height: 90px;
  border-radius: 50%;
  background: var(--coral-light);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36px;
  border: 3px solid var(--text-dark);
  box-shadow: 4px 4px 0 var(--text-dark);
  margin-bottom: 20px;
  animation: float 4s ease-in-out infinite;
}

.empty-state p {
  font-size: 18px;
  font-weight: 800;
  color: var(--text-dark);
  margin: 0 0 6px 0;
}

.empty-state span {
  font-size: 14px;
  color: var(--text-medium);
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
  top: 20px;
  right: 24px;
  animation: float 5s ease-in-out infinite;
}

.deco-star {
  bottom: 24px;
  right: 32px;
  animation: twinkle 3s ease-in-out infinite;
}

@media (max-width: 767px) {
  .agent-view {
    padding: 16px;
  }

  .page-title {
    font-size: 24px;
  }

  .search-bar {
    flex-direction: column;
  }

  .search-input-wrap {
    max-width: 100%;
  }

  .deco-planet,
  .deco-star {
    display: none;
  }
}
</style>
