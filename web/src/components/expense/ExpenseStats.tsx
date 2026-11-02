'use client';

import { useI18n } from '@/i18n/I18nProvider';
import { formatMoney } from '@/lib/format';
import type { Statistics } from '@/lib/types';

export function ExpenseStats({ stats }: { stats: Statistics }) {
  const { t } = useI18n();
  const items = [
    { label: t('expense.stats.rowCount'), value: String(stats.count) },
    { label: t('expense.stats.totalAmount'), value: formatMoney(stats.totalAmount) },
    { label: t('expense.stats.averageAmount'), value: formatMoney(stats.averageAmount) },
    { label: t('expense.stats.medianAmount'), value: formatMoney(stats.medianAmount) },
    {
      label: t('expense.stats.amountRange'),
      value: `${formatMoney(stats.minAmount)} – ${formatMoney(stats.maxAmount)}`,
    },
  ];

  return (
    <div className="stats-grid section" role="group" aria-label={t('expense.stats.rowCount')}>
      {items.map((it) => (
        <div className="stat" key={it.label}>
          <div className="stat__value num">{it.value}</div>
          <div className="stat__label">{it.label}</div>
        </div>
      ))}
    </div>
  );
}
