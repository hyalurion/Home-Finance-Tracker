<template>
  <div class="charts-container">
    <div class="chart-controls">
      <CustomSelect 
        v-model="activeChart" 
        :options="chartTypes"
        @change="renderChart"
        :include-empty-option="false"
      />
      <div class="date-range-picker">
        <input
          type="date"
          v-model="startDate"
          @change="handleStartDateChange"
          :max="endDate"
          class="date-input"
        />
        <span class="range-separator">{{ t('common.to') }}</span>
        <input
          type="date"
          v-model="endDate"
          @change="handleEndDateChange"
          :min="startDate"
          class="date-input"
        />
      </div>
    </div>
    <div class="chart-wrapper">
      <canvas id="expenseChart"></canvas>
    </div>
  </div>
</template>

<script>
import { ref, onMounted, onUnmounted, watch } from 'vue';
import Chart from 'chart.js/auto';
import dayjs from 'dayjs';
import { useI18n } from 'vue-i18n';

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
    const startDate = ref(dayjs().startOf('month').format('YYYY-MM-DD'));
    const endDate = ref(dayjs().endOf('month').format('YYYY-MM-DD'));
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
      { value: 'pie', label: t('chart.pie') },
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
      
      const startDateObj = dayjs(startDate.value);
      const endDateObj = dayjs(endDate.value);
      
      console.log('Date range filter:', startDateObj.format('YYYY-MM-DD'), 'to', endDateObj.format('YYYY-MM-DD'));

      filteredExpenses.value = props.expenses.filter(expense => {
        // 确保expense.date有效
        if (!expense.date) {
          console.warn('Expense with no date:', expense);
          return false;
        }
        
        const expenseDate = dayjs(expense.date);
        // 检查日期解析是否成功
        if (!expenseDate.isValid()) {
          console.warn('Invalid expense date format:', expense.date);
          return false;
        }
        
        // 正确处理日期边界，包含开始和结束日期
        const isAfterStart = expenseDate.isAfter(startDateObj.subtract(1, 'day'));
        const isBeforeEnd = expenseDate.isBefore(endDateObj.add(1, 'day'));
        
        console.log(`Expense date ${expenseDate.format('YYYY-MM-DD')}: isAfterStart=${isAfterStart}, isBeforeEnd=${isBeforeEnd}`);
        
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
        case 'pie':
          return preparePieData();
        case 'line':
          return prepareLineData();
        case 'doughnut':
          return preparePieData(); // 环形图使用与饼图相同的数据
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
            'rgba(255, 99, 132, 0.7)',
            'rgba(54, 162, 235, 0.7)',
            'rgba(255, 206, 86, 0.7)',
            'rgba(75, 192, 192, 0.7)',
            'rgba(153, 102, 255, 0.7)',
            'rgba(255, 159, 64, 0.7)',
            'rgba(199, 199, 199, 0.7)'
          ],
          borderColor: [
            'rgba(255, 99, 132, 1)',
            'rgba(54, 162, 235, 1)',
            'rgba(255, 206, 86, 1)',
            'rgba(75, 192, 192, 1)',
            'rgba(153, 102, 255, 1)',
            'rgba(255, 159, 64, 1)',
            'rgba(199, 199, 199, 1)'
          ],
          borderWidth: 1
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
            'rgba(255, 99, 132, 0.7)',
            'rgba(54, 162, 235, 0.7)',
            'rgba(255, 206, 86, 0.7)',
            'rgba(75, 192, 192, 0.7)',
            'rgba(153, 102, 255, 0.7)',
            'rgba(255, 159, 64, 0.7)',
            'rgba(199, 199, 199, 0.7)'
          ],
          borderColor: [
            'rgba(255, 99, 132, 1)',
            'rgba(54, 162, 235, 1)',
            'rgba(255, 206, 86, 1)',
            'rgba(75, 192, 192, 1)',
            'rgba(153, 102, 255, 1)',
            'rgba(255, 159, 64, 1)',
            'rgba(199, 199, 199, 1)'
          ],
          borderWidth: 1
        }]
      };
    };

    // 准备折线图数据
    const prepareLineData = () => {
      console.log('Preparing line chart data with expenses count:', filteredExpenses.value.length);
        
        // 按时间排序
        const sortedExpenses = [...filteredExpenses.value].sort((a, b) => {
          // 使用date字段替代time字段，与后端数据保持一致
          const dateA = dayjs(a.date || a.time);
          const dateB = dayjs(b.date || b.time);
          return dateA.diff(dateB);
        });
        
        console.log('First expense time:', sortedExpenses.length > 0 ? dayjs(sortedExpenses[0].date || sortedExpenses[0].time).format('YYYY-MM-DD') : 'No data');
        console.log('Last expense time:', sortedExpenses.length > 0 ? dayjs(sortedExpenses[sortedExpenses.length - 1].date || sortedExpenses[sortedExpenses.length - 1].time).format('YYYY-MM-DD') : 'No data');

      // 按日期分组
      const dateData = {};
      sortedExpenses.forEach(expense => {
        // 再次验证日期有效性，使用date字段替代time字段
        const expenseDate = expense.date || expense.time;
        if (!expenseDate || !dayjs(expenseDate).isValid()) {
          console.warn('Skipping expense with invalid date:', expense);
          return;
        }
        
        const dateStr = dayjs(expenseDate).format('YYYY-MM-DD');
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
          fill: false,
          backgroundColor: 'rgba(54, 162, 235, 0.7)',
          borderColor: 'rgba(54, 162, 235, 1)',
          borderWidth: 2,
          tension: 0.3,
          pointBackgroundColor: 'rgba(54, 162, 235, 1)',
          pointRadius: 4,
          pointHoverRadius: 6
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
            const weekday = dayjs(expense.date).day();
            values[weekday] += parseFloat(expense.amount);
          }
        });
        return {
          label: category,
          data: values,
          backgroundColor: `rgba(${Math.floor(Math.random() * 255)}, ${Math.floor(Math.random() * 255)}, ${Math.floor(Math.random() * 255)}, 0.2)`,
          borderColor: `rgba(${Math.floor(Math.random() * 255)}, ${Math.floor(Math.random() * 255)}, ${Math.floor(Math.random() * 255)}, 1)`,
          pointBackgroundColor: `rgba(${Math.floor(Math.random() * 255)}, ${Math.floor(Math.random() * 255)}, ${Math.floor(Math.random() * 255)}, 1)`,
          borderWidth: 1
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
          duration: window.innerWidth < 480 ? 300 : 500, // 小屏幕设备上加快动画速度
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
            hoverRadius: window.innerWidth < 480 ? 6 : 8, // 小屏幕上使用较小的悬停半径
            hitRadius: window.innerWidth < 480 ? 10 : 12, // 增加点击区域，提高移动端可点击性
            radius: window.innerWidth < 480 ? 3 : 4 // 小屏幕上使用较小的点
          }
        }
      };

      // 根据图表类型设置不同的选项
      switch (activeChart.value) {
        case 'bar':
          options = {
            ...commonOptions,
            plugins: {
              legend: {
                position: 'top',
                labels: {
                  font: {
                    size: window.innerWidth < 480 ? 10 : 12
                  },
                  padding: 10
                }
              },
              title: {
                display: true,
                text: t('chart.categoryAnalysis'),
                font: {
                  size: window.innerWidth < 480 ? 14 : 16
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
                  }
                },
                ticks: {
                  font: {
                    size: window.innerWidth < 480 ? 9 : 11
                  }
                }
              },
              x: {
                title: {
                  display: true,
                  text: t('expense.type'),
                  font: {
                    size: window.innerWidth < 480 ? 10 : 12
                  }
                },
                ticks: {
                  font: {
                    size: window.innerWidth < 480 ? 9 : 11
                  },
                  maxRotation: 45,
                  minRotation: 45
                }
              }
            }
          };
          break;
        case 'pie':
          options = {
            ...commonOptions,
            plugins: {
              legend: {
                position: window.innerWidth < 480 ? 'bottom' : 'right',
                labels: {
                  font: {
                    size: window.innerWidth < 480 ? 10 : 12
                  },
                  padding: 10,
                  boxWidth: window.innerWidth < 480 ? 10 : 12
                }
              },
              title: {
                display: true,
                text: t('chart.categoryPercentage'),
                font: {
                  size: window.innerWidth < 480 ? 14 : 16
                }
              }
            }
          };
          break;
        case 'doughnut':
          options = {
            ...commonOptions,
            plugins: {
              legend: {
                position: window.innerWidth < 480 ? 'bottom' : 'right',
                labels: {
                  font: {
                    size: window.innerWidth < 480 ? 10 : 12
                  },
                  padding: 10,
                  boxWidth: window.innerWidth < 480 ? 10 : 12
                }
              },
              title: {
                display: true,
                text: t('chart.categoryPercentage'),
                font: {
                  size: window.innerWidth < 480 ? 14 : 16
                }
              }
            },
            cutout: '50%' // 为环形图设置中心孔大小
          };
          break;
        case 'line':
          options = {
            ...commonOptions,
            // 折线图特定的触摸优化
            interaction: {
              ...commonOptions.interaction,
              // 针对折线图的特殊处理
              intersect: false,
              mode: 'index',
              // 针对小屏幕优化点击区域
              axis: 'x',
              // 小屏幕上增加触摸敏感度
              touch: {
                // 增加触摸事件的响应范围
                radius: window.innerWidth < 480 ? 20 : 10,
                // 启用触摸手势
                enabled: true,
                // 增加触摸滚动的敏感度
                axis: 'x',
                // 禁用触摸缩放以提高性能
                zoom: false
              }
            },
            // 优化动画性能
            animation: {
              ...commonOptions.animation,
              // 小屏幕上禁用一些非必要的动画
              duration: window.innerWidth < 480 ? 200 : 500,
              // 减少动画复杂度
              easing: window.innerWidth < 480 ? 'linear' : 'easeOutQuart'
            },
            // 针对小屏幕优化性能
            elements: {
              point: {
                // 确保point配置是完整的，避免未定义属性
                radius: window.innerWidth < 480 ? 2 : 4,
                // 小屏幕上增大点击区域
                hitRadius: window.innerWidth < 480 ? 15 : 12,
                // 确保pointHoverRadius已定义
                hoverRadius: window.innerWidth < 480 ? 8 : 6,
                // 禁用点悬停动画以提高性能
                hoverAnimationDuration: window.innerWidth < 480 ? 0 : 200,
                // 确保所有必要的point属性都有默认值
                backgroundColor: 'rgba(54, 162, 235, 1)',
                borderColor: 'rgba(54, 162, 235, 1)',
                borderWidth: 1
              },
              line: {
                // 简化线段渲染
                tension: window.innerWidth < 480 ? 0.1 : 0.4,
                // 小屏幕上使用更粗的线条提高可读性
                borderWidth: window.innerWidth < 480 ? 2 : 3
              }
            },
            plugins: {
              legend: {
                position: 'top',
                labels: {
                  font: { size: window.innerWidth < 480 ? 10 : 12 },
                  padding: 10
                }
              },
              title: {
                display: true,
                text: t('chart.trendAnalysis'),
                font: { size: window.innerWidth < 480 ? 14 : 16 }
              },
              tooltip: {
                // 优化提示框性能
                animationDuration: window.innerWidth < 480 ? 100 : 300,
                // 确保提示框在移动设备上正确显示
                caretSize: window.innerWidth < 480 ? 6 : 8,
                backgroundColor: 'rgba(0, 0, 0, 0.8)',
                titleFont: {
                  size: window.innerWidth < 480 ? 12 : 14
                },
                bodyFont: {
                  size: window.innerWidth < 480 ? 11 : 13
                },
                // 提示框最大宽度，避免在小屏幕上溢出
                maxWidth: window.innerWidth < 480 ? 200 : 300,
                // 禁用提示框点击事件，避免与图表点击事件冲突
                enabled: true,
                // 使用外部提示框以提高性能（可选）
                usePointStyle: true
              }
            },
            scales: {
              y: {
                beginAtZero: true,
                title: {
                  display: true,
                  text: t('expense.amount') + ' (' + t('common.currency') + ')',
                  font: { size: window.innerWidth < 480 ? 10 : 12 }
                },
                ticks: {
                  font: { size: window.innerWidth < 480 ? 9 : 11 },
                  // 在小屏幕上显示更少的刻度线
                  maxTicksLimit: window.innerWidth < 480 ? 4 : 6
                },
                // 简化网格线以提高性能
                grid: {
                  color: 'rgba(0, 0, 0, 0.05)'
                }
              },
              x: {
                type: 'category', // 明确指定为类别轴
                title: {
                  display: true,
                  text: t('common.date'),
                  font: { size: window.innerWidth < 480 ? 10 : 12 }
                },
                ticks: {
                  maxTicksLimit: window.innerWidth < 480 ? 5 : 10, // 在小屏幕上显示更少的标签
                  callback: function(value, index, values) {
                    // 在小屏幕上简化日期显示格式
                    if (window.innerWidth < 480) {
                      // 确保value是字符串类型再调用substring
                      const valueStr = String(value);
                      // 只显示月和日，去掉年份
                      return valueStr.length >= 5 ? valueStr.substring(5) : valueStr;
                    }
                    return value;
                  },
                  autoSkip: true,
                  maxRotation: 45,
                  minRotation: 45,
                  font: { size: window.innerWidth < 480 ? 8 : 11 }
                },
                // 简化网格线以提高性能
                grid: {
                  color: 'rgba(0, 0, 0, 0.05)'
                }
              }
            },
            // 禁用一些可能导致性能问题的交互功能
            responsive: true,
            maintainAspectRatio: false,
            // 增加图表容器的padding以在小屏幕上有更好的触摸体验
            layout: {
              padding: window.innerWidth < 480 ? 15 : 25
            }
          };
          break;
        case 'radar':
          options = {
            ...commonOptions,
            plugins: {
              legend: {
                position: window.innerWidth < 480 ? 'bottom' : 'top',
                labels: {
                  font: {
                    size: window.innerWidth < 480 ? 10 : 12
                  },
                  padding: 8,
                  boxWidth: window.innerWidth < 480 ? 10 : 12
                }
              },
              title: {
                display: true,
                text: t('chart.weekdayAnalysis'),
                font: {
                  size: window.innerWidth < 480 ? 14 : 16
                }
              }
            },
            scales: {
              r: {
                angleLines: {
                  display: true
                },
                suggestedMin: 0,
                ticks: {
                  font: {
                    size: window.innerWidth < 480 ? 9 : 11
                  }
                },
                pointLabels: {
                  font: {
                    size: window.innerWidth < 480 ? 10 : 12
                  }
                }
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
  padding: 20px;
  border-radius: 10px;
  background: transparent;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  width: 100%;
  box-sizing: border-box;
}

.chart-controls {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
    flex-wrap: wrap;
    gap: 15px;
  }
  
  .date-range-picker {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  
  .date-input {
    padding: 8px 12px;
    border: 1px solid #dcdfe6;
    border-radius: 4px;
    font-size: 14px;
    background-color: #fff;
    transition: border-color 0.3s;
    min-width: 120px;
  }
  
  .date-input:focus {
    outline: none;
    border-color: #409eff;
  }
  
  .range-separator {
    color: #909399;
    font-size: 14px;
  }

  .chart-wrapper {
    height: 400px;
    margin-bottom: 30px;
    position: relative;
    width: 100%;
  }

#expenseChart {
  max-width: 100% !important;
}

/* 平板设备响应式设计 */
  @media (max-width: 768px) {
    .charts-container {
      padding: 15px;
    }
    
    .chart-controls {
      flex-direction: column;
      align-items: stretch;
    }
    
    .chart-wrapper {
      height: 350px;
    }
    
    .date-range-picker {
      width: 100%;
    }
    
    .date-input {
      width: 100%;
    }
  }

/* 手机设备响应式设计 */
@media (max-width: 480px) {
  .charts-container {
    padding: 10px;
  }
  
  .chart-wrapper {
    height: 300px;
  }
  
  .chart-controls {
    margin-bottom: 15px;
    gap: 10px;
  }
  
  .date-range-picker {
    width: 50%;
    flex-direction: column;
    gap: 8px;
  }
  
  .date-input {
    width: 70%;
    min-width: auto;
  }
  
  .range-separator {
    display: none; /* 在垂直布局中隐藏分隔符 */
  }
}

/* 超小屏幕设备响应式设计 */
@media (max-width: 360px) {
  .chart-wrapper {
    height: 250px;
  }
  
  .date-range-picker {
    width: 50%;
    flex-direction: column;
    gap: 8px;
  }
  
  .date-input {
    width: 100%;
    min-width: auto;
    font-size: 12px; /* 更小的字体以适应超小屏幕 */
    padding: 6px 8px; /* 更小的内边距 */
  }
}

@media (prefers-color-scheme: dark) {
  .charts-container {
    background: rgba(30, 30, 30, 0.7);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
    color: #fff !important;
  }
}
</style>