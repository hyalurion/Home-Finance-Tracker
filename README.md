# Home Finance Tracker

## Project Overview
The Home Finance Tracker is a modern multilingual financial management application designed to help users easily track income and expenses, analyze spending patterns, and improve financial transparency. The system provides a user-friendly interface with powerful data visualization and analysis capabilities.

## Key Features
- **Multilingual Support**: Auto-adapting UI text and date formats (English, Chinese, etc.)
- **Expense Tracking**: Add/view records with details including type, amount, date, and notes
- **Data Export & Import**: Generate Excel reports and support data backup/restore functionality
- **Smart Analytics**:
  - Category breakdowns (food, shopping, transportation, etc.)
  - Interactive spending trend charts with multiple visualization options (bar, pie, line, doughnut, radar charts)
  - Advanced search and filtering system (by type, date range, amount range)
- **Budget Management**: Set and track monthly spending limits with warnings
- **Security Features**: Data encryption and secure storage
- **Responsive Design**: Optimized for both desktop and mobile devices
- **Expense Tracking**: Record and categorize daily expenses with custom categories

## Setup & Usage
### Requirements
- Node.js ≥ 18.0.0
- npm ≥ 9.0.0

### Quick Start
```bash
git clone https://github.com/quiettimejsg/homemoney.git
cd homemoney
# Install dependencies for client and server
npm install
# Start development server
npm run dev  # Starts both frontend and backend
# Alternatively, start services separately
npm run dev:client  # Starts frontend development server
npm run dev:server  # Starts backend development server
```

## Project Structure
- `client/`: Vue.js frontend application
  - `public/`: Static assets
  - `src/`: Source code
    - `components/`: Reusable UI components
    - `views/`: Application views/pages
    - `api/`: API service calls
    - `utils/`: Utility functions
    - `assets/`: Static assets
    - `styles/`: CSS stylesheets
    - `locales/`: Internationalization files
- `server/`: Node.js/Express backend server
  - `src/`: Source code
    - `controllers/`: Request handlers
    - `models/`: Database models
    - `routes/`: API routes
    - `utils/`: Server utilities
  - `data/`: Database files and migrations
- `common.css`: Shared CSS styles
- `start.bat`: Windows startup script

## Available Scripts
### Root Directory
- `npm run dev`: Start both frontend and backend in development mode
- `npm run dev:client`: Start only the frontend in development mode
- `npm run dev:server`: Start only the backend in development mode
- `npm run build`: Build the frontend for production

### Client Directory
- `npm run dev`: Start Vite development server (http://localhost:5173)
- `npm run build`: Build the frontend for production
- `npm run lint`: Run ESLint on the client codebase

### Server Directory
- `npm run dev`: Start backend with nodemon for development
- `npm run start`: Start backend in production mode
- `npm run test`: Run backend tests

## Tech Stack
- **Frontend**:
  - Vue 3 (JavaScript framework)
  - Vite (build tool)
  - Element Plus (Vue 3 UI component library)
  - Chart.js (data visualization)
  - Vue I18n (internationalization)
  - Pinia (state management)
  - Vue Router (client-side routing)
  - Axios (HTTP client)
- **Backend**:
  - Express.js (web server framework)
  - SQLite3 (database)
  - Sequelize ORM (database abstraction)
- **Utilities**:
  - Day.js (date handling)
  - Papa Parse (CSV parsing)
  - bcrypt (encryption)
- **Dev Tools**:
  - ESLint (code quality)
  - Jest (testing)
  - nodemon (development auto-reload)

## API Documentation
The application provides a comprehensive API documentation endpoint that returns all available endpoints with descriptions in both English and Chinese.

### Accessing the API Documentation
To view the complete API documentation, start the server and navigate to:

```
http://localhost:3010/api
```

or view the [api-help.json](api-help.json) file in the project root — snapshot as of 11/3/2025.

This endpoint returns a JSON response containing:
- All available API endpoints organized by category
- HTTP methods (GET, POST, PUT, DELETE)
- English and Chinese descriptions for each endpoint
- Usage instructions for each API

### API Categories
The documentation includes endpoints for:
- Base system health checks
- Expense tracking and management
- Expense tracking and analysis
- JSON file operations
- Payment processing (subscriptions)
- Data import/export functionality
- Member and subscription management
- Logging and monitoring

The API documentation is dynamically generated and will reflect any changes to the API structure.
