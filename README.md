# Home Finance Tracker

[Android App](android/README.md)

## Project Overview
The Home Finance Tracker is a modern multilingual financial management application designed to help users easily track income and expenses, analyze spending patterns, and improve financial transparency. The system provides a user-friendly interface with powerful data visualization and analysis capabilities. Supports Web, Android platforms.

## Key Features
- **Multilingual Support**: Auto-adapting UI text and date formats (English, Simplified Chinese, Traditional Chinese)
- **Expense Tracking**: Add/view records with details including type, amount, date, and notes
- **Data Export & Import**: Generate Excel reports and support data backup/restore functionality
- **Smart Analytics**:
  - Category breakdowns (food, shopping, transportation, etc.)
  - Interactive spending trend charts with multiple visualization options (bar, pie, line, doughnut, radar charts)
  - Advanced search and filtering system (by type, date range, amount range)
- **Budget Management**: Set and track monthly spending limits with warnings
- **Membership System**: Subscription management with premium features
- **Security Features**: Data encryption, secure storage
- **Markdown Support**: Rich text display with syntax highlighting
- **Responsive Design**: Optimized for both desktop and mobile devices
- **Android App**: Native Android application support

## Setup & Usage
### Requirements
- Node.js ≥ 18.0.0
- npm ≥ 9.0.0
- Go ≥ 1.26


### Quick Start
```bash
cd server-go
# Install dependencies
go mod download
# Run the server (port 3010)
go run cmd/server/main.go
# Or build and run
go build -o server.exe cmd/server/main.go
./server.exe
```

The Go server automatically serves the exported web frontend from `web/out` when available, and uses `database.sqlite` in the project root directory.

## Project Structure
- `web/`: Next.js 16 frontend application (App Router, React 19, TypeScript)
  - `public/`: Static assets
  - `src/app/`: App Router pages and layouts
    - `layout.tsx`: Root layout, providers, shared header
    - `page.tsx`: Home page (expense list, budget, AI tools)
    - `charts/`, `membership/`, `recycle-bin/`: Feature routes
    - `globals.css`: Monochrome design system (tokens, components, responsive)
  - `src/components/`: Reusable UI components
    - `expense/`: Expense list, search, stats, table, forms, AI dialogs
    - `ui/`: Dialog, Select, Toast, ConfirmDialog
    - `avatar/`: Avatar cropper
    - `charts/`: Chart.js based visualisation
  - `src/i18n/`: Internationalization provider + locale files (12 languages)
  - `src/lib/`: API clients, formatters, URL state helpers
  - `next.config.mjs`: Build config (static export + dev API proxy)
  - `out/`: Static export consumed by the Go server
- `server-go/`: Go/Gin backend server
  - `cmd/server/`: Application entry point
  - `internal/`: Core application code
    - `handler/`: HTTP request handlers
    - `handlers/`: Expense-specific handlers
    - `models/`: Data models and query structures
    - `repository/`: Database access layer (GORM)
    - `routes/`: API route definitions
    - `service/`: Business logic services
  - `pkg/`: Shared packages
    - `database/`: Database connection and migration
    - `utils/`: Utility functions and response helpers
- `android/`: Android native application (Kotlin) — [Android README](android/README.md)
  - `app/src/main/cpp/`: Native C++ sync engine — [C++ README](android/app/src/main/cpp/README.md)
  - `app/`: Main Android app module
  - `gradle/`: Gradle wrapper configuration
- `.github/`: GitHub Actions workflows
- `.vercel/`: Vercel deployment configuration
- `common.css`: Shared CSS styles
- `start.bat` / `start-dev.bat`: Windows startup scripts

## Available Scripts
### Web Directory (`web/`)
- `npm run dev`: Start the Next.js dev server (http://localhost:3000) — proxies `/api` to the Go backend on :3010
- `npm run build`: Build the Next.js app as a standalone server
- `npm run build:static`: Static export into `web/out/` — this is what the Go server serves
- `npm run start`: Start the built Next.js server
- `npm run lint`: Run ESLint on the web codebase

### Server-Go Directory
- `go run cmd/server/main.go`: Start Go server in development mode (port 3010)
- `go build -o server.exe cmd/server/main.go`: Build Go server binary
- `go mod download`: Download Go module dependencies
- `go mod tidy`: Clean up Go module dependencies

### Building the frontend for the backend
```bash
cd web
npm ci
npm run build:static   # exports to web/out/
cd ../server-go
go build -o server.exe ./cmd/server
./server.exe           # serves the API and web/out on :3010
```
`WEB_DIST` can be set to serve the frontend from a different directory.

## Tech Stack
- **Frontend** (`web/`):
  - Next.js 16 (App Router, React 19, TypeScript)
  - Turbopack (build tool) + static export for the Go server
  - React 19 (UI runtime)
  - Custom lightweight i18n provider (12 languages)
  - Chart.js 4 + react-chartjs-2 (data visualization)
  - Marked (Markdown rendering)
  - Intl.DateTimeFormat (locale-aware date formatting)
  - Native fetch (no HTTP client dependency)
  - Monochrome design system driven by CSS custom properties
- **Backend**:
  - Go 1.26
  - Gin (web framework)
  - GORM (ORM)
  - SQLite (database, via glebarez driver)
  - excelize (Excel export/import)
  - gopsutil (system monitoring)
  - uuid (unique ID generation)
- **Android**:
  - Kotlin 2.4
  - Jetpack Compose
  - Hilt (dependency injection)
  - WorkManager (background sync)
  - C++23 via NDK — [native sync engine](android/app/src/main/cpp/README.md)
- **Dev Tools**:
  - ESLint (code quality)
  - cross-env (cross-platform environment variables for npm scripts)
  - cloc (code statistics in CI)

## API Documentation
The application provides a comprehensive API documentation endpoint that returns all available endpoints with descriptions in both English and Chinese.

### Accessing the API Documentation
To view the complete API documentation, start the server and navigate to:

```
http://localhost:3010/api
```

or view the [api-help.json](api-help.json) file in the project root — snapshot as of 2026-03-28.

This endpoint returns a JSON response containing:
- All available API endpoints organized by category
- HTTP methods (GET, POST, PUT, DELETE)
- English and Chinese descriptions for each endpoint
- Usage instructions for each API

### API Categories
The documentation includes endpoints for:
- Base system health checks
- Expense tracking and management
- Expense statistics and analysis
- JSON file operations
- Member and subscription management
- Data import/export functionality
- Error reporting
- Logging and monitoring

The API documentation is dynamically generated and will reflect any changes to the API structure.
