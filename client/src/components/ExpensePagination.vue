<!-- ExpensePagination.vue -->
<template>
    <div class="pagination-container">
      <div class="pagination">
        <button 
          class="pagination-btn"
          @click.prevent.stop="$emit('page-change', 1)" 
          :disabled="currentPage === 1" 
          :title="$t('app.firstPage')"
        >
          &lt;&lt;
        </button>
        <button 
          class="pagination-btn"
          @click.prevent.stop="$emit('page-change', currentPage - 1)" 
          :disabled="currentPage === 1" 
          :title="$t('app.previousPage')"
        >
          &lt;
        </button>

        <template v-for="page in visiblePages" :key="page">
          <button
            class="pagination-btn"
            :class="{ active: currentPage === page }"
            @click.prevent.stop="$emit('page-change', page)"
            :title="$t('app.page', { page })"
            :disabled="page === '...'"
          >
            {{ page }}
          </button>
        </template>

        <button 
          class="pagination-btn"
          @click.prevent.stop="$emit('page-change', currentPage + 1)" 
          :disabled="currentPage === totalPages" 
          :title="$t('app.nextPage')"
        >
          &gt;
        </button>
        <button 
          class="pagination-btn"
          @click.prevent.stop="$emit('page-change', totalPages)" 
          :disabled="currentPage === totalPages" 
          :title="$t('app.lastPage')"
        >
          &gt;&gt;
        </button>
      </div>
    </div>
  </template>

<script>
export default {
  props: {
    currentPage: Number,
    totalPages: Number,
    visiblePages: Array
  }
};
</script>

<style scoped>
.pagination-container {
  display: flex;
  justify-content: center;
  margin-top: 24px;
  padding: 12px 20px;
  border-radius: 12px;
  transition: all 0.3s ease;
}

.pagination {
  display: flex;
  gap: 8px;
  align-items: center;
}

.pagination-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.2) 0%, rgba(240, 240, 240, 0.1) 100%);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  color: var(--text-primary, #495057);
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  font-size: 14px;
  font-weight: 500;
  border: 1px solid rgba(255, 255, 255, 0.3);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
  position: relative;
  overflow: hidden;
}

.pagination-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, rgba(67, 97, 238, 0.9) 0%, rgba(50, 70, 200, 0.8) 100%);
  color: white;
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(67, 97, 238, 0.4);
  border-color: rgba(67, 97, 238, 0.6);
}

.pagination-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.1) 0%, rgba(240, 240, 240, 0.05) 100%);
  transform: none;
}

.pagination-btn.active {
  background: linear-gradient(135deg, rgba(67, 97, 238, 1) 0%, rgba(50, 70, 200, 0.9) 100%);
  color: white;
  font-weight: 600;
  box-shadow: 0 4px 16px rgba(67, 97, 238, 0.3);
  border-color: rgba(67, 97, 238, 0.8);
}

.pagination-btn.active::before {
  animation: none;
}

/* 深色模式适配 */
@media (prefers-color-scheme: dark) {
  .pagination-btn {
    background: linear-gradient(135deg, rgba(75, 85, 99, 0.3) 0%, rgba(55, 65, 81, 0.2) 100%);
    color: var(--dark-text-primary, #e5e7eb);
    border: 1px solid rgba(75, 85, 99, 0.4);
    box-shadow: 0 2px 10px rgba(0, 0, 0, 0.2);
  }

  .pagination-btn::before {
    background: linear-gradient(
      45deg,
      rgba(255, 255, 255, 0) 40%,
      rgba(255, 255, 255, 0.15) 50%,
      rgba(255, 255, 255, 0) 60%
    );
  }

  .pagination-btn:hover:not(:disabled) {
    background: linear-gradient(135deg, rgba(67, 97, 238, 0.9) 0%, rgba(50, 70, 200, 0.8) 100%);
    color: white;
  }

  .pagination-btn:disabled {
    background: linear-gradient(135deg, rgba(30, 30, 30, 0.4) 0%, rgba(20, 20, 20, 0.3) 100%);
    color: var(--dark-text-secondary, #6b7280);
  }

  .pagination-btn.active {
    background: linear-gradient(135deg, rgba(67, 97, 238, 1) 0%, rgba(50, 70, 200, 0.9) 100%);
    color: white;
  }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .pagination-container {
    margin-top: 20px;
    padding: 10px 16px;
    border-radius: 10px;
  }

  .pagination {
    gap: 6px;
  }

  .pagination-btn {
    width: 36px;
    height: 36px;
    font-size: 13px;
    border-radius: 6px;
  }
}

@media (max-width: 480px) {
  .pagination-container {
    padding: 8px 12px;
  }

  .pagination-btn {
    width: 32px;
    height: 32px;
    font-size: 12px;
  }
}
</style>
