package routes

import (
	"database/sql"
	"fmt"
	"log"
	"net/http"
	"path/filepath"
	"time"

	"github.com/gin-gonic/gin"
)

// SetupHealthRoutes sets up health check related routes
func SetupHealthRoutes(router *gin.Engine, startTime time.Time, db *sql.DB) {
	// Health check endpoint - uses gopsutil for professional monitoring data
	router.GET("/api/health", func(c *gin.Context) {
		// Use gopsutil to get system info
		systemInfo, err := getSystemInfo()
		if err != nil {
			log.Printf("Failed to get system info: %v", err)
			c.JSON(http.StatusInternalServerError, gin.H{
				"success": false,
				"error":   "Failed to get system info: " + err.Error(),
			})
			return
		}

		// Use gopsutil to get memory info
		memoryInfo, err := getMemoryInfo()
		if err != nil {
			log.Printf("Failed to get memory info: %v", err)
			c.JSON(http.StatusInternalServerError, gin.H{
				"success": false,
				"error":   "Failed to get memory info: " + err.Error(),
			})
			return
		}

		// Use gopsutil to get current process CPU usage
		processCPUUsage, err := getProcessCPUUsage()
		if err != nil {
			log.Printf("Failed to get process CPU usage: %v", err)
			c.JSON(http.StatusInternalServerError, gin.H{
				"success": false,
				"error":   "Failed to get process CPU usage: " + err.Error(),
			})
			return
		}

		// Use gopsutil to get CPU info
		_, cpuInfo, err := getCPUInfo()
		if err != nil {
			log.Printf("Failed to get CPU info: %v", err)
			c.JSON(http.StatusInternalServerError, gin.H{
				"success": false,
				"error":   "Failed to get CPU info: " + err.Error(),
			})
			return
		}

		// Check database connection
		dbStatus := "connected"
		dbError := ""
		if db == nil {
			dbStatus = "disconnected"
			dbError = "database instance not provided"
		} else if err := db.Ping(); err != nil {
			dbStatus = "disconnected"
			dbError = err.Error()
		}

		// Check file system paths — the exported web frontend directory.
		// The JSON field names are kept for API compatibility.
		clientDistPath := ResolveStaticDir()
		clientDistExists := clientDistPath != ""
		if clientDistExists {
			clientDistPath = filepath.ToSlash(clientDistPath)
		} else {
			clientDistPath = "web/out"
		}

		// Build CPU info - use gopsutil data
		var cpuModel string
		var cpuCount int
		if len(cpuInfo) > 0 {
			cpuModel = cpuInfo[0].ModelName
			cpuCount = int(cpuInfo[0].Cores)
		} else {
			cpuModel = "Unknown CPU"
			cpuCount = 0
		}

		cpuInfoStruct := CPUInfo{
			Count:        cpuCount,
			Model:        cpuModel,
			UsagePercent: fmt.Sprintf("%.1f%%", processCPUUsage),
		}
		cpuInfoStruct.SystemLoad.Message = "Windows does not support load average as unix"
		cpuInfoStruct.SystemLoad.RawValue = [3]string{"0.00", "0.00", "0.00"}

		// Build resource info
		resourceInfo := ResourceInfo{
			Memory: *memoryInfo,
			CPU:    cpuInfoStruct,
		}

		// Build database info
		databaseInfo := DatabaseInfo{
			Status: dbStatus,
			Error:  dbError,
		}

		// Build file system info
		fileSystemInfo := FileSystemInfo{
			ClientDistExists: clientDistExists,
		}

		// Build service info
		serviceInfo := ServiceInfo{
			Database:   databaseInfo,
			FileSystem: fileSystemInfo,
		}

		// Build path info
		pathInfo := PathInfo{
			ClientDistPath: clientDistPath,
		}

		// Build health status data - use struct to ensure field order
		healthData := HealthCheckResponse{
			Status:      "OK",
			Timestamp:   time.Now().UTC().Format(time.RFC3339),
			Version:     "2026.8",
			Uptime:      fmt.Sprintf("%.2fs", time.Since(startTime).Seconds()),
			Environment: *systemInfo,
			Resources:   resourceInfo,
			Services:    serviceInfo,
			Paths:       pathInfo,
		}

		c.JSON(http.StatusOK, healthData)
	})

	// Lightweight health check endpoint - fully consistent with Node.js version
	router.GET("/api/health/lite", func(c *gin.Context) {
		// Check database connection
		dbStatus := "connected"
		if db == nil {
			dbStatus = "disconnected"
		} else if err := db.Ping(); err != nil {
			dbStatus = "disconnected"
		}

		// Lightweight health status data - fully consistent with Node.js version
		healthData := HealthCheckLiteResponse{
			Status:     "OK",
			Timestamp:  time.Now().UTC().Format(time.RFC3339),
			Database:   dbStatus,
		}

		c.JSON(http.StatusOK, healthData)
	})
}