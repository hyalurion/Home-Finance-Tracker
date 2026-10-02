<template>
  <div class="charts-page">
    <div class="chart-controls glass-panel">
      <LiquidGlassBottomNavBar
        :model-value="activeChart"
        :items="navbarItems"
        @update:modelValue="onChartTypeChange"
        size="medium"
        :always-show-glass="false"
        color="#3b82f6"
        class="chart-type-navbar"
      />
      <div class="date-range-picker glass-input-group">
        <input
          type="date"
          v-model="startDate"
          @change="handleStartDateChange"
          :max="endDate"
          class="date-input glass-input"
        />
        <span class="range-separator">{{ t('common.to') }}</span>
        <input
          type="date"
          v-model="endDate"
          @change="handleEndDateChange"
          :min="startDate"
          class="date-input glass-input"
        />
      </div>
    </div>
    <div v-liquid-glass class="charts-container glass-card">
      <div class="chart-wrapper glass-chart-container">
        <canvas id="expenseChart"></canvas>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, onMounted, onUnmounted, watch } from 'vue';
import Chart from 'chart.js/auto';
import { useI18n } from 'vue-i18n';
import LiquidGlassBottomNavBar from '../liquid-glass/LiquidGlassBottomNavBar.vue';

const isValidDate = (date) => {
  return date instanceof Date && !isNaN(date.getTime());
};

const formatDate = (date) => {
  if (!isValidDate(date)) return '';
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

const parseDate = (dateString) => {
  if (!dateString) return null;
  const date = new Date(dateString);
  return isValidDate(date) ? date : null;
};

const getStartOfMonth = () => {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), 1);
};

const getEndOfMonth = () => {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth() + 1, 0);
};

export default {
  name: 'ExpenseCharts',
  props: {
    expenses: {
      type: Array,
      required: true
    }
  },
  setup(props) {
    const activeChart = ref('bar');
    const startDate = ref(formatDate(getStartOfMonth()));
    const endDate = ref(formatDate(getEndOfMonth()));
    const chartInstances = ref({});
// References to event handlers, used to remove listeners on unmount
const windowResizeHandler = ref(null);
const orientationChangeHandler = ref(null);
const canvasTouchHandlers = ref(null);
    
    const { t } = useI18n();

// Debounce function implementation
const debounce = (func, wait) => {
  let timeout;
  return function executedFunction(...args) {
    const later = () => {
      clearTimeout(timeout);
      func(...args);
    };
    clearTimeout(timeout);
    timeout = setTimeout(later, wait);
  };
};
    const chartTypes = [
      { value: 'bar', label: t('chart.bar') },
      { value: 'line', label: t('chart.line') },
      { value: 'doughnut', label: t('chart.doughnut') },
      { value: 'radar', label: t('chart.radar') }
    ];

    // SVG icons for the chart-switch bottom navigation bar
    const chartIcons = {
      bar: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="100%" height="100%"><line x1="6" y1="20" x2="6" y2="13"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="18" y1="20" x2="18" y2="9"/></svg>',
      line: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="100%" height="100%"><polyline points="3 17 9 11 13 15 21 6"/><line x1="3" y1="21" x2="21" y2="21"/></svg>',
      doughnut: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="100%" height="100%"><circle cx="12" cy="12" r="8"/><circle cx="12" cy="12" r="3" fill="currentColor" stroke="none"/></svg>',
      radar: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="100%" height="100%"><polygon points="12 3 21 12 12 21 3 12"/><line x1="12" y1="12" x2="21" y2="12"/><line x1="12" y1="12" x2="12" y2="21"/><circle cx="12" cy="12" r="2" fill="currentColor" stroke="none"/></svg>'
    };

    // Bottom-navigation items for chart switching (id/label/icon shape)
    const navbarItems = chartTypes.map((type) => ({
      id: type.value,
      label: type.label,
      icon: chartIcons[type.value] || ''
    }));

    // Handle chart-type change from the navigation bar and re-render
    const onChartTypeChange = (val) => {
      activeChart.value = val;
      renderChart();
    };

    // Filter expense data within the date range
    const filteredExpenses = ref([]);

    // Handle start-date change
    const handleStartDateChange = () => {
      console.log('Start date changed to:', startDate.value);
      // Ensure the start date is not later than the end date
      if (startDate.value && endDate.value && startDate.value > endDate.value) {
        startDate.value = endDate.value;
      }
      filterExpenses();
      renderAllCharts();
    };

    // Handle end-date change
    const handleEndDateChange = () => {
      console.log('End date changed to:', endDate.value);
      // Ensure the end date is not earlier than the start date
      if (startDate.value && endDate.value && endDate.value < startDate.value) {
        endDate.value = startDate.value;
      }
      filterExpenses();
      renderAllCharts();
    };

    // Filter the expense data
    const filterExpenses = () => {
      // Ensure the dates are valid
      if (!startDate.value || !endDate.value) {
        console.warn('Invalid date range');
        filteredExpenses.value = [];
        return;
      }
      
      const startDateObj = parseDate(startDate.value);
      const endDateObj = parseDate(endDate.value);
      
      if (!startDateObj || !endDateObj) {
        console.warn('Invalid date range parsing');
        filteredExpenses.value = [];
        return;
      }
      
      console.log('Date range filter:', formatDate(startDateObj), 'to', formatDate(endDateObj));

      filteredExpenses.value = props.expenses.filter(expense => {
        // Ensure expense.date is valid
        if (!expense.date) {
          console.warn('Expense with no date:', expense);
          return false;
        }
        
        const expenseDate = parseDate(expense.date);
        // Check whether date parsing succeeded
        if (!expenseDate) {
          console.warn('Invalid expense date format:', expense.date);
          return false;
        }
        
        // Correctly handle date boundaries, inclusive of start and end dates
        const isAfterStart = expenseDate >= startDateObj;
        const isBeforeEnd = expenseDate <= endDateObj;
        
        console.log(`Expense date ${formatDate(expenseDate)}: isAfterStart=${isAfterStart}, isBeforeEnd=${isBeforeEnd}`);
        
        return isAfterStart && isBeforeEnd;
      });
      
      console.log('Filtered expenses:', filteredExpenses.value);
      console.log('Filtered expenses count:', filteredExpenses.value.length);
    };

    // Prepare chart data
    const prepareChartData = (type) => {
      switch (type) {
        case 'bar':
          return prepareBarData();
        case 'line':
          return prepareLineData();
        case 'doughnut':
          return preparePieData();
        case 'radar':
          return prepareRadarData();
        default:
          return prepareBarData();
      }
    };

    // Prepare bar chart data
    const prepareBarData = () => {
      // Group by category
      const categoryData = {};
      filteredExpenses.value.forEach(expense => {
        if (!categoryData[expense.type]) {
          categoryData[expense.type] = 0;
        }
        categoryData[expense.type] += parseFloat(expense.amount);
      });

      const labels = Object.keys(categoryData);
      const data = Object.values(categoryData);

      return {
        labels,
        datasets: [{
          label: t('expense.amount'),
          data,
          backgroundColor: [
            'rgba(255, 99, 132, 0.4)',
            'rgba(54, 162, 235, 0.4)',
            'rgba(255, 206, 86, 0.4)',
            'rgba(75, 192, 192, 0.4)',
            'rgba(153, 102, 255, 0.4)',
            'rgba(255, 159, 64, 0.4)',
            'rgba(199, 199, 199, 0.4)'
          ],
          borderColor: [
            'rgba(255, 99, 132, 0.8)',
            'rgba(54, 162, 235, 0.8)',
            'rgba(255, 206, 86, 0.8)',
            'rgba(75, 192, 192, 0.8)',
            'rgba(153, 102, 255, 0.8)',
            'rgba(255, 159, 64, 0.8)',
            'rgba(199, 199, 199, 0.8)'
          ],
          borderWidth: 2,
          borderRadius: 8,
          borderSkipped: false,
          hoverBackgroundColor: [
            'rgba(255, 99, 132, 0.6)',
            'rgba(54, 162, 235, 0.6)',
            'rgba(255, 206, 86, 0.6)',
            'rgba(75, 192, 192, 0.6)',
            'rgba(153, 102, 255, 0.6)',
            'rgba(255, 159, 64, 0.6)',
            'rgba(199, 199, 199, 0.6)'
          ],
          hoverBorderColor: [
            'rgba(255, 99, 132, 1)',
            'rgba(54, 162, 235, 1)',
            'rgba(255, 206, 86, 1)',
            'rgba(75, 192, 192, 1)',
            'rgba(153, 102, 255, 1)',
            'rgba(255, 159, 64, 1)',
            'rgba(199, 199, 199, 1)'
          ],
          hoverBorderWidth: 3
        }]
      };
    };

    // Prepare pie chart data
    const preparePieData = () => {
      const categoryData = {};
      filteredExpenses.value.forEach(expense => {
        if (!categoryData[expense.type]) {
          categoryData[expense.type] = 0;
        }
        categoryData[expense.type] += parseFloat(expense.amount);
      });

      const labels = Object.keys(categoryData);
      const data = Object.values(categoryData);

      return {
        labels,
        datasets: [{
          data,
          backgroundColor: [
            'rgba(255, 99, 132, 0.5)',
            'rgba(54, 162, 235, 0.5)',
            'rgba(255, 206, 86, 0.5)',
            'rgba(75, 192, 192, 0.5)',
            'rgba(153, 102, 255, 0.5)',
            'rgba(255, 159, 64, 0.5)',
            'rgba(199, 199, 199, 0.5)'
          ],
          borderColor: [
            'rgba(255, 99, 132, 0.9)',
            'rgba(54, 162, 235, 0.9)',
            'rgba(255, 206, 86, 0.9)',
            'rgba(75, 192, 192, 0.9)',
            'rgba(153, 102, 255, 0.9)',
            'rgba(255, 159, 64, 0.9)',
            'rgba(199, 199, 199, 0.9)'
          ],
          borderWidth: 2,
          hoverBackgroundColor: [
            'rgba(255, 99, 132, 0.7)',
            'rgba(54, 162, 235, 0.7)',
            'rgba(255, 206, 86, 0.7)',
            'rgba(75, 192, 192, 0.7)',
            'rgba(153, 102, 255, 0.7)',
            'rgba(255, 159, 64, 0.7)',
            'rgba(199, 199, 199, 0.7)'
          ],
          hoverBorderColor: [
            'rgba(255, 99, 132, 1)',
            'rgba(54, 162, 235, 1)',
            'rgba(255, 206, 86, 1)',
            'rgba(75, 192, 192, 1)',
            'rgba(153, 102, 255, 1)',
            'rgba(255, 159, 64, 1)',
            'rgba(199, 199, 199, 1)'
          ],
          hoverBorderWidth: 3,
          hoverOffset: 10
        }]
      };
    };

    // Prepare line chart data
    const prepareLineData = () => {
      console.log('Preparing line chart data with expenses count:', filteredExpenses.value.length);
        
        // Sort by time
        const sortedExpenses = [...filteredExpenses.value].sort((a, b) => {
          // Use the date field instead of time, to stay consistent with the backend data
          const dateA = parseDate(a.date || a.time);
          const dateB = parseDate(b.date || b.time);
          return dateA - dateB;
        });
        
        console.log('First expense time:', sortedExpenses.length > 0 ? formatDate(parseDate(sortedExpenses[0].date || sortedExpenses[0].time)) : 'No data');
        console.log('Last expense time:', sortedExpenses.length > 0 ? formatDate(parseDate(sortedExpenses[sortedExpenses.length - 1].date || sortedExpenses[sortedExpenses.length - 1].time)) : 'No data');

      // Group by date
      const dateData = {};
      sortedExpenses.forEach(expense => {
        // Re-validate date validity, using the date field instead of time
        const expenseDate = expense.date || expense.time;
        if (!expenseDate || !parseDate(expenseDate)) {
          console.warn('Skipping expense with invalid date:', expense);
          return;
        }
        
        const dateStr = formatDate(parseDate(expenseDate));
        if (!dateData[dateStr]) {
          dateData[dateStr] = 0;
        }
        dateData[dateStr] += parseFloat(expense.amount) || 0;
        
        console.log(`Expense for ${dateStr}: ${expense.amount}`);
      });
      
      console.log('Date data object:', dateData);

      const labels = Object.keys(dateData);
      const data = Object.values(dateData);
      
      console.log('Line chart labels:', labels);
      console.log('Line chart data points:', data);
      console.log('Number of data points:', data.length);

      return {
        labels,
        datasets: [{
          label: t('expense.dailyExpense'),
          data,
          fill: true,
          backgroundColor: 'rgba(54, 162, 235, 0.1)',
          borderColor: 'rgba(54, 162, 235, 0.8)',
          borderWidth: 3,
          tension: 0.4,
          pointBackgroundColor: 'rgba(54, 162, 235, 1)',
          pointBorderColor: 'rgba(255, 255, 255, 0.8)',
          pointBorderWidth: 2,
          pointRadius: 5,
          pointHoverRadius: 8,
          pointHoverBackgroundColor: 'rgba(54, 162, 235, 1)',
          pointHoverBorderColor: 'rgba(255, 255, 255, 1)',
          pointHoverBorderWidth: 3
        }]
      };
    };

    // Prepare radar chart data
    const prepareRadarData = () => {
      // Get all unique categories
      const categories = [...new Set(filteredExpenses.value.map(expense => expense.type))];
      // Get all unique weekdays
      const weekdays = [
        t('common.sunday'), 
        t('common.monday'), 
        t('common.tuesday'), 
        t('common.wednesday'), 
        t('common.thursday'), 
        t('common.friday'), 
        t('common.saturday')
      ];

      // Group by weekday and category
      const data = categories.map(category => {
        const values = Array(7).fill(0);
        filteredExpenses.value.forEach(expense => {
          if (expense.type === category) {
            const expenseDate = parseDate(expense.date);
            if (expenseDate) {
              const weekday = expenseDate.getDay();
              values[weekday] += parseFloat(expense.amount);
            }
          }
        });
        return {
          label: category,
          data: values,
          backgroundColor: `rgba(${Math.floor(Math.random() * 255)}, ${Math.floor(Math.random() * 255)}, ${Math.floor(Math.random() * 255)}, 0.2)`,
          borderColor: `rgba(${Math.floor(Math.random() * 255)}, ${Math.floor(Math.random() * 255)}, ${Math.floor(Math.random() * 255)}, 0.8)`,
          pointBackgroundColor: `rgba(${Math.floor(Math.random() * 255)}, ${Math.floor(Math.random() * 255)}, ${Math.floor(Math.random() * 255)}, 1)`,
          pointBorderColor: 'rgba(255, 255, 255, 0.8)',
          pointBorderWidth: 2,
          pointRadius: 4,
          pointHoverRadius: 7,
          pointHoverBackgroundColor: `rgba(${Math.floor(Math.random() * 255)}, ${Math.floor(Math.random() * 255)}, ${Math.floor(Math.random() * 255)}, 1)`,
          pointHoverBorderColor: 'rgba(255, 255, 255, 1)',
          pointHoverBorderWidth: 3,
          borderWidth: 2,
          hoverBackgroundColor: `rgba(${Math.floor(Math.random() * 255)}, ${Math.floor(Math.random() * 255)}, ${Math.floor(Math.random() * 255)}, 0.4)`,
          hoverBorderColor: `rgba(${Math.floor(Math.random() * 255)}, ${Math.floor(Math.random() * 255)}, ${Math.floor(Math.random() * 255)}, 1)`,
          hoverBorderWidth: 3
        };
      });

      return {
        labels: weekdays,
        datasets: data
      };
    };

    // Render the main chart
    const renderChart = () => {
      const ctx = document.getElementById('expenseChart');
      
      // Safely destroy the old chart to prevent canvas reuse errors
      try {
        // Check for an existing chart instance and destroy it
        if (chartInstances.value && chartInstances.value.main) {
          chartInstances.value.main.destroy();
          // Clear the reference to allow garbage collection
          chartInstances.value.main = null;
        }
        
        // Extra cleanup of the canvas context to avoid render residue on small screens
        if (ctx && ctx.getContext) {
          const context = ctx.getContext('2d');
          if (context) {
            // Clear the canvas content
            context.clearRect(0, 0, ctx.width, ctx.height);
            // Reset the canvas width and height to force-clear all state
            const width = ctx.width;
            const height = ctx.height;
            ctx.width = width;
            ctx.height = height;
          }
        }
      } catch (error) {
        console.warn('Error destroying chart:', error);
        // Even if destruction fails, still attempt to create the new chart
      }

      const chartData = prepareChartData(activeChart.value);
      
      // Check whether there are data points
      let options = {};
      
      // Common configuration, with optimizations for mobile devices
      const commonOptions = {
        responsive: true,
        maintainAspectRatio: false,
        // Touch-event optimizations for mobile
        interaction: {
          intersect: false,
          mode: 'index',
          // Disable default touch-gesture handling to avoid conflicts with native touch events
          gestures: {
            // Disable pan and zoom on small-screen devices
            pan: window.innerWidth < 768 ? false : true,
            zoom: window.innerWidth < 768 ? false : true
          }
        },
        // Performance-oriented configuration
        animation: {
          duration: window.innerWidth < 480 ? 300 : 500,
          easing: 'easeOutQuart'
        },
        // Canvas event-handling optimizations
        onHover: (event, elements) => {
          // Only change the cursor style when an element is hovered
          event.native.target.style.cursor = elements.length > 0 ? 'pointer' : 'default';
        },
        // Rendering performance optimizations
        elements: {
          point: {
            hoverRadius: window.innerWidth < 480 ? 6 : 8,
            hitRadius: window.innerWidth < 480 ? 10 : 12,
            radius: window.innerWidth < 480 ? 3 : 4
          }
        },
        // Shared chart plugin config. The chart area stays opaque so the
        // glass container behind it reads as a panel, not a smeared canvas.
        plugins: {
          tooltip: {
            backgroundColor: 'rgba(255, 255, 255, 0.9)',
            borderColor: 'rgba(255, 255, 255, 0.3)',
            borderWidth: 1,
            titleColor: '#333',
            bodyColor: '#666',
            padding: 12,
            cornerRadius: 8,
            displayColors: true,
            boxPadding: 4,
            usePointStyle: true,
            boxShadow: '0 4px 12px rgba(0, 0, 0, 0.1)'
          },
          legend: {
            labels: {
              color: '#666',
              font: {
                family: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
              },
              usePointStyle: true,
              pointStyle: 'circle',
              padding: 20
            }
          }
        },
        // Dark mode adaptation
        isDarkMode: window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches
      };

      // Set different options based on the chart type
      switch (activeChart.value) {
        case 'bar':
          options = {
            ...commonOptions,
            plugins: {
              ...commonOptions.plugins,
              legend: {
                position: 'top',
                labels: {
                  font: {
                    size: window.innerWidth < 480 ? 10 : 12
                  },
                  padding: 10,
                  color: '#666'
                }
              },
              title: {
                display: true,
                text: t('chart.categoryAnalysis'),
                font: {
                  size: window.innerWidth < 480 ? 14 : 16
                },
                color: '#333',
                padding: {
                  bottom: 20
                }
              }
            },
            scales: {
              y: {
                beginAtZero: true,
                title: {
                  display: true,
                  text: t('expense.amount') + ' (' + t('common.currency') + ')',
                  font: {
                    size: window.innerWidth < 480 ? 10 : 12
                  },
                  color: '#666'
                },
                ticks: {
                  font: {
                    size: window.innerWidth < 480 ? 9 : 11
                  },
                  color: '#999'
                },
                grid: {
                  color: 'rgba(0, 0, 0, 0.05)',
                  drawBorder: false
                }
              },
              x: {
                title: {
                  display: true,
                  text: t('expense.type'),
                  font: {
                    size: window.innerWidth < 480 ? 10 : 12
                  },
                  color: '#666'
                },
                ticks: {
                  font: {
                    size: window.innerWidth < 480 ? 9 : 11
                  },
                  color: '#999',
                  maxRotation: 45,
                  minRotation: 45
                },
                grid: {
                  display: false,
                  drawBorder: false
                }
              }
            }
          };
          break;
        case 'doughnut':
          options = {
            ...commonOptions,
            plugins: {
              ...commonOptions.plugins,
              legend: {
                position: window.innerWidth < 480 ? 'bottom' : 'right',
                labels: {
                  font: {
                    size: window.innerWidth < 480 ? 10 : 12
                  },
                  padding: 10,
                  boxWidth: window.innerWidth < 480 ? 10 : 12,
                  color: '#666'
                }
              },
              title: {
                display: true,
                text: t('chart.categoryPercentage'),
                font: {
                  size: window.innerWidth < 480 ? 14 : 16
                },
                color: '#333',
                padding: {
                  bottom: 20
                }
              }
            },
            cutout: '60%'
          };
          break;
        case 'line':
          options = {
            ...commonOptions,
            interaction: {
              ...commonOptions.interaction,
              intersect: false,
              mode: 'index',
              axis: 'x',
              touch: {
                radius: window.innerWidth < 480 ? 20 : 10,
                enabled: true,
                axis: 'x',
                zoom: false
              }
            },
            animation: {
              ...commonOptions.animation,
              duration: window.innerWidth < 480 ? 200 : 500,
              easing: window.innerWidth < 480 ? 'linear' : 'easeOutQuart'
            },
            elements: {
              point: {
                radius: window.innerWidth < 480 ? 2 : 4,
                hitRadius: window.innerWidth < 480 ? 15 : 12,
                hoverRadius: window.innerWidth < 480 ? 8 : 6,
                hoverAnimationDuration: window.innerWidth < 480 ? 0 : 200,
                backgroundColor: 'rgba(54, 162, 235, 1)',
                borderColor: 'rgba(255, 255, 255, 0.8)',
                borderWidth: 2
              },
              line: {
                tension: window.innerWidth < 480 ? 0.1 : 0.4,
                borderWidth: window.innerWidth < 480 ? 2 : 3
              }
            },
            plugins: {
              ...commonOptions.plugins,
              legend: {
                position: 'top',
                labels: {
                  font: { size: window.innerWidth < 480 ? 10 : 12 },
                  padding: 10,
                  color: '#666'
                }
              },
              title: {
                display: true,
                text: t('chart.trendAnalysis'),
                font: { size: window.innerWidth < 480 ? 14 : 16 },
                color: '#333',
                padding: {
                  bottom: 20
                }
              }
            },
            scales: {
              y: {
                beginAtZero: true,
                title: {
                  display: true,
                  text: t('expense.amount') + ' (' + t('common.currency') + ')',
                  font: { size: window.innerWidth < 480 ? 10 : 12 },
                  color: '#666'
                },
                ticks: {
                  font: { size: window.innerWidth < 480 ? 9 : 11 },
                  maxTicksLimit: window.innerWidth < 480 ? 4 : 6,
                  color: '#999'
                },
                grid: {
                  color: 'rgba(0, 0, 0, 0.05)',
                  drawBorder: false
                }
              },
              x: {
                type: 'category',
                title: {
                  display: true,
                  text: t('common.date'),
                  font: { size: window.innerWidth < 480 ? 10 : 12 },
                  color: '#666'
                },
                ticks: {
                  maxTicksLimit: window.innerWidth < 480 ? 5 : 10,
                  callback: function(value, index, values) {
                    if (window.innerWidth < 480) {
                      const valueStr = String(value);
                      return valueStr.length >= 5 ? valueStr.substring(5) : valueStr;
                    }
                    return value;
                  },
                  autoSkip: true,
                  maxRotation: 45,
                  minRotation: 45,
                  font: { size: window.innerWidth < 480 ? 8 : 11 },
                  color: '#999'
                },
                grid: {
                  color: 'rgba(0, 0, 0, 0.05)',
                  drawBorder: false
                }
              }
            },
            responsive: true,
            maintainAspectRatio: false,
            layout: {
              padding: window.innerWidth < 480 ? 15 : 25
            }
          };
          break;
        case 'radar':
          options = {
            ...commonOptions,
            plugins: {
              ...commonOptions.plugins,
              legend: {
                position: window.innerWidth < 480 ? 'bottom' : 'top',
                labels: {
                  font: {
                    size: window.innerWidth < 480 ? 10 : 12
                  },
                  padding: 8,
                  boxWidth: window.innerWidth < 480 ? 10 : 12,
                  color: '#666'
                }
              },
              title: {
                display: true,
                text: t('chart.weekdayAnalysis'),
                font: {
                  size: window.innerWidth < 480 ? 14 : 16
                },
                color: '#333',
                padding: {
                  bottom: 20
                }
              }
            },
            scales: {
              r: {
                angleLines: {
                  display: true,
                  color: 'rgba(0, 0, 0, 0.05)'
                },
                grid: {
                  color: 'rgba(0, 0, 0, 0.05)',
                  drawBorder: false
                },
                pointLabels: {
                  font: {
                    size: window.innerWidth < 480 ? 10 : 12
                  },
                  color: '#666'
                },
                ticks: {
                  font: {
                    size: window.innerWidth < 480 ? 9 : 11
                  },
                  color: '#999',
                  backdropColor: 'rgba(255, 255, 255, 0.8)',
                  backdropPadding: 4
                },
                suggestedMin: 0
              }
            }
          };
          break;
      }

      // Ensure ctx exists
      if (!ctx) {
        console.error('Canvas element not found');
        return;
      }
      
      // Safely create a new chart
      try {
        // Ensure ctx exists and is usable
        if (!ctx || !ctx.getContext) {
          console.error('Canvas context not available');
          return;
        }
        
        // Ensure chartData and options are valid
        if (!chartData || !options) {
          console.error('Invalid chart data or options');
          return;
        }
        
        // Detect dark mode
        const isDarkMode = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
        
        // Adjust colours based on dark mode
        if (isDarkMode) {
          if (options.plugins && options.plugins.tooltip) {
            options.plugins.tooltip.backgroundColor = 'rgba(42, 45, 53, 0.95)';
            options.plugins.tooltip.titleColor = '#E4E6EB';
            options.plugins.tooltip.bodyColor = '#B0B4BD';
            options.plugins.tooltip.borderColor = 'rgba(255, 255, 255, 0.14)';
          }
          if (options.plugins && options.plugins.legend && options.plugins.legend.labels) {
            options.plugins.legend.labels.color = '#B0B4BD';
          }
          if (options.plugins && options.plugins.title) {
            options.plugins.title.color = '#E4E6EB';
          }
          if (options.scales) {
            if (options.scales.y) {
              if (options.scales.y.title) options.scales.y.title.color = '#B0B4BD';
              if (options.scales.y.ticks) options.scales.y.ticks.color = '#8a8f98';
            }
            if (options.scales.x) {
              if (options.scales.x.title) options.scales.x.title.color = '#B0B4BD';
              if (options.scales.x.ticks) options.scales.x.ticks.color = '#8a8f98';
            }
            if (options.scales.r) {
              if (options.scales.r.ticks) {
                options.scales.r.ticks.color = '#8a8f98';
                options.scales.r.ticks.backdropColor = 'rgba(42, 45, 53, 0.85)';
              }
              if (options.scales.r.pointLabels) {
                options.scales.r.pointLabels.color = '#B0B4BD';
              }
              if (options.scales.r.grid) {
                options.scales.r.grid.color = 'rgba(255, 255, 255, 0.08)';
              }
              if (options.scales.r.angleLines) {
                options.scales.r.angleLines.color = 'rgba(255, 255, 255, 0.08)';
              }
            }
          }
        }
        
        // On small-screen devices, use a lighter chart config for better performance
        if (window.innerWidth < 480) {
          // Keep the animation duration short
          if (!options.animation) options.animation = {};
          options.animation.duration = 200;
          
          // Simplify interaction to avoid performance issues
          if (!options.interaction) options.interaction = {};
          options.interaction.intersect = false;
          options.interaction.mode = 'index';
          options.interaction.axis = 'x';
        }
        
        // Create the new chart instance
        chartInstances.value.main = new Chart(ctx, {
          type: activeChart.value,
          data: chartData,
          options
        });
        
        console.log('Chart created successfully with type:', activeChart.value);
        console.log('Chart data:', chartData);
      } catch (error) {
        console.error('Error creating chart:', error);
        // Ensure the chartInstances reference is reset
        chartInstances.value.main = null;
      }
    };

    // Render the category pie chart (now integrated into the main chart)

    // Render the trend line chart (now integrated into the main chart)

    // Render all charts
    const renderAllCharts = () => {
      renderChart();
    };

    // Watch for expense data changes
    watch(
      () => props.expenses,
      () => {
        filterExpenses();
        renderAllCharts();
      },
      { deep: true }
    );

    // On component mount
    onMounted(() => {
      filterExpenses();
      renderAllCharts();
      
      // Add a window resize listener to handle device rotation
      const handleResize = debounce(() => {
        // Ensure the chart re-renders correctly on device rotation
        if (chartInstances.value.main) {
          // Destroy the old chart first to avoid memory leaks
          chartInstances.value.main.destroy();
          // Re-render the chart to fit the new screen size
          renderAllCharts();
        }
      }, 250); // Add debounce to avoid frequent triggering
      
      // Add a device orientation change listener
      const handleOrientationChange = () => {
        // Defer execution to give the browser time to adjust the layout
        setTimeout(() => {
          handleResize();
        }, 300);
      };
      
      window.addEventListener('resize', handleResize);
      window.addEventListener('orientationchange', handleOrientationChange);
      
      // Store listener references so they can be removed on unmount
      windowResizeHandler.value = handleResize;
      orientationChangeHandler.value = handleOrientationChange;
      
      // Add touch-event optimizations for the canvas element
      const canvas = document.getElementById('expenseChart');
      if (canvas) {
        // Prevent the default touch behaviour, but only under certain conditions
        const handleTouchStart = (e) => {
          // Special handling only for line charts on small-screen devices
          if (window.innerWidth < 480 && activeChart.value === 'line') {
            // Prevent default behaviour to avoid scroll and zoom conflicts
            e.preventDefault();
            
            // Use setTimeout to avoid blocking the main thread
            setTimeout(() => {
              // Check whether the chart instance exists and the canvas is still usable
              if (chartInstances.value && chartInstances.value.main && document.getElementById('expenseChart')) {
                // Re-trigger the click event to ensure the chart responds normally
                const clickEvent = new MouseEvent('click', {
                  clientX: e.touches[0].clientX,
                  clientY: e.touches[0].clientY,
                  bubbles: true,
                  cancelable: true
                });
                canvas.dispatchEvent(clickEvent);
              }
            }, 0);
          }
        };
        
        const handleTouchMove = (e) => {
          if (window.innerWidth < 480) {
            e.preventDefault(); // Prevent page scrolling
          }
        };
        
        const handleTouchEnd = (e) => {
          if (window.innerWidth < 480) {
            e.preventDefault();
          }
        };
        
        canvas.addEventListener('touchstart', handleTouchStart, { passive: false });
        canvas.addEventListener('touchmove', handleTouchMove, { passive: false });
        canvas.addEventListener('touchend', handleTouchEnd, { passive: false });
        
        // Store event handler references so they can be removed on unmount
        canvasTouchHandlers.value = {
          touchstart: handleTouchStart,
          touchmove: handleTouchMove,
          touchend: handleTouchEnd
        };
      }
    });

    // Cleanup on component unmount
      onUnmounted(() => {
        // Destroy the chart instance
        if (chartInstances.value.main) {
          try {
            chartInstances.value.main.destroy();
          } catch (error) {
            console.warn('Error destroying chart:', error);
          }
          chartInstances.value.main = null;
        }
        
        // Remove the window resize listener
        if (windowResizeHandler.value) {
          window.removeEventListener('resize', windowResizeHandler.value);
          windowResizeHandler.value = null;
        }
        
        // Remove the device orientation change listener
        if (orientationChangeHandler.value) {
          window.removeEventListener('orientationchange', orientationChangeHandler.value);
          orientationChangeHandler.value = null;
        }
        
        // Remove the canvas touch event listeners
        const canvas = document.getElementById('expenseChart');
        if (canvas && canvasTouchHandlers.value) {
          if (canvasTouchHandlers.value.touchstart) {
            canvas.removeEventListener('touchstart', canvasTouchHandlers.value.touchstart, { passive: false });
          }
          if (canvasTouchHandlers.value.touchmove) {
            canvas.removeEventListener('touchmove', canvasTouchHandlers.value.touchmove, { passive: false });
          }
          if (canvasTouchHandlers.value.touchend) {
            canvas.removeEventListener('touchend', canvasTouchHandlers.value.touchend, { passive: false });
          }
          canvasTouchHandlers.value = null;
        }
      });

    return {
      activeChart,
      chartTypes,
      navbarItems,
      onChartTypeChange,
      startDate,
      endDate,
      handleStartDateChange,
      handleEndDateChange,
      renderChart,
      t
    };
  }
};
</script>

<style scoped src="../styles/components/ExpenseCharts.css"></style>