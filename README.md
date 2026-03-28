# Home Finance Tracker

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

### Quick Start
```bash
git clone https://github.com/quiettimejsg/homemoney.git
cd homemoney
# Install dependencies for root, client and server
npm install
cd client && npm install && cd ..
cd server && npm install && cd ..
# Start development server
npm run dev  # Starts backend server
# Alternatively, start services separately
npm run dev:client  # Starts frontend development server (port 5173)
npm run dev:server  # Starts backend development server (port 3010)
```

## Project Structure
- `client/`: Vue.js frontend application
  - `public/`: Static assets
  - `src/`: Source code
    - `api/`: API service calls
    - `components/`: Reusable UI components (Glass design system)
    - `composables/`: Vue composition API utilities
    - `views/`: Application views/pages
    - `router/`: Vue Router configuration
    - `stores/`: Pinia state management
    - `utils/`: Utility functions
    - `assets/`: Static assets
    - `styles/`: CSS stylesheets
    - `locales/`: Internationalization files (en-US, zh-CN, zh-TW)
  - `vite.config.js`: Vite build configuration with PWA support
- `server/`: Node.js/Express backend server
  - `src/`: Source code
    - `controllers/`: Request handlers
    - `models/`: Database models (Sequelize)
    - `routes/`: API routes
    - `utils/`: Server utilities
    - `migrations/`: Database migration scripts
  - `config/`: Server configuration
  - `data/`: Database files
- `android/`: Android native application (Kotlin)
  - `app/`: Main Android app module
  - `gradle/`: Gradle wrapper configuration
- `.github/`: GitHub Actions workflows
- `.vercel/`: Vercel deployment configuration
- `common.css`: Shared CSS styles
- `start.bat` / `start-dev.bat`: Windows startup scripts

## Available Scripts
### Root Directory
- `npm run dev`: Start backend server
- `npm run dev:client`: Start frontend in development mode (port 5173)
- `npm run dev:server`: Start backend with nodemon for development (port 3010)
- `npm run build`: Build the frontend for production
- `npm run start`: Start backend in production mode

### Client Directory
- `npm run dev`: Start Vite development server (http://localhost:5173)
- `npm run build`: Build the frontend for production
- `npm run lint`: Run ESLint on the client codebase

### Server Directory
- `npm run dev`: Start backend with nodemon for development
- `npm run start`: Start backend in production mode
- `npm run server`: Start backend with memory optimization
- `npm run test`: Run backend tests (Jest)
- `npm run lint`: Run ESLint on the server codebase
- `npm run migrate`: Run database migrations

## Tech Stack
- **Frontend**:
  - Vue 3.5 (JavaScript framework)
  - Vite 8 (build tool)
  - Vue Router 5 (client-side routing)
  - Pinia 3 (state management)
  - Vue I18n 11 (internationalization)
  - Chart.js 4 (data visualization)
  - Font Awesome 7 (icons)
  - Day.js (date handling)
  - Papa Parse (CSV parsing)
  - XLSX (Excel export/import)
  - Marked + Highlight.js (Markdown rendering)
  - Lunar JavaScript (Chinese lunar calendar)
  - vite-plugin-pwa (PWA support)
- **Backend**:
  - Express.js 5 (web server framework)
  - SQLite3 (database)
  - Sequelize ORM (database abstraction)
  - bcrypt (encryption)
  - JWT (authentication)
  - Multer (file upload)
  - node-schedule (scheduled tasks)
- **Android**:
  - Kotlin
  - Capacitor
  - Hilt (dependency injection)
  - WorkManager (background sync)
- **Dev Tools**:
  - ESLint (code quality)
  - Jest (testing)
  - nodemon (development auto-reload)
  - unplugin-auto-import / unplugin-vue-components (auto import)

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
