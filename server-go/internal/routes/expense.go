package routes

import (
	"github.com/gin-gonic/gin"
	"homemoney/internal/handlers"
	"homemoney/internal/repository"
)

// SetupExpenseRoutes sets up expense record related routes - fully consistent with Node.js version
func SetupExpenseRoutes(router *gin.Engine, expenseRepo *repository.ExpenseRepository) {
	expenseHandler := handlers.NewExpenseHandler(expenseRepo)

	// Create route group
	api := router.Group("/api")
	{
		// Expense record route group
		expenses := api.Group("/expenses")
		{
		// Get expense record list (supports pagination, filtering, sorting)
		// Register both the trailing-slash ("/") and no-slash ("") variants so
		// the client can call /api/expenses without a 301/307 redirect loop
		// when proxied behind Next.js (which strips trailing slashes by default).
		expenses.GET("", expenseHandler.GetExpenses)
		expenses.GET("/", expenseHandler.GetExpenses)

			// Get expense records grouped by date
			expenses.GET("/by-date", expenseHandler.GetExpensesByDate)

			// Get expense statistics
			expenses.GET("/statistics", expenseHandler.GetExpenseStatistics)

			// ---------- Recycle bin endpoints (soft-deleted records) ----------
			// List all soft-deleted expenses (recycle bin contents)
			expenses.GET("/deleted", expenseHandler.GetDeletedExpenses)

			// Restore every soft-deleted expense
			expenses.POST("/restore/all", expenseHandler.RestoreAllExpenses)

			// Batch-restore selected soft-deleted expenses
			expenses.POST("/restore/batch", expenseHandler.RestoreExpensesBatch)

			// Restore a single soft-deleted expense
			expenses.POST("/:id/restore", expenseHandler.RestoreExpense)

			// Permanently delete every soft-deleted expense
			expenses.DELETE("/deleted/all", expenseHandler.PermanentDeleteAllExpenses)

			// Batch permanently delete selected soft-deleted expenses
			expenses.DELETE("/deleted/batch", expenseHandler.PermanentDeleteExpensesBatch)

		// Create new expense record
		// Same trailing-slash handling as above.
		expenses.POST("", expenseHandler.CreateExpense)
		expenses.POST("/", expenseHandler.CreateExpense)

			// Sync expense records
			expenses.POST("/sync", expenseHandler.SyncExpenses)

			// Batch create
			expenses.POST("/batch", expenseHandler.BatchCreateExpense)

			// Update expense record
			expenses.PUT("/:id", expenseHandler.UpdateExpense)

			// Get single expense by ID
			expenses.GET("/:id", expenseHandler.GetExpenseByID)

			// Soft delete expense record
			expenses.DELETE("/:id", expenseHandler.DeleteExpense)

			// Hard delete expense record (permanently remove from DB)
			expenses.DELETE("/:id/hard", expenseHandler.HardDeleteExpense)
		}
	}
}
