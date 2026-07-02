
<template>
  <div class="main-layout">
    <!-- Mobile backdrop for sidebar -->
    <div class="sidebar-backdrop" :class="{ open: sidebarOpen }" @click="sidebarOpen = false"></div>

    <!-- Sidebar -->
    <aside class="sidebar" :class="{ open: sidebarOpen }">
      <div class="sidebar-logo">
        <div class="sidebar-logo-icon">
          <el-icon><Star /></el-icon>
        </div>
        <span class="sidebar-logo-text">AI Agent</span>
      </div>

      <nav class="sidebar-nav">
        <router-link
          v-for="item in menuItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: $route.path === item.path || $route.path.startsWith(item.path + '/') }"
          @click="sidebarOpen = false"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </router-link>
      </nav>

      <div class="sidebar-deco">
        <div class="deco-dot" style="background: var(--coral);"></div>
        <div class="deco-dot" style="background: var(--teal);"></div>
        <div class="deco-dot" style="background: var(--golden);"></div>
        <div class="deco-dot" style="background: var(--lavender);"></div>
      </div>
    </aside>

    <!-- Main area -->
    <div class="main-area">
      <header class="header">
        <div class="header-left">
          <button class="sidebar-toggle" @click="sidebarOpen = !sidebarOpen" aria-label="打开菜单">
            <el-icon><Fold v-if="sidebarOpen" /><Expand v-else /></el-icon>
          </button>
          <h1 class="header-title">{{ pageTitle }}</h1>
          <div class="deco-dot animate-float" style="background: var(--golden); width: 10px; height: 10px;"></div>
        </div>

        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <span class="user-name">{{ userStore.userInfo?.username || 'Admin' }}</span>
              <span class="user-role">管理员</span>
            </span>
            <div class="user-avatar">{{ (userStore.userInfo?.username || 'A').charAt(0).toUpperCase() }}</div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><Setting /></el-icon> 个人设置
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="page-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  HomeFilled,
  ChatDotRound,
  Document,
  Setting,
  Connection,
  Tools,
  Star,
  Expand,
  Fold,
  SwitchButton,
  Folder
} from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const sidebarOpen = ref(false)

const menuItems = [
  { path: '/dashboard', title: '首页', icon: HomeFilled },
  { path: '/chat', title: '对话', icon: ChatDotRound },
  { path: '/knowledge', title: '知识库', icon: Document },
  { path: '/file', title: '文件管理', icon: Folder },
  { path: '/agent', title: 'Agent 配置', icon: Setting },
  { path: '/mcp', title: 'MCP 工具', icon: Connection },
  { path: '/settings', title: '设置', icon: Tools }
]

const pageTitle = computed(() => {
  const current = menuItems.find(item => route.path === item.path || route.path.startsWith(item.path + '/'))
  return current?.title || route.meta?.title || 'AI Agent'
})

const handleCommand = (command: string) => {
  if (command === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(() => {
      userStore.logout()
      router.push('/login')
      ElMessage.success('已退出登录')
    })
  } else if (command === 'profile') {
    router.push('/settings')
  }
}
</script>

<style scoped>
.main-layout {
  display: flex;
  min-height: 100vh;
  background: var(--cream);
}

.sidebar {
  width: 240px;
  background: var(--sidebar-bg);
  border-right: 3px solid var(--text-dark);
  min-height: 100vh;
  position: fixed;
  left: 0;
  top: 0;
  display: flex;
  flex-direction: column;
  z-index: 50;
  transition: transform 0.3s ease;
}

.sidebar-logo {
  padding: 20px 24px;
  border-bottom: 3px solid var(--text-dark);
  display: flex;
  align-items: center;
  gap: 12px;
}

.sidebar-logo-icon {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--coral);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  border: 3px solid var(--text-dark);
  box-shadow: 2px 2px 0px var(--text-dark);
}

.sidebar-logo-icon :deep(.el-icon) {
  font-size: 20px;
}

.sidebar-logo-text {
  color: var(--text-dark);
  font-weight: 900;
  font-size: 18px;
  letter-spacing: -0.5px;
}

.sidebar-nav {
  flex: 1;
  padding: 16px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: 16px;
  color: var(--text-medium);
  font-size: 14px;
  font-weight: 800;
  text-decoration: none;
  transition: all 0.25s ease;
  border: 3px solid transparent;
  border-left: 4px solid transparent;
}

.nav-item:hover {
  background: var(--cream);
  color: var(--text-dark);
  transform: translateX(4px);
}

.nav-item.active {
  background: var(--coral-pale);
  color: var(--coral);
  border-left: 4px solid var(--coral);
  font-weight: 800;
}

.nav-item :deep(.el-icon) {
  font-size: 20px;
}

.sidebar-deco {
  padding: 16px;
  display: flex;
  gap: 8px;
  justify-content: center;
  align-items: center;
  border-top: 2px dashed rgba(45, 52, 54, 0.08);
}

.main-area {
  margin-left: 240px;
  flex: 1;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  width: calc(100% - 240px);
}

.header {
  height: 64px;
  background: var(--card-bg);
  border-bottom: 3px solid var(--text-dark);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  position: sticky;
  top: 0;
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-title {
  font-size: 22px;
  font-weight: 900;
  color: var(--text-dark);
  letter-spacing: -0.5px;
}

.sidebar-toggle {
  display: none;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  border: 3px solid var(--text-dark);
  background: var(--cream);
  color: var(--text-dark);
  cursor: pointer;
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.sidebar-toggle:hover {
  background: var(--golden-pale);
  transform: scale(1.05);
}

.sidebar-toggle :deep(.el-icon) {
  font-size: 22px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-info {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  cursor: pointer;
  padding-right: 4px;
}

.user-name {
  font-size: 14px;
  font-weight: 800;
  color: var(--text-dark);
}

.user-role {
  font-size: 12px;
  color: var(--text-medium);
  font-weight: 700;
}

.user-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--coral);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 900;
  font-size: 16px;
  border: 3px solid var(--text-dark);
  box-shadow: 2px 2px 0px var(--text-dark);
  transition: all 0.2s ease;
  cursor: pointer;
}

.user-avatar:hover {
  transform: scale(1.1) rotate(-5deg);
}

.page-content {
  flex: 1;
  padding: 24px;
  background: var(--cream);
  position: relative;
  min-height: calc(100vh - 64px);
}

.sidebar-backdrop {
  display: none;
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  z-index: 49;
  opacity: 0;
  transition: opacity 0.3s ease;
}

.sidebar-backdrop.open {
  display: block;
  opacity: 1;
}

/* Mobile / Tablet */
@media (max-width: 1023px) {
  .sidebar {
    transform: translateX(-100%);
    width: 260px;
    z-index: 60;
    border-right: 3px solid var(--text-dark);
  }
  .sidebar.open {
    transform: translateX(0);
  }
  .main-area {
    margin-left: 0;
    width: 100%;
  }
  .sidebar-toggle {
    display: inline-flex;
  }
  .sidebar-backdrop.open {
    display: block;
  }
}

@media (max-width: 767px) {
  .header {
    height: 56px;
    padding: 0 16px;
  }
  .header-title {
    font-size: 18px;
  }
  .user-info {
    display: none;
  }
  .user-avatar {
    width: 36px;
    height: 36px;
    font-size: 14px;
  }
  .page-content {
    padding: 16px;
  }
}

@media (min-width: 1024px) {
  .sidebar {
    transform: translateX(0) !important;
  }
  .sidebar-backdrop {
    display: none !important;
  }
}
</style>
