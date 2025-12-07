<template>
  <div :class="['glass-upload-wrapper', { 'dark-theme': darkTheme }]">
    <div 
      class="glass-upload-dropzone"
      @click="triggerFileInput"
      @dragover.prevent
      @drop="handleDrop"
    >
      <div class="glass-upload-icon">
        <slot name="icon">
          <svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
            <polyline points="17 8 12 3 7 8"></polyline>
            <line x1="12" y1="3" x2="12" y2="15"></line>
          </svg>
        </slot>
      </div>
      <div class="glass-upload-text">
        <slot name="text">点击上传</slot>
      </div>
      <input 
        ref="fileInput" 
        type="file" 
        :multiple="multiple" 
        class="glass-upload-input" 
        @change="handleFileChange"
      >
    </div>
    
    <!-- 文件列表 -->
    <div v-if="files.length > 0" class="glass-upload-file-list">
      <div 
        v-for="(file, index) in files" 
        :key="file.uid || index" 
        class="glass-upload-file-item"
      >
        <div class="glass-upload-file-info">
          <div class="glass-upload-file-name">{{ file.name }}</div>
          <div class="glass-upload-file-size">{{ formatFileSize(file.size) }}</div>
        </div>
        <button class="glass-upload-file-remove" @click="removeFile(index)">
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  multiple: {
    type: Boolean,
    default: false
  },
  darkTheme: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['file-change', 'file-remove'])

const fileInput = ref(null)
const files = ref([])

// 触发文件选择
const triggerFileInput = () => {
  fileInput.value?.click()
}

// 处理文件选择
const handleFileChange = (event) => {
  const selectedFiles = Array.from(event.target.files)
  if (selectedFiles.length > 0) {
    selectedFiles.forEach(file => {
      files.value.push({
        uid: Date.now() + Math.random().toString(36).substr(2, 9),
        name: file.name,
        size: file.size,
        raw: file
      })
    })
    emit('file-change', files.value)
    // 清空input以便再次选择相同文件
    event.target.value = ''
  }
}

// 处理拖放
const handleDrop = (event) => {
  event.preventDefault()
  const droppedFiles = Array.from(event.dataTransfer.files)
  if (droppedFiles.length > 0) {
    droppedFiles.forEach(file => {
      files.value.push({
        uid: Date.now() + Math.random().toString(36).substr(2, 9),
        name: file.name,
        size: file.size,
        raw: file
      })
    })
    emit('file-change', files.value)
  }
}

// 移除文件
const removeFile = (index) => {
  files.value.splice(index, 1)
  emit('file-change', files.value)
  emit('file-remove', index)
}

// 格式化文件大小
const formatFileSize = (size) => {
  if (size < 1024) {
    return size + ' B'
  } else if (size < 1024 * 1024) {
    return (size / 1024).toFixed(2) + ' KB'
  } else {
    return (size / (1024 * 1024)).toFixed(2) + ' MB'
  }
}
</script>

<style scoped>
.glass-upload-wrapper {
  width: 100%;
}

.glass-upload-dropzone {
  position: relative;
  border-radius: 8px;
  padding: 32px;
  backdrop-filter: blur(10px);
  border: 2px dashed rgba(255, 255, 255, 0.3);
  box-shadow: 0 4px 16px rgba(31, 38, 135, 0.1);
  background: rgba(255, 255, 255, 0.6);
  cursor: pointer;
  transition: all 0.3s ease;
  text-align: center;
}

.glass-upload-dropzone:hover {
  border-color: rgba(59, 130, 246, 0.5);
  background: rgba(255, 255, 255, 0.8);
  box-shadow: 0 8px 24px rgba(31, 38, 135, 0.15);
}

.glass-upload-icon {
  margin-bottom: 16px;
  color: #4a5568;
}

.glass-upload-text {
  color: #718096;
  font-size: 14px;
}

.glass-upload-input {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  opacity: 0;
  cursor: pointer;
}

.glass-upload-file-list {
  margin-top: 16px;
}

.glass-upload-file-item {
  position: relative;
  border-radius: 8px;
  padding: 12px;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.3);
  box-shadow: 0 4px 12px rgba(31, 38, 135, 0.08);
  background: rgba(255, 255, 255, 0.8);
  margin-bottom: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.glass-upload-file-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.glass-upload-file-name {
  color: #4a5568;
  font-size: 14px;
  font-weight: 500;
}

.glass-upload-file-size {
  color: #718096;
  font-size: 12px;
}

.glass-upload-file-remove {
  background: none;
  border: none;
  cursor: pointer;
  color: #e53e3e;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.glass-upload-file-remove:hover {
  background: rgba(229, 62, 62, 0.1);
}

/* Dark theme */
.glass-upload-wrapper.dark-theme .glass-upload-dropzone {
  background: rgba(26, 32, 44, 0.6);
  border-color: rgba(255, 255, 255, 0.15);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
}

.glass-upload-wrapper.dark-theme .glass-upload-dropzone:hover {
  border-color: rgba(59, 130, 246, 0.5);
  background: rgba(26, 32, 44, 0.8);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
}

.glass-upload-wrapper.dark-theme .glass-upload-icon {
  color: #cbd5e0;
}

.glass-upload-wrapper.dark-theme .glass-upload-text {
  color: #a0aec0;
}

.glass-upload-wrapper.dark-theme .glass-upload-file-item {
  background: rgba(26, 32, 44, 0.8);
  border-color: rgba(255, 255, 255, 0.15);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.glass-upload-wrapper.dark-theme .glass-upload-file-name {
  color: #e2e8f0;
}

.glass-upload-wrapper.dark-theme .glass-upload-file-size {
  color: #a0aec0;
}

.glass-upload-wrapper.dark-theme .glass-upload-file-remove {
  color: #fc8181;
}

.glass-upload-wrapper.dark-theme .glass-upload-file-remove:hover {
  background: rgba(252, 129, 129, 0.1);
}
</style>