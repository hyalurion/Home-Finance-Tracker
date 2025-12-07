<template>
  <div class="custom-upload">
    <input
      ref="fileInput"
      type="file"
      :accept="accept"
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
  }
});

const emit = defineEmits(['success', 'error']);

const fileInput = ref(null);

const triggerUpload = (event) => {
  event.preventDefault();
  fileInput.value?.click();
};

const handleFileChange = async (event) => {
  const file = event.target.files[0];
  if (!file) return;

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
  } finally {
    // 重置文件输入，以便可以重新选择相同的文件
    event.target.value = '';
  }
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
