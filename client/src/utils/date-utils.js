/**
 * 格式化日期为相对日期格式（今天、昨天、X天前等）
 * @param {string} dateString - 日期字符串 (YYYY-MM-DD)
 * @param {Function} t - Vue i18n 翻译函数
 * @returns {string} 格式化后的相对日期字符串
 */
export const formatRelativeDate = (dateString, t) => {
  try {
    console.log('formatRelativeDate called with:', dateString);
    const [year, month, day] = dateString.split('-').map(Number);
    const date = new Date(year, month - 1, day);
    const today = new Date();
    
    today.setHours(0, 0, 0, 0);
    
    const diffTime = today - date;
    const daysBetween = Math.floor(diffTime / (1000 * 60 * 60 * 24));
    
    console.log('Date calculation:', { date, today, diffTime, daysBetween });
    
    const dayOfWeek = date.getDay();
    const weekdayKeys = [
      'common.sunday',
      'common.monday',
      'common.tuesday',
      'common.wednesday',
      'common.thursday',
      'common.friday',
      'common.saturday'
    ];
    const weekdayString = t(weekdayKeys[dayOfWeek]);
    
    let result;
    if (daysBetween === 0) {
      result = `${t('common.date_today')}（${weekdayString}）`;
    } else if (daysBetween === 1) {
      result = `${t('common.date_yesterday')}（${weekdayString}）`;
    } else if (daysBetween >= 2 && daysBetween <= 6) {
      result = `${t('common.date_days_ago', { days: daysBetween })}（${weekdayString}）`;
    } else {
      result = `${dateString}（${weekdayString}）`;
    }
    
    console.log('formatRelativeDate result:', result);
    return result;
  } catch (e) {
    console.error('formatRelativeDate error:', e);
    return dateString;
  }
};

/**
 * 格式化日期为 YYYY-MM-DD 格式
 * @param {string|Date} date - 日期字符串或Date对象
 * @returns {string} 格式化后的日期字符串
 */
export const formatDate = (date) => {
  if (!date) return '';
  const targetDate = typeof date === 'string' ? new Date(date) : date;
  if (isNaN(targetDate.getTime())) return '';

  const year = targetDate.getFullYear();
  const month = String(targetDate.getMonth() + 1).padStart(2, '0');
  const day = String(targetDate.getDate()).padStart(2, '0');

  return `${year}-${month}-${day}`;
};

/**
 * 检查日期是否已过期
 * @param {string|Date} date - 日期字符串或Date对象
 * @returns {boolean} 是否过期
 */
export const isExpired = (date) => {
  if (!date) return false;
  const targetDate = typeof date === 'string' ? new Date(date) : date;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return targetDate < today;
};

/**
 * 检查日期是否即将过期（7天内）
 * @param {string|Date} date - 日期字符串或Date对象
 * @returns {boolean} 是否即将过期
 */
export const isSoonExpired = (date) => {
  if (!date) return false;
  const targetDate = typeof date === 'string' ? new Date(date) : date;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const oneWeekLater = new Date(today);
  oneWeekLater.setDate(today.getDate() + 7);
  return targetDate >= today && targetDate <= oneWeekLater;
};

/**
 * 获取当前日期的字符串表示（YYYY-MM-DD）
 * @returns {string} 当前日期
 */
export const getCurrentDate = () => {
  return formatDate(new Date());
};

/**
 * 计算两个日期之间的天数差
 * @param {string|Date} startDate - 开始日期
 * @param {string|Date} endDate - 结束日期
 * @returns {number} 天数差
 */
export const getDaysDifference = (startDate, endDate) => {
  if (!startDate || !endDate) return 0;
  const start = typeof startDate === 'string' ? new Date(startDate) : startDate;
  const end = typeof endDate === 'string' ? new Date(endDate) : endDate;
  const diffTime = end - start;
  return Math.ceil(diffTime / (1000 * 60 * 60 * 24));
};
