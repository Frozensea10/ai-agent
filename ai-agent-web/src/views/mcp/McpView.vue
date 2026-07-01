<template>
  <div class="mcp-container">
    <div class="page-header">
      <div class="header-text">
        <h1 class="page-title">MCP 工具</h1>
        <p class="page-subtitle">连接外部工具，扩展 AI 能力边界</p>
      </div>
      <button class="btn-pill btn-primary-pill add-btn" @click="resetForm(); showAddServer = true">
        <el-icon><Plus /></el-icon> 添加 MCP Server
      </button>
    </div>

    <el-row :gutter="20">
      <el-col :xs="24" :lg="16">
        <div class="hand-card section-card">
          <div class="card-title">
            <span class="title-dot" style="background: var(--teal)"></span>
            <h3>工具列表</h3>
          </div>
          <el-table :data="tools" v-loading="loading" class="cute-table">
            <el-table-column prop="toolCode" label="工具编码" width="180" />
            <el-table-column prop="toolName" label="工具名称" width="150" />
            <el-table-column prop="description" label="描述" show-overflow-tooltip />
            <el-table-column prop="toolType" label="类型" width="100">
              <template #default="{ row }">
                <span class="hand-tag small" :style="row.toolType === 'built_in' ? { background: 'var(--mint)' } : { background: 'var(--lavender)' }">
                  {{ row.toolType === 'built_in' ? '内置' : 'MCP' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="source" label="来源" width="120" />
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <button class="btn-pill btn-primary-pill" style="padding: 6px 14px; font-size: 12px" @click="openExecuteDialog(row)">
                  执行
                </button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>

      <el-col :xs="24" :lg="8">
        <div class="hand-card section-card">
          <div class="card-title">
            <span class="title-dot" style="background: var(--coral)"></span>
            <h3>MCP Server</h3>
            <button class="refresh-btn" @click="loadConnectionStatus">
              <el-icon><Refresh /></el-icon>
            </button>
          </div>

          <div class="server-list">
            <div v-for="server in servers" :key="server.id" class="server-card" :class="getServerStatusClass(server)">
              <div class="server-status-dot"></div>
              <div class="server-body">
                <div class="server-name">
                  {{ server.serverName }}
                  <span v-if="connectionStatus[server.serverName]" class="hand-tag tiny" :style="getConnectionTagStyle(server)">
                    {{ connectionStatus[server.serverName] === 'connected' ? '已连接' : '未连接' }}
                  </span>
                </div>
                <div class="server-meta">
                  <span class="hand-tag tiny" style="background: var(--cream)">{{ server.serverType }}</span>
                  <span class="hand-tag tiny" :style="server.status === 'active' ? { background: 'var(--mint)' } : { background: 'var(--coral-light)' }">
                    {{ server.status === 'active' ? '启用' : '禁用' }}
                  </span>
                </div>
                <div class="server-actions">
                  <button class="action-link" @click="handleEditServer(server)">编辑</button>
                  <button class="action-link" @click="handleToggleStatus(server)">
                    {{ server.status === 'active' ? '禁用' : '启用' }}
                  </button>
                  <button class="action-link danger" @click="handleDeleteServer(server.id!)">删除</button>
                </div>
              </div>
            </div>
          </div>

          <div v-if="servers.length === 0" class="empty-state-small">
            <el-icon><Tools /></el-icon>
            <span>暂无 MCP Server</span>
          </div>
        </div>
      </el-col>
    </el-row>

    <img src="/assets/cute-rocket.jpg" class="deco-img deco-rocket" alt="rocket" />

    <!-- 添加/编辑 MCP Server 对话框 -->
    <el-dialog v-model="showAddServer" :title="isEditing ? '编辑 MCP Server' : '添加 MCP Server'" width="520px" class="cute-dialog">
      <el-form :model="serverForm" label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="serverForm.serverName" placeholder="Server 名称" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="serverForm.serverType" placeholder="选择类型" style="width: 100%">
            <el-option label="HTTP (SSE)" value="http" />
            <el-option label="Stdio" value="stdio" />
          </el-select>
        </el-form-item>
        <el-form-item label="配置" required>
          <el-input
            v-model="serverForm.transportConfig"
            type="textarea"
            :rows="6"
            placeholder='HTTP 示例: {"baseUrl": "http://localhost:3000"}&#10;Stdio 示例: {"command": "npx", "args": ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"]}'
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddServer = false">取消</el-button>
        <el-button type="primary" @click="handleSaveServer" :loading="submitting">
          {{ isEditing ? '更新' : '确认' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 执行工具对话框 -->
    <el-dialog v-model="showExecute" :title="`执行工具: ${currentTool?.toolName}`" width="600px" class="cute-dialog">
      <el-form v-if="currentTool?.configSchema" label-width="100px">
        <el-form-item
          v-for="(prop, key) in currentTool.configSchema.properties"
          :key="key"
          :label="prop.description || key"
          :required="currentTool.configSchema.required?.includes(key)"
        >
          <el-input
            v-if="prop.type === 'string'"
            v-model="executeParams[key]"
            :placeholder="prop.description"
            :type="isCodeKey(key) ? 'textarea' : 'text'"
            :rows="isCodeKey(key) ? 6 : 1"
          />
          <el-input-number
            v-else-if="prop.type === 'number' || prop.type === 'integer'"
            v-model="executeParams[key]"
          />
          <el-switch
            v-else-if="prop.type === 'boolean'"
            v-model="executeParams[key]"
          />
        </el-form-item>
      </el-form>
      <div v-else class="empty-state-small">
        <el-icon><Tools /></el-icon>
        <span>该工具无需参数</span>
      </div>

      <div v-if="executeResult" class="execute-result">
        <el-divider />
        <div class="result-header">
          <span>执行结果</span>
          <span class="hand-tag tiny" :style="executeResult.success ? { background: 'var(--mint)' } : { background: 'var(--coral)' }">
            {{ executeResult.success ? '成功' : '失败' }}
          </span>
          <span v-if="executeResult.executeTimeMs" class="execute-time">
            {{ executeResult.executeTimeMs }}ms
          </span>
        </div>
        <pre v-if="executeResult.data" class="result-content">{{ formatResult(executeResult.data) }}</pre>
        <el-alert v-if="executeResult.errorMessage" :title="executeResult.errorMessage" type="error" show-icon />
      </div>

      <template #footer>
        <el-button @click="showExecute = false">关闭</el-button>
        <el-button type="primary" @click="handleExecute" :loading="executing">
          执行
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Tools } from '@element-plus/icons-vue'
import {
  listTools,
  listMcpServers,
  createMcpServer,
  updateMcpServer,
  updateMcpServerStatus,
  deleteMcpServer,
  getServerConnectionStatus,
  executeTool,
  type ToolInfo,
  type McpServer,
  type ToolExecuteResult,
} from '@/api/mcp'

const loading = ref(false)
const tools = ref<ToolInfo[]>([])
const servers = ref<McpServer[]>([])
const connectionStatus = ref<Record<string, string>>({})
const showAddServer = ref(false)
const showExecute = ref(false)
const submitting = ref(false)
const executing = ref(false)
const isEditing = ref(false)
const editingId = ref<number | null>(null)
const currentTool = ref<ToolInfo | null>(null)
const executeParams = ref<Record<string, any>>({})
const executeResult = ref<ToolExecuteResult | null>(null)

const serverForm = ref<McpServer>({
  serverName: '',
  serverType: 'http',
  transportConfig: '',
  status: 'active',
})

const loadData = async () => {
  loading.value = true
  try {
    const [toolsRes, serversRes] = await Promise.all([
      listTools(),
      listMcpServers(),
    ])
    if (toolsRes.code === 200) {
      tools.value = toolsRes.data || []
    }
    if (serversRes.code === 200) {
      servers.value = serversRes.data || []
    }
    await loadConnectionStatus()
  } catch (error) {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

const loadConnectionStatus = async () => {
  try {
      const res = await getServerConnectionStatus()
      if (res.code === 200) {
        connectionStatus.value = res.data || {}
      }
    } catch (error) {
      console.warn('获取连接状态失败', error)
    }
}

const getServerStatusClass = (server: McpServer) => {
  const isConnected = connectionStatus.value[server.serverName] === 'connected'
  if (server.status !== 'active') return 'inactive'
  return isConnected ? 'connected' : 'disconnected'
}

const getConnectionTagStyle = (server: McpServer) => {
  const isConnected = connectionStatus.value[server.serverName] === 'connected'
  return { background: isConnected ? 'var(--mint)' : 'var(--coral-light)' }
}

const resetForm = () => {
  serverForm.value = {
    serverName: '',
    serverType: 'http',
    transportConfig: '',
    status: 'active',
  }
  isEditing.value = false
  editingId.value = null
}

const handleSaveServer = async () => {
  if (!serverForm.value.serverName || !serverForm.value.transportConfig) {
    ElMessage.warning('请填写完整信息')
    return
  }

  try {
    JSON.parse(serverForm.value.transportConfig)
  } catch (e) {
    ElMessage.warning('配置必须是有效的 JSON 格式')
    return
  }

  submitting.value = true
  try {
    let res
    if (isEditing.value && editingId.value) {
      res = await updateMcpServer(editingId.value, serverForm.value)
    } else {
      res = await createMcpServer(serverForm.value)
    }
    if (res.code === 200) {
      ElMessage.success(isEditing.value ? '更新成功' : '添加成功')
      showAddServer.value = false
      resetForm()
      loadData()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (error) {
    ElMessage.error('操作失败')
  } finally {
    submitting.value = false
  }
}

const handleEditServer = (server: McpServer) => {
  isEditing.value = true
  editingId.value = server.id || null
  serverForm.value = {
    serverName: server.serverName,
    serverType: server.serverType,
    transportConfig: server.transportConfig,
    status: server.status,
  }
  showAddServer.value = true
}

const handleToggleStatus = async (server: McpServer) => {
  const newStatus = server.status === 'active' ? 'inactive' : 'active'
  try {
    const res = await updateMcpServerStatus(server.id!, newStatus)
    if (res.code === 200) {
      ElMessage.success(newStatus === 'active' ? '已启用' : '已禁用')
      loadData()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const handleDeleteServer = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定删除该 MCP Server 吗？', '提示', {
      type: 'warning',
    })
    const res = await deleteMcpServer(id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      loadData()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch (error) {
    // 取消删除
  }
}

const openExecuteDialog = (tool: ToolInfo) => {
  currentTool.value = tool
  executeParams.value = {}
  executeResult.value = null
  showExecute.value = true
}

const handleExecute = async () => {
  if (!currentTool.value) return
  executing.value = true
  try {
    const res = await executeTool(currentTool.value.toolCode, executeParams.value)
    if (res.code === 200) {
      executeResult.value = res.data
      if (res.data?.success) {
        ElMessage.success('执行成功')
      } else {
        ElMessage.warning('执行失败: ' + res.data?.errorMessage)
      }
    } else {
      ElMessage.error(res.message || '执行失败')
    }
  } catch (error) {
    ElMessage.error('执行失败')
  } finally {
    executing.value = false
  }
}

const isCodeKey = (key: string | number) => key === 'code'

const formatResult = (data: any) => {
  if (typeof data === 'string') return data
  return JSON.stringify(data, null, 2)
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.mcp-container {
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

.add-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 22px;
  font-size: 15px;
}

.section-card {
  padding: 24px;
  margin-bottom: 20px;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
}

.card-title h3 {
  margin: 0;
  font-size: 20px;
  font-weight: 800;
  color: var(--text-dark);
  flex: 1;
}

.title-dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid var(--text-dark);
}

.refresh-btn {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: 2px solid var(--text-dark);
  background: var(--cream);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-dark);
  transition: transform 0.3s ease;
}

.refresh-btn:hover {
  transform: rotate(180deg);
}

.server-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.server-card {
  display: flex;
  gap: 12px;
  padding: 16px;
  border-radius: 16px;
  background: var(--card-bg);
  border: 3px solid var(--text-dark);
  box-shadow: 4px 4px 0 var(--text-dark);
  transition: transform 0.2s ease;
  position: relative;
  overflow: hidden;
}

.server-card:hover {
  transform: translateY(-3px);
}

.server-card.connected::before,
.server-card.disconnected::before,
.server-card.inactive::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 6px;
}

.server-card.connected::before {
  background: var(--mint);
}

.server-card.disconnected::before {
  background: var(--coral);
}

.server-card.inactive::before {
  background: var(--golden);
}

.server-status-dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid var(--text-dark);
  flex-shrink: 0;
  margin-top: 4px;
}

.server-card.connected .server-status-dot {
  background: var(--mint);
}

.server-card.disconnected .server-status-dot {
  background: var(--coral);
}

.server-card.inactive .server-status-dot {
  background: var(--golden);
}

.server-body {
  flex: 1;
  min-width: 0;
}

.server-name {
  font-weight: 800;
  font-size: 16px;
  color: var(--text-dark);
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.server-meta {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.server-actions {
  display: flex;
  gap: 12px;
}

.action-link {
  background: none;
  border: none;
  padding: 0;
  font-size: 13px;
  font-weight: 700;
  color: var(--text-medium);
  cursor: pointer;
  text-decoration: underline;
  text-decoration-style: wavy;
  text-underline-offset: 3px;
  font-family: 'Nunito', sans-serif;
}

.action-link:hover {
  color: var(--coral);
}

.action-link.danger:hover {
  color: var(--coral);
}

.hand-tag {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 999px;
  border: 2px solid var(--text-dark);
  color: var(--text-dark);
  font-weight: 700;
  font-size: 13px;
  font-family: 'Nunito', sans-serif;
}

.hand-tag.small {
  padding: 4px 10px;
  font-size: 12px;
}

.hand-tag.tiny {
  padding: 2px 8px;
  font-size: 11px;
  border-width: 2px;
}

.empty-state-small {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  color: var(--text-medium);
  gap: 10px;
  font-size: 14px;
  font-weight: 700;
}

.empty-state-small .el-icon {
  font-size: 32px;
}

.execute-result {
  margin-top: 16px;
}

.result-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
  font-weight: 800;
  color: var(--text-dark);
}

.execute-time {
  color: var(--text-medium);
  font-size: 12px;
  font-weight: 600;
}

.result-content {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 12px;
  max-height: 300px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: monospace;
  font-size: 13px;
  border: 2px solid var(--text-dark);
}

.deco-img {
  position: absolute;
  width: 80px;
  height: 80px;
  object-fit: contain;
  pointer-events: none;
}

.deco-rocket {
  bottom: 24px;
  right: 24px;
  animation: float 4s ease-in-out infinite;
}

:deep(.cute-table) {
  --el-table-border-color: var(--text-dark);
  --el-table-header-bg-color: var(--cream);
  --el-table-row-hover-bg-color: var(--cream);
  border: 3px solid var(--text-dark);
  border-radius: 16px;
  overflow: hidden;
}

:deep(.cute-table th) {
  font-weight: 800;
  color: var(--text-dark);
}

:deep(.cute-table td) {
  font-weight: 600;
  color: var(--text-dark);
}

@media (max-width: 767px) {
  .mcp-container {
    padding: 16px;
  }

  .page-title {
    font-size: 24px;
  }

  .deco-rocket {
    display: none;
  }
}
</style>
