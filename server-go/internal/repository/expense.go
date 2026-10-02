package repository

import (
	"fmt"
	"time"

	"homemoney/internal/models"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

// ExpenseRepository expense record data repository
type ExpenseRepository struct {
	db *gorm.DB
}

// NewExpenseRepository creates a new expense repository
func NewExpenseRepository(db *gorm.DB) *ExpenseRepository {
	return &ExpenseRepository{
		db: db,
	}
}

// DB exposes the underlying handle so callers can build ad-hoc queries
// (currently only the AI data export, which needs the raw filtered set).
func (r *ExpenseRepository) DB() *gorm.DB {
	return r.db
}

// Create creates an expense record
func (r *ExpenseRepository) Create(expense *models.Expense) error {
	if err := expense.Validate(); err != nil {
		return fmt.Errorf("validation failed: %w", err)
	}
	return r.db.Create(expense).Error
}

// FindByID finds an expense record by ID
func (r *ExpenseRepository) FindByID(id string) (*models.Expense, error) {
	var expense models.Expense
	if err := r.db.First(&expense, "id = ?", id).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, nil
		}
		return nil, err
	}
	return &expense, nil
}

// FindWithPagination finds expense records with pagination
func (r *ExpenseRepository) FindWithPagination(query *models.ExpenseQuery) ([]models.Expense, int64, error) {
	var expenses []models.Expense
	var total int64

	// Validate query parameters
	if err := query.Validate(); err != nil {
		return nil, 0, fmt.Errorf("query parameter validation failed: %w", err)
	}

	// Build base query
	baseQuery := r.db.Model(&models.Expense{})

	// Apply query conditions
	query.ApplyToQuery(baseQuery)

	// Calculate total count
	if err := baseQuery.Count(&total).Error; err != nil {
		return nil, 0, err
	}

	// Apply sort and pagination
	query.ApplySort(baseQuery)
	baseQuery = baseQuery.Offset(query.Offset).Limit(query.Limit)

	// Execute query
	if err := baseQuery.Find(&expenses).Error; err != nil {
		return nil, 0, err
	}

	return expenses, total, nil
}

// Delete soft-deletes an expense record (sets deletedAt)
func (r *ExpenseRepository) Delete(id string) error {
	now := time.Now().UnixMilli()
	result := r.db.Model(&models.Expense{}).Where("id = ? AND deletedAt IS NULL", id).Update("deletedAt", now)
	if result.Error != nil {
		return result.Error
	}
	if result.RowsAffected == 0 {
		return fmt.Errorf("record not found")
	}
	return nil
}

// HardDelete hard-deletes an expense record
func (r *ExpenseRepository) HardDelete(id string) error {
	result := r.db.Where("id = ?", id).Delete(&models.Expense{})
	if result.Error != nil {
		return result.Error
	}
	if result.RowsAffected == 0 {
		return fmt.Errorf("record not found")
	}
	return nil
}

// GetStatistics retrieves statistics data
func (r *ExpenseRepository) GetStatistics(query *models.ExpenseQuery) (*models.ExpenseStats, error) {
	if query != nil {
		if err := query.Validate(); err != nil {
			return nil, fmt.Errorf("query parameter validation failed: %w", err)
		}
	}
	return models.GetStatsWithSQL(r.db, query)
}

// GetMeta retrieves metadata
func (r *ExpenseRepository) GetMeta() (*models.ExpenseMeta, error) {
	var meta models.ExpenseMeta

	// Get unique types
	var uniqueTypes []string
	if err := r.db.Model(&models.Expense{}).Distinct().Pluck("type", &uniqueTypes).Error; err != nil {
		return nil, err
	}
	meta.UniqueTypes = uniqueTypes

	// Get available months - since date is now string type, use substring to get year-month
	var availableMonths []string
	if err := r.db.Model(&models.Expense{}).
		Order("date DESC").
		Distinct().
		Pluck("SUBSTRING(date, 1, 7)", &availableMonths).Error; err != nil {
		return nil, fmt.Errorf("failed to get month data: %w", err)
	}
	meta.AvailableMonths = availableMonths

	return &meta, nil
}

// Exists checks if a record exists (not deleted)
func (r *ExpenseRepository) Exists(id string) (bool, error) {
	var count int64
	if err := r.db.Model(&models.Expense{}).Where("id = ? AND deletedAt IS NULL", id).Count(&count).Error; err != nil {
		return false, err
	}
	return count > 0, nil
}

// BatchCreate batch creates expense records
func (r *ExpenseRepository) BatchCreate(expenses []models.Expense) error {
	if len(expenses) == 0 {
		return nil
	}

	// Validate all records
	for i, expense := range expenses {
		if err := expense.Validate(); err != nil {
			return fmt.Errorf("record %d validation failed: %w", i+1, err)
		}
	}

	// Process in batches, 50 records per batch
	batchSize := 50
	for i := 0; i < len(expenses); i += batchSize {
		end := i + batchSize
		if end > len(expenses) {
			end = len(expenses)
		}

		batch := expenses[i:end]
		if err := r.db.CreateInBatches(batch, batchSize).Error; err != nil {
			return fmt.Errorf("batch %d creation failed: %w", i/batchSize+1, err)
		}
	}
	return nil
}

// FindByDates finds records by date list - used for import deduplication
func (r *ExpenseRepository) FindByDates(dates []string) ([]models.Expense, error) {
	if len(dates) == 0 {
		return nil, nil
	}
	var expenses []models.Expense
	if err := r.db.Where("date IN ? AND deletedAt IS NULL", dates).Find(&expenses).Error; err != nil {
		return nil, err
	}
	return expenses, nil
}

// Update updates an expense record
func (r *ExpenseRepository) Update(expense *models.Expense) error {
	if err := expense.Validate(); err != nil {
		return fmt.Errorf("validation failed: %w", err)
	}
	return r.db.Save(expense).Error
}

// SyncExpenses syncs expense records - fully consistent with JS version
func (r *ExpenseRepository) SyncExpenses(lastSyncTime *int64, changes []models.Expense, localIDs []string) ([]models.Expense, []gin.H, error) {
	serverChanges := make([]models.Expense, 0)
	conflicts := make([]gin.H, 0)

	// If client provided localIDs, return records missing from client, plus
	// any soft-deleted tombstones for records the client already holds.
		if len(localIDs) > 0 {
			// All server record IDs (active AND tombstoned), used to compute the
			// diff against the client's known IDs.
			var allServerRecords []models.Expense
			if err := r.db.Model(&models.Expense{}).Select("id").Find(&allServerRecords).Error; err != nil {
				return nil, nil, fmt.Errorf("failed to get server records: %w", err)
			}

			allServerIDs := make(map[string]bool)
			for _, r := range allServerRecords {
				allServerIDs[r.ID] = true
			}
			localIDSet := make(map[string]bool)
			for _, id := range localIDs {
				localIDSet[id] = true
			}

			// Records that exist on the server but not on the client. Returned in
			// full (active or tombstoned) so newly deleted records propagate even
			// to clients that never held an active copy.
			var missingIDs []string
			for id := range allServerIDs {
				if !localIDSet[id] {
					missingIDs = append(missingIDs, id)
				}
			}
			if len(missingIDs) > 0 {
				if err := r.db.Where("id IN ?", missingIDs).Find(&serverChanges).Error; err != nil {
					return nil, nil, fmt.Errorf("failed to get missing records: %w", err)
				}
			}

			// Server's current state for IDs the client already holds. Returning
			// the full row (active OR tombstoned, changed since lastSyncTime)
			// lets a restore on one device clear the tombstone on every other
			// device — not just propagate fresh deletes. The client's
			// upsertExpense skips rows whose updatedAt is not newer, so unchanged
			// records are not re-applied.
			knownQuery := r.db.Where("id IN ?", localIDs)
			if lastSyncTime != nil && *lastSyncTime > 0 {
				knownQuery = knownQuery.Where("updatedAt > ?", *lastSyncTime)
			}
			var knownRecords []models.Expense
			if err := knownQuery.Find(&knownRecords).Error; err != nil {
				return nil, nil, fmt.Errorf("failed to get known records: %w", err)
			}
			serverChanges = append(serverChanges, knownRecords...)
		} else if lastSyncTime != nil && *lastSyncTime > 0 {
			// Incremental pull: records changed after lastSyncTime, including
			// tombstones (a soft-delete bumps updatedAt, so it is returned once).
			if err := r.db.Where("updatedAt > ?", *lastSyncTime).Order("updatedAt ASC").Find(&serverChanges).Error; err != nil {
				return nil, nil, fmt.Errorf("failed to get updated records: %w", err)
			}
		}

	// Process changes submitted by client
	for _, change := range changes {
		if change.DeletedAt != nil {
			// Delete operation — only apply if the client's delete is newer than
			// the server record, so a stale delete cannot overwrite a newer
			// restore (last-write-wins by updatedAt, consistent with updates).
			var serverRecord models.Expense
			err := r.db.Where("id = ?", change.ID).First(&serverRecord).Error
			if err == nil && change.UpdatedAt > serverRecord.UpdatedAt {
				r.db.Model(&serverRecord).Updates(map[string]interface{}{
					"deletedAt": *change.DeletedAt,
					"updatedAt": change.UpdatedAt,
				})
			}
			continue
		}

		var serverRecord models.Expense
		err := r.db.Where("id = ?", change.ID).First(&serverRecord).Error
		if err == gorm.ErrRecordNotFound {
			// Create new record
			if createErr := r.Create(&change); createErr != nil {
				fmt.Printf("Error processing change: %s, %v\n", change.ID, createErr)
			}
		} else if err != nil {
			fmt.Printf("Error processing change: %s, %v\n", change.ID, err)
		} else {
			if change.UpdatedAt > serverRecord.UpdatedAt {
				// Client version is newer, update server. A restore (client sends
				// DeletedAt == nil) must clear any existing tombstone so the
				// un-delete propagates to every other device.
				r.db.Model(&serverRecord).Updates(map[string]interface{}{
					"type":      change.Type,
					"remark":    change.Remark,
					"amount":    change.Amount,
					"date":      change.Date,
					"version":   change.Version,
					"updatedAt": change.UpdatedAt,
					"deletedAt": nil,
				})
			} else if change.UpdatedAt < serverRecord.UpdatedAt {
				// Conflict
				conflicts = append(conflicts, gin.H{
					"id":              change.ID,
					"clientVersion":   change.Version,
					"serverVersion":   serverRecord.Version,
					"clientUpdatedAt": change.UpdatedAt,
					"serverUpdatedAt": serverRecord.UpdatedAt,
					"serverData":      serverRecord,
				})
			}
		}
	}

	return serverChanges, conflicts, nil
}

// GetExpensesByDate groups expenses by date - fully consistent with JS version getExpensesByDate
// Returns format: [{date, count, totalAmount, expenses}, ...]
// Implements smart pagination: ensures records from the same date group are not split across pages
func (r *ExpenseRepository) GetExpensesByDate(query *models.ExpenseQuery) ([]models.DateGroup, int64, *models.ExpenseMeta, error) {
	var allExpenses []models.Expense
	var total int64

	baseQuery := r.db.Model(&models.Expense{})
	query.ApplyToQuery(baseQuery)

	// Calculate total count
	if err := baseQuery.Count(&total).Error; err != nil {
		return nil, 0, nil, err
	}

	// Apply sort and get all matching data
	query.ApplySort(baseQuery)
	if err := baseQuery.Find(&allExpenses).Error; err != nil {
		return nil, 0, nil, err
	}

	// Group by date
	groupedMap := make(map[string][]models.Expense)
	var dateOrder []string
	for _, e := range allExpenses {
		if _, exists := groupedMap[e.Date]; !exists {
			dateOrder = append(dateOrder, e.Date)
		}
		groupedMap[e.Date] = append(groupedMap[e.Date], e)
	}

	// Add statistics for each date group, consistent with JS version format
	dateGroups := make([]models.DateGroup, 0, len(groupedMap))
	for _, date := range dateOrder {
		expenses := groupedMap[date]
		var totalAmount float64
		for _, exp := range expenses {
			totalAmount += exp.Amount
		}
		dateGroups = append(dateGroups, models.DateGroup{
			Date:        date,
			Count:       len(expenses),
			TotalAmount: totalAmount,
			Expenses:    expenses,
		})
	}

	// Sort date groups by sort type - consistent with JS version
	isAmountSort := query.Sort == "amountAsc" || query.Sort == "amountDesc"
	if isAmountSort {
		// When sorting by amount, date groups are sorted by the first record's amount in the group
		for i := 0; i < len(dateGroups); i++ {
			for j := i + 1; j < len(dateGroups); j++ {
				amountA := dateGroups[i].Expenses[0].Amount
				amountB := dateGroups[j].Expenses[0].Amount
				if query.Sort == "amountDesc" {
					if amountA < amountB {
						dateGroups[i], dateGroups[j] = dateGroups[j], dateGroups[i]
					}
				} else {
					if amountA > amountB {
						dateGroups[i], dateGroups[j] = dateGroups[j], dateGroups[i]
					}
				}
			}
		}
	} else {
		// Sort by date
		for i := 0; i < len(dateGroups); i++ {
			for j := i + 1; j < len(dateGroups); j++ {
				if query.Sort == "dateDesc" {
					if dateGroups[i].Date < dateGroups[j].Date {
						dateGroups[i], dateGroups[j] = dateGroups[j], dateGroups[i]
					}
				} else {
					if dateGroups[i].Date > dateGroups[j].Date {
						dateGroups[i], dateGroups[j] = dateGroups[j], dateGroups[i]
					}
				}
			}
		}
	}

	// Smart pagination: ensures records from the same date group are not split across pages
	// Paginate by date groups
	pageSize := query.Limit
	if pageSize < 1 {
		pageSize = 10
	}
	var pages [][]models.DateGroup
	var currentPageData []models.DateGroup
	var currentRecordCount int

	for _, group := range dateGroups {
		groupRecordCount := group.Count
		// If current page is empty, or adding this group won't exceed record limit too much, add to current page
		if currentRecordCount == 0 || currentRecordCount+groupRecordCount <= int(float64(pageSize)*1.5) {
			currentPageData = append(currentPageData, group)
			currentRecordCount += groupRecordCount
		} else {
			// Start a new page
			pages = append(pages, currentPageData)
			currentPageData = []models.DateGroup{group}
			currentRecordCount = groupRecordCount
		}
	}
	// Add the last page
	if len(currentPageData) > 0 {
		pages = append(pages, currentPageData)
	}

	// Get the requested page data
	pageNum := 1
	if query.Limit > 0 {
		pageNum = query.Offset/query.Limit + 1
	}
	var pagedData []models.DateGroup
	if pageNum > 0 && pageNum <= len(pages) {
		pagedData = pages[pageNum-1]
	}

	// Get metadata
	meta, err := r.GetMeta()
	if err != nil {
		meta = &models.ExpenseMeta{}
	}

	return pagedData, total, meta, nil
}

// FindAll retrieves all expense records (used for migration testing)
func (r *ExpenseRepository) FindAll() ([]models.Expense, error) {
	var expenses []models.Expense
	if err := r.db.Where("deletedAt IS NULL").Order("date DESC").Find(&expenses).Error; err != nil {
		return nil, err
	}
	return expenses, nil
}

// GetDeletedExpenses retrieves all soft-deleted expenses (deletedAt IS NOT NULL),
// ordered by deletedAt DESC (newest deletion first).
func (r *ExpenseRepository) GetDeletedExpenses() ([]models.Expense, error) {
	var expenses []models.Expense
	if err := r.db.Where("deletedAt IS NOT NULL").Order("deletedAt DESC").Find(&expenses).Error; err != nil {
		return nil, err
	}
	return expenses, nil
}

// RestoreExpense restores a single soft-deleted expense by clearing its deletedAt
// and bumping version / updatedAt so the change propagates through sync.
func (r *ExpenseRepository) RestoreExpense(id string) error {
	now := time.Now().UnixMilli()
	result := r.db.Model(&models.Expense{}).
		Where("id = ? AND deletedAt IS NOT NULL", id).
		Updates(map[string]interface{}{
			"deletedAt": nil,
			"updatedAt": now,
			"version":   gorm.Expr("version + 1"),
		})
	if result.Error != nil {
		return result.Error
	}
	if result.RowsAffected == 0 {
		return fmt.Errorf("deleted record not found")
	}
	return nil
}

// RestoreExpenses batch-restores a list of soft-deleted expenses.
func (r *ExpenseRepository) RestoreExpenses(ids []string) error {
	if len(ids) == 0 {
		return nil
	}
	now := time.Now().UnixMilli()
	result := r.db.Model(&models.Expense{}).
		Where("id IN ? AND deletedAt IS NOT NULL", ids).
		Updates(map[string]interface{}{
			"deletedAt": nil,
			"updatedAt": now,
			"version":   gorm.Expr("version + 1"),
		})
	if result.Error != nil {
		return result.Error
	}
	return nil
}

// RestoreAllExpenses restores every soft-deleted expense.
func (r *ExpenseRepository) RestoreAllExpenses() error {
	now := time.Now().UnixMilli()
	result := r.db.Model(&models.Expense{}).
		Where("deletedAt IS NOT NULL").
		Updates(map[string]interface{}{
			"deletedAt": nil,
			"updatedAt": now,
			"version":   gorm.Expr("version + 1"),
		})
	if result.Error != nil {
		return result.Error
	}
	return nil
}

// PermanentDeleteExpenses batch hard-deletes selected soft-deleted expenses.
func (r *ExpenseRepository) PermanentDeleteExpenses(ids []string) error {
	if len(ids) == 0 {
		return nil
	}
	result := r.db.Where("id IN ? AND deletedAt IS NOT NULL", ids).Delete(&models.Expense{})
	if result.Error != nil {
		return result.Error
	}
	return nil
}

// PermanentDeleteAllExpenses hard-deletes every soft-deleted expense.
func (r *ExpenseRepository) PermanentDeleteAllExpenses() error {
	result := r.db.Where("deletedAt IS NOT NULL").Delete(&models.Expense{})
	if result.Error != nil {
		return result.Error
	}
	return nil
}
