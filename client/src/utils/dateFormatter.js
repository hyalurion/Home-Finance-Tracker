const isValidDate = (date) => {
  return date instanceof Date && !isNaN(date.getTime());
};

const padZero = (num) => {
  return num.toString().padStart(2, '0');
};

const monthNames = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December'
];

const parseDate = (date) => {
  if (date instanceof Date) return date;
  return new Date(date);
};

export const formatDateByLocale = (date, locale) => {
  const d = parseDate(date);
  if (!isValidDate(d)) return '';

  try {
    switch (locale) {
    case 'en-US':
      return `${monthNames[d.getMonth()]} ${d.getDate()}, ${d.getFullYear()}`;
    case 'zh-CN':
    case 'zh-TW':
      return `${d.getFullYear()}年${padZero(d.getMonth() + 1)}月${padZero(d.getDate())}日`;
    default:
      return `${d.getFullYear()}-${padZero(d.getMonth() + 1)}-${padZero(d.getDate())}`;
    }
  } catch (error) {
    console.error('日期格式化错误:', error);
    return `${d.getFullYear()}-${padZero(d.getMonth() + 1)}-${padZero(d.getDate())}`;
  }
};

export const formatMonthLabelByLocale = (yearMonth, locale) => {
  const d = parseDate(yearMonth);
  if (!isValidDate(d)) return '';

  try {
    switch (locale) {
    case 'zh-CN':
    case 'zh-TW':
      return `${d.getFullYear()}年${padZero(d.getMonth() + 1)}月`;
    case 'en-US':
      return `${monthNames[d.getMonth()]} ${d.getFullYear()}`;
    default:
      return `${d.getFullYear()}-${padZero(d.getMonth() + 1)}`;
    }
  } catch (error) {
    console.error('月份格式化错误:', error);
    return `${d.getFullYear()}-${padZero(d.getMonth() + 1)}`;
  }
};
