<template>
  <div class="knowledge-view">
    <div class="page-header">
      <div class="header-text">
        <h1 class="page-title">知识库管理</h1>
        <p class="page-subtitle">整理你的知识，让 AI 更懂你</p>
      </div>
      <button class="btn-pill btn-primary-pill create-btn" @click="showCreateDialog = true">
        <el-icon><Plus /></el-icon> 创建知识库
      </button>
    </div>

    <div class="search-bar">
      <div class="hand-input search-input-wrap">
        <el-icon><Search /></el-icon>
        <el-input v-model="searchKeyword" placeholder="搜索知识库..." clearable />
      </div>
    </div>

    <el-row :gutter="20" class="kb-list">
      <el-col :xs="24" :sm="12" :lg="8" v-for="kb in filteredKnowledgeBases" :key="kb.id">
        <div class="hand-card kb-card" :style="{ '--accent': getPastelColor(kb.id) }">
          <div class="kb-card-header">
            <div class="kb-icon" :style="{ background: getPastelColor(kb.id) }">
              <el-icon><Collection /></el-icon>
            </div>
            <div class="kb-title-wrap">
              <div class="kb-title">{{ kb.kbName }}</div>
              <div class="kb-code">{{ kb.kbCode }}</div>
            </div>
            <el-dropdown @command="(cmd: string) => handleCommand(cmd, kb)" trigger="click">
              <div class="kb-more">
                <el-icon><More /></el-icon>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="upload">上传文档</el-dropdown-item>
                  <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
          <div class="kb-body">
            <div class="kb-desc">{{ kb.description || '暂无描述' }}</div>
            <div class="kb-meta">
              <span class="kb-meta-item">
                <el-icon><Document /></el-icon> {{ kb.documentCount }} 文档
              </span>
              <span class="kb-meta-item">
                <el-icon><Timer /></el-icon> {{ formatDate(kb.createdAt) }}
              </span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <div v-if="filteredKnowledgeBases.length === 0" class="empty-state">
      <div class="empty-icon">
        <el-icon><Collection /></el-icon>
      </div>
      <p>还没有知识库</p>
      <span>创建一个，开始积累智慧吧</span>
    </div>

    <img src="/assets/cute-rocket.jpg" class="deco-img deco-rocket" alt="rocket" />
    <img src="/assets/cute-cloud.jpg" class="deco-img deco-cloud" alt="cloud" />

    <!-- 创建知识库对话框 -->
    <el-dialog v-model="showCreateDialog" title="创建知识库" width="500px" class="cute-dialog">
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-width="100px">
        <el-form-item label="名称" prop="kbName">
          <el-input v-model="createForm.kbName" placeholder="知识库名称" />
        </el-form-item>
        <el-form-item label="编码" prop="kbCode">
          <el-input v-model="createForm.kbCode" placeholder="唯一编码，如: company-docs" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="createForm.description" type="textarea" :rows="3" placeholder="知识库描述" />
        </el-form-item>
        <el-form-item label="嵌入模型" prop="embeddingModel">
          <el-select v-model="createForm.embeddingModel" placeholder="选择嵌入模型" style="width: 100%">
            <el-option label="text-embedding-3-small" value="text-embedding-3-small" />
            <el-option label="text-embedding-3-large" value="text-embedding-3-large" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreate" :loading="creating">创建</el-button>
      </template>
    </el-dialog>

    <!-- 上传文档对话框 -->
    <el-dialog v-model="showUploadDialog" title="上传文档" width="500px" class="cute-dialog">
      <el-upload
        ref="uploadRef"
        drag
        :auto-upload="false"
        :on-change="handleFileChange"
        accept=".pdf,.docx,.doc,.txt,.md"
        :limit="1"
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">拖拽文件到此处或 <em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">支持 PDF、Word、TXT、Markdown 文件，最大 50MB</div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button type="primary" @click="handleUpload" :loading="uploading" :disabled="!selectedFile">
          上传并解析
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Document, Timer, More, UploadFilled, Collection, Search } from '@element-plus/icons-vue'
import type { FormInstance, FormRules, UploadFile, UploadInstance } from 'element-plus'
import { getKnowledgeBases, createKnowledgeBase, deleteKnowledgeBase, uploadDocument } from '@/api/knowledge'
import type { KnowledgeBase } from '@/types/knowledge'

const pastelColors = ['#E0F7FA', '#F8BBD0', '#FFF9C4', '#E8F5E9', '#F3E5F5', '#FFE0B2']

const getPastelColor = (id: number | string) => {
  const index = Number(id) % pastelColors.length
  return pastelColors[index]
}

const knowledgeBases = ref<KnowledgeBase[]>([])
const searchKeyword = ref('')
const showCreateDialog = ref(false)
const showUploadDialog = ref(false)
const creating = ref(false)
const uploading = ref(false)
const currentKb = ref<KnowledgeBase | null>(null)
const createFormRef = ref<FormInstance>()
const uploadRef = ref<UploadInstance>()
const selectedFile = ref<UploadFile | null>(null)

const createForm = reactive({
  kbName: '',
  kbCode: '',
  description: '',
  embeddingModel: 'text-embedding-3-small'
})

const createRules: FormRules = {
  kbName: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }],
  kbCode: [{ required: true, message: '请输入知识库编码', trigger: 'blur' }],
}

const filteredKnowledgeBases = computed(() => {
  if (!searchKeyword.value.trim()) return knowledgeBases.value
  const keyword = searchKeyword.value.toLowerCase()
  return knowledgeBases.value.filter(kb =>
    kb.kbName.toLowerCase().includes(keyword) ||
    kb.kbCode.toLowerCase().includes(keyword) ||
    (kb.description && kb.description.toLowerCase().includes(keyword))
  )
})

const loadKnowledgeBases = async () => {
  try {
    const res = await getKnowledgeBases()
    knowledgeBases.value = res.data
  } catch (error: any) {
    ElMessage.error(error.message || '加载知识库失败')
  }
}

const handleCreate = async () => {
  if (!createFormRef.value) return
  await createFormRef.value.validate(async (valid) => {
    if (valid) {
      creating.value = true
      try {
        await createKnowledgeBase(createForm)
        ElMessage.success('创建成功')
        showCreateDialog.value = false
        createFormRef.value?.resetFields()
        await loadKnowledgeBases()
      } catch (error: any) {
        ElMessage.error(error.message || '创建失败')
      } finally {
        creating.value = false
      }
    }
  })
}

const handleCommand = (command: string, kb: KnowledgeBase) => {
  if (command === 'upload') {
    currentKb.value = kb
    selectedFile.value = null
    showUploadDialog.value = true
  } else if (command === 'delete') {
    ElMessageBox.confirm('确定要删除该知识库吗？此操作不可恢复', '警告', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    }).then(async () => {
      try {
        await deleteKnowledgeBase(kb.id)
        ElMessage.success('删除成功')
        await loadKnowledgeBases()
      } catch (error: any) {
        ElMessage.error(error.message || '删除失败')
      }
    })
  }
}

const handleFileChange = (file: UploadFile) => {
  selectedFile.value = file
}

const handleUpload = async () => {
  if (!selectedFile.value || !currentKb.value) return
  const file = selectedFile.value.raw
  if (!file) return

  uploading.value = true
  try {
    await uploadDocument(currentKb.value.id, file)
    ElMessage.success('文档上传并解析成功')
    showUploadDialog.value = false
    selectedFile.value = null
    uploadRef.value?.clearFiles()
    await loadKnowledgeBases()
  } catch (error: any) {
    ElMessage.error(error.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

const formatDate = (date: string) => {
  return new Date(date).toLocaleDateString('zh-CN')
}

onMounted(() => {
  loadKnowledgeBases()
})
</script>

<style scoped>
.knowledge-view {
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
  max-width: 480px;
}

.search-input-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 16px;
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

.kb-list {
  margin-bottom: 20px;
}

.kb-card {
  padding: 20px;
  margin-bottom: 20px;
  border-left: 6px solid var(--accent, var(--coral));
  cursor: pointer;
  transition: transform 0.2s ease;
}

.kb-card:hover {
  transform: translateY(-4px);
}

.kb-card-header {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 14px;
}

.kb-icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-dark);
  font-size: 22px;
  border: 3px solid var(--text-dark);
  box-shadow: 3px 3px 0 var(--text-dark);
  flex-shrink: 0;
}

.kb-title-wrap {
  flex: 1;
  min-width: 0;
}

.kb-title {
  font-weight: 800;
  font-size: 16px;
  color: var(--text-dark);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-code {
  font-size: 12px;
  color: var(--text-medium);
  font-weight: 600;
  margin-top: 2px;
}

.kb-more {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--text-medium);
  transition: all 0.2s ease;
}

.kb-more:hover {
  background: var(--cream);
  color: var(--coral);
}

.kb-body {
  padding-top: 14px;
  border-top: 2px dashed var(--text-light);
}

.kb-desc {
  color: var(--text-medium);
  font-size: 14px;
  line-height: 1.5;
  margin-bottom: 12px;
  font-weight: 600;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.kb-meta {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.kb-meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-medium);
  font-weight: 700;
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
  background: var(--lavender);
  color: var(--text-dark);
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

.deco-rocket {
  top: 20px;
  right: 24px;
  animation: float 4s ease-in-out infinite;
}

.deco-cloud {
  bottom: 30px;
  left: 24px;
  animation: drift 6s ease-in-out infinite;
}

@media (max-width: 767px) {
  .knowledge-view {
    padding: 16px;
  }

  .page-title {
    font-size: 24px;
  }

  .deco-rocket,
  .deco-cloud {
    display: none;
  }
}
</style>
