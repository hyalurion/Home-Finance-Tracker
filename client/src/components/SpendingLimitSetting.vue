<template>
  <!-- 独立弹窗容器 -->
  <transition name="dialog-overlay">
    <div v-if="modelValue" class="custom-dialog-overlay" @click.self="closeDialog">
      <transition name="dialog-content">
        <div v-if="modelValue" class="custom-dialog settings-panel-dialog" :class="{ 'dark-theme': darkTheme }">
          <div class="dialog-header">
            <h3 class="dialog-title">{{ $t('spending.settings.title') }}</h3>
            <button class="dialog-close-btn" @click="closeDialog" aria-label="关闭">
              <FontAwesomeIcon icon="times" />
            </button>
          </div>
          
          <div class="dialog-body">
            <div class="spending-limit-setting">
              <MessageTip v-model:message="successMessage" type="success" />
              <MessageTip v-model:message="errorMessage" type="error" />
              <div class="setting-header">
                <h3 class="setting-title">{{ $t('spending.settings.on') }}</h3>
                <GlassSwitch
                  v-model="spendingStore.isLimitEnabled"
                  @change="handleToggleEnabled"
                  active-text=""
                  inactive-text=""
                  class="enable-switch"
                >
                  <template #active-text>{{ $t('spending.settings.enabled') }}</template>
                  <template #inactive-text>{{ $t('spending.settings.disabled') }}</template>
                </GlassSwitch>
              </div>

              <div class="setting-content" v-if="spendingStore.isLimitEnabled">
                <!-- 月度限制设置 -->
                <div class="setting-item">
                  <label class="setting-label">{{ $t('spending.settings.monthlyLimit') }}</label>
                  <GlassInputNumber
                    v-model="localLimit"
                    @change="handleLimitChange"
                    :min="0"
                    :max="999999"
                    :step="100"
                    :precision="2"
                    :placeholder="$t('spending.settings.enterLimit')"
                    class="limit-input"
                    size="large"
                    prefix="¥"
                  />
                </div>

                <!-- 警告阈值设置 -->
                <div class="setting-item">
                  <label class="setting-label">{{ $t('spending.settings.warningThreshold') }}</label>
                  <GlassInputNumber
                    v-model="thresholdPercentage"
                    @change="handleThresholdChange"
                    :min="1"
                    :max="100"
                    :step="1"
                    :precision="0"
                    :placeholder="$t('spending.settings.enterThreshold')"
                    class="threshold-input"
                    size="large"
                    suffix="%"
                  />
                </div>
              </div>

              <!-- 禁用状态说明 -->
              <div class="disabled-notice" v-else>
                <div class="notice-icon">
                  <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <circle cx="12" cy="12" r="10"></circle>
                    <line x1="12" y1="16" x2="12" y2="12"></line>
                    <line x1="12" y1="8" x2="12.01" y2="8"></line>
                  </svg>
                </div>
                <span>{{ $t('spending.settings.disabledNotice') }}</span>
              </div>
            </div>
          </div>
        </div>
      </transition>
    </div>
  </transition>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue';
import { useSpendingStore } from '../stores/spending.js';
import { useI18n } from 'vue-i18n';
import GlassSwitch from './GlassSwitch.vue';
import GlassInputNumber from './GlassInputNumber.vue';
import MessageTip from './MessageTip.vue';

const { t } = useI18n();
const spendingStore = useSpendingStore();

// Props
const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  darkTheme: {
    type: Boolean,
    default: false
  }
});

// Emits
const emit = defineEmits(['update:modelValue']);

// 本地状态
const localLimit = ref(0);
const thresholdPercentage = ref(80);
const successMessage = ref('');
const errorMessage = ref('');

// 方法
const handleToggleEnabled = (enabled) => {
  spendingStore.toggleLimitEnabled(enabled);
  if (enabled && spendingStore.monthlyLimit <= 0) {
    // 如果启用但没有设置限制，提示用户设置
    errorMessage.value = t('spending.settings.pleaseSetLimit');
  }
};

const handleLimitChange = (value) => {
  if (value !== null && value >= 0) {
    spendingStore.setMonthlyLimit(value);
  }
};

const handleThresholdChange = (value) => {
  if (value !== null && value >= 1 && value <= 100) {
    const threshold = value / 100;
    spendingStore.setWarningThreshold(threshold);
  }
};

// 监听store变化，同步到本地状态
watch(() => spendingStore.monthlyLimit, (newValue) => {
  localLimit.value = newValue;
}, { immediate: true });

watch(() => spendingStore.warningThreshold, (newValue) => {
  thresholdPercentage.value = Math.round(newValue * 100);
}, { immediate: true });

// 关闭弹窗
const closeDialog = () => {
  emit('update:modelValue', false);
};

// 组件挂载时加载设置
onMounted(() => {
  spendingStore.loadSettings();
});
</script>

<style scoped>
.custom-dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
  backdrop-filter: blur(4px);
}

.custom-dialog {
  background: #ffffff;
  border-radius: 12px;
  width: 90%;
  max-width: 600px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  animation: dialog-fade-in 0.3s ease-out;
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  border-bottom: 1px solid #e4e7ed;
}

.dialog-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.dialog-close-btn {
  background: none;
  border: none;
  font-size: 24px;
  color: #909399;
  cursor: pointer;
  padding: 0;
  width: 24px;
  height: 24px;
  line-height: 24px;
  text-align: center;
}

.dialog-close-btn:hover {
  color: #606266;
}

.dialog-body {
  padding: 24px;
}

.spending-limit-setting {
  background: transparent;
  padding: 0;
  box-shadow: none;
}

.setting-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e4e7ed;
}

.setting-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.setting-content {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  flex-wrap: wrap;
}

.setting-item {
  margin-bottom: 24px;
  flex: 1;
}

.setting-label {
  display: block;
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 500;
  color: #606266;
}

.limit-input {
  width: 100%;
  max-width: 300px;
}

.threshold-input {
  width: 100%;
  max-width: 300px;
}

.currency-symbol {
  color: #909399;
  font-weight: 500;
}

.disabled-notice {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  color: #909399;
  font-size: 14px;
}

.notice-icon {
  margin-right: 8px;
  font-size: 16px;
}

/* 动画效果 */
@keyframes dialog-fade-in {
  from {
    opacity: 0;
    transform: translateY(-20px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes dialog-fade-out {
  from {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
  to {
    opacity: 0;
    transform: translateY(-10px) scale(0.98);
  }
}

/* 遮罩层过渡 */
.dialog-overlay-enter-active,
.dialog-overlay-leave-active {
  transition: opacity 0.3s ease;
}

.dialog-overlay-enter-from,
.dialog-overlay-leave-to {
  opacity: 0;
}

/* 弹窗内容过渡 */
.dialog-content-enter-active {
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.dialog-content-leave-active {
  transition: transform 0.2s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.dialog-content-enter-from {
  opacity: 0;
  transform: translateY(-20px) scale(0.95);
}

.dialog-content-leave-to {
  opacity: 0;
  transform: translateY(-10px) scale(0.98);
}

/* 对话框关闭按钮动画 */
.dialog-close-btn:hover {
  transform: rotate(90deg);
}

.dialog-close-btn {
  transition: transform 0.3s ease;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .custom-dialog {
    width: 95%;
    margin: 10px;
  }
  
  .dialog-header,
  .dialog-body {
    padding: 16px;
  }
  
  .spending-limit-setting {
    padding: 0;
  }
}

/* 深色模式适配 */
@media (prefers-color-scheme: dark) {
  .custom-dialog-overlay {
    background-color: rgba(0, 0, 0, 0.7);
  }
  
  .custom-dialog {
    background: rgba(30, 30, 30, 0.9);
    border: 1px solid #333;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
  }
  
  .dialog-header {
    border-bottom-color: #444;
  }
  
  .dialog-title {
    color: #f9fafb;
  }
  
  .dialog-close-btn {
    color: #9ca3af;
  }
  
  .dialog-close-btn:hover {
    color: #e5e7eb;
  }
  
  .spending-limit-setting {
    background: transparent;
    border: none;
    box-shadow: none;
  }

  .setting-header {
    border-bottom: 1px solid #444;
  }

  .setting-title {
    color: #f9fafb;
  }

  .setting-label {
    color: #9ca3af;
  }

  .currency-symbol {
    color: #9ca3af;
  }

  .negative-amount {
    color: #f87171;
  }
}
</style>
