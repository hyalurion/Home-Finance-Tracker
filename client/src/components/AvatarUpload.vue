<template>
  <div class="avatar-upload-container">
    <!-- 头像预览 -->
    <div class="avatar-preview-container">
      <div class="avatar-preview" @click="triggerFileInput">
        <img v-if="avatarUrl" :src="avatarUrl" alt="Avatar" class="avatar-image" />
        <div v-else class="avatar-placeholder">
          <span>{{ computedPlaceholder }}</span>
        </div>
      </div>
      <input
        type="file"
        ref="fileInput"
        accept="image/*"
        style="display: none"
        @change="handleFileChange"
      />
    </div>

    <!-- 裁剪弹窗 -->
    <transition name="cropper-fade">
      <div v-if="showCropper" class="cropper-overlay" @click="closeCropper">
        <div class="cropper-container" @click.stop>
        <div class="cropper-header">
          <h3>{{ $t('avatar.cropTitle') }}</h3>
          <button class="close-button" @click="closeCropper">&times;</button>
        </div>
        
        <div class="cropper-content">
          <canvas ref="canvas" class="cropper-canvas"></canvas>
          <input
            type="range"
            v-model="zoomLevel"
            min="1"
            max="3"
            step="0.1"
            class="zoom-slider"
          />
        </div>

        <div class="cropper-footer">
          <button class="cancel-button" @click="closeCropper">{{ $t('avatar.cancel') }}</button>
          <button class="confirm-button" @click="confirmCrop">{{ $t('avatar.confirm') }}</button>
        </div>
      </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, watch, computed, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { updateUserAvatar } from '@/api/membership'

const { t } = useI18n()

// 定义组件属性
const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  placeholder: {
    type: String,
    default: 'Click to upload avatar'
  },
  maxSize: {
    type: Number,
    default: 10 // MB
  },
  size: {
    type: Number,
    default: 200 // 头像尺寸
  },
  username: {
    type: String,
    required: true
  }
})

// 定义组件事件
const emit = defineEmits(['update:modelValue', 'avatar-uploaded'])

// 组件状态
const avatarUrl = ref(props.modelValue)
const showCropper = ref(false)
const fileInput = ref(null)
const canvas = ref(null)
const context = ref(null)
const currentImage = ref(null)
const zoomLevel = ref(1)
const imagePosition = ref({ x: 0, y: 0 })
const isDragging = ref(false)
const lastMousePos = ref({ x: 0, y: 0 })

// 动态计算 placeholder，支持国际化
const computedPlaceholder = computed(() => {
  return props.placeholder || t('avatar.placeholder')
})

// 触发文件选择
const triggerFileInput = () => {
  // 重置文件输入框的值，确保每次选择相同文件也能触发change事件
  if (fileInput.value) {
    fileInput.value.value = ''
  }
  fileInput.value?.click()
}

// 处理文件选择
const handleFileChange = (event) => {
  const file = event.target.files[0]
  if (!file) return

  // 检查文件大小
  if (file.size > props.maxSize * 1024 * 1024) {
    alert(t('avatar.error.sizeLimit', { maxSize: props.maxSize }))
    return
  }

  // 检查文件类型
  if (!file.type.startsWith('image/')) {
    alert(t('avatar.error.invalidType'))
    return
  }

  // 读取文件并显示裁剪界面
  const reader = new FileReader()
  reader.onload = (e) => {
    const img = new Image()
    img.onload = () => {
      console.log('Image loaded:', img.width, 'x', img.height)
      currentImage.value = img
      showCropper.value = true
      
      // 延迟执行，确保DOM已经更新
      nextTick(() => {
        initCanvas()
        drawImage()
        console.log('Image drawn on canvas:', canvas.value?.width, 'x', canvas.value?.height)
      })
    }
    img.src = e.target.result
  }
  reader.readAsDataURL(file)
}

// 初始化画布
const initCanvas = () => {
  if (!canvas.value) return

  const ctx = canvas.value.getContext('2d')
  context.value = ctx

  // 确保画布大小正确
  const container = canvas.value.parentElement
  if (container) {
    canvas.value.width = container.clientWidth
    canvas.value.height = container.clientHeight
  } else {
    canvas.value.width = props.size
    canvas.value.height = props.size
  }

  // 清空画布
  ctx.clearRect(0, 0, canvas.value.width, canvas.value.height)

  // 添加鼠标事件处理
  canvas.value.addEventListener('mousedown', handleMouseDown)
  canvas.value.addEventListener('mousemove', handleMouseMove)
  canvas.value.addEventListener('mouseup', handleMouseUp)
  canvas.value.addEventListener('mouseleave', handleMouseUp)
}

// 绘制图像
const drawImage = () => {
  if (!canvas.value || !context.value || !currentImage.value) {
    console.log('Drawing skipped:', !canvas.value ? 'no canvas' : !context.value ? 'no context' : 'no image')
    return
  }

  const ctx = context.value
  const img = currentImage.value
  const canvasSize = canvas.value.width

  // 清空画布
  ctx.clearRect(0, 0, canvasSize, canvasSize)

  // 计算图像显示大小
  const imgRatio = img.width / img.height
  let displayWidth, displayHeight

  // 确保图像至少能填满画布
  if (imgRatio > 1) {
    // 宽图
    displayHeight = canvasSize
    displayWidth = displayHeight * imgRatio
  } else {
    // 高图
    displayWidth = canvasSize
    displayHeight = displayWidth / imgRatio
  }

  // 应用缩放
  displayWidth *= zoomLevel.value
  displayHeight *= zoomLevel.value

  // 计算绘制位置（居中显示）
  const x = (canvasSize - displayWidth) / 2 + imagePosition.value.x
  const y = (canvasSize - displayHeight) / 2 + imagePosition.value.y

  console.log('Drawing image:', {
    imgWidth: img.width,
    imgHeight: img.height,
    canvasSize: canvasSize,
    displayWidth: displayWidth,
    displayHeight: displayHeight,
    x: x,
    y: y,
    zoom: zoomLevel.value
  })

  // 绘制图像
  ctx.drawImage(img, x, y, displayWidth, displayHeight)

  // 绘制裁剪框
  ctx.save()
  ctx.globalCompositeOperation = 'destination-in'
  ctx.beginPath()
  ctx.arc(canvasSize / 2, canvasSize / 2, canvasSize / 2, 0, Math.PI * 2)
  ctx.fill()
  ctx.restore()
}

// 鼠标事件处理
const handleMouseDown = (e) => {
  isDragging.value = true
  lastMousePos.value = {
    x: e.offsetX,
    y: e.offsetY
  }
}

const handleMouseMove = (e) => {
  if (!isDragging.value) return

  const deltaX = e.offsetX - lastMousePos.value.x
  const deltaY = e.offsetY - lastMousePos.value.y

  imagePosition.value = {
    x: imagePosition.value.x + deltaX,
    y: imagePosition.value.y + deltaY
  }

  lastMousePos.value = {
    x: e.offsetX,
    y: e.offsetY
  }

  drawImage()
}

const handleMouseUp = () => {
  isDragging.value = false
}

// 关闭裁剪窗口
const closeCropper = () => {
  showCropper.value = false
  zoomLevel.value = 1
  imagePosition.value = { x: 0, y: 0 }
  currentImage.value = null
  
  // 移除鼠标事件监听
  if (canvas.value) {
    canvas.value.removeEventListener('mousedown', handleMouseDown)
    canvas.value.removeEventListener('mousemove', handleMouseMove)
    canvas.value.removeEventListener('mouseup', handleMouseUp)
    canvas.value.removeEventListener('mouseleave', handleMouseUp)
  }
}

// 确认裁剪
const confirmCrop = async () => {
  if (!canvas.value || !props.username) return

  // 获取裁剪后的图像数据URL
  const dataUrl = canvas.value.toDataURL('image/png', 0.8)
  
  try {
    // 上传头像到后端
    await updateUserAvatar(props.username, dataUrl)
    
    // 更新头像URL
    avatarUrl.value = dataUrl
    
    // 通知父组件
    emit('update:modelValue', dataUrl)
    emit('avatar-uploaded', dataUrl)
    
    // 关闭裁剪窗口
    closeCropper()
  } catch (error) {
    console.error('上传头像失败:', error)
    alert(t('avatar.error.uploadFailed'))
  }
}

// 监听modelValue变化
const updateAvatarUrl = (newVal) => {
  avatarUrl.value = newVal
}

watch(
  () => props.modelValue,
  (newVal) => {
    updateAvatarUrl(newVal)
  }
)

// 监听缩放变化，重新绘制图像
watch(
  () => zoomLevel.value,
  () => {
    drawImage()
  }
)

// 监听showCropper变化，确保画布初始化完成后绘制图像
watch(
  () => showCropper.value,
  (newVal) => {
    if (newVal && currentImage.value) {
      // 确保画布已经初始化
      initCanvas()
      drawImage()
    }
  }
)
</script>

<style scoped>
.avatar-upload-container {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.avatar-preview-container {
  position: relative;
  cursor: pointer;
}

.avatar-preview {
  position: relative;
  width: 100px;
  height: 100px;
}

.avatar-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 50%;
}

.avatar-placeholder {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: rgba(240, 240, 240, 0.2);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 2px dashed rgba(217, 217, 217, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  font-size: 14px;
  transition: all 0.3s ease;
}

.avatar-placeholder:hover {
  background: rgba(240, 240, 240, 0.3);
  border-color: rgba(64, 158, 255, 0.5);
}

/* 裁剪弹窗样式 */
.cropper-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

/* 弹窗过渡动画 */
.cropper-fade-enter-active,
.cropper-fade-leave-active {
  transition: opacity 0.3s ease, visibility 0.3s ease;
}

.cropper-fade-enter-from,
.cropper-fade-leave-to {
  opacity: 0;
  visibility: hidden;
}

.cropper-container {
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(15px);
  -webkit-backdrop-filter: blur(15px);
  border-radius: 12px;
  width: 90%;
  max-width: 500px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.18);
  transform: scale(0.9);
}

/* 容器缩放动画 */
.cropper-fade-enter-active .cropper-container,
.cropper-fade-leave-active .cropper-container {
  transition: transform 0.3s ease;
}

.cropper-fade-enter-from .cropper-container,
.cropper-fade-leave-to .cropper-container {
  transform: scale(0.9);
}

.cropper-fade-enter-to .cropper-container,
.cropper-fade-leave-from .cropper-container {
  transform: scale(1);
}

.cropper-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.18);
}

.cropper-header h3 {
  margin: 0;
  font-size: 18px;
  color: #303133;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.close-button {
  background: rgba(255, 255, 255, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.18);
  font-size: 24px;
  color: #909399;
  cursor: pointer;
  padding: 0;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: all 0.3s ease;
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}

.close-button:hover {
  background: rgba(255, 255, 255, 0.3);
  color: #303133;
  transform: rotate(90deg);
}

.cropper-content {
  padding: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.cropper-canvas {
  width: 100%;
  max-width: 400px;
  height: 400px;
  object-fit: contain;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  cursor: move;
  background: rgba(255, 255, 255, 0.9);
  display: block;
}

.zoom-slider {
  margin-top: 20px;
  width: 100%;
  height: 6px;
  border-radius: 3px;
  background: rgba(255, 255, 255, 0.3);
  outline: none;
  -webkit-appearance: none;
  appearance: none;
}

.zoom-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.8);
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
}

.zoom-slider::-moz-range-thumb {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.8);
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
  border: none;
}

.cropper-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.18);
}

.cancel-button, .confirm-button {
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  border: 1px solid rgba(255, 255, 255, 0.18);
  transition: all 0.3s ease;
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  font-weight: 500;
}

.cancel-button {
  background: rgba(255, 255, 255, 0.2);
  color: #606266;
}

.cancel-button:hover {
  background: rgba(255, 255, 255, 0.3);
  color: #303133;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.confirm-button {
  background: rgba(64, 158, 255, 0.8);
  color: white;
  border-color: rgba(64, 158, 255, 0.5);
}

.confirm-button:hover {
  background: rgba(102, 177, 255, 0.8);
  border-color: rgba(102, 177, 255, 0.5);
  transform: translateY(-1px);
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.3);
}

/* 深色模式支持 */
@media (prefers-color-scheme: dark) {
  .cropper-container {
    background: rgba(32, 32, 32, 0.8);
    border-color: rgba(255, 255, 255, 0.08);
  }

  .cropper-header h3 {
    color: #e0e0e0;
    text-shadow: 0 1px 2px rgba(0, 0, 0, 0.5);
  }

  .avatar-placeholder {
    background: rgba(48, 48, 48, 0.8);
    border-color: rgba(64, 64, 64, 0.8);
    color: #a0a0a0;
  }

  .cropper-header,
  .cropper-footer {
    border-color: rgba(255, 255, 255, 0.08);
  }

  .close-button {
    background: rgba(64, 64, 64, 0.8);
    border-color: rgba(255, 255, 255, 0.08);
    color: #a0a0a0;
  }

  .close-button:hover {
    background: rgba(80, 80, 80, 0.8);
    color: #e0e0e0;
  }

  .cancel-button {
    background: rgba(64, 64, 64, 0.8);
    color: #e0e0e0;
    border-color: rgba(255, 255, 255, 0.08);
  }

  .cancel-button:hover {
    background: rgba(80, 80, 80, 0.8);
    color: #fff;
  }

  .confirm-button {
    background: rgba(64, 158, 255, 0.6);
    border-color: rgba(64, 158, 255, 0.3);
  }

  .confirm-button:hover {
    background: rgba(102, 177, 255, 0.6);
    border-color: rgba(102, 177, 255, 0.4);
  }

  .cropper-canvas {
    background: rgba(32, 32, 32, 0.9);
  }

  .zoom-slider {
    background: rgba(64, 64, 64, 0.8);
  }

  .zoom-slider::-webkit-slider-thumb {
    background: rgba(128, 128, 128, 0.8);
  }

  .zoom-slider::-moz-range-thumb {
    background: rgba(128, 128, 128, 0.8);
  }
}
</style>
