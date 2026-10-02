<!-- ExpensePagination.vue -->
<!-- Pagination buttons keep a static glass look via CSS and stay fully
     clickable; they use their own class rather than v-liquid-glass so small,
     densely packed controls keep predictable click targets. -->
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

<style scoped src="../styles/components/ExpensePagination.css"></style>
