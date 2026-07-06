<template>
  <div class="dashboard">
    <div class="dashboard-header">
      <h1 class="page-title">欢迎来到 AI Agent 智能助手</h1>
      <p class="page-subtitle">今天也是充满创造力的一天呢</p>
      <img src="/assets/cute-sun.jpg" class="deco-img deco-sun" alt="sun" />
      <img src="/assets/cute-cloud.jpg" class="deco-img deco-cloud" alt="cloud" />
    </div>

    <el-row :gutter="20" class="stats-row">
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="hand-card stat-card" style="--accent: var(--teal)">
          <div class="stat-icon" style="background: var(--teal)">
            <el-icon><ChatDotRound /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.chatCount }}</div>
            <div class="stat-label">对话次数</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="hand-card stat-card" style="--accent: var(--lavender)">
          <div class="stat-icon" style="background: var(--lavender)">
            <el-icon><Document /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.docCount }}</div>
            <div class="stat-label">知识库文档</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="hand-card stat-card" style="--accent: var(--golden)">
          <div class="stat-icon" style="background: var(--golden)">
            <el-icon><Setting /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.agentCount }}</div>
            <div class="stat-label">Agent 数量</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="hand-card stat-card" style="--accent: var(--mint)">
          <div class="stat-icon" style="background: var(--mint)">
            <el-icon><Tools /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.toolCount }}</div>
            <div class="stat-label">工具数量</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="dashboard-row">
      <el-col :xs="24" :lg="12">
        <div class="hand-card quick-card">
          <div class="card-title">
            <span class="title-dot" style="background: var(--coral)"></span>
            <h3>快速操作</h3>
          </div>
          <p class="card-desc">选择一个入口开始你的智能之旅</p>
          <div class="quick-actions">
            <button class="btn-pill btn-primary-pill quick-btn" @click="$router.push('/chat')">
              <el-icon><ChatDotRound /></el-icon> 开始对话
            </button>
            <button class="btn-pill btn-primary-pill quick-btn" style="background: var(--teal); border-color: var(--teal)" @click="$router.push('/knowledge')">
              <el-icon><Document /></el-icon> 管理知识库
            </button>
            <button class="btn-pill btn-primary-pill quick-btn" style="background: var(--lavender); border-color: var(--lavender)" @click="$router.push('/agent')">
              <el-icon><Setting /></el-icon> 配置 Agent
            </button>
            <button class="btn-pill btn-primary-pill quick-btn" style="background: var(--golden); border-color: var(--golden)" @click="$router.push('/mcp')">
              <el-icon><Tools /></el-icon> 查看工具
            </button>
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :lg="12">
        <div class="hand-card activity-card">
          <div class="card-title">
            <span class="title-dot" style="background: var(--mint)"></span>
            <h3>最近动态</h3>
          </div>
          <div class="activity-list">
            <div class="activity-item">
              <div class="activity-icon" style="background: var(--coral-light)">
                <el-icon><Star /></el-icon>
              </div>
              <div class="activity-text">
                <div class="activity-title">系统初始化完成</div>
                <div class="activity-time">刚刚</div>
              </div>
            </div>
            <div class="activity-item">
              <div class="activity-icon" style="background: var(--teal)">
                <el-icon><ChatDotRound /></el-icon>
              </div>
              <div class="activity-text">
                <div class="activity-title">欢迎使用 AI Agent</div>
                <div class="activity-time">今天</div>
              </div>
            </div>
            <div class="activity-item">
              <div class="activity-icon" style="background: var(--lavender)">
                <el-icon><Lightning /></el-icon>
              </div>
              <div class="activity-text">
                <div class="activity-title">智能助手已就位</div>
                <div class="activity-time">今天</div>
              </div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <img src="/assets/happy-star.jpg" class="deco-img deco-star" alt="star" />
  </div>
</template>

<script setup lang="ts">
import { reactive, onMounted } from 'vue'
import { ChatDotRound, Document, Setting, Tools, Star, Lightning } from '@element-plus/icons-vue'
import { listSessions } from '@/api/chat'
import { getKnowledgeBases } from '@/api/knowledge'
import { listAgents } from '@/api/agent'
import { listTools } from '@/api/mcp'
import type { ChatSession } from '@/types/chat'
import type { KnowledgeBase } from '@/types/knowledge'
import type { AgentConfig } from '@/types/agent'
import type { ToolInfo } from '@/api/mcp'

const stats = reactive({
  chatCount: 0,
  docCount: 0,
  agentCount: 0,
  toolCount: 0
})

async function loadStats() {
  try {
    const [sessions, bases, agents, tools] = await Promise.all([
      listSessions().catch(() => [] as ChatSession[]),
      getKnowledgeBases().catch(() => [] as KnowledgeBase[]),
      listAgents().catch(() => [] as AgentConfig[]),
      listTools().catch(() => [] as ToolInfo[])
    ])
    stats.chatCount = (sessions || []).reduce((sum, s) => sum + (s.messageCount || 0), 0)
    stats.docCount = (bases || []).reduce((sum, b) => sum + (b.documentCount || 0), 0)
    stats.agentCount = (agents || []).length
    stats.toolCount = (tools || []).length
  } catch (e) {
    console.error('加载统计数据失败', e)
  }
}

onMounted(() => {
  loadStats()
})
</script>

<style scoped>
.dashboard {
  padding: 24px;
  position: relative;
  min-height: 100%;
}

.dashboard-header {
  text-align: center;
  margin-bottom: 32px;
  position: relative;
}

.page-title {
  font-size: 32px;
  font-weight: 900;
  color: var(--text-dark);
  margin: 0 0 8px 0;
}

.page-subtitle {
  font-size: 16px;
  color: var(--text-medium);
  margin: 0;
  font-weight: 600;
}

.stats-row {
  margin-bottom: 24px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  margin-bottom: 20px;
  border-left: 6px solid var(--accent, var(--coral));
}

.stat-icon {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 24px;
  border: 3px solid var(--text-dark);
  box-shadow: 3px 3px 0 var(--text-dark);
  flex-shrink: 0;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 32px;
  font-weight: 900;
  color: var(--text-dark);
  line-height: 1;
}

.stat-label {
  font-size: 14px;
  color: var(--text-medium);
  font-weight: 700;
  margin-top: 4px;
}

.dashboard-row {
  margin-bottom: 24px;
}

.quick-card,
.activity-card {
  padding: 24px;
  height: 100%;
  margin-bottom: 20px;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.card-title h3 {
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

.card-desc {
  color: var(--text-medium);
  font-weight: 600;
  margin: 0 0 20px 0;
  font-size: 14px;
}

.quick-actions {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
}

.quick-btn {
  width: 100%;
  justify-content: center;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  font-size: 15px;
}

.activity-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.activity-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px;
  border-radius: 16px;
  background: var(--cream);
  border: 2px solid var(--text-dark);
  transition: transform 0.2s ease;
}

.activity-item:hover {
  transform: translateX(4px);
}

.activity-icon {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 18px;
  border: 2px solid var(--text-dark);
  flex-shrink: 0;
}

.activity-text {
  flex: 1;
}

.activity-title {
  font-weight: 700;
  color: var(--text-dark);
  font-size: 15px;
}

.activity-time {
  font-size: 12px;
  color: var(--text-medium);
  font-weight: 600;
  margin-top: 2px;
}

.deco-sun {
  top: 0;
  right: 24px;
  animation: float 4s ease-in-out infinite;
}

.deco-cloud {
  top: 48px;
  left: 16px;
  animation: drift 6s ease-in-out infinite;
}

.deco-star {
  bottom: 24px;
  right: 32px;
  animation: twinkle 3s ease-in-out infinite;
}

@media (max-width: 767px) {
  .dashboard {
    padding: 16px;
  }

  .page-title {
    font-size: 24px;
  }

  .stat-card {
    padding: 16px;
  }

  .quick-actions {
    grid-template-columns: 1fr;
  }

  .deco-img {
    display: none;
  }
}
</style>
