<template>
  <div class="file-view">
    <div class="page-header">
      <div class="header-text">
        <h1 class="page-title">文件管理</h1>
        <p class="page-subtitle">上传、预览和管理你的文件资源</p>
      </div>
      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        :on-change="handleFileChange"
        :show-file-list="false"
        accept="*"
      >
        <button class="btn-pill btn-primary-pill create-btn" type="button">
          <el-icon><Upload /></el-icon> 上传文件
        </button>
      </el-upload>
    </div>

    <div class="file-list" v-loading="loading">
      <div class="file-list-header">
        <span class="file-col-name">文件名</span>
        <span class="file-col-size">大小</span>
        <span class="file-col-type">类型</span>
        <span class="file-col-actions">操作</span>
      </div>

      <div class="file-item" v-for="file in fileList" :key="file.objectName">
        <div class="file-col-name">
          <div class="file-icon">
            <el-icon><Document /></el-icon>
          </div>
          <div class="file-info">
            <div class="file-name" :title="file.objectName">{{ file.objectName }}</div>
            <div class="file-date">{{ formatDate(file.lastModified) }}</div>
          </div>
        </div>
        <div class="file-col-size">{{ formatSize(file.size) }}</div>
        <div class="file-col-type">{{ file.contentType || '未知' }}</div>
        <div class="file-col-actions">
          <el-button link type="primary" @click="handlePreview(file)">预览</el-button>
          <el-button link type="primary" @click="handleDownload(file)">下载</el-button>
          <el-button link type="danger" @click="handleDelete(file)">删除</el-button>
        </div>
      </div>

      <div v-if="fileList.length === 0 && !loading" class="empty-state">
        <div class="empty-icon">
          <el-icon><Folder /></el-icon>
        </div>
        <p>还没有文件</p>
        <span>点击右上角上传文件</span>
      </div>
    </div>

    <!-- 预览对话框 -->
    <el-dialog v-model="previewVisible" title="文件预览" width="70%" class="cute-dialog">
      <div class="preview-body">
        <img v-if="isImage" :src="previewUrl" class="preview-image" alt="preview" />
        <iframe v-else-if="isPdf" :src="previewUrl" class="preview-frame"></iframe>
        <div v-else class="preview-unsupported">
          <el-icon><Document /></el-icon>
          <p>该文件类型不支持在线预览</p>
          <el-button type="primary" @click="downloadCurrent">点击下载</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Document, Folder } from '@element-plus/icons-vue'
import type { UploadFile, UploadInstance } from 'element-plus'
import { listFiles, uploadFile, deleteFile, getPreviewUrl, downloadFile } from '@/api/file'
import type { FileRecord } from '@/types/file'

const loading = ref(false)
const fileList = ref<FileRecord[]>([])
const uploadRef = ref<UploadInstance>()
const selectedFile = ref<UploadFile | null>(null)

const previewVisible = ref(false)
const previewUrl = ref('')
const currentFile = ref<FileRecord | null>(null)

const isImage = computed(() => {
  if (!currentFile.value) return false
  return (currentFile.value.contentType || '').startsWith('image/')
})

const isPdf = computed(() => {
  if (!currentFile.value) return false
  return currentFile.value.contentType === 'application/pdf'
})

const loadFiles = async () => {
  loading.value = true
  try {
    const res = await listFiles()
    fileList.value = res.data || []
  } catch (error: any) {
    ElMessage.error(error.message || '加载文件列表失败')
  } finally {
    loading.value = false
  }
}

const handleFileChange = async (file: UploadFile) => {
  if (!file.raw) return
  selectedFile.value = file
  try {
    await uploadFile(file.raw)
    ElMessage.success('文件上传成功')
    selectedFile.value = null
    uploadRef.value?.clearFiles()
    await loadFiles()
  } catch (error: any) {
    ElMessage.error(error.message || '上传失败')
  }
}

const handleDelete = (file: FileRecord) => {
  ElMessageBox.confirm('确定要删除该文件吗？此操作不可恢复', '警告', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  }).then(async () => {
    try {
      await deleteFile(file.objectName)
      ElMessage.success('删除成功')
      await loadFiles()
    } catch (error: any) {
      ElMessage.error(error.message || '删除失败')
    }
  })
}

const handleDownload = async (file: FileRecord) => {
  try {
    const response = await downloadFile(file.objectName)
    if (!response.ok) {
      throw new Error('下载失败')
    }
    const blob = await response.blob()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = file.objectName
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  } catch (error: any) {
    ElMessage.error(error.message || '下载失败')
  }
}

const handlePreview = async (file: FileRecord) => {
  currentFile.value = file
  try {
    const res = await getPreviewUrl(file.objectName)
    previewUrl.value = res.data
    previewVisible.value = true
  } catch (error: any) {
    ElMessage.error(error.message || '生成预览链接失败')
  }
}

const downloadCurrent = () => {
  if (currentFile.value) {
    handleDownload(currentFile.value)
  }
}

const formatSize = (bytes?: number) => {
  if (bytes === undefined || bytes === null) return '-'
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

const formatDate = (date?: string) => {
  if (!date) return '-'
  return new Date(date).toLocaleString('zh-CN')
}

onMounted(() => {
  loadFiles()
})
</script>

<style scoped>
.file-view {
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
  cursor: pointer;
  border: none;
  background: var(--coral);
  color: white;
  border-radius: 999px;
  font-weight: 700;
  box-shadow: 3px 3px 0 var(--text-dark);
  transition: all 0.2s ease;
}

.create-btn:hover {
  transform: translateY(-2px);
  box-shadow: 4px 4px 0 var(--text-dark);
}

.file-list {
  background: var(--card-bg);
  border: 3px solid var(--text-dark);
  border-radius: 20px;
  box-shadow: 5px 5px 0 var(--text-dark);
  overflow: hidden;
}

.file-list-header {
  display: grid;
  grid-template-columns: 2fr 120px 140px 200px;
  gap: 16px;
  padding: 16px 20px;
  background: var(--coral-pale);
  border-bottom: 3px solid var(--text-dark);
  font-weight: 800;
  color: var(--text-dark);
  font-size: 14px;
}

.file-item {
  display: grid;
  grid-template-columns: 2fr 120px 140px 200px;
  gap: 16px;
  align-items: center;
  padding: 14px 20px;
  border-bottom: 2px dashed var(--text-light);
  transition: background 0.2s ease;
}

.file-item:last-child {
  border-bottom: none;
}

.file-item:hover {
  background: var(--cream);
}

.file-col-name {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.file-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: var(--lavender);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-dark);
  border: 3px solid var(--text-dark);
  flex-shrink: 0;
}

.file-info {
  min-width: 0;
}

.file-name {
  font-weight: 700;
  color: var(--text-dark);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-date {
  font-size: 12px;
  color: var(--text-medium);
  font-weight: 600;
  margin-top: 2px;
}

.file-col-size,
.file-col-type {
  color: var(--text-medium);
  font-weight: 600;
  font-size: 14px;
}

.file-col-actions {
  display: flex;
  gap: 8px;
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

.preview-body {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 300px;
  background: var(--cream);
  border-radius: 12px;
}

.preview-image {
  max-width: 100%;
  max-height: 60vh;
  border-radius: 12px;
}

.preview-frame {
  width: 100%;
  height: 60vh;
  border: none;
  border-radius: 12px;
}

.preview-unsupported {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  color: var(--text-medium);
  font-size: 16px;
}

@media (max-width: 767px) {
  .file-view {
    padding: 16px;
  }

  .page-title {
    font-size: 24px;
  }

  .file-list-header,
  .file-item {
    grid-template-columns: 1fr 80px 100px 120px;
    gap: 8px;
    padding: 12px 14px;
  }
}
</style>
