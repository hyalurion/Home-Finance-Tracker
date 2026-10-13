package com.chronie.homemoney.ui.expense

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronie.homemoney.core.common.Language
import com.chronie.homemoney.data.sync.SyncScheduler
import com.chronie.homemoney.data.vlm.OnDeviceModelManager
import com.chronie.homemoney.domain.model.Expense
import com.chronie.homemoney.domain.model.ExpenseFilters
import com.chronie.homemoney.domain.model.ExpenseStatistics
import com.chronie.homemoney.domain.model.ExpenseType
import com.chronie.homemoney.domain.model.SortOption
import com.chronie.homemoney.domain.repository.ExpenseRepository
import com.chronie.homemoney.domain.usecase.TranslateRemarkUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import javax.inject.Inject

/**
 * ViewModel for the expense list screen.
 *
 * Manages paginated loading, filtering, sorting, grouping by date,
 * and deletion of expense records. Also computes aggregate statistics
 * for the current filter criteria.
 *
 * The UI observes [uiState] which contains both the flat and
 * date-grouped expense lists suitable for a grouped LazyColumn.
 *
 * When the on-device AI model is downloaded, remarks can be translated
 * into the current app language via the local model — see
 * [remarkTranslations] and [translateRemark]. All inference runs offline.
 */
@HiltViewModel
class ExpenseListViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val syncScheduler: SyncScheduler,
    private val modelManager: OnDeviceModelManager,
    private val translateRemarkUseCase: TranslateRemarkUseCase,
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ExpenseListUiState())
    val uiState: StateFlow<ExpenseListUiState> = _uiState.asStateFlow()

    /**
     * Per-expense remark translation state, keyed by expense id.
     * Only populated while the on-device AI model is downloaded.
     */
    private val _remarkTranslations = MutableStateFlow<Map<String, RemarkTranslationState>>(emptyMap())
    val remarkTranslations: StateFlow<Map<String, RemarkTranslationState>> = _remarkTranslations.asStateFlow()

    /** True once the on-device model package has been verified on disk. */
    private val _isModelDownloaded = MutableStateFlow(modelManager.isModelReady())
    val isModelDownloaded: StateFlow<Boolean> = _isModelDownloaded.asStateFlow()

    init {
        // Pick up a model downloaded/verified in a previous session.
        modelManager.refreshState()
        viewModelScope.launch {
            modelManager.state.collect { state ->
                _isModelDownloaded.value = state is OnDeviceModelManager.ModelState.Ready
            }
        }
        loadExpenses()
        loadStatistics()
    }
    
    /**
     * Groups expenses by date string, applying the given sort order.
     *
     * First sorts the entire list globally, then groups into a
     * [LinkedHashMap] to preserve insertion order. When sorting by date,
     * the groups are returned as a sorted map.
     *
     * @param expenses The flat list of expense records to group.
     * @param sortBy The active sort option from the current filters.
     * @return A map of date string -> list of expenses for that date.
     */
    private fun groupExpensesByDate(expenses: List<Expense>, sortBy: SortOption): Map<String, List<Expense>> {
        // Globally sort expenses by date
        val globallySortedExpenses = when (sortBy) {
            SortOption.DATE_ASC -> expenses.sortedBy { it.date }
            SortOption.DATE_DESC -> expenses.sortedByDescending { it.date }
            SortOption.AMOUNT_ASC -> expenses.sortedBy { it.amount }
            SortOption.AMOUNT_DESC -> expenses.sortedByDescending { it.amount }
        }
        
        // Use LinkedHashMap to group by date while maintaining order
        val grouped = LinkedHashMap<String, MutableList<Expense>>()
        
        for (expense in globallySortedExpenses) {
            val date = expense.date
            grouped.computeIfAbsent(date) { mutableListOf() }.add(expense)
        }
        
        // Sort each group by date (ensure order is correct)
        val sortedGroups = grouped.mapValues { (_, dateExpenses) ->
            when (sortBy) {
                SortOption.DATE_ASC -> dateExpenses.sortedBy { it.date }
                SortOption.DATE_DESC -> dateExpenses.sortedByDescending { it.date }
                SortOption.AMOUNT_ASC -> dateExpenses.sortedBy { it.amount }
                SortOption.AMOUNT_DESC -> dateExpenses.sortedByDescending { it.amount }
            }
        }
        
        // If sorted by date, return sorted map by date
        return if (sortBy == SortOption.DATE_ASC || sortBy == SortOption.DATE_DESC) {
            sortedGroups.toSortedMap(compareByDescending { it })
        } else {
            sortedGroups
        }
    }
    
    /**
     * Loads the first page of expenses based on current filters.
     * @param refresh If true, resets to page 1 and clears existing data.
     */
    fun loadExpenses(refresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val currentState = _uiState.value
            val page = if (refresh) 1 else currentState.currentPage
            val filters = currentState.filters
            
            val result = expenseRepository.getExpensesList(
                page = page,
                limit = currentState.pageSize,
                filters = filters
            )
            
            result.fold(
                onSuccess = { expenses ->
                    val newExpenses = if (refresh) {
                        expenses
                    } else {
                        currentState.expenses + expenses
                    }
                    
                    val grouped = groupExpensesByDate(newExpenses, filters.sortBy)
                    
                    _uiState.update {
                        it.copy(
                            expenses = newExpenses,
                            groupedExpenses = grouped,
                            currentPage = page,
                            hasMore = expenses.size >= currentState.pageSize,
                            isLoading = false,
                            error = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Unknown error"
                        )
                    }
                }
            )
        }
    }
    
    /**
     * Loads the next page of expenses (infinite scroll).
     * Deduplicates by expense ID to prevent key conflicts in LazyColumn.
     */
    fun loadMore() {
        val currentState = _uiState.value
        if (!currentState.isLoading && currentState.hasMore) {
            viewModelScope.launch {
                val nextPage = currentState.currentPage + 1
                _uiState.update { it.copy(isLoading = true, error = null) }
                
                val result = expenseRepository.getExpensesList(
                    page = nextPage,
                    limit = currentState.pageSize,
                    filters = currentState.filters
                )
                
                result.fold(
                    onSuccess = { expenses ->
                        // Use distinctBy to prevent duplicate IDs causing key conflicts in LazyColumn
                        val newExpenses = (currentState.expenses + expenses)
                            .distinctBy { it.id }
                        val grouped = groupExpensesByDate(newExpenses, currentState.filters.sortBy)
                        
                        _uiState.update {
                            it.copy(
                                expenses = newExpenses,
                                groupedExpenses = grouped,
                                currentPage = nextPage,
                                hasMore = expenses.size >= currentState.pageSize,
                                isLoading = false,
                                error = null
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = error.message ?: "Unknown error"
                            )
                        }
                    }
                )
            }
        }
    }
    
    /** Loads aggregate statistics for the current filter criteria. */
    fun loadStatistics() {
        viewModelScope.launch {
            val filters = _uiState.value.filters
            val result = expenseRepository.getStatistics(filters)
            
            result.fold(
                onSuccess = { statistics ->
                    _uiState.update { it.copy(statistics = statistics) }
                },
                onFailure = { /* Ignore statistics errors */ }
            )
        }
    }
    
    /**
     * Applies new filters and reloads from page 1.
     * Clears existing data before reloading.
     */
    fun updateFilters(filters: ExpenseFilters) {
        _uiState.update {
            it.copy(
                filters = filters,
                currentPage = 1,  // Reset to first page after filters change
                expenses = emptyList()  // Clear existing data
            )
        }
        loadExpenses(refresh = true)
        loadStatistics()
    }
    
    fun updateKeyword(keyword: String) {
        val newFilters = _uiState.value.filters.copy(keyword = keyword.ifBlank { null })
        updateFilters(newFilters)
    }
    
    fun updateType(type: ExpenseType?) {
        val newFilters = _uiState.value.filters.copy(type = type)
        updateFilters(newFilters)
    }
    
    fun updateMonth(month: String?) {
        val newFilters = _uiState.value.filters.copy(month = month)
        updateFilters(newFilters)
    }
    
    fun updateAmountRange(minAmount: Double?, maxAmount: Double?) {
        val newFilters = _uiState.value.filters.copy(
            minAmount = minAmount,
            maxAmount = maxAmount
        )
        updateFilters(newFilters)
    }
    
    fun updateSortOption(sortOption: SortOption) {
        val newFilters = _uiState.value.filters.copy(sortBy = sortOption)
        updateFilters(newFilters)
    }
    
    fun resetFilters() {
        updateFilters(ExpenseFilters())
    }
    
    fun nextPage() {
        _uiState.update { it.copy(currentPage = it.currentPage + 1) }
        loadExpenses()
    }
    
    fun previousPage() {
        if (_uiState.value.currentPage > 1) {
            _uiState.update { it.copy(currentPage = it.currentPage - 1) }
            loadExpenses()
        }
    }
    
    fun goToPage(page: Int) {
        if (page >= 1) {
            _uiState.update { it.copy(currentPage = page) }
            loadExpenses()
        }
    }
    
    /** Pull-to-refresh: resets to page 1 and reloads. */
    fun refresh() {
        _uiState.update { it.copy(currentPage = 1, expenses = emptyList()) }
        loadExpenses(refresh = true)
        loadStatistics()
    }
    
    /**
     * Deletes an expense record.
     * Optimistically removes it from the local list and refreshes statistics.
     */
    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            expenseRepository.deleteExpense(expense.id).fold(
                onSuccess = {
                    // Remove deleted expense from current list and grouped data
                    val updatedExpenses = _uiState.value.expenses.filter { it.id != expense.id }
                    val grouped = groupExpensesByDate(updatedExpenses, _uiState.value.filters.sortBy)
                    
                    _uiState.update {
                        it.copy(
                            expenses = updatedExpenses,
                            groupedExpenses = grouped
                        )
                    }

                    // Drop any cached translation for the deleted record.
                    _remarkTranslations.update { it - expense.id }
                    
                    // Reload statistics after delete
                    loadStatistics()

                    // Push delete to the server promptly. Without this, the
                    // tombstone would only be flushed by the hourly periodic sync,
                    // and a network-first list refresh in the meantime could
                    // resurrect the record from the cloud and drop the pending delete.
                    try {
                        syncScheduler.triggerImmediateSync()
                    } catch (e: Exception) {
                        android.util.Log.w(
                            "ExpenseListViewModel",
                            "Failed to trigger sync after delete",
                            e
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            error = error.message ?: "Failed to delete expense"
                        )
                    }
                }
            )
        }
    }

    /**
     * Translates the remark of [expense] into the current app language
     * using the on-device AI model (offline, no network access).
     *
     * Tapping the button cycles through: Translating -> Success
     * (showing translated) -> toggles back to the original remark ->
     * toggles to the translated remark again. A blank remark is a no-op;
     * the UI never shows the button for empty remarks anyway.
     */
    fun translateRemark(expense: Expense) {
        val remark = expense.remark?.trim().orEmpty()
        if (remark.isBlank()) return

        val current = _remarkTranslations.value[expense.id]
        if (current is RemarkTranslationState.Translating) return // debounce double taps

        // Existing translation toggles between original and translated.
        if (current is RemarkTranslationState.Success) {
            _remarkTranslations.update {
                it + (expense.id to current.copy(showingTranslated = !current.showingTranslated))
            }
            return
        }

        _remarkTranslations.update { it + (expense.id to RemarkTranslationState.Translating) }

        viewModelScope.launch {
            val targetLanguage = Language.fromLocale(
                context.resources.configuration.locales[0]
            )
            translateRemarkUseCase(remark, targetLanguage).fold(
                onSuccess = { translated ->
                    // The model returns the text verbatim when it is already
                    // in the target language or has no translatable content.
                    val unchanged = translated.trim().equals(remark, ignoreCase = true)
                    _remarkTranslations.update {
                        it + (expense.id to RemarkTranslationState.Success(
                            translatedText = translated,
                            unchanged = unchanged,
                            showingTranslated = true
                        ))
                    }
                },
                onFailure = { error ->
                    _remarkTranslations.update {
                        it + (expense.id to RemarkTranslationState.Error(error.message))
                    }
                }
            )
        }
    }
}

/**
 * Complete UI state for the expense list screen.
 *
 * @property expenses Flat list of all loaded expense records.
 * @property groupedExpenses Expenses grouped by date for the grouped list view.
 * @property statistics Aggregate statistics for the current filter set.
 * @property filters Active filter/sort criteria.
 * @property currentPage Current pagination page (1-based).
 * @property pageSize Number of items per page for pagination.
 * @property totalItems Total number of matching items.
 * @property hasMore Whether there are more pages to load.
 * @property isLoading Whether a data load is in progress.
 * @property error Error message to display, or null.
 */
data class ExpenseListUiState(
    val expenses: List<Expense> = emptyList(),
    val groupedExpenses: Map<String, List<Expense>> = emptyMap(),
    val statistics: ExpenseStatistics = ExpenseStatistics(
        count = 0,
        totalAmount = 0.0,
        averageAmount = 0.0,
        medianAmount = 0.0,
        minAmount = 0.0,
        maxAmount = 0.0
    ),
    val filters: ExpenseFilters = ExpenseFilters(),
    val currentPage: Int = 1,
    val pageSize: Int = 20,  // Increase page size
    val totalItems: Int = 0,
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * Summary data for a single date group in the expense list.
 *
 * @property date The date string (YYYY-MM-DD) for this group.
 * @property expenses All expense records on this date.
 * @property totalAmount Sum of all expense amounts on this date.
 * @property count Number of expense records on this date.
 */
data class ExpenseDateGroup(
    val date: String,
    val expenses: List<Expense>,
    val totalAmount: Double,
    val count: Int
)

/**
 * Translation lifecycle for a single expense remark, driven by the
 * on-device AI model. Keyed by expense id in
 * [ExpenseListViewModel.remarkTranslations].
 */
sealed interface RemarkTranslationState {
    /** Inference is running; the button shows a spinner. */
    data object Translating : RemarkTranslationState

    /**
     * Translation finished.
     *
     * @param translatedText the model output (may equal the original
     *   remark when it was already in the target language).
     * @param unchanged true when the translated text is effectively the
     *   original — the UI surfaces an "already in your language" hint
     *   instead of a duplicate translation.
     * @param showingTranslated whether the remark row currently displays
     *   the translation (true) or the original text (false). Tapping the
     *   button again toggles this flag without re-running the model.
     */
    data class Success(
        val translatedText: String,
        val unchanged: Boolean,
        val showingTranslated: Boolean
    ) : RemarkTranslationState

    /** Model not ready, engine crash, or empty output. */
    data class Error(val message: String?) : RemarkTranslationState
}
