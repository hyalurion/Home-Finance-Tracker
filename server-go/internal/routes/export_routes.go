package routes

import (
	"homemoney/internal/handler"
	"homemoney/internal/repository"

	"github.com/gin-gonic/gin"
)

// SetupExportRoutes sets up export/import related routes - fully consistent with JS version
func SetupExportRoutes(router *gin.Engine, expenseRepo *repository.ExpenseRepository) {
	exportHandler := handler.NewExportHandler(expenseRepo)
	importHandler := handler.NewImportHandler(expenseRepo)

	// Export Excel
	router.GET("/api/export/excel", exportHandler.ExportExcel)

	// Export the dataset used by the AI Q&A flow (manual hand-off to an AI chat app)
	aiDataHandler := handler.NewAIDataHandler(expenseRepo)
	router.GET("/api/export/ai-data", aiDataHandler.ExportAIData)

	// Import Excel
	router.POST("/api/import/excel", importHandler.ImportExcel)
}