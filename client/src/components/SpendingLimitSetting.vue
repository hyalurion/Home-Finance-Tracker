<template>
  <div class="spending-limit-setting">
    <div class="setting-header">
      <h3 class="setting-title">{{ $t('spending.settings.title') }}</h3>
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
</template>

<script setup>
import { ref, watch, onMounted } from 'vue';
import { useSpendingStore } from '../stores/spending.js';
import { useI18n } from 'vue-i18n';
import { ElMessage } from 'element-plus';
import GlassSwitch from './GlassSwitch.vue';
import GlassInputNumber from './GlassInputNumber.vue';

const { t } = useI18n();
const spendingStore = useSpendingStore();

// 本地状态
const localLimit = ref(0);
const thresholdPercentage = ref(80);

// 方法
const handleToggleEnabled = (enabled) => {
  spendingStore.toggleLimitEnabled(enabled);
  if (enabled && spendingStore.monthlyLimit <= 0) {
    // 如果启用但没有设置限制，提示用户设置
    ElMessage.info(t('spending.settings.pleaseSetLimit'));
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

// 组件挂载时加载设置
onMounted(() => {
  spendingStore.loadSettings();
});
</script>

<style scoped>
.spending-limit-setting {
  background: #ffffff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
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
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.setting-content {
  display: flex;
  flex-direction: row;
  gap: 32px;
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

/* 响应式设计 */
@media (max-width: 768px) {
  .spending-limit-setting {
    padding: 16px;
  }

  .setting-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
}

/* 深色模式适配 */
@media (prefers-color-scheme: dark) {
  .spending-limit-setting {
    background: rgba(30, 30, 30, 0.7);
    border: 1px solid #333;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.3);
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
