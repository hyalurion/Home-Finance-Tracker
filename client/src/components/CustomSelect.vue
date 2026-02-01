<template>
  <div class="custom-select" :class="{ 'open': isOpen }" @click.stop="toggleDropdown">
    <div class="select-trigger" ref="triggerRef">
      <span>{{ displayText }}</span>
      <i class="select-icon"></i>
    </div>
    <transition name="dropdown-fade">
      <div v-if="isOpen" class="select-dropdown" ref="dropdownRef">
        <div 
          v-if="includeEmptyOption" 
          class="select-option" 
          :class="{ 'selected': !modelValue && modelValue !== 0 }" 
          @click.stop="selectValue('')"
        >
          {{ emptyOptionLabel }}
        </div>
        <div 
          v-for="option in options" 
          :key="typeof option === 'object' ? option[props.optionValueKey] : option" 
          class="select-option" 
          :class="{ 'selected': isSelected(option) }" 
          @click.stop="selectValue(typeof option === 'object' ? option[props.optionValueKey] : option)"
        >
          {{ typeof option === 'object' ? option[props.optionLabelKey] : option }}
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue';

const props = defineProps({
  modelValue: {
    type: [String, Number],
    default: ''
  },
  options: {
    type: Array,
    required: true
  },
  emptyOptionLabel: {
    type: String,
    default: 'All'
  },
  includeEmptyOption: {
    type: Boolean,
    default: true
  },
  valueFormatter: {
    type: Function,
    default: (value) => value
  },
  optionValueKey: {
    type: String,
    default: 'value'
  },
  optionLabelKey: {
    type: String,
    default: 'label'
  }
});

const emit = defineEmits(['update:modelValue', 'change']);

const isOpen = ref(false);
const triggerRef = ref(null);
const dropdownRef = ref(null);

// 计算显示文本
const displayText = computed(() => {
  try {
    // 处理空值情况
    if (!props.modelValue && props.modelValue !== 0) {
      return props.emptyOptionLabel;
    }
    
    // 强制字符串比较以处理可能的类型差异
    const modelValueStr = String(props.modelValue);
    
    // 1. 首先检查是否有值匹配
    // 支持对象格式和非对象格式的选项
    for (const option of props.options) {
      if (typeof option === 'object') {
        // 对象格式选项
        const optionValue = option[props.optionValueKey];
        
        // 尝试多种比较方式，确保类型差异不影响匹配
        const isMatch = 
          optionValue === props.modelValue || 
          String(optionValue) === modelValueStr ||
          (typeof optionValue === 'string' && typeof props.modelValue === 'string' && 
           optionValue.trim() === props.modelValue.trim());
        
        if (isMatch) {
          // 找到匹配项，返回其label属性
          const label = option[props.optionLabelKey];
          return label !== undefined && label !== null ? String(label) : modelValueStr;
        }
      } else {
        // 非对象格式选项，直接比较
        if (option === props.modelValue || String(option) === modelValueStr) {
          return String(option);
        }
      }
    }
    
    // 2. 如果直接匹配失败，尝试从选项的label中查找
    // 这是为了处理可能的特殊情况，如选项顺序变化等
    for (const option of props.options) {
      if (typeof option === 'object') {
        const optionValue = option[props.optionValueKey];
        // 尝试宽松比较
        if (String(optionValue).toLowerCase() === modelValueStr.toLowerCase()) {
          return String(option[props.optionLabelKey]);
        }
      }
    }
    
    // 3. 如果提供了自定义格式化函数，使用它
    if (props.valueFormatter) {
      try {
        return props.valueFormatter(props.modelValue);
      } catch (err) {
        console.error('Error in custom value formatter:', err.message);
      }
    }
    
    // 4. 如果以上都失败，最后兜底返回modelValue的字符串表示
    // 记录未找到匹配的情况，避免过度频繁
    if (Math.random() < 0.2) { // 20%概率记录，避免过多日志
      console.warn('No exact match found for value:', { 
        value: props.modelValue,
        optionsCount: props.options?.length || 0 
      });
    }
    
    return modelValueStr;
  } catch (error) {
    console.error('Error in displayText computation:', { 
      error: error.message,
      modelValue: props.modelValue
    });
    return String(props.modelValue || props.emptyOptionLabel);
  }
});

// 切换下拉菜单
const toggleDropdown = () => {
  const newState = !isOpen.value;
  console.log('Dropdown toggle:', { newState, componentId: props.modelValue?.toString() || 'unknown' });
  isOpen.value = newState;
};

// 选择值
const selectValue = (value) => {
  console.log('Select option changed:', {
    componentId: props.modelValue?.toString() || 'unknown',
    from: props.modelValue,
    to: value
  });
  emit('update:modelValue', value);
  emit('change', value);
  isOpen.value = false;
};

// 检查是否选中
const isSelected = (option) => {
  if (!props.modelValue && props.modelValue !== 0) {
    return false;
  }
  
  const modelValueStr = String(props.modelValue);
  
  if (typeof option === 'object') {
    const optionValue = option[props.optionValueKey];
    // 多种比较方式，确保类型差异不影响判断
    const isMatch = optionValue === props.modelValue || 
           String(optionValue) === modelValueStr ||
           (typeof optionValue === 'string' && typeof props.modelValue === 'string' &&
            optionValue.trim() === props.modelValue.trim());
    
    // 添加日志记录对象类型选项的匹配情况，避免过度频繁
    if (isMatch && Math.random() < 0.1) { // 10%概率记录，避免过多日志
      console.log('Object option matched:', {
        option: option[props.optionLabelKey],
        value: optionValue
      });
    }
    
    return isMatch;
  } else {
    const isMatch = option === props.modelValue || String(option) === modelValueStr;
    
    // 添加日志记录非对象类型选项的匹配情况，避免过度频繁
    if (isMatch && Math.random() < 0.1) { // 10%概率记录，避免过多日志
      console.log('Simple option matched:', { option });
    }
    
    return isMatch;
  }
};

// 点击外部关闭下拉菜单
const handleClickOutside = (event) => {
  if (triggerRef.value && !triggerRef.value.contains(event.target) &&
      dropdownRef.value && !dropdownRef.value.contains(event.target)) {
    console.log('Click outside detected, closing dropdown');
    isOpen.value = false;
  }
};

// 监听键盘事件
const handleKeydown = (event) => {
  if (event.key === 'Escape') {
    isOpen.value = false;
  }
};

onMounted(() => {
  console.log('CustomSelect component mounted:', {
    optionsCount: props.options?.length || 0,
    includeEmptyOption: props.includeEmptyOption,
    initialValue: props.modelValue
  });
  
  document.addEventListener('click', handleClickOutside);
  document.addEventListener('keydown', handleKeydown);
});

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside);
  document.removeEventListener('keydown', handleKeydown);
});
</script>

<style scoped>
.custom-select {
  position: relative;
  width: auto;
  max-width: 60%;
  font-size: 14px;
}

.select-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 15px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
  cursor: pointer;
  transition: all 0.2s ease;
}

.select-trigger:hover {
  border-color: #cbd5e0;
  background: #fff;
  transition: all 0.2s ease;
}

.select-trigger:focus {
  outline: none;
  border-color: #4361ee;
  box-shadow: 0 0 0 3px rgba(67, 97, 238, 0.15);
}

.select-trigger {
  transition: all 0.2s ease;
}

.select-icon {
  width: 16px;
  height: 16px;
  position: relative;
  transition: transform 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.select-icon::before {
  content: '';
  position: absolute;
  top: 50%;
  left: 0;
  width: 6px;
  height: 6px;
  border-right: 2px solid #495057;
  border-bottom: 2px solid #495057;
  transform: translateY(-60%) rotate(45deg);
}

.custom-select.open .select-icon {
  transform: rotate(180deg);
  transition: transform 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.select-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  margin-top: 4px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  max-height: 240px;
  overflow-y: auto;
  z-index: 1000;
  transform-origin: top center;
}

/* Vue过渡动画类 - 增强版 */
.dropdown-fade-enter-active,
.dropdown-fade-leave-active {
  transition: all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.dropdown-fade-enter-from {
  opacity: 0;
  transform: translateY(-15px) scale(0.9);
  visibility: hidden;
}

.dropdown-fade-leave-to {
  opacity: 0;
  transform: translateY(-5px) scale(0.95);
  visibility: hidden;
}

/* 确保深色模式下也能看到动画 */
:deep(.dropdown-fade-enter-active),
:deep(.dropdown-fade-leave-active) {
  transition: all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

:deep(.dropdown-fade-enter-from),
:deep(.dropdown-fade-leave-to) {
  opacity: 0;
  visibility: hidden;
}

:deep(.dropdown-fade-enter-from) {
  transform: translateY(-15px) scale(0.9);
}

:deep(.dropdown-fade-leave-to) {
  transform: translateY(-5px) scale(0.95);
}

/* 自定义滚动条样式 - 正常模式 */
.select-dropdown::-webkit-scrollbar {
  width: 6px;
}

.select-dropdown::-webkit-scrollbar-track {
  background: #f1f5f9;
  border-radius: 3px;
  margin: 4px 0;
}

.select-dropdown::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 3px;
  transition: background 0.2s ease;
}

.select-dropdown::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}

.select-option {
  padding: 10px 15px 10px 30px;
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;
}

.select-option:hover {
  background: #f0f7ff;
  color: #4361ee;
}

.select-option.selected {
  background: transparent;
  color: #4361ee;
  position: relative;
}

.select-option.selected::before {
  content: '✓';
  position: absolute;
  left: 15px;
  font-weight: bold;
}



/* 响应式设计 */
@media (max-width: 768px) {
  .custom-select {
    max-width: 100%;
  }
  
  .select-trigger {
    padding: 10px 12px;
    font-size: 13px;
  }
}

/* 深色模式适配 */
@media (prefers-color-scheme: dark) {
  .select-trigger {
    background: #1f2937;
    border-color: #374151;
    color: #e5e7eb;
  }
  
  .select-trigger:hover {
    background: #374151;
    border-color: #4b5563;
  }
  
  .select-icon::before {
    border-right-color: #9ca3af;
    border-bottom-color: #9ca3af;
  }
  
  .select-dropdown {
    background: #1f2937;
    border-color: #374151;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
  }

  /* 自定义滚动条样式 - 深色模式 */
  .select-dropdown::-webkit-scrollbar {
    width: 6px;
  }

  .select-dropdown::-webkit-scrollbar-track {
    background: #374151;
    border-radius: 3px;
    margin: 4px 0;
  }

  .select-dropdown::-webkit-scrollbar-thumb {
    background: #4b5563;
    border-radius: 3px;
    transition: background 0.2s ease;
  }

  .select-dropdown::-webkit-scrollbar-thumb:hover {
    background: #6b7280;
  }
  
  .select-option {
    color: #e5e7eb;
  }
  
  .select-option:hover {
    background: #374151;
    color: #a5b4fc;
  }
  
  .select-option.selected {
    background: transparent;
    color: #a5b4fc;
    position: relative;
  }

  .select-option.selected::before {
    content: '✓';
    position: absolute;
    left: 15px;
    font-weight: bold;
  }


}
</style>