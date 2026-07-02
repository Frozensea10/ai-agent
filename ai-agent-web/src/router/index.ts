import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    name: 'Layout',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: { title: '首页' }
      },
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('@/views/chat/ChatView.vue'),
        meta: { title: '对话' }
      },
      {
        path: 'knowledge',
        name: 'Knowledge',
        component: () => import('@/views/knowledge/KnowledgeView.vue'),
        meta: { title: '知识库' }
      },
      {
        path: 'agent',
        name: 'Agent',
        component: () => import('@/views/agent/AgentView.vue'),
        meta: { title: 'Agent 配置' }
      },
      {
        path: 'mcp',
        name: 'Mcp',
        component: () => import('@/views/mcp/McpView.vue'),
        meta: { title: 'MCP 工具' }
      },
      {
        path: 'file',
        name: 'File',
        component: () => import('@/views/file/FileView.vue'),
        meta: { title: '文件管理' }
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/settings/SettingsView.vue'),
        meta: { title: '设置' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()
  
  if (!userStore.isLoggedIn && !to.meta.public) {
    next('/login')
  } else if (userStore.isLoggedIn && to.path === '/login') {
    next('/')
  } else {
    next()
  }
})

export default router
