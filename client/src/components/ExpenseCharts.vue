<template>
  <div class="charts-container glass-card">
    <div class="chart-controls glass-panel">
      <CustomSelect 
        v-model="activeChart" 
        :options="chartTypes"
        @change="renderChart"
        :include-empty-option="false"
        class="glass-select"
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
    <div class="chart-wrapper glass-chart-container">
      <canvas id="expenseChart"></canvas>
    </div>
  </div>
</template>

<script>
import { ref, onMounted, onUnmounted, watch } from 'vue';
import Chart from 'chart.js/auto';
import { useI18n } from 'vue-i18n';

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
// 事件处理函数引用，用于组件卸载时移除监听器
const windowResizeHandler = ref(null);
const orientationChangeHandler = ref(null);
const canvasTouchHandlers = ref(null);
    
    const { t } = useI18n();

// 防抖函数实现
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

    // 过滤日期范围内的支出数据
    const filteredExpenses = ref([]);

    // 处理开始日期变化
    const handleStartDateChange = () => {
      console.log('Start date changed to:', startDate.value);
      // 确保开始日期不晚于结束日期
      if (startDate.value && endDate.value && startDate.value > endDate.value) {
        startDate.value = endDate.value;
      }
      filterExpenses();
      renderAllCharts();
    };

    // 处理结束日期变化
    const handleEndDateChange = () => {
      console.log('End date changed to:', endDate.value);
      // 确保结束日期不早于开始日期
      if (startDate.value && endDate.value && endDate.value < startDate.value) {
        endDate.value = startDate.value;
      }
      filterExpenses();
      renderAllCharts();
    };

    // 过滤支出数据
    const filterExpenses = () => {
      // 确保日期有效
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
        // 确保expense.date有效
        if (!expense.date) {
          console.warn('Expense with no date:', expense);
          return false;
        }
        
        const expenseDate = parseDate(expense.date);
        // 检查日期解析是否成功
        if (!expenseDate) {
          console.warn('Invalid expense date format:', expense.date);
          return false;
        }
        
        // 正确处理日期边界，包含开始和结束日期
        const isAfterStart = expenseDate >= startDateObj;
        const isBeforeEnd = expenseDate <= endDateObj;
        
        console.log(`Expense date ${formatDate(expenseDate)}: isAfterStart=${isAfterStart}, isBeforeEnd=${isBeforeEnd}`);
        
        return isAfterStart && isBeforeEnd;
      });
      
      console.log('Filtered expenses:', filteredExpenses.value);
      console.log('Filtered expenses count:', filteredExpenses.value.length);
    };

    // 准备图表数据
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

    // 准备柱状图数据
    const prepareBarData = () => {
      // 按类别分组
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

    // 准备饼图数据
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

    // 准备折线图数据
    const prepareLineData = () => {
      console.log('Preparing line chart data with expenses count:', filteredExpenses.value.length);
        
        // 按时间排序
        const sortedExpenses = [...filteredExpenses.value].sort((a, b) => {
          // 使用date字段替代time字段，与后端数据保持一致
          const dateA = parseDate(a.date || a.time);
          const dateB = parseDate(b.date || b.time);
          return dateA - dateB;
        });
        
        console.log('First expense time:', sortedExpenses.length > 0 ? formatDate(parseDate(sortedExpenses[0].date || sortedExpenses[0].time)) : 'No data');
        console.log('Last expense time:', sortedExpenses.length > 0 ? formatDate(parseDate(sortedExpenses[sortedExpenses.length - 1].date || sortedExpenses[sortedExpenses.length - 1].time)) : 'No data');

      // 按日期分组
      const dateData = {};
      sortedExpenses.forEach(expense => {
        // 再次验证日期有效性，使用date字段替代time字段
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

    // 准备雷达图数据
    const prepareRadarData = () => {
      // 获取所有唯一类别
      const categories = [...new Set(filteredExpenses.value.map(expense => expense.type))];
      // 获取所有唯一周几
      const weekdays = [
        t('common.sunday'), 
        t('common.monday'), 
        t('common.tuesday'), 
        t('common.wednesday'), 
        t('common.thursday'), 
        t('common.friday'), 
        t('common.saturday')
      ];

      // 按周几和类别分组
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

    // 渲染主图表
    const renderChart = () => {
      const ctx = document.getElementById('expenseChart');
      
      // 安全地销毁旧图表，防止Canvas重用错误
      try {
        // 检查是否存在旧图表实例并销毁
        if (chartInstances.value && chartInstances.value.main) {
          chartInstances.value.main.destroy();
          // 清空引用，确保垃圾回收
          chartInstances.value.main = null;
        }
        
        // 额外清理Canvas上下文，避免在小屏幕设备上的渲染残留
        if (ctx && ctx.getContext) {
          const context = ctx.getContext('2d');
          if (context) {
            // 清除Canvas内容
            context.clearRect(0, 0, ctx.width, ctx.height);
            // 重置Canvas的宽度和高度，强制清除所有状态
            const width = ctx.width;
            const height = ctx.height;
            ctx.width = width;
            ctx.height = height;
          }
        }
      } catch (error) {
        console.warn('Error destroying chart:', error);
        // 即使销毁失败，也要继续尝试创建新图表
      }

      const chartData = prepareChartData(activeChart.value);
      
      // 检查是否有数据点
      let options = {};
      
      // 通用配置，特别是针对移动设备的优化
      const commonOptions = {
        responsive: true,
        maintainAspectRatio: false,
        // 针对移动端触摸事件的优化
        interaction: {
          intersect: false,
          mode: 'index',
          // 禁用默认的触摸手势处理，避免与原生触摸事件冲突
          gestures: {
            // 对于小屏幕设备，禁用平移和缩放功能
            pan: window.innerWidth < 768 ? false : true,
            zoom: window.innerWidth < 768 ? false : true
          }
        },
        // 优化性能的配置
        animation: {
          duration: window.innerWidth < 480 ? 300 : 500,
          easing: 'easeOutQuart'
        },
        // 针对Canvas的事件处理优化
        onHover: (event, elements) => {
          // 仅在有元素被悬停时改变鼠标样式
          event.native.target.style.cursor = elements.length > 0 ? 'pointer' : 'default';
        },
        // 优化渲染性能
        elements: {
          point: {
            hoverRadius: window.innerWidth < 480 ? 6 : 8,
            hitRadius: window.innerWidth < 480 ? 10 : 12,
            radius: window.innerWidth < 480 ? 3 : 4
          }
        },
        // 液态玻璃效果 - 通用配置
        plugins: {
          tooltip: {
            backgroundColor: 'rgba(255, 255, 255, 0.9)',
            backdropFilter: 'blur(10px)',
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
        // 深色模式适配
        isDarkMode: window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches
      };

      // 根据图表类型设置不同的选项
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

      // 确保ctx存在
      if (!ctx) {
        console.error('Canvas element not found');
        return;
      }
      
      // 安全地创建新图表
      try {
        // 确保ctx存在且可用
        if (!ctx || !ctx.getContext) {
          console.error('Canvas context not available');
          return;
        }
        
        // 确保chartData和options有效
        if (!chartData || !options) {
          console.error('Invalid chart data or options');
          return;
        }
        
        // 检测深色模式
        const isDarkMode = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
        
        // 根据深色模式调整颜色
        if (isDarkMode) {
          if (options.plugins && options.plugins.tooltip) {
            options.plugins.tooltip.backgroundColor = 'rgba(30, 30, 30, 0.9)';
            options.plugins.tooltip.titleColor = '#fff';
            options.plugins.tooltip.bodyColor = '#ccc';
            options.plugins.tooltip.borderColor = 'rgba(255, 255, 255, 0.1)';
          }
          if (options.plugins && options.plugins.legend && options.plugins.legend.labels) {
            options.plugins.legend.labels.color = '#ccc';
          }
          if (options.plugins && options.plugins.title) {
            options.plugins.title.color = '#fff';
          }
          if (options.scales) {
            if (options.scales.y) {
              if (options.scales.y.title) options.scales.y.title.color = '#ccc';
              if (options.scales.y.ticks) options.scales.y.ticks.color = '#999';
            }
            if (options.scales.x) {
              if (options.scales.x.title) options.scales.x.title.color = '#ccc';
              if (options.scales.x.ticks) options.scales.x.ticks.color = '#999';
            }
            if (options.scales.r) {
              if (options.scales.r.ticks) {
                options.scales.r.ticks.color = '#999';
                options.scales.r.ticks.backdropColor = 'rgba(30, 30, 30, 0.8)';
              }
              if (options.scales.r.pointLabels) {
                options.scales.r.pointLabels.color = '#ccc';
              }
              if (options.scales.r.grid) {
                options.scales.r.grid.color = 'rgba(255, 255, 255, 0.05)';
              }
              if (options.scales.r.angleLines) {
                options.scales.r.angleLines.color = 'rgba(255, 255, 255, 0.05)';
              }
            }
          }
        }
        
        // 在小屏幕设备上，使用较小的图表配置以提高性能
        if (window.innerWidth < 480) {
          // 确保动画持续时间较短
          if (!options.animation) options.animation = {};
          options.animation.duration = 200;
          
          // 简化交互以避免性能问题
          if (!options.interaction) options.interaction = {};
          options.interaction.intersect = false;
          options.interaction.mode = 'index';
          options.interaction.axis = 'x';
        }
        
        // 创建新图表实例
        chartInstances.value.main = new Chart(ctx, {
          type: activeChart.value,
          data: chartData,
          options
        });
        
        console.log('Chart created successfully with type:', activeChart.value);
        console.log('Chart data:', chartData);
      } catch (error) {
        console.error('Error creating chart:', error);
        // 确保chartInstances引用被重置
        chartInstances.value.main = null;
      }
    };

    // 渲染类别饼图（现在已整合到主图表中）

    // 渲染趋势折线图（现在已整合到主图表中）

    // 渲染所有图表
    const renderAllCharts = () => {
      renderChart();
    };

    // 监听支出数据变化
    watch(
      () => props.expenses,
      () => {
        filterExpenses();
        renderAllCharts();
      },
      { deep: true }
    );

    // 组件挂载时
    onMounted(() => {
      filterExpenses();
      renderAllCharts();
      
      // 添加窗口大小变化监听，处理设备旋转
      const handleResize = debounce(() => {
        // 确保在设备旋转时正确重新渲染图表
        if (chartInstances.value.main) {
          // 先销毁旧图表，避免内存泄漏
          chartInstances.value.main.destroy();
          // 重新渲染图表以适应新的屏幕大小
          renderAllCharts();
        }
      }, 250); // 添加防抖以避免频繁触发
      
      // 添加设备方向变化监听
      const handleOrientationChange = () => {
        // 延迟执行，让浏览器有时间调整布局
        setTimeout(() => {
          handleResize();
        }, 300);
      };
      
      window.addEventListener('resize', handleResize);
      window.addEventListener('orientationchange', handleOrientationChange);
      
      // 存储监听器引用以便在组件卸载时移除
      windowResizeHandler.value = handleResize;
      orientationChangeHandler.value = handleOrientationChange;
      
      // 添加canvas元素的触摸事件优化
      const canvas = document.getElementById('expenseChart');
      if (canvas) {
        // 防止触摸事件的默认行为，但只在特定条件下
        const handleTouchStart = (e) => {
          // 只在折线图且小屏幕设备上进行特殊处理
          if (window.innerWidth < 480 && activeChart.value === 'line') {
            // 阻止默认行为以避免滚动和缩放冲突
            e.preventDefault();
            
            // 使用setTimeout避免阻塞主线程
            setTimeout(() => {
              // 检查图表实例是否存在且canvas元素仍然可用
              if (chartInstances.value && chartInstances.value.main && document.getElementById('expenseChart')) {
                // 重新触发点击事件以确保图表正常响应
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
            e.preventDefault(); // 防止页面滚动
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
        
        // 存储事件处理函数引用以便在组件卸载时移除
        canvasTouchHandlers.value = {
          touchstart: handleTouchStart,
          touchmove: handleTouchMove,
          touchend: handleTouchEnd
        };
      }
    });

    // 组件卸载时的清理
      onUnmounted(() => {
        // 销毁图表实例
        if (chartInstances.value.main) {
          try {
            chartInstances.value.main.destroy();
          } catch (error) {
            console.warn('Error destroying chart:', error);
          }
          chartInstances.value.main = null;
        }
        
        // 移除窗口大小变化监听
        if (windowResizeHandler.value) {
          window.removeEventListener('resize', windowResizeHandler.value);
          windowResizeHandler.value = null;
        }
        
        // 移除设备方向变化监听
        if (orientationChangeHandler.value) {
          window.removeEventListener('orientationchange', orientationChangeHandler.value);
          orientationChangeHandler.value = null;
        }
        
        // 移除canvas触摸事件监听
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

<style scoped>
.charts-container {
  padding: 24px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  box-shadow: 
    0 8px 32px rgba(0, 0, 0, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 0.2),
    inset 0 -1px 0 rgba(0, 0, 0, 0.05);
  width: 100%;
  box-sizing: border-box;
  border: 1px solid rgba(255, 255, 255, 0.18);
  position: relative;
  overflow: hidden;
}

.glass-panel {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  flex-wrap: wrap;
  gap: 16px;
  padding: 16px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.15);
  box-shadow: 
    0 4px 16px rgba(0, 0, 0, 0.05),
    inset 0 1px 0 rgba(255, 255, 255, 0.15);
  position: relative;
  z-index: 10;
}

.glass-input-group {
  display: flex;
  align-items: center;
  gap: 12px;
}

.glass-input {
  padding: 10px 16px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 12px;
  font-size: 14px;
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  min-width: 150px;
  color: #333;
  box-shadow: 
    0 2px 8px rgba(0, 0, 0, 0.05),
    inset 0 1px 0 rgba(255, 255, 255, 0.1);
}

.glass-input:focus {
  outline: none;
  border-color: rgba(64, 158, 255, 0.5);
  background: rgba(255, 255, 255, 0.1);
  box-shadow: 
    0 4px 12px rgba(64, 158, 255, 0.15),
    inset 0 1px 0 rgba(255, 255, 255, 0.2);
  transform: translateY(-1px);
}

.glass-input:hover {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.3);
}

.range-separator {
  color: #666;
  font-size: 14px;
  font-weight: 500;
  opacity: 0.8;
}

.glass-chart-container {
  height: 400px;
  margin-bottom: 30px;
  position: relative;
  width: 100%;
  padding: 20px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 
    0 4px 20px rgba(0, 0, 0, 0.05),
    inset 0 1px 0 rgba(255, 255, 255, 0.1);
  transition: all 0.3s ease;
  z-index: 1;
}

.glass-chart-container:hover {
  box-shadow: 
    0 8px 32px rgba(0, 0, 0, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 0.15);
  transform: translateY(-2px);
}

#expenseChart {
  max-width: 100% !important;
}

/* 确保下拉菜单显示在图表容器之上 */
:deep(.glass-select) {
  position: relative;
  z-index: 100;
}

:deep(.glass-select .select-dropdown) {
  z-index: 200 !important;
}

/* 平板设备响应式设计 */
@media (max-width: 768px) {
  .charts-container {
    padding: 20px;
    border-radius: 16px;
  }
  
  .glass-panel {
    flex-direction: column;
    align-items: stretch;
    padding: 14px;
  }
  
  .glass-chart-container {
    height: 350px;
    padding: 16px;
  }
  
  .glass-input-group {
    width: 100%;
  }
  
  .glass-input {
    width: 100%;
  }
}

/* 手机设备响应式设计 */
@media (max-width: 480px) {
  .charts-container {
    padding: 16px;
    border-radius: 14px;
  }
  
  .glass-chart-container {
    height: 300px;
    padding: 12px;
  }
  
  .glass-panel {
    margin-bottom: 16px;
    gap: 12px;
    padding: 12px;
  }
  
  .glass-input-group {
    gap: 10px;
  }
  
  .glass-input {
    min-width: auto;
    padding: 8px 12px;
    font-size: 13px;
  }
}

/* 超小屏幕设备响应式设计 */
@media (max-width: 360px) {
  .glass-chart-container {
    height: 250px;
    padding: 10px;
  }
  
  .glass-input-group {
    width: 50%;
    flex-direction: column;
    gap: 8px;
  }
  
  .glass-input {
    width: 100%;
    min-width: auto;
    font-size: 12px;
    padding: 6px 10px;
  }
}

@media (prefers-color-scheme: dark) {
  .charts-container {
    background: rgba(20, 20, 20, 0.6);
    box-shadow: 
      0 8px 32px rgba(0, 0, 0, 0.4),
      inset 0 1px 0 rgba(255, 255, 255, 0.1),
      inset 0 -1px 0 rgba(0, 0, 0, 0.2);
    border-color: rgba(255, 255, 255, 0.08);
    color: #fff;
  }
  
  .glass-panel {
    background: rgba(30, 30, 30, 0.5);
    border-color: rgba(255, 255, 255, 0.08);
    box-shadow: 
      0 4px 16px rgba(0, 0, 0, 0.3),
      inset 0 1px 0 rgba(255, 255, 255, 0.08);
  }
  
  .glass-input {
    background: rgba(40, 40, 40, 0.6);
    border-color: rgba(255, 255, 255, 0.1);
    color: #fff;
    box-shadow: 
      0 2px 8px rgba(0, 0, 0, 0.3),
      inset 0 1px 0 rgba(255, 255, 255, 0.05);
  }
  
  .glass-input:focus {
    border-color: rgba(64, 158, 255, 0.6);
    background: rgba(50, 50, 50, 0.7);
    box-shadow: 
      0 4px 12px rgba(64, 158, 255, 0.2),
      inset 0 1px 0 rgba(255, 255, 255, 0.1);
  }
  
  .glass-input:hover {
    background: rgba(45, 45, 45, 0.6);
    border-color: rgba(255, 255, 255, 0.15);
  }
  
  .glass-chart-container {
    background: rgba(30, 30, 30, 0.4);
    border-color: rgba(255, 255, 255, 0.08);
    box-shadow: 
      0 4px 20px rgba(0, 0, 0, 0.3),
      inset 0 1px 0 rgba(255, 255, 255, 0.05);
  }
  
  .glass-chart-container:hover {
    box-shadow: 
      0 8px 32px rgba(0, 0, 0, 0.4),
      inset 0 1px 0 rgba(255, 255, 255, 0.08);
  }
  
  .range-separator {
    color: #aaa;
  }
}
</style>