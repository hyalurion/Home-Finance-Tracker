<template>
  <div class="custom-upload">
    <input
      ref="fileInput"
      type="file"
      :accept="accept"
      :multiple="multiple"
      :capture="capture"
      class="file-input"
      @change="handleFileChange"
      style="display: none;"
    />
    <slot :trigger-upload="triggerUpload"></slot>
  </div>
</template>

<script setup>
import { ref } from 'vue';

const props = defineProps({
  action: {
    type: String,
    required: true
  },
  accept: {
    type: String,
    default: ''
  },
  showFileList: {
    type: Boolean,
    default: true
  },
  multiple: {
    type: Boolean,
    default: false
  },
  capture: {
    type: String,
    default: null
  }
});

const emit = defineEmits(['success', 'error']);

const fileInput = ref(null);

const triggerUpload = (event, options = {}) => {
  // 只有当event存在时才调用preventDefault
  if (event && event.preventDefault) {
    event.preventDefault();
  }
  
  // 安卓设备特殊处理
  if (isAndroid()) {
    // 根据选项设置capture属性
    if (options.capture) {
      fileInput.value.capture = options.capture;
    } else {
      // 不设置capture属性，允许用户选择是拍照还是从相册选择
      fileInput.value.removeAttribute('capture');
    }
  }
  
  fileInput.value?.click();
};

// 检测是否为安卓设备
const isAndroid = () => {
  const userAgent = navigator.userAgent.toLowerCase();
  return userAgent.includes('android');
};

const handleFileChange = async (event) => {
  const files = Array.from(event.target.files);
  if (files.length === 0) return;

  // 安卓设备特殊处理
  if (isAndroid() && files.length > 1) {
    // 安卓设备多文件上传时的特殊处理
    console.log('安卓设备多文件上传处理');
  }

  // 为每个文件执行上传
  for (const file of files) {
    try {
      const formData = new FormData();
      formData.append('file', file);

      const response = await fetch(props.action, {
        method: 'POST',
        body: formData
      });

      if (response.ok) {
        const data = await response.json();
        emit('success', data, file);
      } else {
        const errorData = await response.json().catch(() => ({}));
        emit('error', {
          status: response.status,
          response: errorData,
          responseText: JSON.stringify(errorData)
        }, file);
      }
    } catch (error) {
      emit('error', {
        status: 0,
        response: null,
        responseText: error.message
      }, file);
    }
  }

  // 重置文件输入，以便可以重新选择相同的文件
  event.target.value = '';
};
</script>

<style scoped>
.custom-upload {
  display: inline-block;
}

.file-input {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
}
</style>
