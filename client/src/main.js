import { createApp } from 'vue';
import axios from 'axios';
import { setupAxiosInterceptors } from './utils/offlineDataSync.js';
import { initGlobalErrorMonitoring, tryReportFailedLogs, initConsoleLogging } from './utils/operationLogger.js';

// Single stylesheet entry point. styles/index.css imports the global layers in
// a deliberate order (tokens -> primitives -> layout -> ui -> effects -> app shell).
import './styles/index.css';

import liquidGlassDirective from './directives/liquidGlass.js';

import router from './router';
import i18n from './locales/i18n.js';
// Import i18n instance from locales directory (contains full language packages)
import { createPinia } from 'pinia';

// Import Font Awesome library
import { library } from '@fortawesome/fontawesome-svg-core'
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import {
  faPlus, faUpload, faDownload, faMicrochip,
  faFileAlt, faStar, faEnvelope, faQuestionCircle,
  faChartPie, faSyncAlt, faCog, faChartLine, faTimes,
  faEdit, faTrashAlt, faFileExport, faArrowUp, faArrowDown,
  faChevronLeft, faUndo, faCheckSquare, faHourglassHalf,
  faHome, faExclamationTriangle, faSkull
} from '@fortawesome/free-solid-svg-icons'

// Add icons to the library
library.add(
  faPlus, faUpload, faDownload, faMicrochip,
  faFileAlt, faStar, faEnvelope, faQuestionCircle,
  faChartPie, faSyncAlt, faCog, faChartLine, faTimes,
  faEdit, faTrashAlt, faFileExport, faArrowUp, faArrowDown,
  faChevronLeft, faUndo, faCheckSquare, faHourglassHalf,
  faHome, faExclamationTriangle, faSkull
)

import App from './App.vue';
// Set up Axios offline interceptors
setupAxiosInterceptors(axios);

// Initialize global error monitoring
initGlobalErrorMonitoring();

// Try to report failed logs
tryReportFailedLogs();

// Initialize console logging capture
// - levels: Log levels to capture
// - maxLength: Maximum length of log messages to prevent excessive data
initConsoleLogging({
  levels: ['log', 'error', 'warn', 'info'],
  maxLength: 5000
});

export { i18n };
const pinia = createPinia();

const app = createApp({
  components: { App },
  template: `
    <Suspense>
      <App />
      <template #fallback>Loading...</template>
    </Suspense>
  `
});
app.use(pinia); // Apply Pinia instance

// Register Font Awesome components
app.component('FontAwesomeIcon', FontAwesomeIcon)
// Register the liquid-glass directive: enables containers like cards/dialogs/tooltips to render as frosted glass
app.directive('liquid-glass', liquidGlassDirective)
app.use(router);
app.use(i18n);
app.mount('#app');
console.log('[App Initialization] Application mounted successfully');