<template>
  <!-- 全局会员资格检查弹窗 -->
  <div v-if="showMembershipModal" class="donation-modal-overlay">
    <div class="donation-modal-content">
      <h2 class="donation-modal-title">请开通会员<br>Please Activate Membership</h2>
      <p class="donation-modal-message">为了继续使用完整功能，请开通会员订阅。开通会员后您可以无限制使用所有功能。<br>To continue using all features, please activate a membership subscription. With an active membership, you can enjoy unlimited access to all functionalities.</p>
      <div class="donation-modal-footer">
        <GlassButton type="primary" @click="proceedToMembership" :dark-theme="isDarkMode">
          开通会员<br>Activate Membership
        </GlassButton>
        <GlassButton type="primary" @click="exportMonthData" :dark-theme="isDarkMode">
          或者免费导出本月数据图片<br>Or avail yourself of the complimentary exportation for the current month's data schematic
        </GlassButton>
      </div>
    </div>
  </div>

  <div class="container">
    <!-- 主弹窗 -->
    <div v-if="isLoading" class="loading-alert">{{ t('app.loading') }}</div>
    <div v-if="error" class="error-alert">{{ error }}</div>
    <MessageTip v-model:message="successMessage" type="success" />
    <MessageTip v-model:message="errorMessage" type="error" />

    <Header :title="$t('app.title')" />
    
    <!-- 七彩欢迎信息 -->
    <div class="welcome-text">
      {{ t('app.welcome', { username: username || '' }) }}
    </div>
    
    <!-- 当前日期时间显示 -->
    <div class="datetime-container">
      <div class="date-part">{{ formattedDate }}</div>
      <div class="time-part">{{ formattedTime }}</div>
    </div>

       <div v-if="isAndroidDevice" class="android-download-section">
         <GlassButton 
           type="info" 
           size="large" 
           class="android-download-btn"
           @click="openAndroidAppStore"
           :dark-theme="isDarkMode"
           style="width: 100%;display: flex; justify-content: center; margin-bottom: 20px; white-space: normal; height: auto; line-height: 1.5; padding: 16px;"
         >
           <div style="text-align: center;">
             <div>在应用商店获取安卓版本客户端</div>
             <div style="font-size: 12px; opacity: 0.8; margin-top: 4px;">提升使用体验！</div>
           </div>
         </GlassButton>
       </div>

    <!-- 功能组网格布局 -->
    <div class="function-section">
      <!-- 手机端选单 -->
      <div class="mobile-selector">
        <CustomSelect
          v-model="selectedFunctionGroup"
          :options="functionGroups"
          :include-empty-option="false"
          style="width: 100%;"
        />
      </div>
      
      <!-- 桌面端网格布局 -->
      <div class="card-grid">
        <!-- 主要功能组 -->
        <GlassCard :dark-theme="isDarkMode" :title="t('function.primary')">
          <div class="card-content">
            <GlassButton type="primary" @click="showAddDialog = true" :dark-theme="isDarkMode">
              <template #icon><FontAwesomeIcon icon="plus" /></template>
              {{ t('expense.addRecord') }}
            </GlassButton>
            <el-upload
              class="upload-excel"
              action="/api/import/excel"
              :show-file-list="false"
              :on-success="handleImportSuccess"
              :on-error="handleImportError"
              accept=".xlsx, .xls"
              :dark-theme="isDarkMode"
            >
              <GlassButton type="warning" @click="$event.preventDefault()" :dark-theme="isDarkMode">
                <template #icon><FontAwesomeIcon icon="upload" /></template>
                {{ t('import.title') }}
              </GlassButton>
              </el-upload>
            <GlassButton type="primary" @click="exportMonthData" :dark-theme="isDarkMode">
              <template #icon><FontAwesomeIcon icon="download" /></template>
              导出本月数据
            </GlassButton>
          </div>
        </GlassCard>
        
        <!-- AI功能组 -->
        <GlassCard :dark-theme="isDarkMode" :title="t('function.aiFeatures')">
          <div class="card-content">
            <GlassButton type="primary" @click="showAiAddDialog = true" :dark-theme="isDarkMode">
              <template #icon><FontAwesomeIcon icon="microchip" /></template>
              AI智能记录
            </GlassButton>
            <GlassButton type="primary" @click="showAiReportDialog = true" :dark-theme="isDarkMode">
              <template #icon><FontAwesomeIcon icon="file-alt" /></template>
              AI消费问答
            </GlassButton>
          </div>
        </GlassCard>
        
        <!-- 其他组件组 -->
        <GlassCard :dark-theme="isDarkMode" :title="t('function.other')">
          <div class="card-content">
            <GlassButton type="success" @click="goToMembership" :dark-theme="isDarkMode">
              <template #icon><FontAwesomeIcon icon="star" /></template>
              {{ t('membership.title') }}
            </GlassButton>
          </div>
        </GlassCard>

        <!-- 支持与帮助组 -->
        <GlassCard :dark-theme="isDarkMode" :title="t('function.support')">
          <div class="card-content">
            <GlassButton type="primary" @click="handleFeedback" :dark-theme="isDarkMode">
              <template #icon><FontAwesomeIcon icon="envelope" /></template>
              {{ t('feedback.title') }}
            </GlassButton>
            <GlassButton type="primary" @click="goToHowToUse" :dark-theme="isDarkMode">
              <template #icon><FontAwesomeIcon icon="question-circle" /></template>
              {{ t('howToUse.title') }}
            </GlassButton>
          </div>
        </GlassCard>
      </div>
      
      <!-- 手机端按钮显示 -->
      <div class="mobile-buttons">
        <!-- 主要功能组按钮 -->
        <div v-if="selectedFunctionGroup === 'primary'" class="mobile-button-group">
          <GlassButton type="primary" @click="showAddDialog = true" size="large" class="mobile-btn" :dark-theme="isDarkMode">
            <template #icon><FontAwesomeIcon icon="plus" /></template>
            {{ t('expense.addRecord') }}
          </GlassButton>
          <el-upload
            class="upload-excel"
            action="/api/import/excel"
            :show-file-list="false"
            :on-success="handleImportSuccess"
            :on-error="handleImportError"
            accept=".xlsx, .xls"
            :dark-theme="isDarkMode"
          >
            <GlassButton type="warning" size="large" @click="$event.preventDefault()" :dark-theme="isDarkMode">
              <template #icon><FontAwesomeIcon icon="upload" /></template>
              {{ t('import.title') }}
            </GlassButton>
          </el-upload>
          <GlassButton type="primary" @click="exportMonthData" size="large" class="mobile-btn" :dark-theme="isDarkMode">
            <template #icon><FontAwesomeIcon icon="download" /></template>
            导出本月数据
          </GlassButton>
        </div>
        
        <!-- AI功能组按钮 -->
        <div v-else-if="selectedFunctionGroup === 'ai'" class="mobile-button-group">
          <GlassButton type="primary" @click="showAiAddDialog = true" size="large" class="mobile-btn" :dark-theme="isDarkMode">
            <template #icon><FontAwesomeIcon icon="microchip" /></template>
            AI智能记录
          </GlassButton>
          <GlassButton type="primary" @click="showAiReportDialog = true" size="large" class="mobile-btn" :dark-theme="isDarkMode">
            <template #icon><FontAwesomeIcon icon="file-alt" /></template>
            AI消费问答
          </GlassButton>
        </div>
        
        <!-- 其他组件组按钮 -->
        <div v-else-if="selectedFunctionGroup === 'other'" class="mobile-button-group">
          <GlassButton type="success" @click="goToMembership" :dark-theme="isDarkMode" class="mobile-btn">
            <template #icon><FontAwesomeIcon icon="star" /></template>
            {{ t('membership.title') }}
          </GlassButton>
        </div>
        
        <!-- 关于我们组按钮 -->
        <div v-else-if="selectedFunctionGroup === 'about'" class="mobile-button-group">
          <GlassButton type="primary" @click="handleFeedback" :dark-theme="isDarkMode" class="mobile-btn">
            <template #icon><FontAwesomeIcon icon="envelope" /></template>
            {{ t('feedback.title') }}
          </GlassButton>
          <GlassButton type="primary" @click="goToHowToUse" :dark-theme="isDarkMode" class="mobile-btn">
            <template #icon><FontAwesomeIcon icon="question-circle" /></template>
            使用方法
          </GlassButton>
        </div>
      </div>
    </div>

    <!-- 月度消费限制显示 -->
    <SpendingLimitDisplay :expenses="Expenses" />
    
    <!-- 图表分析按钮 -->
    <div style="display: flex; justify-content: center; margin-bottom: 20px;">
      <GlassButton type="primary" @click="goToCharts" :dark-theme="isDarkMode">
        <template #icon><FontAwesomeIcon icon="chart-pie" /></template>
        {{ t('chart.title') }}
      </GlassButton>
    </div>
    
    <ExpenseList 
      :refresh-trigger="refreshTrigger" 
      @edit="handleEditExpense"
      @delete="handleDeleteExpense"
    />
    <div :class="['header']"></div>
    <Transition name="button">
      <ExportButton
        v-if="Expenses.length > 0"
        @export-excel="() => exportToExcel(Expenses)"
      />
      <div v-else class="no-data">{{ t('home.noDataForExport') }}</div>
    </Transition>
  </div>

  <!-- 悬浮刷新按钮 -->
  <div class="floating-refresh-btn">
    <GlassButton type="primary" @click="refreshPage()" :dark-theme="isDarkMode" circle>
      <template #icon><FontAwesomeIcon icon="sync-alt" /></template>
    </GlassButton>
  </div>

  <!-- 自定义添加记录弹窗 -->
  <transition name="dialog-fade">
    <div v-if="showAddDialog" class="custom-dialog-overlay" @click.self="closeAddDialog">
      <div class="custom-dialog" :class="{ 'dark-theme': isDarkMode }">
        <div class="dialog-header">
          <h3 class="dialog-title">{{ t('expense.addDialogTitle') }}</h3>
          <button class="dialog-close-btn" @click="closeAddDialog" aria-label="关闭">
            ×
          </button>
        </div>
        
        <div class="dialog-body">
          <form class="custom-form" @submit.prevent="handleAddRecord">
            <div class="form-group">
              <label class="form-label" :class="{ 'error': formErrors.type }">
                {{ t('expense.type') }}
              </label>
              <CustomSelect 
                v-model="form.type" 
                :options="expenseTypes.map(type => ({ label: type, value: type }))"
                :empty-option-label="t('expense.selectType')"
                class="form-select"
                :class="{ 'error': formErrors.type }"
              />
              <span v-if="formErrors.type" class="error-message">{{ formErrors.type }}</span>
            </div>
            
            <div class="form-group">
              <label class="form-label" :class="{ 'error': formErrors.amount }">
                {{ t('expense.amount') }}
              </label>
              <input 
                v-model="form.amount" 
                type="number" 
                step="0.01" 
                min="0" 
                class="form-input" 
                :class="{ 'error': formErrors.amount }" 
                :placeholder="0"
                required
              />
              <span v-if="formErrors.amount" class="error-message">{{ formErrors.amount }}</span>
            </div>
            
            <div class="form-group">
              <label class="form-label" :class="{ 'error': formErrors.date }">
                {{ t('expense.date') }}
              </label>
              <input 
                v-model="form.date" 
                type="date" 
                class="form-input" 
                :class="{ 'error': formErrors.date }" 
                required
              />
              <span v-if="formErrors.date" class="error-message">{{ formErrors.date }}</span>
            </div>
            
            <div class="form-group">
              <label class="form-label">{{ t('expense.remark') }}</label>
              <textarea 
                v-model="form.remark" 
                class="form-textarea" 
                :placeholder="t('expense.enterRemark')"
              ></textarea>
            </div>
          </form>
        </div>
        
        <div class="dialog-footer">
          <GlassButton type="secondary" @click="closeAddDialog">{{ t('common.cancel') }}</GlassButton>
          <GlassButton type="primary" @click="handleAddRecord">{{ t('common.confirm') }}</GlassButton>
        </div>
      </div>
    </div>
  </transition>

  <!-- 编辑消费记录对话框 -->
  <transition name="dialog-fade">
    <div v-if="showEditDialog" class="custom-dialog-overlay" @click.self="showEditDialog = false">
      <div class="custom-dialog" :class="{ 'dark-theme': isDarkMode }">
        <div class="dialog-header">
          <h3 class="dialog-title">{{ t('expense.edit') }}</h3>
          <button class="dialog-close-btn" @click="showEditDialog = false" aria-label="关闭">
            ×
          </button>
        </div>
        
        <div class="dialog-body">
          <form class="custom-form" @submit.prevent="confirmEdit">
            <div class="form-group">
              <label class="form-label" :class="{ 'error': editErrors.type }">
                {{ t('expense.type') }}
              </label>
              <CustomSelect 
                v-model="editingExpense.type" 
                :options="expenseTypes.map(type => ({ label: type, value: type }))"
                :empty-option-label="t('expense.selectType')"
                class="form-select"
                :class="{ 'error': editErrors.type }"
              />
              <span v-if="editErrors.type" class="error-message">{{ editErrors.type }}</span>
            </div>
            
            <div class="form-group">
              <label class="form-label" :class="{ 'error': editErrors.amount }">
                {{ t('expense.amount') }}
              </label>
              <input 
                v-model="editingExpense.amount" 
                type="number" 
                step="0.01" 
                min="0" 
                class="form-input" 
                :class="{ 'error': editErrors.amount }" 
                :placeholder="0"
                required
              />
              <span v-if="editErrors.amount" class="error-message">{{ editErrors.amount }}</span>
            </div>
            
            <div class="form-group">
              <label class="form-label" :class="{ 'error': editErrors.date }">
                {{ t('expense.date') }}
              </label>
              <input 
                v-model="editingExpense.date" 
                type="date" 
                class="form-input" 
                :class="{ 'error': editErrors.date }" 
                required
              />
              <span v-if="editErrors.date" class="error-message">{{ editErrors.date }}</span>
            </div>
            
            <div class="form-group">
              <label class="form-label">{{ t('expense.remark') }}</label>
              <textarea 
                v-model="editingExpense.remark" 
                class="form-textarea" 
                :placeholder="t('expense.enterRemark')"
              ></textarea>
            </div>
          </form>
        </div>
        
        <div class="dialog-footer">
          <GlassButton type="secondary" @click="showEditDialog = false">{{ t('common.cancel') }}</GlassButton>
          <GlassButton type="primary" @click="confirmEdit">{{ t('common.confirm') }}</GlassButton>
        </div>
      </div>
    </div>
  </transition>
  
  <!-- 删除确认对话框 -->
  <transition name="dialog-fade">
    <div v-if="showDeleteDialog" class="custom-dialog-overlay" @click.self="showDeleteDialog = false">
      <div class="custom-dialog" :class="{ 'dark-theme': isDarkMode }">
        <div class="dialog-header">
          <h3 class="dialog-title">{{ t('expense.deleteConfirm') }}</h3>
          <button class="dialog-close-btn" @click="showDeleteDialog = false" aria-label="关闭">
            ×
          </button>
        </div>
        
        <div class="dialog-body">
          <p>{{ t('expense.deleteMessage') }}</p>
        </div>
        
        <div class="dialog-footer">
          <GlassButton type="secondary" @click="showDeleteDialog = false">{{ t('common.cancel') }}</GlassButton>
          <GlassButton type="warning" @click="confirmDelete">{{ t('common.delete') }}</GlassButton>
        </div>
      </div>
    </div>
  </transition>

  <!-- API密钥设置对话框 -->
  <GlassDialog v-model:visible="showApiKeyDialog" title="设置SiliconFlow API密钥" width="50%" :dark-theme="isDarkMode" :z-index="9999">
    <GlassForm :model="apiKeyForm" ref="apiKeyFormRef">
      <GlassFormItem :label="'API密钥'" prop="apiKey">
        <GlassInput
          v-model="apiKeyForm.apiKey"
          placeholder="请输入您的SiliconFlow API密钥"
          type="password"
          show-password
          :dark-theme="isDarkMode"
        ></GlassInput>
      </GlassFormItem>
        <div style="margin-top: 10px; font-size: 12px;">
          获取API密钥: <a href="https://console.siliconflow.cn/api-keys" target="_blank">https://console.siliconflow.cn/api-keys</a>
        </div>
    </GlassForm>
    <template #footer>
      <GlassButton @click="showApiKeyDialog = false" :dark-theme="isDarkMode">取消</GlassButton>
      <GlassButton type="primary" @click="handleApiKeySave" :dark-theme="isDarkMode">保存</GlassButton>
    </template>
  </GlassDialog>

  <!-- AI智能记录对话框 -->
  <GlassDialog v-model:visible="showAiAddDialog" title="AI智能记录" width="80%" :dark-theme="isDarkMode">
    <GlassForm :model="aiForm" ref="aiFormRef">
      <GlassFormItem :label="'输入文本描述'">
        <GlassInput
          v-model="aiForm.text"
          type="textarea"
          :rows="4"
          placeholder="请输入消费记录的详细描述，例如：今天上午在超市买了苹果和牛奶，共花费56.8元。"
          :dark-theme="isDarkMode"
        ></GlassInput>
      </GlassFormItem>
      <GlassFormItem :label="'或上传图片'">
        <GlassUpload
          v-model:file-list="aiForm.image"
          class="avatar-uploader"
          action=""
          :auto-upload="false"
          :on-change="handleImageChange"
          :show-file-list="true"
          accept=".jpg,.jpeg,.png,.gif"
          :dark-theme="isDarkMode"
        >
          <GlassButton size="small" type="primary" :dark-theme="isDarkMode">点击上传</GlassButton>
          <template #tip>
            <div class="glass-upload__tip">
              请上传包含消费信息的图片（如收据、账单截图等）
            </div>
          </template>
        </GlassUpload>
      </GlassFormItem>
      <div :class="['api-key-prompt', { 'dark-theme': isDarkMode }]">
        <GlassButton 
          type="info" 
          @click="showApiKeyDialog = true" 
          :dark-theme="isDarkMode" 
          size="small"
          style="margin: 0 auto; display: block;"
        >
          API密钥设置
        </GlassButton>
      </div>
    </GlassForm>
    <template #footer>
      <GlassButton @click="handleAiCancel" :dark-theme="isDarkMode">{{ t('common.cancel') }}</GlassButton>
      <GlassButton type="primary" @click="handleAiGenerate" :disabled="isParsing" :dark-theme="isDarkMode">
        {{ isParsing ? '生成中...' : '生成记录' }}
      </GlassButton>
    </template>
  </GlassDialog>

  <!-- 多条记录编辑对话框 -->
  <GlassDialog v-model:visible="showMultiRecordsDialog" title="AI生成的多条记录" width="90%" :dark-theme="isDarkMode">
    <div v-if="multiRecords.length === 0" class="no-records">
      {{ t('expense.noRecords') }}
    </div>
    <div v-else class="multi-records-container">
      <!-- 全选功能 -->
      <div class="select-all-container" style="margin-bottom: 20px;">
        <GlassCheckbox v-model="selectAll" @change="handleSelectAllChange" :dark-theme="isDarkMode">{{ t('common.selectAll') }}</GlassCheckbox>
      </div>
      
      <!-- 记录列表 -->
      <div v-for="(record, index) in multiRecords" :key="index" class="record-item" style="margin-bottom: 15px; padding: 15px; border: 1px solid #e4e7ed; border-radius: 4px;">
        <div style="display: flex; align-items: center; margin-bottom: 10px;">
          <GlassCheckbox v-model="record.selected" @change="handleRecordSelectChange" :dark-theme="isDarkMode"></GlassCheckbox>
          <span style="margin-left: 10px; font-weight: 500;">{{ t('expense.record') }} {{ index + 1 }}</span>
        </div>
        <div style="display: flex; flex-wrap: wrap; gap: 15px;">
          <div style="flex: 1; min-width: 200px;">
            <label style="display: block; margin-bottom: 5px;">{{ t('expense.type') }}:</label>
            <CustomSelect 
              v-model="record.type" 
              :options="expenseTypes.map(type => ({ label: type, value: type }))"
              :empty-option-label="t('expense.selectType')"
              style="width: 100%;"
            />
          </div>
          <div style="flex: 1; min-width: 200px;">
            <label style="display: block; margin-bottom: 5px;">{{ t('expense.amount') }}:</label>
            <GlassInput v-model="record.amount" :placeholder="0" type="text" style="width: 100%;" :dark-theme="isDarkMode" />
          </div>
          <div style="flex: 1; min-width: 200px;">
            <label style="display: block; margin-bottom: 5px;">{{ t('expense.date') }}:</label>
            <input type="date" v-model="record.date" :placeholder="t('expense.selectDate')" class="el-input__inner" style="width: 100%;">
          </div>
        </div>
        <div style="margin-top: 15px;">
          <label style="display: block; margin-bottom: 5px;">{{ t('expense.remark') }}:</label>
          <GlassInput v-model="record.remark" :placeholder="t('expense.enterRemark')" type="textarea" :rows="2" style="width: 100%;" :dark-theme="isDarkMode"></GlassInput>
        </div>
      </div>
    </div>
    <template #footer>
      <GlassButton @click="handleMultiRecordsCancel" :dark-theme="isDarkMode">{{ t('common.cancel') }}</GlassButton>
      <GlassButton type="primary" @click="handleMultiRecordsSubmit" :dark-theme="isDarkMode">{{ t('common.submit') }} ({{ selectedRecordsCount }}/{{ multiRecords.length }})</GlassButton>
    </template>
  </GlassDialog>

  <!-- AI消费问答对话框 -->
  <GlassDialog v-model:visible="showAiReportDialog" title="AI消费问答" width="90%" height="80vh" :dark-theme="isDarkMode">
    <div class="ai-report-container">
      <!-- 问题输入区域 -->
      <div class="report-question-section" style="margin-bottom: 10px;">
        <GlassForm label-position="top">
          <GlassFormItem label="输入您的问题">
            <GlassButton 
              @click="clearReportQuestion" 
              :dark-theme="isDarkMode"
              style="position: absolute; top: 15px; right: 15px; width: 30px; height: 30px; padding: 0;"
            >×</GlassButton>
            <GlassInput
              v-model="reportQuestion"
              type="textarea"
              :rows="3"
              placeholder="您可以向AI提问关于您的消费情况，例如：'我本月的主要消费类别是什么？'或'如何减少我的日常开支？'"
              :dark-theme="isDarkMode"
            />
          </GlassFormItem>
            <div style=" display: flex; justify-content: center; flex-wrap: wrap;">
              <GlassButton type="primary" @click="handleGenerateReport" :disabled="isGeneratingReport" :dark-theme="isDarkMode">
                {{ isGeneratingReport ? '生成中...' : '生成' }}
              </GlassButton>
            </div>
        </GlassForm>
      </div>
      
      <!-- 报告内容显示区域 -->
      <div class="report-content-section">
        <div v-if="!reportContent" class="no-report-content">
          请点击"生成"按钮开始分析您的消费数据
        </div>
        <div v-else class="report-content" v-html="renderedReportContent" style="white-space: pre-wrap;">
        </div>
      </div>
    </div>
  </GlassDialog>

    <MarkdownDialog
      v-model:visible="showMarkdownDialog"
      :title="markdownTitle"
      :content="markdownContent"
    />
  


</template>

<script setup>
import GlassDialog from '@/components/GlassDialog.vue';
import GlassForm from '@/components/GlassForm.vue';
import GlassFormItem from '@/components/GlassFormItem.vue';
import GlassUpload from '@/components/GlassUpload.vue';
import GlassCheckbox from '@/components/GlassCheckbox.vue';

import axios from 'axios';
import { ref, computed, onMounted, onBeforeUnmount, reactive, defineAsyncComponent, watch } from 'vue';
import { marked } from 'marked';

import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import CustomSelect from '@/components/CustomSelect.vue';
import Papa from 'papaparse';

import { useExpenseData } from '@/composables/useExpenseData';
import { useExcelExport } from '@/composables/useExcelExport';
import { fetchAllPages, createCancellationController } from '@/utils/pagination';
import { ExpenseAPI } from '@/api/expenses';

import MessageTip from '@/components/MessageTip.vue';
import Header from '@/components/Header.vue';
import ExpenseList from '@/components/ExpenseList.vue';
import ExpenseCharts from '@/components/ExpenseCharts.vue';
import ExportButton from '@/components/ExportButton.vue';
import MarkdownDialog from '@/components/MarkdownDialog.vue';
import SpendingLimitDisplay from '@/components/SpendingLimitDisplay.vue';
import GlassCard from '@/components/GlassCard.vue';
import GlassButton from '@/components/GlassButton.vue';
import GlassInput from '@/components/GlassInput.vue';


const { t, locale } = useI18n();
const router = useRouter();

// 用户名响应式变量 - 必须在所有使用前定义
const username = ref(localStorage.getItem('username') || '');

// 安卓设备检测
const isAndroidDevice = ref(false);

const detectAndroidDevice = () => {
  const userAgent = navigator.userAgent || navigator.vendor || window.opera;
  // 检查是否为安卓设备
  const androidRegex = /Android/i;
  isAndroidDevice.value = androidRegex.test(userAgent);
};

// 播放警报声 - 支持循环多秒
const playAlertSound = (duration = 5) => {
  try {
    const audioContext = new (window.AudioContext || window.webkitAudioContext)();
    const oscillator = audioContext.createOscillator();
    const gainNode = audioContext.createGain();
    
    oscillator.connect(gainNode);
    gainNode.connect(audioContext.destination);
    
    oscillator.type = 'sine';
    gainNode.gain.setValueAtTime(0.1, audioContext.currentTime);
    
    // 创建循环效果
    let currentTime = audioContext.currentTime;
    const cycleDuration = 1; // 每次循环的持续时间（秒）
    const cycles = Math.ceil(duration / cycleDuration);
    
    // 生成多个频率变化的循环
    for (let i = 0; i < cycles; i++) {
      const startTime = currentTime + i * cycleDuration;
      
      // 设置上升和下降的音调变化
      oscillator.frequency.setValueAtTime(1000, startTime);
      oscillator.frequency.exponentialRampToValueAtTime(1200, startTime + 0.3);
      oscillator.frequency.exponentialRampToValueAtTime(800, startTime + 0.7);
      oscillator.frequency.exponentialRampToValueAtTime(1000, startTime + 1);
    }
    
    // 最后淡出
    gainNode.gain.setValueAtTime(0.1, audioContext.currentTime + duration - 0.5);
    gainNode.gain.exponentialRampToValueAtTime(0.001, audioContext.currentTime + duration);
    
    oscillator.start(audioContext.currentTime);
    oscillator.stop(audioContext.currentTime + duration);
  } catch (error) {
    console.error('播放警报声失败:', error);
  }
}

// 显示大额消费警告 - 支持回调
let warningCallback = null;
const showLargeExpenseWarning = (callback = null) => {
  playAlertSound(5); // 播放5秒的循环警报声
  
  // 存储回调函数
  warningCallback = callback;
  
  // 创建全屏警告弹窗
  const warningContainer = document.createElement('div');
  warningContainer.id = 'largeExpenseWarning'; // 添加ID以便查找
  warningContainer.style.position = 'fixed';
  warningContainer.style.top = '0';
  warningContainer.style.left = '0';
  warningContainer.style.width = '100%';
  warningContainer.style.height = '100%';
  warningContainer.style.backgroundColor = 'rgba(255, 0, 0, 0.8)';
  warningContainer.style.zIndex = '99999'; // 提高z-index确保显示在最上层
  warningContainer.style.display = 'flex';
  warningContainer.style.flexDirection = 'column';
  warningContainer.style.justifyContent = 'center';
  warningContainer.style.alignItems = 'center';
  warningContainer.style.color = 'white';
  warningContainer.style.fontSize = '3rem';
  warningContainer.style.fontWeight = 'bold';
  warningContainer.style.textAlign = 'center';
  warningContainer.style.padding = '2rem';
  warningContainer.style.animation = 'blink 0.5s infinite alternate';
  
  // 添加闪烁动画样式
  const styleSheet = document.createElement('style');
  styleSheet.id = 'largeExpenseWarningStyle';
  styleSheet.textContent = `
    @keyframes blink {
      from { opacity: 1; }
      to { opacity: 0.7; }
    }
    #largeExpenseWarning {
      position: fixed !important;
      z-index: 99999 !important;
    }
  `;
  document.head.appendChild(styleSheet);
  
  const warningText = document.createElement('div');
  warningText.textContent = t('expense.largeExpense.warning');
  warningText.style.marginBottom = '2rem';
  warningText.style.textShadow = '2px 2px 4px rgba(0, 0, 0, 0.5)';
  
  const confirmButton = document.createElement('button');
  confirmButton.id = 'largeExpenseConfirmButton';
  confirmButton.textContent = t('expense.largeExpense.confirm');
  confirmButton.style.padding = '0.8rem 2rem';
  confirmButton.style.fontSize = '1.2rem';
  confirmButton.style.backgroundColor = 'white';
  confirmButton.style.color = 'red';
  confirmButton.style.border = 'none';
  confirmButton.style.borderRadius = '4px';
  confirmButton.style.cursor = 'pointer';
  confirmButton.style.fontWeight = 'bold';
  confirmButton.style.zIndex = '100000'; // 确保按钮也在最上层
  
  confirmButton.addEventListener('click', () => {
    document.body.removeChild(warningContainer);
    document.head.removeChild(styleSheet);
    // 执行回调函数
    if (typeof warningCallback === 'function') {
      warningCallback();
      warningCallback = null;
    }
  });
  
  // 确保即使在对话框后面也能看到警告
  const dialogs = document.querySelectorAll('.el-dialog__wrapper');
  dialogs.forEach(dialog => {
    dialog.style.zIndex = '99998'; // 让对话框在警告后面
  });
  
  warningContainer.appendChild(warningText);
  warningContainer.appendChild(confirmButton);
  document.body.appendChild(warningContainer);
};

// 检查并显示大额消费警告
const checkAndShowLargeExpenseWarning = async (records) => {
  // 检查是否有单笔大于500元的消费
  const hasLargeExpense = records.some(record => parseFloat(record.amount) > 500);
  
  if (hasLargeExpense) {
    // 创建一个Promise来等待用户点击确认按钮
    return new Promise(resolve => {
      // 使用回调函数支持
      showLargeExpenseWarning(() => {
        resolve();
      });
      
      // 添加超时机制，确保即使用户不点击也能继续
      setTimeout(() => {
        // 尝试找到并点击确认按钮
        const confirmButton = document.getElementById('largeExpenseConfirmButton');
        if (confirmButton) {
          confirmButton.click();
        } else {
          // 如果找不到按钮，直接解析Promise
          resolve();
          // 清理警告弹窗
          const warningContainer = document.getElementById('largeExpenseWarning');
          const styleSheet = document.getElementById('largeExpenseWarningStyle');
          if (warningContainer) document.body.removeChild(warningContainer);
          if (styleSheet) document.head.removeChild(styleSheet);
          warningCallback = null;
        }
      }, 10000); // 10秒超时
    });
  }
};

// 按钮状态变量
const showAddDialog = ref(false);
const showMarkdownDialog = ref(false);
const showAiAddDialog = ref(false);
// 新增：显示多条记录的对话框
const showMultiRecordsDialog = ref(false);
// 新增：显示AI报告对话框
const showAiReportDialog = ref(false);

// 新增：编辑和删除对话框状态
const showEditDialog = ref(false);
const showDeleteDialog = ref(false);
const editingExpenseId = ref('');
const editingExpense = reactive({
  id: '',
  type: '',
  amount: '',
  date: '',
  remark: ''
});
const editErrors = reactive({
  type: '',
  amount: '',
  date: ''
});

// 功能组数据和选中状态（用于手机端）
const selectedFunctionGroup = ref('primary');
// 使用computed属性确保t函数正确初始化后再获取翻译值
const functionGroups = computed(() => [
  { label: t('function.primary'), value: 'primary' },
  { label: t('function.aiFeatures'), value: 'ai' },
  { label: t('function.other'), value: 'other' },
  { label: t('function.aboutus'), value: 'about' }
]);
const aiForm = reactive({
  text: '',
  image: []
});
const isParsing = ref(false);
// 新增：存储多条记录的数据结构
const multiRecords = ref([]);
// 新增：全选状态
const selectAll = ref(false);
// 新增：报告相关状态
const isGeneratingReport = ref(false);
const reportContent = ref('');
const reportQuestion = ref('');

// 配置marked选项
marked.setOptions({
  breaks: true,
  gfm: true
});

// 渲染报告内容为HTML
const renderedReportContent = computed(() => {
  return marked.parse(reportContent.value);
});

// 新增：计算已选择的记录数量
const selectedRecordsCount = computed(() => {
  return multiRecords.value.filter(record => record.selected).length;
});

// 导入AI API
import { parseTextToRecord, parseImageToRecord, setApiKey, generateExpenseReport } from '@/api/aiRecord';

// API密钥相关
const showApiKeyDialog = ref(false);
const apiKeyForm = reactive({
  apiKey: localStorage.getItem('siliconflow_api_key') || ''
});

// 导入会员API
import { checkMemberStatus } from '@/api/membership';

// 全局会员资格检查弹窗状态
const showMembershipModal = ref(false);
const hasActiveMembership = ref(false);
let membershipCheckTimer = null;


// 前往图表页面
const goToCharts = () => {
  router.push('/charts');
};

// 前往会员订阅页面
const goToMembership = () => {
  router.push('/membership');
};

// 前往会员订阅页面
const proceedToMembership = () => {
  // 保存当前页面作为重定向目标
  const currentPath = window.location.pathname + window.location.search;
  localStorage.setItem('redirectAfterMembership', currentPath);
  // 跳转到会员订阅页面
  router.push('/membership');
};

// 处理编辑消费记录
const handleEditExpense = (expense) => {
  console.log('Editing expense:', expense);
  editingExpenseId.value = expense.id;
  editingExpense.id = expense.id;
  editingExpense.type = expense.type || '';
  editingExpense.amount = expense.amount || '';
  editingExpense.date = expense.date || ''; // 直接使用date字段的值
  editingExpense.remark = expense.remark || '';
  
  // 清空错误提示
  editErrors.type = '';
  editErrors.amount = '';
  editErrors.date = '';
  
  showEditDialog.value = true;
};

// 处理删除消费记录
const handleDeleteExpense = (id) => {
  console.log('Deleting expense:', id);
  editingExpenseId.value = id;
  showDeleteDialog.value = true;
};

// 验证编辑表单
const validateEditForm = () => {
  let isValid = true;
  
  // 清空错误
  editErrors.type = '';
  editErrors.amount = '';
  editErrors.date = '';
  
  // 验证类型
  if (!editingExpense.type.trim()) {
    editErrors.type = t('expense.typeRequired');
    isValid = false;
  }
  
  // 验证金额
  const amount = parseFloat(editingExpense.amount);
  if (!editingExpense.amount || isNaN(amount) || amount <= 0) {
    editErrors.amount = t('expense.amountRequired');
    isValid = false;
  }
  
  // 验证日期
  if (!editingExpense.date) {
    editErrors.date = t('expense.dateRequired');
    isValid = false;
  }
  
  return isValid;
};

// 确认编辑消费记录
  const confirmEdit = async () => {
    if (!validateEditForm()) {
      return;
    }
    
    try {
      const expenseData = {
        type: editingExpense.type.trim(),
        amount: parseFloat(editingExpense.amount).toFixed(2),
        date: editingExpense.date, // 直接使用YYYY-MM-DD格式
        remark: editingExpense.remark.trim()
      };

      console.log('Updating expense:', expenseData);
      await ExpenseAPI.updateExpense(editingExpense.id, expenseData);
    
    successMessage.value = t('expense.updateSuccess');
    showEditDialog.value = false;
    
    // 刷新消费记录列表
    refreshTrigger.value++;
  } catch (error) {
    console.error('Update expense failed:', error);
    errorMessage.value = t('expense.updateFailed');
  }
};

// 确认删除消费记录
const confirmDelete = async () => {
  try {
    console.log('Deleting expense:', editingExpenseId.value);
    await ExpenseAPI.deleteExpense(editingExpenseId.value);
    
    successMessage.value = t('expense.deleteSuccess');
    showDeleteDialog.value = false;
    
    // 刷新消费记录列表
    refreshTrigger.value++;
  } catch (error) {
    console.error('Delete expense failed:', error);
    errorMessage.value = t('expense.deleteFailed');
  }
};

// 检查会员状态
const checkMembership = async () => {
  try {
    console.log(`检查会员状态: username=${username.value}`);
    
    // 初始状态：默认不是会员，显示弹窗
    hasActiveMembership.value = false;
    showMembershipModal.value = true;
    
    // 异步检查实际状态
    const isActive = await checkMemberStatus(username.value);
    console.log(`会员状态检查结果: isActive=${isActive}`);
    hasActiveMembership.value = isActive;
    
    // 根据实际状态更新弹窗显示
    showMembershipModal.value = !hasActiveMembership.value;
  } catch (error) {
    console.error('检查会员状态失败:', error);
    // 出错时保持默认状态（非会员），显示弹窗
    hasActiveMembership.value = false;
    showMembershipModal.value = true;
  }
};

// 导出本月数据图片
const exportMonthData = () => {
  window.open('/photo.html', '_blank');
};

// 防止用户通过ESC键关闭弹窗
const preventEscClose = (e) => {
  if (e.key === 'Escape' && showMembershipModal.value) {
    e.preventDefault();
    e.stopPropagation();
  }
};

// 定期检查会员状态
const startMembershipCheckInterval = () => {
  // 清除之前可能存在的定时器
  if (membershipCheckTimer) {
    clearInterval(membershipCheckTimer);
  }
  
  // 每5分钟检查一次会员状态
  membershipCheckTimer = setInterval(async () => {
    await checkMembership();
  }, 300000);
};

// 监听路由变化，确保用户不能绕过会员检查
const handleRouteChange = () => {
  if (showMembershipModal.value) {
    // 除非用户在会员页面或photo.html，否则强制显示弹窗
    const currentPath = window.location.pathname;
    if (currentPath !== '/membership' && currentPath !== '/photo.html') {
      showMembershipModal.value = true;
    }
  }
};

// 组件卸载时清理事件监听器
onBeforeUnmount(() => {
  if (dateTimeTimer) {
    clearInterval(dateTimeTimer);
  }
  
  if (membershipCheckTimer) {
    clearInterval(membershipCheckTimer);
  }
  
  document.removeEventListener('keydown', preventEscClose);
  window.removeEventListener('popstate', handleRouteChange);
});

// 导入处理
const handleImportSuccess = () => {
  successMessage.value = t('import.success');
};

const handleImportError = (error) => {
  errorMessage.value = t('import.failed');
  console.error('Import error:', error);
};

// 打开安卓应用商店
const openAndroidAppStore = () => {
  window.open('https://universal-launcher.netlify.app/app-store.html', '_blank');
};
const markdownContent = ref('');
const markdownTitle = ref('');

// 当前日期时间状态
const currentDateTime = ref('');
const formattedDate = ref('');
const formattedTime = ref('');
let dateTimeTimer = null;

// 初始加载和语言变化时重新加载
// 创建闪烁星星效果
const createSparkles = () => {
  const welcomeText = document.querySelector('.welcome-text');
  if (!welcomeText) return;
  
  // 清除已有的星星效果
  const existingSparkles = welcomeText.querySelectorAll('.sparkle');
  existingSparkles.forEach(sparkle => sparkle.remove());
  
  const textRect = welcomeText.getBoundingClientRect();
  const numSparkles = 15;
  
  for (let i = 0; i < numSparkles; i++) {
    const sparkle = document.createElement('div');
    sparkle.classList.add('sparkle');
    
    // 随机位置
    const x = Math.random() * textRect.width;
    const y = Math.random() * textRect.height;
    
    sparkle.style.left = `${x}px`;
    sparkle.style.top = `${y}px`;
    
    // 随机动画延迟和持续时间
    const delay = Math.random() * 5;
    const duration = 1 + Math.random() * 2;
    
    sparkle.style.animation = `sparkleAnimation ${duration}s ${delay}s infinite`;
    
    welcomeText.appendChild(sparkle);
  }
};

// 创建脉动光环
const createPulseRings = () => {
  const welcomeText = document.querySelector('.welcome-text');
  if (!welcomeText) return;
  
  // 清除已有的光环
  const existingRings = welcomeText.querySelectorAll('.pulse-ring');
  existingRings.forEach(ring => ring.remove());
  
  const numRings = 3;
  
  for (let i = 0; i < numRings; i++) {
    const ring = document.createElement('div');
    ring.classList.add('pulse-ring');
    
    const delay = i * 1.5;
    const duration = 4.5;
    
    ring.style.animation = `pulse ${duration}s ${delay}s infinite`;
    
    welcomeText.appendChild(ring);
  }
};

// 初始化华丽欢迎效果
const initWelcomeEffects = () => {
  // 延迟执行以确保DOM已经渲染
  setTimeout(() => {
    createSparkles();
    createPulseRings();
  }, 100);
};

onMounted(async () => {
  // 检测安卓设备
  detectAndroidDevice();
  
  // 初始化并启动日期时间更新
  updateDateTime();
  dateTimeTimer = setInterval(updateDateTime, 1000);
  
  // 添加新的事件监听器以强制会员弹窗
  document.addEventListener('keydown', preventEscClose);
  
  // 检查会员状态
  await checkMembership();
  
  // 启动会员状态检查机制
  startMembershipCheckInterval();
  
  // 监听路由变化
  window.addEventListener('popstate', handleRouteChange);
  
  // 初始化华丽欢迎效果
  initWelcomeEffects();
  
  try {
    await fetchData(false);
  } catch (err) {
    console.error('Failed to initialize data:', err);
    error.value = t('error.dataInitializationFailed');
  }
});

// 导入操作日志工具
import { logUserAction } from '@/utils/operationLogger';

// 清理定时器和事件监听器
onBeforeUnmount(() => {
  if (dateTimeTimer) {
    clearInterval(dateTimeTimer);
  }
  
  if (membershipCheckTimer) {
    clearInterval(membershipCheckTimer);
  }
  
  // 移除强制会员弹窗相关的事件监听器
  document.removeEventListener('keydown', preventEscClose);
  window.removeEventListener('popstate', handleRouteChange);
});

// 更新日期时间函数
const updateDateTime = () => {
  const now = new Date();
  // 根据当前语言环境和设备时区格式化日期时间
  const fullOptions = {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    weekday: 'long',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  };
  
  const dateOptions = {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    weekday: 'long'
  };
  
  const timeOptions = {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  };
  
  currentDateTime.value = now.toLocaleString(locale.value, fullOptions);
  formattedDate.value = now.toLocaleDateString(locale.value, dateOptions);
  formattedTime.value = now.toLocaleTimeString(locale.value, timeOptions);
};

// 对话框相关数据
const expenseTypes = ['日常用品', '奢侈品', '通讯费用', '食品', '零食糖果', '冷饮', '方便食品', '纺织品', '饮品', '调味品', '交通出行', '餐饮', '医疗费用', '水果', '其他', '水产品', '乳制品', '礼物人情', '旅行度假', '政务', '水电煤气'];
const form = reactive({
  type: '',
  amount: '',
  date: '',
  remark: ''
});

// 表单错误状态
const formErrors = reactive({
  type: '',
  amount: '',
  date: ''
});

// 检测深色模式
const isDarkMode = ref(window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);

// 监听深色模式变化
if (window.matchMedia) {
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
    isDarkMode.value = e.matches;
  });
}

// 关闭添加对话框
const closeAddDialog = () => {
  // 清除表单错误
  Object.keys(formErrors).forEach(key => {
    formErrors[key] = '';
  });
  showAddDialog.value = false;
};

// 验证表单
const validateForm = () => {
  let isValid = true;
  
  // 清除之前的错误
  Object.keys(formErrors).forEach(key => {
    formErrors[key] = '';
  });
  
  // 验证类型
  if (!form.type) {
    formErrors.type = t('expense.selectType');
    isValid = false;
  }
  
  // 验证金额
  if (!form.amount) {
    formErrors.amount = t('expense.inputAmount');
    isValid = false;
  } else {
    const amountStr = form.amount.toString().replace(',', '.');
    const amount = Number(amountStr);
    if (isNaN(amount) || amount <= 0 || !/^\d+(\.\d{1,2})?$/.test(amountStr)) {
      formErrors.amount = t('expense.invalidAmountFormat');
      isValid = false;
    }
  }
  
  // 验证日期
  if (!form.date) {
    formErrors.date = t('expense.selectDate');
    isValid = false;
  }
  
  return isValid;
};

const handleAddRecord = async () => {
  try {
    // 记录添加记录操作开始
    logUserAction('record_add_start', { 
      type: form.type, 
      amount: form.amount,
      date: form.date
    });
    
    // 使用自定义验证函数
    if (!validateForm()) {
      // 表单验证失败，错误信息已经在表单中显示
      return;
    }

    // 处理金额（这里只是再次确认，因为已经在validateForm中验证过）
    const amountStr = form.amount.toString().replace(',', '.');
    const amount = Number(amountStr);

    // 添加详细日志来跟踪日期
    console.log('用户选择的原始日期:', form.date);
    
    // 格式化日期为YYYY-MM-DD格式
    const userSelectedDate = form.date ? new Date(form.date).toISOString().split('T')[0] : '';
    console.log('格式化后的用户选择日期:', userSelectedDate);
    
    // 获取当前日期用于比较
    const today = new Date().toISOString().split('T')[0];
    console.log('今天的日期:', today);

    // 构建符合API要求的请求数据
    const expenseData = {
      type: form.type,
      amount: parseFloat(parseFloat(form.amount).toFixed(2)),
      remark: form.remark,
      date: userSelectedDate // 服务器需要的日期字段
    };
    console.log('发送到服务器的数据:', expenseData);

    // 检查是否有单笔大于500元的消费
    await checkAndShowLargeExpenseWarning([expenseData]);

    // 使用与Expenses.vue相同的批量提交接口，明确指定为1条记录
    await axios.post('/api/expenses', expenseData, {
      headers: {
        'Content-Type': 'application/json'
      }
    });
    showAddDialog.value = false;
    // 添加成功后刷新数据
    await fetchData(true);
    // 触发ExpenseList组件刷新
    refreshTrigger.value++;
    successMessage.value = t('expense.addSuccess');
    
    // 记录添加成功
    logUserAction('record_add_success', { 
      type: expenseData.type, 
      amount: expenseData.amount,
      date: expenseData.date
    });
    
    // 重置表单
    Object.assign(form, { type: '', amount: '', date: '', remark: '' });
  } catch (error) {
    console.error('添加记录失败:', error);
    console.error('错误详情:', { status: error.response?.status, data: error.response?.data, headers: error.response?.headers });
    // 区分表单验证错误和API错误
    let errorMsg;
    if (error.name === 'ValidationError') {
      errorMsg = error.message;
    } else if (error.response) {
      // 服务器响应错误
      const status = error.response.status;
      const serverMsg = error.response.data?.message || '服务器处理异常';
      if (status >= 500) {
        errorMsg = t('expense.serverError', { error: serverMsg });
      } else if (status === 400) {
        errorMsg = t('expense.badRequest', { error: serverMsg });
      } else {
        errorMsg = t('expense.networkError', { error: `${status} - ${serverMsg}` });
      }
    } else if (error.request) {
      // 无响应错误（网络问题）
      errorMsg = t('expense.networkTimeout');
    } else {
      errorMsg = t('expense.unknownError', { error: error.message || '未知错误' });
    }
    errorMessage.value = errorMsg;
    
    // 记录添加失败
    logUserAction('record_add_failed', { 
      error: errorMsg,
      attemptedData: { type: form.type, amount: form.amount, date: form.date }
    });
  }
};

// 状态数据
const Expenses = ref([]);
const isLoading = ref(false);
const refreshTrigger = ref(0); // 用于触发ExpenseList组件刷新的数据版本号

// 导出功能
const { exportToExcel } = useExcelExport();

// 费用数据管理
const {
  fetchData: originalFetchData,
  errorMessage,
  error,
  successMessage
} = useExpenseData();

// 封装 fetchData
const fetchData = async (forceRefresh = false) => {
  console.log('fetchData called, forceRefresh:', forceRefresh);
  await originalFetchData(forceRefresh);
  await loadExpenses();
};

// 创建取消控制器
let paginationController = null;

// 载入消费数据（从SQLite数据库） - 使用分页加载优化性能
const loadExpenses = async () => {
  if (isLoading.value) return;

  // 取消之前的请求
  if (paginationController) {
    paginationController.abort();
  }

  paginationController = createCancellationController();
  isLoading.value = true;

  try {
    console.log('开始使用分页加载数据...');

    // 使用分页工具获取所有数据
    const allData = await fetchAllPages({
      apiCall: ({ page, limit }) => 
        axios.get(`/api/expenses?page=${page}&limit=${limit}`),
      pageSize: 100,           // 每页100条记录
      maxConcurrent: 3,        // 最多3个并发请求
      signal: paginationController.signal,
      onProgress: (progressData) => {
        console.log(`数据加载进度: ${progressData.progress}% (${progressData.loaded}/${progressData.total})`);
        // 可以在这里更新进度条显示
      },
      onError: (error) => {
        console.error('分页加载错误:', error);
        throw error;
      }
    });

    // 确保数据格式正确
    Expenses.value = allData
      .map(item => ({
        type: item.type?.trim() || item.type,
        remark: item.remark?.trim() || item.remark,
        amount: Number(item.amount),
        date: item.date
      }))
      .filter(item => !isNaN(item.amount) && item.amount > 0);

    if (Expenses.value.length === 0) {
      console.warn('loadExpenses: No valid data found in API response');
    } else {
      console.log('loadExpenses: 分页加载完成, count:', Expenses.value.length);
    }
  } catch (err) {
    if (err.message !== '操作已被取消') {
      const errorInfo = err.response
        ? `${err.response.status} ${err.message}: ${JSON.stringify(err.response.data)}`
        : err.message;
      errorMessage.value = t('common.loadFailed', { error: errorInfo });
      error.value = errorMessage.value;

      console.error('loadExpenses: Error Details:', err);
      Expenses.value = [];
    } else {
      console.log('数据加载被用户取消');
    }
  } finally {
    isLoading.value = false;
  }
};

// 取消数据加载
const cancelDataLoad = () => {
  if (paginationController) {
    paginationController.abort();
    console.log('数据加载已取消');
  }
};

// 处理AI智能记录生成
const aiFormRef = ref(null);

const handleImageChange = (file, fileList) => {
  aiForm.image = fileList;
};

// 处理AI智能记录取消
const handleAiCancel = () => {
  showAiAddDialog.value = false;
  // 重置AI表单
  aiForm.text = '';
  aiForm.image = [];
};

// 新增：处理全选/取消全选
const handleSelectAllChange = (value) => {
  multiRecords.value.forEach(record => {
    record.selected = value;
  });
};

// 新增：处理单个记录选择变化
const handleRecordSelectChange = () => {
  const allSelected = multiRecords.value.every(record => record.selected);
  const noneSelected = multiRecords.value.every(record => !record.selected);
  
  selectAll.value = allSelected;
  // 处理半选中状态
  if (!allSelected && !noneSelected) {
    selectAll.value = undefined;
  }
};

// 新增：处理多条记录对话框取消
const handleMultiRecordsCancel = () => {
  showMultiRecordsDialog.value = false;
  multiRecords.value = [];
  selectAll.value = false;
};

// 新增：处理多条记录提交
const handleMultiRecordsSubmit = async () => {
  try {
    // 获取所有选中的记录
    const selectedRecords = multiRecords.value.filter(record => record.selected);
    console.log('Multi records submit started:', { recordCount: selectedRecords.length });
    
    if (selectedRecords.length === 0) {
      errorMessage.value = t('expense.selectAtLeastOne');
      return;
    }
    
    // 验证并格式化所有记录
    const validRecords = [];
    for (const record of selectedRecords) {
      // 验证金额
      if (!record.amount || isNaN(record.amount) || Number(record.amount) <= 0) {
        throw new Error(`第${multiRecords.value.indexOf(record) + 1}条记录的金额无效`);
      }
      
      // 验证类型
      if (!record.type) {
        throw new Error(`第${multiRecords.value.indexOf(record) + 1}条记录的类型不能为空`);
      }
      
      // 验证日期
      if (!record.date) {
        throw new Error(`第${multiRecords.value.indexOf(record) + 1}条记录的日期不能为空`);
      }
      
      // 格式化记录
      validRecords.push({
        type: record.type,
        amount: parseFloat(parseFloat(record.amount).toFixed(2)),
        remark: record.remark || '',
        date: record.date // 使用date字段，不再需要time字段
      });
      console.log('Valid record prepared:', { index: validRecords.length, type: record.type, amount: record.amount });
    }
    
    // 检查是否有单笔大于500元的消费
    await checkAndShowLargeExpenseWarning(validRecords);
    
    // 提交所有记录
    for (const record of validRecords) {
      await axios.post('/api/expenses', record, {
        headers: {
          'Content-Type': 'application/json'
        }
      });
    }
    
    // 关闭对话框
    showMultiRecordsDialog.value = false;
    
    // 刷新数据
    await fetchData(true);
    
    successMessage.value = `${validRecords.length}条记录添加成功`;
    console.log('Multi records submit successful:', { totalRecords: validRecords.length });
    
    // 重置数据
    multiRecords.value = [];
    selectAll.value = false;
  } catch (error) {
    console.error('批量添加记录失败:', error);
    console.error('批量添加错误详情:', { message: error.message, stack: error.stack });
    errorMessage.value = `添加记录失败: ${error.message}`;
  }
};

const handleAiGenerate = async () => {
  try {
    // 检查API密钥
    if (!checkApiKey()) {
      console.log('AI generation skipped: API key not configured');
      return;
    }
    
    // 检查是否有输入
    if (!aiForm.text && (!aiForm.image || aiForm.image.length === 0)) {
      console.log('AI generation skipped: No input text or image provided');
      errorMessage.value = '请输入文本描述或上传图片';
      return;
    }
    
    console.log('AI record generation started:', { hasText: !!aiForm.text, hasImage: !!aiForm.image.length });

    isParsing.value = true;
    let parsedDataList;

    // 解析文本或图片
    if (aiForm.text) {
      console.log('Parsing text input for AI generation:', { textPreview: aiForm.text.substring(0, 50) + (aiForm.text.length > 50 ? '...' : '') });
      parsedDataList = await parseTextToRecord(aiForm.text);
    } else if (aiForm.image && aiForm.image.length > 0) {
      console.log('Parsing image input for AI generation:', { fileName: aiForm.image[0].name, size: aiForm.image[0].size });
      parsedDataList = await parseImageToRecord(aiForm.image[0].raw);
    }

    // 处理解析结果
    if (parsedDataList && parsedDataList.length > 0) {
      console.log('AI generation successful:', { recordCount: parsedDataList.length });
      if (parsedDataList.length === 1) {
        // 只有一条记录，保持原有逻辑
        const parsedData = parsedDataList[0];
        console.log('Single record generated:', { type: parsedData.type, amount: parsedData.amount, date: parsedData.date });
        form.type = parsedData.type || '';
        form.amount = parsedData.amount || '';
        form.date = parsedData.date || '';
        form.remark = parsedData.remark || '';

        // 关闭AI对话框，打开普通编辑对话框
        showAiAddDialog.value = false;
        showAddDialog.value = true;
        successMessage.value = 'AI已成功生成记录，请检查并确认';
      } else {
        // 多条记录，显示多条记录对话框
        console.log('Multiple records generated:', parsedDataList.map(r => ({ type: r.type, amount: r.amount })));
        multiRecords.value = parsedDataList.map(record => ({
          ...record,
          date: record.date || '',
          amount: record.amount || ''
        }));
        showAiAddDialog.value = false;
        showMultiRecordsDialog.value = true;
        successMessage.value = `AI已成功生成${parsedDataList.length}条记录，请检查并确认`;
      }
    }
  } catch (error) {
    console.error('AI生成记录失败:', error);
    console.error('AI generation error details:', { message: error.message, stack: error.stack });
    errorMessage.value = 'AI生成记录失败，请重试';
  } finally {
    isParsing.value = false;
    // 重置AI表单
    aiForm.text = '';
    aiForm.image = [];
  }
};

// 处理AI报告生成
const handleGenerateReport = async () => {
  try {
    // 检查API密钥
    if (!checkApiKey()) {
      console.log('Report generation skipped: API key not configured');
      return;
    }
    
    // 检查是否有消费数据
    if (!Expenses || Expenses.length === 0) {
      console.log('Report generation skipped: No expense data available');
      errorMessage.value = '没有足够的消费数据来生成报告';
      return;
    }
    
    console.log('AI report generation started:', { 
      question: reportQuestion.value,
      recordCount: Expenses.value.length 
    });

    isGeneratingReport.value = true;
    reportContent.value = '';
    
    // 生成报告
    console.log('Calling expense report generation API');
    const content = await generateExpenseReport(Expenses.value, reportQuestion.value);
    reportContent.value = content;
    console.log('AI report generation successful:', { contentLength: content.length });
    
    successMessage.value = 'AI已成功生成消费报告';
  } catch (error) {
    console.error('AI生成报告失败:', error);
    console.error('AI report generation error details:', { message: error.message, stack: error.stack });
    errorMessage.value = 'AI生成报告失败，请重试';
  } finally {
    isGeneratingReport.value = false;
  }
};

// 清空报告问题
const clearReportQuestion = () => {
  reportQuestion.value = '';
};

// 处理API密钥设置
const handleApiKeySave = () => {
  if (apiKeyForm.apiKey) {
    console.log('API key save requested');
    localStorage.setItem('siliconflow_api_key', apiKeyForm.apiKey);
    setApiKey(apiKeyForm.apiKey);
    showApiKeyDialog.value = false;
    successMessage.value = 'API密钥已保存';
    console.log('API key saved successfully');
  } else {
    console.log('API key save failed: Empty key provided');
    errorMessage.value = '请输入有效的API密钥';
  }
};

// 检查是否已设置API密钥
const checkApiKey = () => {
  const savedApiKey = localStorage.getItem('siliconflow_api_key');
  if (!savedApiKey) {
    errorMessage.value = '请先设置SiliconFlow API密钥';
    showApiKeyDialog.value = true;
    return false;
  }
  setApiKey(savedApiKey);
  return true;
};

// 处理反馈按钮点击事件
const handleFeedback = () => {
  try {
    const feedbackUrl = 'https://wj.qq.com/s2/24109109/3572/';
    window.open(feedbackUrl, '_blank');
  } catch (error) {
    console.error('打开反馈链接失败:', error);
    errorMessage.value = '打开反馈链接失败，请重试';
  }
};

// 跳转到如何使用页面
const goToHowToUse = () => {
  window.open('/how-to-use/how-to-use.html', '_blank');
};

// Function to force the browser to re-fetch new frontend data
const refreshPage = () => {
  // Force a full reload to bypass cache and fetch fresh data
  // Add a timestamp parameter to ensure the cache is invalidated
  if (window.location.reload) {
    window.location.href = window.location.href.split('?')[0] + '?t=' + new Date().getTime();
    window.location.reload(true);
  }
}


</script>

<style scoped>
/* 定义 CSS 变量 */
:root {
  /* 弹窗样式变量 */
  --popup-bg: rgba(0,0,0,0.5);
  --popup-content-bg: #fff;
  --popup-btn-bg: #4CAF50;
  --popup-btn-color: white;
  --text-primary: #333;
  --text-secondary: #666;
  --bg-primary: #fff;
  --border-primary: #e0e0e0;
  --primary-color: #4CAF50;
  --error-bg: #ffebee;
  --error-border: #ffcdd2;
  --donation-modal-overlay: rgba(0, 0, 0, 0.8);
}

/* API密钥提示框样式 */
.api-key-prompt {
  margin-top: 16px;
  padding: 12px;
  background-color: #f0f2f5;
  border-radius: 4px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.api-key-prompt span {
  font-size: 14px;
  color: #606266;
}

@media (prefers-color-scheme: dark) {
.api-key-prompt {
  background-color: #2a3142;
}

.api-key-prompt span {
  color: #a0aec0;
}
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 2rem 1rem;
  color: var(--text-primary);
  background: transparent;
  transition: all 0.3s ease;
}

/* 华丽七彩欢迎文本样式 */
.welcome-text {
  position: relative;
  text-align: center;
  font-size: 2.5rem;
  font-weight: 800;
  margin: 20px 0;
  padding: 20px 40px;
  letter-spacing: 2px;
  background: linear-gradient(90deg, 
    #ff0000, #ff7f00, #ffff00, #00ff00, 
    #0000ff, #4b0082, #9400d3, #ff0000);
  background-size: 400% 100%;
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  animation: rainbowText 3s linear infinite, float 6s ease-in-out infinite;
  text-shadow: 0 0 10px rgba(255, 255, 255, 0.3);
  z-index: 2;
}

.welcome-text::before {
  content: '';
  position: absolute;
  top: -5px;
  left: -5px;
  right: -5px;
  bottom: -5px;
  background: inherit;
  background-size: 400% 100%;
  filter: blur(20px);
  opacity: 0.7;
  z-index: -1;
  animation: rainbowText 3s linear infinite;
}

.welcome-text::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(45deg, 
    rgba(255, 0, 0, 0.2), 
    rgba(255, 127, 0, 0.2), 
    rgba(255, 255, 0, 0.2), 
    rgba(0, 255, 0, 0.2), 
    rgba(0, 0, 255, 0.2), 
    rgba(75, 0, 130, 0.2), 
    rgba(148, 0, 211, 0.2));
  background-size: 400% 400%;
  border-radius: 15px;
  z-index: -2;
  animation: gradientShift 8s ease infinite;
}

.sparkle {
  position: absolute;
  width: 4px;
  height: 4px;
  background: white;
  border-radius: 50%;
  box-shadow: 0 0 10px 2px rgba(255, 255, 255, 0.8);
  opacity: 0;
  z-index: 1;
}

.pulse-ring {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 100%;
  height: 100%;
  border: 2px solid rgba(255, 255, 255, 0.2);
  border-radius: 15px;
  opacity: 0;
  z-index: -1;
}

@keyframes rainbowText {
  0% {
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0% 50%;
  }
}

@keyframes float {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-15px);
  }
}

@keyframes gradientShift {
  0%, 100% {
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
}

@keyframes sparkleAnimation {
  0%, 100% {
    opacity: 0;
    transform: scale(0);
  }
  50% {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes pulse {
  0% {
    transform: translate(-50%, -50%) scale(1);
    opacity: 0.8;
  }
  100% {
    transform: translate(-50%, -50%) scale(1.5);
    opacity: 0;
  }
}

.error-alert {
  padding: 1rem;
  margin-bottom: 1rem;
  background: var(--error-bg);
  border: 1px solid var(--error-border);
  border-radius: 8px;
  color: #d32f2f;
}

.btn {
  padding: 0.5rem 1rem;
  border-radius: 8px;
  transition: all 0.2s ease;
  cursor: pointer; /* 添加手势 */
}

.prev-btn, .next-btn {
  background: var(--primary-color);
  color: white;
  border: none;
}

.chart-btn {
  background: rgba(76, 175, 80, 0.1);
  color: var(--text-primary);
  border: 1px solid var(--border-primary);
}

.chart-btn.active {
  background: var(--primary-color);
  color: white;
  border-color: transparent;
}

.no-data {
  text-align: center;
  color: var(--text-secondary);
  padding: 2rem;
  min-height: 120px;
}

@media (max-width: 768px) {
  .container {
    padding: 1rem;
  }

  .chart-controls {
    margin: 1.5rem 0;
  }

  .month-label {
    font-size: 1rem;
  }

  .btn {
    padding: 0.4rem 0.8rem;
    font-size: 0.9rem;
  }
}

/* 过渡动画 */
.chart-enter-active,
.chart-leave-active {
  transition: opacity 0.5s ease;
}

.chart-enter-from,
.chart-leave-to {
  opacity: 0;
}

.button-enter-active,
.button-leave-active {
  transition: opacity 0.5s ease;
}

.button-enter-from,
.button-leave-to {
  opacity: 0;
}
/* 日期时间显示样式 */
.datetime-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin: 1.5rem 0;
  padding: 1rem;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.datetime-container:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.1);
}

.date-part {
  font-size: 1.2rem;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 0.5rem;
  letter-spacing: 0.5px;
}

.time-part {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--primary-color);
  letter-spacing: 1px;
  animation: timePulse 1s ease-in-out infinite;
}

/* 深色模式适配 */
@media (prefers-color-scheme: dark) {
  .datetime-container {
    background: rgba(0, 0, 0, 0.3);
  }
  
  .date-part {
    color: #e5e7eb;
  }
  
  .time-part {
    color: #4ade80;
  }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .datetime-container {
    margin: 1rem 0;
    padding: 0.8rem;
  }
  
  .date-part {
    font-size: 1rem;
  }
  
  .time-part {
    font-size: 1.2rem;
  }
}

/* 输入框容器（修正选择器确保生效） */
.confirm-input-container {
  position: relative;
  margin: 1.5rem 0;
  width: 100%;
  max-width: 300px;
}

/* 输入框基础样式（添加组件作用域前缀） */
.confirm-input {
  width: 90%;
  padding: 12px 16px;
  font-size: 1rem;
  background: var(--bg-primary);
  border: 2px solid var(--border-primary);
  border-radius: 8px;
  outline: none;
  transition: all 0.3s ease;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.05);
  color: var(--text-primary);
}

/* 输入框占位符样式 */
.confirm-input::placeholder {
  color: var(--text-secondary);
  font-weight: 400;
}

/* 悬停效果 */
.confirm-input:hover {
  border-color: #b0b0b0;
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.08);
}

/* 聚焦效果 */
.confirm-input:focus {
  border-color: #4361ee;
  box-shadow:
    0 4px 12px rgba(67, 97, 238, 0.2),
    0 0 0 3px rgba(67, 97, 238, 0.15);
  transform: translateY(-1px);
}

/* 输入框标签动画 */
.confirm-input-label {
  position: absolute;
  top: 13px;
  left: 15px;
  color: #888;
  pointer-events: none;
  transition: all 0.3s ease;
  background: white;
  padding: 0 4px;
}

.confirm-input:focus + .input-label,
.confirm-input:not(:placeholder-shown) + .input-label {
  top: -8px;
  left: 10px;
  font-size: 0.8rem;
  color: #4361ee;
  font-weight: 600;
}

/* 错误状态 */
.confirm-input-error .custom-input {
  border-color: #f44336;
}

.confirm-input-error .input-label {
  color: #f44336;
}

/* 禁用状态 */
.confirm-input:disabled {
  background: #f8f8f8;
  cursor: not-allowed;
  opacity: 0.7;
}

/* 悬浮刷新按钮样式 */
.floating-refresh-btn {
  position: fixed;
  bottom: 30px;
  right: 30px;
  z-index: 1000;
}

/* 全局强制捐款弹窗样式 */
.donation-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: #000000;
  opacity: 0.95;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 999999; /* 确保在最顶层 */
  overflow: hidden;
}

.donation-modal-content {
  background: var(--popup-content-bg);
  padding: 2.5rem;
  border-radius: 12px;
  width: 90%;
  max-width: 500px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.3);
  text-align: center;
  animation: donationModalAppear 0.5s ease-out;
}

@keyframes donationModalAppear {
  from {
    opacity: 0;
    transform: translateY(-50px) scale(0.9);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.donation-modal-title {
  font-size: 1.8rem;
  color: white;
  margin-bottom: 1.5rem;
  font-weight: 600;
}

.donation-modal-message {
  font-size: 1.1rem;
  color: white;
  margin-bottom: 2rem;
  line-height: 1.6;
}

.donation-amount-container {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  margin-bottom: 2rem;
}

.donation-amount-input {
  width: 120px;
  padding: 0.75rem 1rem;
  font-size: 1.2rem;
  border: 2px solid var(--border-primary);
  border-radius: 6px;
  text-align: center;
  transition: all 0.3s ease;
}

.donation-amount-input:focus {
  outline: none;
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(76, 175, 80, 0.2);
}

.donation-currency {
  font-size: 1.2rem;
  color: white;
}

.donation-modal-footer {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
}

.donation-modal-footer button {
  padding: 0.75rem 2rem;
  font-size: 1.1rem;
  border-radius: 8px;
  width: 100%;
  max-width: 320px;
  justify-content: center;
}

/* 阻止背景滚动 */
body.donation-modal-open {
  overflow: hidden;
}

/* AI报告内容的Markdown样式 */
.report-content {
  line-height: 1.6;
  padding: 10px 0;
}

.report-content h1,
.report-content h2,
.report-content h3,
.report-content h4,
.report-content h5,
.report-content h6 {
  margin-top: 1.5em;
  margin-bottom: 0.5em;
  font-weight: 600;
  color: var(--text-primary);
}

.report-content h1 {
  font-size: 1.8em;
  border-bottom: 1px solid #eee;
  padding-bottom: 0.3em;
}

.report-content h2 {
  font-size: 1.5em;
}

.report-content h3 {
  font-size: 1.2em;
}

.report-content p {
  margin-bottom: 1em;
  color: var(--text-primary);
}

.report-content ul,
.report-content ol {
  margin-left: 2em;
  margin-bottom: 1em;
  color: var(--text-primary);
}

.report-content li {
  margin-bottom: 0.5em;
}

.report-content strong {
  font-weight: 600;
}

.report-content em {
  font-style: italic;
}

.report-content code {
  background-color: #f5f5f5;
  padding: 0.2em 0.4em;
  border-radius: 3px;
  font-family: 'Courier New', Courier, monospace;
  font-size: 0.9em;
}

.report-content pre {
  background-color: #f5f5f5;
  padding: 1em;
  border-radius: 4px;
  overflow-x: auto;
  margin-bottom: 1em;
  font-family: 'Courier New', Courier, monospace;
}

.report-content pre code {
  background-color: transparent;
  padding: 0;
}

.report-content blockquote {
  border-left: 4px solid #ddd;
  padding-left: 1em;
  color: #666;
  margin-left: 0;
  margin-right: 0;
  margin-bottom: 1em;
}

.report-content table {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 1em;
}

.report-content th,
.report-content td {
  padding: 8px 12px;
  border: 1px solid #ddd;
}

.report-content th {
  background-color: #f9f9f9;
  font-weight: 600;
}

.report-content tr:nth-child(even) {
  background-color: #f9f9f9;
}

/* 深色模式适配 */
@media (prefers-color-scheme: dark) {
  .report-content h1,
  .report-content h2,
  .report-content h3,
  .report-content p,
  .report-content ul,
  .report-content ol {
    color: #e5e7eb;
  }
  
  .report-content h1 {
    border-bottom-color: rgba(255, 255, 255, 0.1);
  }
  
  .report-content code,
  .report-content pre {
    background-color: rgba(255, 255, 255, 0.1);
  }
  
  .report-content blockquote {
    border-left-color: rgba(255, 255, 255, 0.2);
    color: #9ca3af;
  }
  
  .report-content th {
    background-color: rgba(255, 255, 255, 0.05);
  }
  
  .report-content tr:nth-child(even) {
    background-color: rgba(255, 255, 255, 0.05);
  }
  
  .report-content th,
  .report-content td {
    border-color: rgba(255, 255, 255, 0.1);
  }
}
/* 自定义对话框样式 */
.custom-dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.custom-dialog {
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.15);
  width: 90%;
  max-width: 500px;
  max-height: 90vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  transition: all 0.3s ease;
}

.custom-dialog.dark-theme {
  background: #2d3748;
  color: #e2e8f0;
}

/* 对话框动画 */
.dialog-fade-enter-active,
.dialog-fade-leave-active {
  transition: all 0.3s ease;
}

.dialog-fade-enter-from,
.dialog-fade-leave-to {
  opacity: 0;
}

/* 背景遮罩动画 */
.custom-dialog-overlay .custom-dialog0 {
  transform: scale(1);
  transition: transform 0.3s ease 0.1s;
}

.dialog-fade-enter-active .custom-dialog {
  transform: scale(1);
  transition: all 0.3s ease;
}

.dialog-fade-enter-from .custom-dialog {
  transform: scale(0.7); /* 减小初始缩放比例，使动画更明显 */
}

.dialog-fade-leave-active .custom-dialog {
  transform: scale(0.7) translateY(-20px);
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid #e2e8f0;
}

.custom-dialog.dark-theme .dialog-header {
  border-bottom-color: #4a5568;
}

.dialog-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1a202c;
}

.custom-dialog.dark-theme .dialog-title {
  color: #f7fafc;
}

.dialog-close-btn {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: #718096;
  padding: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: all 0.2s ease;
}

.dialog-close-btn:hover {
  background-color: #f7fafc;
  color: #4a5568;
}

.custom-dialog.dark-theme .dialog-close-btn:hover {
  background-color: #4a5568;
  color: #e2e8f0;
}

.dialog-body {
  padding: 24px;
  overflow-y: auto;
  flex: 1;
}

.custom-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-label {
  font-size: 14px;
  font-weight: 500;
  color: #2d3748;
}

.custom-dialog.dark-theme .form-label {
  color: #e2e8f0;
}

.form-label.error {
  color: #e53e3e;
}

.form-input,
.form-textarea {
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 14px;
  transition: all 0.2s ease;
  background-color: #ffffff;
  color: #1a202c;
}

.custom-dialog.dark-theme .form-select,
.custom-dialog.dark-theme .form-input,
.custom-dialog.dark-theme .form-textarea {
  background-color: #4a5568;
  border-color: #718096;
  color: #e2e8f0;
}

.form-select:hover,
.form-input:hover,
.form-textarea:hover {
  border-color: #cbd5e0;
}

.custom-dialog.dark-theme .form-select:hover,
.custom-dialog.dark-theme .form-input:hover,
.custom-dialog.dark-theme .form-textarea:hover {
  border-color: #a0aec0;
}

.form-select:focus,
.form-input:focus,
.form-textarea:focus {
  outline: none;
  border-color: #4299e1;
  box-shadow: 0 0 0 3px rgba(66, 153, 225, 0.1);
}

.custom-dialog.dark-theme .form-select:focus,
.custom-dialog.dark-theme .form-input:focus,
.custom-dialog.dark-theme .form-textarea:focus {
  box-shadow: 0 0 0 3px rgba(66, 153, 225, 0.2);
}

.form-select.error,
.form-input.error {
  border-color: #e53e3e;
}

.form-select.error:focus,
.form-input.error:focus {
  box-shadow: 0 0 0 3px rgba(229, 62, 62, 0.1);
}

.custom-dialog.dark-theme .form-select.error:focus,
.custom-dialog.dark-theme .form-input.error:focus {
  box-shadow: 0 0 0 3px rgba(229, 62, 62, 0.2);
}

.form-textarea {
  resize: vertical;
  min-height: 80px;
}

.error-message {
  font-size: 12px;
  color: #e53e3e;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid #e2e8f0;
}

.custom-dialog.dark-theme .dialog-footer {
  border-top-color: #4a5568;
}

.btn {
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  border: none;
  outline: none;
}

.btn-secondary {
  background-color: #f7fafc;
  color: #4a5568;
}

.btn-secondary:hover {
  background-color: #edf2f7;
}

.custom-dialog.dark-theme .btn-secondary {
  background-color: #4a5568;
  color: #e2e8f0;
}

.custom-dialog.dark-theme .btn-secondary:hover {
  background-color: #718096;
}

.btn-primary {
  background-color: #4299e1;
  color: #ffffff;
}

.btn-primary:hover {
  background-color: #3182ce;
}

.custom-dialog.dark-theme .btn-primary {
  background-color: #3182ce;
}

.custom-dialog.dark-theme .btn-primary:hover {
  background-color: #2c5282;
}

/* 功能组样式 */
.function-section {
  margin-bottom: 20px;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 20px;
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-content {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

/* 响应式设计 */
@media (max-width: 640px) {
  .custom-dialog {
    width: 95%;
    margin: 20px;
  }
  
  .dialog-header,
  .dialog-body,
  .dialog-footer {
    padding: 16px;
  }
  
  .dialog-title {
    font-size: 16px;
  }
  
  .btn {
    padding: 8px 16px;
    font-size: 13px;
  }

  .welcome-text {
    font-size: 16px;
    font-weight: 600;
  }
  
  /* 手机端样式 */
  .card-grid {
    display: none; /* 隐藏桌面端网格布局 */
  }
  
  .mobile-selector {
    margin-bottom: 20px;
  }
  
  .mobile-buttons {
    display: block;
  }
  
  /* 手机端按钮组样式 */
  .mobile-button-group {
    display: flex;
    flex-direction: column;
    gap: 15px;
    width: 100%;
    margin: 0;
    padding: 0;
  }
  
  /* 确保所有按钮大小一致 */
  .mobile-btn {
    width: 100%;
    justify-content: center;
    align-items: center;
    padding: 14px 20px;
    font-size: 16px;
    line-height: 1.5;
    white-space: nowrap;
    margin: 0;
    box-sizing: border-box;
    min-height: 48px;
  }
}

/* 桌面端样式 */
@media (min-width: 641px) {
  .mobile-selector {
    display: none; /* 隐藏手机端选单 */
  }
  
  .mobile-buttons {
    display: none; /* 隐藏手机端按钮 */
  }
}
</style>
