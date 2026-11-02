/**
 * Formatting helpers: currency, dates, locale-aware month labels.
 * Kept dependency-free (uses Intl) to keep the bundle lean.
 */

/** Format a number as a fixed 2-decimal money string. */
export function formatMoney(amount: number | string | null | undefined): string {
  const n = typeof amount === 'number' ? amount : parseFloat(String(amount));
  if (Number.isNaN(n)) return '0.00';
  return n.toFixed(2);
}

/** Current month string in YYYY-MM. */
export function currentMonth(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

/**
 * Locale-aware month label, e.g. "2026年10月" (zh) or "Oct 2026" (en).
 * @param yearMonth  'YYYY-MM'
 * @param locale     BCP-47 tag (e.g. 'zh-CN')
 */
export function formatMonthLabelByLocale(yearMonth: string, locale: string): string {
  if (!yearMonth || !/^\d{4}-\d{2}$/.test(yearMonth)) return yearMonth || '';
  const [y, m] = yearMonth.split('-').map(Number);
  const date = new Date(y, m - 1, 1);
  try {
    return new Intl.DateTimeFormat(locale, { year: 'numeric', month: 'long' }).format(date);
  } catch {
    return `${y}-${String(m).padStart(2, '0')}`;
  }
}

/** Friendly date label relative to today (Today / Yesterday / N days ago). */
export function relativeDateLabel(dateStr: string, t: (k: string, p?: Record<string, string | number>) => string): string {
  const d = new Date(dateStr);
  if (Number.isNaN(d.getTime())) return dateStr;
  const today = new Date();
  const startOfToday = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  const diffDays = Math.round((startOfToday.getTime() - new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()) / 86400000);
  if (diffDays === 0) return t('common.date_today');
  if (diffDays === 1) return t('common.date_yesterday');
  if (diffDays > 1) return t('common.date_days_ago', { days: diffDays });
  return dateStr;
}

/** ISO weekday label (Sunday..Saturday) for a date string. */
export function weekdayLabel(dateStr: string, t: (k: string) => string): string {
  const d = new Date(dateStr);
  if (Number.isNaN(d.getTime())) return '';
  const idx = d.getDay(); // 0 = Sunday
  const keys = [
    'common.sunday',
    'common.monday',
    'common.tuesday',
    'common.wednesday',
    'common.thursday',
    'common.friday',
    'common.saturday',
  ];
  return t(keys[idx]);
}

/**
 * Locale-aware full date using the browser's Intl.DateTimeFormat,
 * e.g. "Nov 2, 2026" (en) or "2026年11月2日" (zh-CN).
 * Avoids emitting raw YYYY-MM-DD strings. Falls back to the input on parse error.
 */
export function formatDateByLocale(dateStr: string, locale: string): string {
  const d = new Date(dateStr);
  if (Number.isNaN(d.getTime())) return dateStr;
  try {
    return new Intl.DateTimeFormat(locale, { year: 'numeric', month: 'short', day: 'numeric' }).format(d);
  } catch {
    return dateStr;
  }
}

/**
 * Locale-aware short date (month + day) via Intl.DateTimeFormat,
 * e.g. "Nov 2" (en) or "11月2日" (zh-CN). Used for compact row labels.
 */
export function formatDateShortByLocale(dateStr: string, locale: string): string {
  const d = new Date(dateStr);
  if (Number.isNaN(d.getTime())) return dateStr;
  try {
    return new Intl.DateTimeFormat(locale, { month: 'short', day: 'numeric' }).format(d);
  } catch {
    return dateStr;
  }
}

/**
 * Smart expense date: shows a relative label (Today / Yesterday / N days ago)
 * for recent dates and a locale-formatted date for older ones. This is the
 * single place that decides how an expense date is displayed in the UI, so we
 * never render a raw "YYYY-MM-DD" / "MM-DD" string to the user.
 */
export function formatExpenseDate(
  dateStr: string,
  locale: string,
  t: (k: string, p?: Record<string, string | number>) => string,
): string {
  const d = new Date(dateStr);
  if (Number.isNaN(d.getTime())) return dateStr;
  const today = new Date();
  const startOfToday = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  const diffDays = Math.round(
    (startOfToday.getTime() - new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()) / 86400000,
  );
  if (diffDays === 0) return t('common.date_today');
  if (diffDays === 1) return t('common.date_yesterday');
  if (diffDays >= 2 && diffDays <= 6) return t('common.date_days_ago', { days: diffDays });
  return formatDateByLocale(dateStr, locale);
}
