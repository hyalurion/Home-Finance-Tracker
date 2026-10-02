<template>
  <!-- Fixed Header outside .container — avoids flex-gap overlap with first row -->
  <Header :title="$t('app.title')" />

  <div class="container">
    <!-- Main dialogs -->
    <div v-if="isLoading" class="loading-alert">{{ t('app.loading') }}</div>
    <div v-if="error" class="error-alert">{{ error }}</div>
    <MessageTip v-model:message="successMessage" type="success" />
    <MessageTip v-model:message="errorMessage" type="error" />

    <!-- Function group grid layout -->
    <div class="function-section">
      <!-- Mobile selector -->
      <div class="mobile-selector">
        <CustomSelect
          v-model="selectedFunctionGroup"
          :options="functionGroups"
          :include-empty-option="false"
          style="width: 100%;"
        />
      </div>
      
      <!-- Desktop grid layout -->
      <div class="card-grid">
        <!-- Primary function group -->
        <GlassCard :title="t('function.primary')">
          <div class="card-content">
            <GlassButton type="primary" @click="showAddDialog = true">
              <template #icon><FontAwesomeIcon icon="plus" /></template>
              {{ t('expense.addRecord') }}
            </GlassButton>
            <GlassButton type="primary" @click="goToCharts">
              <template #icon><FontAwesomeIcon icon="chart-pie" /></template>
              {{ t('chart.title') }}
            </GlassButton>
          </div>
        </GlassCard>
        
        <!-- AI function group -->
        <GlassCard :title="t('function.aiFeatures')">
          <div class="card-content">
            <GlassButton type="primary" @click="showAiAddDialog = true">
              <template #icon><FontAwesomeIcon icon="microchip" /></template>
              {{ t('ai.smartRecord.title') }}
            </GlassButton>
            <GlassButton type="primary" @click="showAiReportDialog = true">
              <template #icon><FontAwesomeIcon icon="file-alt" /></template>
              {{ t('ai.report.title') }}
            </GlassButton>
          </div>
        </GlassCard>
      </div>
      
      <!-- Mobile buttons -->
      <div class="mobile-buttons">
        <!-- Primary function group buttons -->
        <div v-if="selectedFunctionGroup === 'primary'" class="mobile-button-group">
          <GlassButton type="primary" @click="showAddDialog = true" size="large" class="mobile-btn" >
            <template #icon><FontAwesomeIcon icon="plus" /></template>
            {{ t('expense.addRecord') }}
          </GlassButton>
          <GlassButton type="primary" @click="goToCharts" size="large" class="mobile-btn">
            <template #icon><FontAwesomeIcon icon="chart-pie" /></template>
            {{ t('chart.title') }}
          </GlassButton>
        </div>
        
        <!-- AI function group buttons -->
        <div v-else-if="selectedFunctionGroup === 'ai'" class="mobile-button-group">
          <GlassButton type="primary" @click="showAiAddDialog = true" size="large" class="mobile-btn">
            <template #icon><FontAwesomeIcon icon="microchip" /></template>
            {{ t('ai.smartRecord.title') }}
          </GlassButton>
          <GlassButton type="primary" @click="showAiReportDialog = true" size="large" class="mobile-btn">
            <template #icon><FontAwesomeIcon icon="file-alt" /></template>
            {{ t('ai.report.title') }}
          </GlassButton>
        </div>
      </div>
    </div>

    <!-- Monthly spending limit display -->
    <SpendingLimitDisplay :expenses="Expenses" />
    
    <ExpenseList
      :refresh-trigger="refreshTrigger"
      @edit="handleEditExpense"
      @delete="handleDeleteExpense"
      @data-loaded="handleDataLoaded"
    />
    <div :class="['header']"></div>
    <Transition name="button">
      <div v-if="expenseListTotal > 0 || Expenses.length > 0" class="export-buttons-container">
        <div class="export-buttons-group">
          <CustomUpload
            class="upload-excel"
            action="/api/import/excel"
            :show-file-list="false"
            @success="handleImportSuccess"
            @error="handleImportError"
            accept=".xlsx, .xls"
          >
            <template #default="{ triggerUpload }">
              <GlassButton type="warning" @click="triggerUpload">
                <template #icon><FontAwesomeIcon icon="upload" /></template>
                {{ t('import.title') }}
              </GlassButton>
            </template>
          </CustomUpload>
          <ExportButton
            @export-excel="exportToExcel"
          />
          <GlassButton type="primary" @click="exportMonthData">
            <template #icon><FontAwesomeIcon icon="download" /></template>
            {{ t('export.monthData') }}
          </GlassButton>
          <GlassButton type="secondary" @click="goToRecycleBin">
            <template #icon><FontAwesomeIcon icon="trash-alt" /></template>
            {{ t('common.trash') }}
          </GlassButton>
        </div>
      </div>
      <div v-else class="no-data">{{ t('home.noDataForExport') }}</div>
    </Transition>
  </div>

  <!-- Custom add-record dialog -->
  <transition name="dialog-fade">
    <div v-if="showAddDialog" class="custom-dialog-overlay" @click.self="closeAddDialog">
      <div class="custom-dialog">
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

  <!-- Edit expense record dialog -->
  <transition name="dialog-fade">
    <div v-if="showEditDialog" class="custom-dialog-overlay" @click.self="showEditDialog = false">
      <div class="custom-dialog">
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
  
  <!-- Delete confirmation dialog -->
  <transition name="dialog-fade">
    <div v-if="showDeleteDialog" class="custom-dialog-overlay" @click.self="showDeleteDialog = false">
      <div class="custom-dialog">
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

  <!-- AI smart-record dialog: the app only produces a prompt, the description
       and the receipt image are typed/attached by the user inside the AI app -->
  <GlassDialog v-model:visible="showAiAddDialog" :title="t('ai.smartRecord.title')" width="80%">
    <div class="ai-step">
      <div class="ai-handoff-hint">
        <ol>
          <li>{{ t('ai.smartRecord.step1') }}</li>
          <li>{{ t('ai.smartRecord.step2') }}</li>
          <li>{{ t('ai.smartRecord.step3') }}</li>
        </ol>
      </div>
      <GlassFormItem :label="t('ai.smartRecord.promptLabel')">
        <textarea v-model="aiPrompt" class="ai-prompt-box ai-prompt-box--readonly" readonly rows="14"></textarea>
        <div class="ai-prompt-actions">
          <GlassButton
            type="primary"
            size="small"
            @click="copyText(aiPrompt, t('ai.smartRecord.copyButton'))"
          >{{ t('ai.smartRecord.copyButton') }}</GlassButton>
        </div>
      </GlassFormItem>
      <GlassFormItem :label="t('ai.smartRecord.replyLabel')">
        <!-- The sample JSON inside `ai.smartRecord.replyPlaceholder` keeps its
             braces escaped as \{ \} in the locale files: vue-i18n parses a bare
             `{name}` as an interpolation token and would refuse to compile. -->
        <GlassInput
          v-model="aiReply"
          type="textarea"
          :rows="8"
          :placeholder="t('ai.smartRecord.replyPlaceholder')"
        ></GlassInput>
      </GlassFormItem>
    </div>

    <!-- A named slot template must stay a direct child of the dialog: nesting one
         inside a v-if / v-else branch breaks the template compiler. -->
    <template #footer>
      <GlassButton @click="handleAiCancel">{{ t('common.cancel') }}</GlassButton>
      <GlassButton type="primary" @click="handleAiGenerate" :disabled="isParsing">
        {{ isParsing ? t('ai.smartRecord.parsing') : t('ai.smartRecord.submit') }}
      </GlassButton>
    </template>
  </GlassDialog>

  <!-- Multi-record edit dialog -->
  <GlassDialog v-model:visible="showMultiRecordsDialog" title="AI生成的多条记录" width="90%">
    <div v-if="multiRecords.length === 0" class="no-records">
      {{ t('expense.noRecords') }}
    </div>
    <div v-else class="multi-records-container">
      <!-- Select-all feature -->
      <div class="select-all-container" style="margin-bottom: 20px;">
        <GlassCheckbox v-model="selectAll" @change="handleSelectAllChange">{{ t('common.selectAll') }}</GlassCheckbox>
      </div>
      
      <!-- Record list -->
      <div v-for="(record, index) in multiRecords" :key="index" class="record-item" style="margin-bottom: 15px; padding: 15px; border: 1px solid #e4e7ed; border-radius: 4px;">
        <div style="display: flex; align-items: center; margin-bottom: 10px;">
          <GlassCheckbox v-model="record.selected" @change="handleRecordSelectChange"></GlassCheckbox>
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
            <GlassInput v-model="record.amount" :placeholder="0" type="text" style="width: 100%;" />
          </div>
          <div style="flex: 1; min-width: 200px;">
            <label style="display: block; margin-bottom: 5px;">{{ t('expense.date') }}:</label>
            <input type="date" v-model="record.date" :placeholder="t('expense.selectDate')" class="input__inner" style="width: 100%;">
          </div>
        </div>
        <div style="margin-top: 15px;">
          <label style="display: block; margin-bottom: 5px;">{{ t('expense.remark') }}:</label>
          <GlassInput v-model="record.remark" :placeholder="t('expense.enterRemark')" type="textarea" :rows="2" style="width: 100%;"></GlassInput>
        </div>
      </div>
    </div>
    <template #footer>
      <GlassButton @click="handleMultiRecordsCancel">{{ t('common.cancel') }}</GlassButton>
      <GlassButton type="primary" @click="handleMultiRecordsSubmit">{{ t('common.submit') }} ({{ selectedRecordsCount }}/{{ multiRecords.length }})</GlassButton>
    </template>
  </GlassDialog>

  <!-- AI spending Q&A dialog: only the export + the prompt live here, the
       question itself is typed by the user inside the third-party AI app -->
  <GlassDialog v-model:visible="showAiReportDialog" :title="t('ai.report.title')" width="90%" height="80vh">
    <div class="ai-report-container">
      <!-- Data filter section -->
      <div class="report-filter-section">
        <AIReportFilter
          @filter-change="handleFilterChange"
        />
      </div>

      <!-- Export + prompt hand-off buttons -->
      <div class="ai-report-actions">
        <GlassButton @click="handleDownloadAIData" :disabled="isDownloadingData">
          {{ isDownloadingData ? t('ai.report.downloading') : t('ai.report.downloadButton') }}
        </GlassButton>
        <GlassButton type="primary" @click="handleGenerateReport">
          {{ t('ai.report.generateButton') }}
        </GlassButton>
      </div>

      <!-- Hand-off instructions -->
      <div v-if="!reportPrompt && !reportContent" class="ai-handoff-hint">
        <ol>
          <li>{{ t('ai.report.hint1', { file: aiXlsxFileName || t('ai.report.defaultFileName') }) }}</li>
          <li>{{ t('ai.report.hint2') }}</li>
          <li>{{ t('ai.report.hint3') }}</li>
          <li>{{ t('ai.report.hint4') }}</li>
        </ol>
      </div>

      <!-- Prompt + paste-back area -->
      <div v-if="reportPrompt" class="report-prompt-section">
        <div class="ai-handoff-hint">
          {{ t('ai.report.promptHint', { file: aiXlsxFileName || '-' }) }}
        </div>
        <GlassFormItem :label="t('ai.report.promptLabel')">
          <textarea v-model="reportPrompt" class="ai-prompt-box ai-prompt-box--readonly" readonly rows="14"></textarea>
          <div class="ai-prompt-actions">
            <GlassButton
              type="primary"
              size="small"
              @click="copyText(reportPrompt, t('ai.report.copyButton'))"
            >{{ t('ai.report.copyButton') }}</GlassButton>
          </div>
        </GlassFormItem>
        <GlassFormItem :label="t('ai.report.replyLabel')">
          <GlassInput
            v-model="reportReply"
            type="textarea"
            :rows="8"
            :placeholder="t('ai.report.replyPlaceholder')"
          ></GlassInput>
        </GlassFormItem>
        <div class="ai-report-actions">
          <GlassButton @click="clearReportHandoff">{{ t('ai.report.clearButton') }}</GlassButton>
          <GlassButton type="primary" @click="handleRenderReport">{{ t('ai.report.pasteButton') }}</GlassButton>
        </div>
      </div>

      <!-- Report content display section -->
      <div class="report-content-section">
        <div v-if="!reportContent" class="no-report-content">
          {{ t('ai.report.emptyContent') }}
        </div>
        <div v-else class="report-content" v-html="renderedReportContent">
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
import GlassCheckbox from '@/components/GlassCheckbox.vue';

import axios from 'axios';
import { ref, computed, onMounted, reactive, watch } from 'vue';
import { marked } from 'marked';

import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import i18n from '@/locales/i18n';
import CustomSelect from '@/components/CustomSelect.vue';

import { useExpenseData } from '@/composables/useExpenseData';
import { fetchAllPages, createCancellationController } from '@/utils/pagination';
import { ExpenseAPI } from '@/api/expenses';

import MessageTip from '@/components/MessageTip.vue';
import Header from '@/components/Header.vue';
import ExpenseList from '@/components/ExpenseList.vue';
import ExportButton from '@/components/ExportButton.vue';
import MarkdownDialog from '@/components/MarkdownDialog.vue';
import SpendingLimitDisplay from '@/components/SpendingLimitDisplay.vue';
import GlassCard from '@/components/GlassCard.vue';
import GlassButton from '@/components/GlassButton.vue';
import GlassInput from '@/components/GlassInput.vue';
import CustomUpload from '@/components/CustomUpload.vue';
import AIReportFilter from '@/components/AIReportFilter.vue';

const { t } = useI18n();
const router = useRouter();

// Play an alert sound - supports looping for multiple seconds
const playAlertSound = (duration = 5) => {
  try {
    const audioContext = new (window.AudioContext || window.webkitAudioContext)();
    const oscillator = audioContext.createOscillator();
    const gainNode = audioContext.createGain();
    
    oscillator.connect(gainNode);
    gainNode.connect(audioContext.destination);
    
    oscillator.type = 'sine';
    gainNode.gain.setValueAtTime(0.1, audioContext.currentTime);
    
    // Create a looping effect
    let currentTime = audioContext.currentTime;
    const cycleDuration = 1; // Duration of each loop (seconds)
    const cycles = Math.ceil(duration / cycleDuration);
    
    // Generate multiple cycles with varying frequency
    for (let i = 0; i < cycles; i++) {
      const startTime = currentTime + i * cycleDuration;
      
      // Set rising and falling pitch changes
      oscillator.frequency.setValueAtTime(1000, startTime);
      oscillator.frequency.exponentialRampToValueAtTime(1200, startTime + 0.3);
      oscillator.frequency.exponentialRampToValueAtTime(800, startTime + 0.7);
      oscillator.frequency.exponentialRampToValueAtTime(1000, startTime + 1);
    }
    
    // Fade out at the end
    gainNode.gain.setValueAtTime(0.1, audioContext.currentTime + duration - 0.5);
    gainNode.gain.exponentialRampToValueAtTime(0.001, audioContext.currentTime + duration);
    
    oscillator.start(audioContext.currentTime);
    oscillator.stop(audioContext.currentTime + duration);
  } catch (error) {
    console.error('播放警报声失败:', error);
  }
}

// Show a large-expense warning - supports a callback
let warningCallback = null;
const showLargeExpenseWarning = (callback = null) => {
  playAlertSound(5); // Play a 5-second looping alert sound
  
  // Store the callback function
  warningCallback = callback;
  
  // Create a full-screen warning dialog
  const warningContainer = document.createElement('div');
  warningContainer.id = 'largeExpenseWarning'; // Add an id for later lookup
  warningContainer.style.position = 'fixed';
  warningContainer.style.top = '0';
  warningContainer.style.left = '0';
  warningContainer.style.width = '100%';
  warningContainer.style.height = '100%';
  warningContainer.style.backgroundColor = 'rgba(255, 0, 0, 0.8)';
  warningContainer.style.zIndex = '99999'; // Raise z-index so it stays on top
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
  
  // Add the blink animation styles
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
  confirmButton.style.zIndex = '100000'; // Keep the button on top too
  
  confirmButton.addEventListener('click', () => {
    document.body.removeChild(warningContainer);
    document.head.removeChild(styleSheet);
    // Execute the callback function
    if (typeof warningCallback === 'function') {
      warningCallback();
      warningCallback = null;
    }
  });
  
  // Ensure the warning is visible even behind dialogs
  const dialogs = document.querySelectorAll('.dialog__wrapper');
  dialogs.forEach(dialog => {
    dialog.style.zIndex = '99998'; // Keep dialogs behind the warning
  });
  
  warningContainer.appendChild(warningText);
  warningContainer.appendChild(confirmButton);
  document.body.appendChild(warningContainer);
};

// Check and show the large-expense warning
const checkAndShowLargeExpenseWarning = async (records) => {
  // Check whether any single expense exceeds 500 yuan
  const hasLargeExpense = records.some(record => parseFloat(record.amount) > 500);
  
  if (hasLargeExpense) {
    // Create a Promise that waits for the user to click the confirm button
    return new Promise(resolve => {
      // Use callback-function support
      showLargeExpenseWarning(() => {
        resolve();
      });
      
      // Add a timeout so it continues even if the user never clicks
      setTimeout(() => {
        // Try to find and click the confirm button
        const confirmButton = document.getElementById('largeExpenseConfirmButton');
        if (confirmButton) {
          confirmButton.click();
        } else {
          // If the button is not found, resolve the Promise directly
          resolve();
          // Clean up the warning dialog
          const warningContainer = document.getElementById('largeExpenseWarning');
          const styleSheet = document.getElementById('largeExpenseWarningStyle');
          if (warningContainer) document.body.removeChild(warningContainer);
          if (styleSheet) document.head.removeChild(styleSheet);
          warningCallback = null;
        }
      }, 10000); // 10-second timeout
    });
  }
};

// Button state variables
const showAddDialog = ref(false);
const showMarkdownDialog = ref(false);
const showAiAddDialog = ref(false);
// Added: dialog for showing multiple records
const showMultiRecordsDialog = ref(false);
// Added: AI report dialog
const showAiReportDialog = ref(false);

// Added: edit and delete dialog state
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

// Function group data and selected state (for mobile)
const selectedFunctionGroup = ref('primary');
// Use a computed property so translation values are fetched only after t is initialized
const functionGroups = computed(() => [
  { label: t('function.primary'), value: 'primary' },
  { label: t('function.aiFeatures'), value: 'ai' }
]);
const isParsing = ref(false);
// Added: data structure for storing multiple records
const multiRecords = ref([]);
// Added: select-all state
const selectAll = ref(false);
// Added: report-related state
const isGeneratingReport = ref(false);
const reportContent = ref('');
// Hand-off state for the AI Q&A flow. The question itself is never collected
// here: the user types it inside the third-party AI app after pasting the
// generated prompt.
const reportPrompt = ref('');
const reportReply = ref('');
// Added: filtered statistics data
const filteredStats = ref({
  totalCount: 0,
  totalAmount: 0,
  averageAmount: 0,
  medianAmount: 0,
  minAmount: 0,
  maxAmount: 0,
  filteredExpenses: []
});

// Get the expense data passed to the AI
const aiExpenses = computed(() => {
  return filteredStats.value.filteredExpenses || [];
});

// Configure marked options
marked.setOptions({
  breaks: true,
  gfm: true
});

// Render the report content as HTML
const renderedReportContent = computed(() => {
  return marked.parse(reportContent.value);
});

// Added: count the selected records
const selectedRecordsCount = computed(() => {
  return multiRecords.value.filter(record => record.selected).length;
});

// Import the AI helpers (prompt builders + lenient reply parser, no network)
import { buildAddRecordPrompt, buildReportPrompt, parseAiReply } from '@/api/aiRecord';

// AI hand-off state for the smart-record flow
const aiPrompt = ref('');
const aiReply = ref('');
// The xlsx the user downloads before asking the AI (filename is echoed in the prompt)
const aiXlsxFileName = ref('');
const isDownloadingData = ref(false);

// Copy an arbitrary string via the async clipboard API with a textarea fallback.
const copyText = async (text, label = '内容') => {
  if (!text) return;
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
    } else {
      const ta = document.createElement('textarea');
      ta.value = text;
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.select();
      document.execCommand('copy');
      document.body.removeChild(ta);
    }
    successMessage.value = `${label}已复制到剪贴板`;
  } catch (error) {
    console.error('Copy failed:', error);
    errorMessage.value = `${label}复制失败，请手动选中后复制`;
  }
};

// Navigate to the charts page
const goToCharts = () => {
  router.push('/charts');
};

// Navigate to the recycle bin page
const goToRecycleBin = () => {
  router.push('/recycle-bin');
};

// Handle editing an expense record
const handleEditExpense = (expense) => {
  console.log('Editing expense:', expense);
  editingExpenseId.value = expense.id;
  editingExpense.id = expense.id;
  editingExpense.type = expense.type || '';
  editingExpense.amount = expense.amount || '';
  editingExpense.date = expense.date || ''; // Use the date field value directly
  editingExpense.remark = expense.remark || '';
  
  // Clear the error messages
  editErrors.type = '';
  editErrors.amount = '';
  editErrors.date = '';
  
  showEditDialog.value = true;
};

// Handle deleting an expense record
const handleDeleteExpense = (id) => {
  console.log('Deleting expense:', id);
  editingExpenseId.value = id;
  showDeleteDialog.value = true;
};

// Validate the edit form
const validateEditForm = () => {
  let isValid = true;
  
  // Clear the errors
  editErrors.type = '';
  editErrors.amount = '';
  editErrors.date = '';
  
  // Validate the type
  if (!editingExpense.type.trim()) {
    editErrors.type = t('expense.typeRequired');
    isValid = false;
  }
  
  // Validate the amount
  const amount = parseFloat(editingExpense.amount);
  if (!editingExpense.amount || isNaN(amount) || amount <= 0) {
    editErrors.amount = t('expense.amountRequired');
    isValid = false;
  }
  
  // Validate the date
  if (!editingExpense.date) {
    editErrors.date = t('expense.dateRequired');
    isValid = false;
  }
  
  return isValid;
};

// Confirm editing the expense record
  const confirmEdit = async () => {
    if (!validateEditForm()) {
      return;
    }
    
    try {
      const expenseData = {
        type: editingExpense.type.trim(),
        amount: parseFloat(editingExpense.amount).toFixed(2),
        date: editingExpense.date, // Use the YYYY-MM-DD format directly
        remark: editingExpense.remark.trim()
      };

      console.log('Updating expense:', expenseData);
      await ExpenseAPI.updateExpense(editingExpense.id, expenseData);
    
    successMessage.value = t('expense.updateSuccess');
    showEditDialog.value = false;
    
    // Refresh the expense record list
    refreshTrigger.value++;
  } catch (error) {
    console.error('Update expense failed:', error);
    errorMessage.value = t('expense.updateFailed');
  }
};

// Confirm deleting the expense record
const confirmDelete = async () => {
  try {
    console.log('Deleting expense:', editingExpenseId.value);
    await ExpenseAPI.deleteExpense(editingExpenseId.value);
    
    successMessage.value = t('expense.deleteSuccess');
    showDeleteDialog.value = false;
    
    // Refresh the expense record list
    refreshTrigger.value++;
  } catch (error) {
    console.error('Delete expense failed:', error);
    errorMessage.value = t('expense.deleteFailed');
  }
};

// Import handling
const handleImportSuccess = (response) => {
  const message = response?.message || t('import.success')
  successMessage.value = `${message}${t('import.refreshNotice')}`
  setTimeout(() => {
    refreshPage()
  }, 3000)
}

const handleImportError = (error) => {
  const errorMessageText = error?.message || t('import.failed')
  errorMessage.value = errorMessageText
  console.error('Import error:', error)
}

// Export this month's data as an image
const exportMonthData = () => {
  window.open('/photo.html', '_blank');
};

const markdownContent = ref('');
const markdownTitle = ref('');

// Import the operation-logging utility
import { logUserAction } from '@/utils/operationLogger';

// Dialog-related data
const expenseTypes = ['日常用品', '奢侈品', '通讯费用', '食品', '零食糖果', '冷饮', '方便食品', '纺织品', '饮品', '调味品', '交通出行', '餐饮', '医疗费用', '水果', '其他', '水产品', '乳制品', '礼物人情', '旅行度假', '政务', '水电煤气', '美容美发', '豆制品', '个护美妆', '电子产品', '家用电器', '五金', '服装'];
const form = reactive({
  type: '',
  amount: '',
  date: '',
  remark: ''
});

// Form error state
const formErrors = reactive({
  type: '',
  amount: '',
  date: ''
});

// Close the add dialog
const closeAddDialog = () => {
  // Clear the form errors
  Object.keys(formErrors).forEach(key => {
    formErrors[key] = '';
  });
  showAddDialog.value = false;
};

// Validate the form
const validateForm = () => {
  let isValid = true;
  
  // Clear previous errors
  Object.keys(formErrors).forEach(key => {
    formErrors[key] = '';
  });
  
  // Validate the type
  if (!form.type) {
    formErrors.type = t('expense.selectType');
    isValid = false;
  }
  
  // Validate the amount
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
  
  // Validate the date
  if (!form.date) {
    formErrors.date = t('expense.selectDate');
    isValid = false;
  }
  
  return isValid;
};

const handleAddRecord = async () => {
  try {
    // Log the start of the add-record operation
    logUserAction('record_add_start', { 
      type: form.type, 
      amount: form.amount,
      date: form.date
    });
    
    // Use the custom validation function
    if (!validateForm()) {
      // Form validation failed; the error is already shown in the form
      return;
    }

    // Add detailed logs to trace the date
    console.log('User selected original date:', form.date);
    
    // Format the date as YYYY-MM-DD
    const userSelectedDate = form.date ? new Date(form.date).toISOString().split('T')[0] : '';
    console.log('Formatted user selected date:', userSelectedDate);
    
    // Get today's date for comparison
    const today = new Date().toISOString().split('T')[0];
    console.log('Today\'s date:', today);

    // Build the request payload expected by the API
    const expenseData = {
      type: form.type,
      amount: parseFloat(parseFloat(form.amount).toFixed(2)),
      remark: form.remark,
      date: userSelectedDate // Date field required by the server
    };
    console.log('Data sent to server:', expenseData);

    // Check whether any single expense exceeds 500 yuan
    await checkAndShowLargeExpenseWarning([expenseData]);

    // Use the same batch-submit endpoint as Expenses.vue, explicitly for 1 record
    await axios.post('/api/expenses', expenseData, {
      headers: {
        'Content-Type': 'application/json'
      }
    });
    showAddDialog.value = false;
    // Refresh data after a successful add
    await fetchData(true);
    // Trigger the ExpenseList component to refresh
    refreshTrigger.value++;
    successMessage.value = t('expense.addSuccess');
    
    // Log a successful add
    logUserAction('record_add_success', { 
      type: expenseData.type, 
      amount: expenseData.amount,
      date: expenseData.date
    });
    
    // Reset the form
    Object.assign(form, { type: '', amount: '', date: '', remark: '' });
  } catch (error) {
    console.error('Add record failed error:', error);
    console.error('Error details:', { status: error.response?.status, data: error.response?.data, headers: error.response?.headers });
    // Distinguish form-validation errors from API errors
    let errorMsg;
    if (error.name === 'ValidationError') {
      errorMsg = error.message;
    } else if (error.response) {
      // Server response error
      const status = error.response.status;
      const serverMsg = error.response.data?.message || 'Server error';
      if (status >= 500) {
        errorMsg = t('expense.serverError', { error: serverMsg });
      } else if (status === 400) {
        errorMsg = t('expense.badRequest', { error: serverMsg });
      } else {
        errorMsg = t('expense.networkError', { error: `${status} - ${serverMsg}` });
      }
    } else if (error.request) {
      // No-response error (network issue)
      errorMsg = t('expense.networkTimeout');
    } else {
      errorMsg = t('expense.unknownError', { error: error.message || 'Unknown error' });
    }
    errorMessage.value = errorMsg;
    
    // Log a failed add
    logUserAction('record_add_failed', { 
      error: errorMsg,
      attemptedData: { type: form.type, amount: form.amount, date: form.date }
    });
  }
};

// State data
const Expenses = ref([]);
const isLoading = ref(false);
const refreshTrigger = ref(0); // Data version number used to trigger ExpenseList refresh
const expenseListTotal = ref(0); // Total record count within the ExpenseList component

// Handle the ExpenseList data-loaded event
const handleDataLoaded = (data) => {
  console.log('ExpenseList data loaded:', data);
  expenseListTotal.value = data.total || 0;
};

// Export feature - call the backend API to download Excel
const exportToExcel = async () => {
  try {
    const currentLang = localStorage.getItem('appLang') || i18n.global.locale.value || 'zh-CN'
    const response = await fetch(`/api/export/excel?lang=${currentLang}`)
    
    if (!response.ok) {
      throw new Error(t('export.failed'))
    }
    
    const blob = await response.blob()
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    const now = new Date()
    const timestamp = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}${String(now.getHours()).padStart(2, '0')}${String(now.getMinutes()).padStart(2, '0')}${String(now.getSeconds()).padStart(2, '0')}`
    link.download = `expenses_${timestamp}.xlsx`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
  } catch (error) {
    console.error('Export failed:', error)
    errorMessage.value = t('export.failed')
  }
}

// Expense data management
const {
  fetchData: originalFetchData,
  errorMessage,
  error,
  successMessage
} = useExpenseData();

// Wrap fetchData
const fetchData = async (forceRefresh = false) => {
  console.log('fetchData called, forceRefresh:', forceRefresh);
  await originalFetchData(forceRefresh);
  await loadExpenses();
};

// Create a cancellation controller
let paginationController = null;

// Load expense data (from the SQLite database) - use paginated loading for performance
const loadExpenses = async () => {
  if (isLoading.value) return;

  // Cancel the previous request
  if (paginationController) {
    paginationController.abort();
  }

  paginationController = createCancellationController();
  isLoading.value = true;

  try {
    console.log('Start loading expenses with pagination...');

    // Use the pagination utility to fetch all data
    const allData = await fetchAllPages({
      apiCall: ({ page, limit }) => 
        axios.get(`/api/expenses?page=${page}&limit=${limit}`),
      pageSize: 100,           // 100 records per page
      maxConcurrent: 3,        // At most 3 concurrent requests
      signal: paginationController.signal,
      onProgress: (progressData) => {
        console.log(`Pagination progress: ${progressData.progress}% (${progressData.loaded}/${progressData.total})`);
        // Progress bar could be updated here
      },
      onError: (error) => {
        console.error('Pagination error:', error);
        throw error;
      }
    });

    // Ensure the data format is correct
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
      console.log('loadExpenses: Pagination completed, count:', Expenses.value.length);
    }
  } catch (err) {
    if (err.message !== 'Operation canceled') {
      const errorInfo = err.response
        ? `${err.response.status} ${err.message}: ${JSON.stringify(err.response.data)}`
        : err.message;
      errorMessage.value = t('common.loadFailed', { error: errorInfo });
      error.value = errorMessage.value;

      console.error('loadExpenses: Error Details:', err);
      Expenses.value = [];
    } else {
      console.log('Pagination canceled by user request');
    }
  } finally {
    isLoading.value = false;
  }
};

// Refresh the prompt every time the dialog opens so the embedded date is current.
watch(showAiAddDialog, (visible) => {
  if (visible) handleAiBuildPrompt();
});

// Handle cancellation of AI smart-record
const handleAiCancel = () => {
  showAiAddDialog.value = false;
  // Reset the hand-off state
  aiPrompt.value = '';
  aiReply.value = '';
};

// Added: handle select-all / deselect-all
const handleSelectAllChange = (value) => {
  multiRecords.value.forEach(record => {
    record.selected = value;
  });
};

// Added: handle single-record selection change
const handleRecordSelectChange = () => {
  const allSelected = multiRecords.value.every(record => record.selected);
  const noneSelected = multiRecords.value.every(record => !record.selected);
  
  selectAll.value = allSelected;
  // Handle the indeterminate (half-selected) state
  if (!allSelected && !noneSelected) {
    selectAll.value = undefined;
  }
};

// Added: handle multi-record dialog cancel
const handleMultiRecordsCancel = () => {
  showMultiRecordsDialog.value = false;
  multiRecords.value = [];
  selectAll.value = false;
};

// Added: handle multi-record submit
const handleMultiRecordsSubmit = async () => {
  try {
    // Get all selected records
    const selectedRecords = multiRecords.value.filter(record => record.selected);
    console.log('Multi records submit started:', { recordCount: selectedRecords.length });
    
    if (selectedRecords.length === 0) {
      errorMessage.value = t('expense.selectAtLeastOne');
      return;
    }
    
    // Validate and format all records
    const validRecords = [];
    for (const record of selectedRecords) {
      // Validate the amount
      if (!record.amount || isNaN(record.amount) || Number(record.amount) <= 0) {
        throw new Error(`Record ${multiRecords.value.indexOf(record) + 1} is invalid amount`);
      }
      
      // Validate the type
      if (!record.type) {
        throw new Error(`Record ${multiRecords.value.indexOf(record) + 1} is missing type`);
      }
      
      // Validate the date
      if (!record.date) {
        throw new Error(`Record ${multiRecords.value.indexOf(record) + 1} is missing date`);
      }
      
      // Format the record
      validRecords.push({
        type: record.type,
        amount: parseFloat(parseFloat(record.amount).toFixed(2)),
        remark: record.remark || '',
        date: record.date // Use the date field; the time field is no longer needed
      });
      console.log('Valid record prepared:', { index: validRecords.length, type: record.type, amount: record.amount });
    }
    
    // Check whether any single expense exceeds 500 yuan
    await checkAndShowLargeExpenseWarning(validRecords);
    
    // Submit all records
    for (const record of validRecords) {
      await axios.post('/api/expenses', record, {
        headers: {
          'Content-Type': 'application/json'
        }
      });
    }
    
    // Close the dialog
    showMultiRecordsDialog.value = false;
    
    // Refresh the data
    await fetchData(true);
    
    successMessage.value = `${validRecords.length} records added successfully`;
    console.log('Multi records submit successful:', { totalRecords: validRecords.length });
    
    // Reset the data
    multiRecords.value = [];
    selectAll.value = false;
  } catch (error) {
    console.error('Multi records submit failed:', error);
    console.error('Error details:', { message: error.message, stack: error.stack });
    errorMessage.value = `Multi records submit failed: ${error.message}`;
  }
};

// Build the extraction prompt the user copies into a third-party AI app.
const handleAiBuildPrompt = () => {
  aiPrompt.value = buildAddRecordPrompt();
  aiReply.value = '';
};

// Parse the pasted AI answer and hand the records to the existing edit/multi flows.
const handleAiGenerate = () => {
  const result = parseAiReply(aiReply.value);
  if (!result.ok) {
    errorMessage.value = result.message;
    return;
  }

  const parsedDataList = result.records;
  console.log('AI records parsed from pasted answer:', { recordCount: parsedDataList.length });

  if (parsedDataList.length === 1) {
    const parsedData = parsedDataList[0];
    form.type = parsedData.type || '';
    form.amount = parsedData.amount || '';
    form.date = parsedData.date || '';
    form.remark = parsedData.remark || '';

    showAiAddDialog.value = false;
    showAddDialog.value = true;
    successMessage.value = 'AI已成功生成记录，请检查并确认';
  } else {
    multiRecords.value = parsedDataList.map(record => ({
      type: record.type || '其他',
      amount: record.amount,
      date: record.date,
      remark: record.remark || '',
      selected: true,
    }));
    showAiAddDialog.value = false;
    showMultiRecordsDialog.value = true;
    successMessage.value = `AI已成功生成${parsedDataList.length}条记录，请检查并确认`;
  }

  // Reset the hand-off state so the next run starts clean.
  aiPrompt.value = '';
  aiReply.value = '';
};

// Handle filter condition changes
const handleFilterChange = (data) => {
  filteredStats.value = data;
  console.log('Filter stats updated:', data);
};

// Build the filter-condition description
const buildFilterDescription = (stats, year, month, types) => {
  const conditions = [];
  
  // Time-range description
  if (year && month) {
    conditions.push(`- **时间范围**：${year}年${parseInt(month)}月`);
  } else if (year) {
    conditions.push(`- **时间范围**：${year}年全年度`);
  } else if (month) {
    conditions.push(`- **时间范围**：所有年份的${parseInt(month)}月`);
  } else {
    conditions.push(`- **时间范围**：所有时间`);
  }
  
  // Expense-type description
  if (types && types.length > 0) {
    conditions.push(`- **消费类型**：${types.join('、')}`);
  } else {
    conditions.push(`- **消费类型**：全部类型（共不超过28种）`);
  }
  
  // Record-count note
  conditions.push(`- **数据量**：共${stats?.totalCount || 0}条记录`);
  
  return conditions.join('\n');
};


// Build the AI hand-off prompt for the current filter. The question is left out
// on purpose - it is asked inside the AI app straight after this prompt.
const handleGenerateReport = () => {
  const stats = filteredStats.value || {};
  const conditions = filteredStats.value?.filterConditions || {};
  reportPrompt.value = buildReportPrompt({
    xlsxFileName: aiXlsxFileName.value,
    stats,
    filterDescription: buildFilterDescription(stats, conditions.year, conditions.month, conditions.types),
  });
};

// Paste the AI answer back and render it as Markdown.
const handleRenderReport = () => {
  const text = reportReply.value.trim();
  if (!text) {
    errorMessage.value = '请先粘贴 AI 返回的回答';
    return;
  }
  reportContent.value = text;
  successMessage.value = 'AI 回答已渲染，请检查后关闭本窗口';
};

// Download the xlsx the AI should analyse (filename is echoed inside the prompt).
const handleDownloadAIData = async () => {
  const conditions = filteredStats.value?.filterConditions || {};
  const currentLang = localStorage.getItem('appLang') || i18n.global.locale.value || 'zh-CN';

  // Mirror the backend's deterministic filename rule.
  aiXlsxFileName.value = buildAIDataFilename(conditions.year, conditions.month, conditions.types);

  const params = new URLSearchParams({ lang: currentLang });
  if (conditions.year) params.append('year', conditions.year);
  if (conditions.month) params.append('month', conditions.month);
  if (conditions.types && conditions.types.length) params.append('types', conditions.types.join(','));

  isDownloadingData.value = true;
  try {
    const response = await fetch('/api/export/ai-data?' + params.toString());
    if (!response.ok) throw new Error('HTTP ' + response.status);
    const blob = await response.blob();
    const link = document.createElement('a');
    link.href = window.URL.createObjectURL(blob);
    link.download = aiXlsxFileName.value;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(link.href);
    successMessage.value = '数据表格已下载，请记住文件名';
  } catch (error) {
    console.error('AI data export failed:', error);
    errorMessage.value = '数据表格下载失败，请稍后重试';
  } finally {
    isDownloadingData.value = false;
  }
};

// Mirror the backend's deterministic filename rule.
const buildAIDataFilename = (year, month, types) => {
  let base = 'expenses';
  if (year && month) base = 'expenses_' + year + '-' + month;
  else if (year) base = 'expenses_' + year;
  else if (types && types.length) base = 'expenses_types_' + types.length;
  return base + '.xlsx';
};

// Clear the AI Q&A hand-off state
const clearReportHandoff = () => {
  reportPrompt.value = '';
  reportReply.value = '';
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

<style scoped src="../styles/views/HomeView.css"></style>
