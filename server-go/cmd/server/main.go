package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"homemoney/internal/repository"
	"homemoney/internal/routes"
	"homemoney/internal/service"
	"homemoney/pkg/database"

	"github.com/gin-gonic/gin"
)

func main() {
	// Record server start time
	startTime := time.Now()

	// Initialize logging
	log.SetFlags(log.LstdFlags | log.Lshortfile)

	// Load configuration
	config := routes.GetDefaultConfig()
	if port := os.Getenv("PORT"); port != "" {
		config.Port = port
	}

	// Initialize database
	db, err := database.InitDB("./database.sqlite")
	if err != nil {
		log.Fatalf("Database initialization failed: %v", err)
	}
	defer func() { _ = db.Close() }()

	// Create Repository instances
	expenseRepo := repository.NewExpenseRepository(db.GetDB())

	// Create member-related Repository instance
	memberRepo := repository.NewMemberRepository(db.GetDB())

	// Set Gin mode
	if os.Getenv("GIN_MODE") == "release" {
		gin.SetMode(gin.ReleaseMode)
	}

	// Create router engine
	router := gin.New()
	// Serve exact paths without auto-redirecting trailing slashes. The web
	// client calls /api/expenses (no trailing slash); letting Gin redirect it
	// to /api/expenses/ caused an infinite 301/308 loop behind the Next.js
	// proxy (which strips trailing slashes by default).
	router.RedirectTrailingSlash = false

	// Add middleware
	router.Use(
		// Recover from panic
		gin.CustomRecovery(func(c *gin.Context, recovered interface{}) {
			log.Printf("Panic recovered: %v", recovered)
			c.JSON(http.StatusInternalServerError, gin.H{
				"success": false,
				"error":   "Internal server error",
				"code":    "INTERNAL_ERROR",
			})
		}),
		// Request logging
		gin.Logger(),
		// CORS middleware - consistent with JS version
		func(c *gin.Context) {
			origin := c.Request.Header.Get("Origin")
			allowedOrigins := map[string]bool{
				"http://localhost:5173":  true,
				"http://0.0.0.0:5173":   true,
			}
			if allowedOrigins[origin] {
				c.Header("Access-Control-Allow-Origin", origin)
			} else {
				c.Header("Access-Control-Allow-Origin", "http://localhost:5173")
			}
			c.Header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
			c.Header("Access-Control-Allow-Headers", "Content-Type, Authorization")
			c.Header("Access-Control-Allow-Credentials", "true")
			if c.Request.Method == "OPTIONS" {
				c.AbortWithStatus(200)
				return
			}
			c.Next()
		},
	)

	// Increase JSON parsing size limit, consistent with JS version (10MB)
	router.MaxMultipartMemory = 10 << 20 // 10MB

	// Set up system-related routes (health check and API docs)
	sqlDB, err := db.GetDB().DB()
	if err != nil {
		log.Fatalf("Failed to get underlying SQL DB: %v", err)
	}
	routes.SetupHealthRoutes(router, startTime, sqlDB)
	routes.SetupHelpRoutes(router)

	// Set up API routes
	routes.SetupExpenseRoutes(router, expenseRepo)

	// Set up member-related API routes - corresponding to JS version memberRoutes
	routes.SetupMemberRoutes(router, memberRepo)

	// Set up error report routes
	errorReportRepo := repository.NewErrorReportRepository(db.GetDB())
	routes.SetupErrorReportRoutes(router, errorReportRepo)

	// Set up export/import routes
	routes.SetupExportRoutes(router, expenseRepo)

	// Initialize service instances
	// Create JSON file service instance
	jsonFileService := service.NewJsonFileService()
	// Create log service instance
	logService := service.NewLogService(db.GetDB())

	// Register routes
	routes.SetupJsonFileRoutes(router.Group("/api"), jsonFileService)
	routes.SetupLogRoutes(router.Group("/api"), logService)

	// Serve the exported web frontend (web/out) next to the API. The export is
	// produced by "npm run build:static" inside the web/ directory.
	if distPath := routes.ResolveStaticDir(); distPath != "" {
		log.Printf("Serving frontend static files from: %s", distPath)
		routes.SetupStaticRoutes(router, distPath)
	} else {
		log.Printf("Frontend static files not found (expected web/out) - serving the API only")
	}

	// Create HTTP server
	srv := &http.Server{
		Addr:         config.Host + ":" + config.Port,
		Handler:      router,
		ReadTimeout:  config.ReadTimeout,
		WriteTimeout: config.WriteTimeout,
		IdleTimeout:  config.IdleTimeout,
	}

	// Start server
	go func() {
		log.Printf("Server started on port %s", config.Port)
		log.Printf("API docs: http://localhost:%s/api", config.Port)

		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("Server failed to start: %v", err)
		}
	}()

	// Wait for interrupt signal to gracefully shutdown server
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	log.Println("Shutting down server...")

	// Gracefully shutdown server
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if err := srv.Shutdown(ctx); err != nil {
		log.Fatalf("Server forced shutdown: %v", err)
	}

	log.Println("Server exited")
}

// SetupLogLevel sets the log level
func SetupLogLevel(level string) {
	switch level {
	case "debug":
		gin.SetMode(gin.DebugMode)
	case "release":
		gin.SetMode(gin.ReleaseMode)
	default:
		gin.SetMode(gin.TestMode)
	}
}

// The static asset detection that used to live here is now part of
// routes.SetupStaticRoutes, which resolves every request against the exported
// frontend directory.
