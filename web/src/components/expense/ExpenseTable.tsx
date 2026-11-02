'use client';

import { useI18n } from '@/i18n/I18nProvider';
import { formatExpenseDate, formatDateShortByLocale, formatMoney, weekdayLabel } from '@/lib/format';
import type { ByDateGroup, Expense } from '@/lib/types';

interface Props {
  groups: ByDateGroup[];
  sortOption: string;
  onSort: (field: 'date' | 'amount') => void;
  onEdit: (expense: Expense) => void;
  onDelete: (id: string) => void;
}

export function ExpenseTable({ groups, sortOption, onSort, onEdit, onDelete }: Props) {
  const { t, locale } = useI18n();

  const sortIndicator = (field: 'date' | 'amount') => {
    const base = field === 'date' ? 'date' : 'amount';
    if (!sortOption.startsWith(base)) return '';
    return sortOption.endsWith('Asc') ? ' ↑' : ' ↓';
  };

  return (
    <div className="table-scroll section">
      <table className="table">
        <thead>
          <tr>
            <th className="sortable" onClick={() => onSort('date')}>
              {t('common.date')}{sortIndicator('date')}
            </th>
            <th>{t('expense.columns.name')}</th>
            <th className="num sortable" onClick={() => onSort('amount')}>
              {t('expense.amount')}{sortIndicator('amount')}
            </th>
            <th>{t('expense.remark')}</th>
            <th style={{ textAlign: 'right' }}>{t('common.action')}</th>
          </tr>
        </thead>
        <tbody>
          {groups.map((group) => (
            <GroupBlock
              key={group.date}
              group={group}
              t={t}
              locale={locale}
              onEdit={onEdit}
              onDelete={onDelete}
            />
          ))}
        </tbody>
      </table>
    </div>
  );
}

function GroupBlock({
  group,
  t,
  locale,
  onEdit,
  onDelete,
}: {
  group: ByDateGroup;
  t: (k: string) => string;
  locale: string;
  onEdit: (e: Expense) => void;
  onDelete: (id: string) => void;
}) {
  return (
    <>
      <tr className="group-row">
        <td colSpan={5}>
          <div className="row" style={{ justifyContent: 'space-between' }}>
            <span className="group-date">
              {formatExpenseDate(group.date, locale, t)} <span className="faint">· {weekdayLabel(group.date, t)}</span>
            </span>
            <span className="num muted">
              {group.count} · {formatMoney(group.totalAmount)}
            </span>
          </div>
        </td>
      </tr>
      {group.expenses.map((exp) => (
        <tr key={exp.id}>
          <td className="faint">{formatDateShortByLocale(group.date, locale)}</td>
          <td><span className="tag">{exp.type}</span></td>
          <td className="num">{formatMoney(exp.amount)}</td>
          <td className="muted">{exp.remark || '—'}</td>
          <td>
            <div className="table__actions">
              <button className="icon-btn" style={{ width: 32, height: 32 }} aria-label={t('common.edit')} onClick={() => onEdit(exp)}>
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 20h9" /><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z" /></svg>
              </button>
              <button className="icon-btn" style={{ width: 32, height: 32 }} aria-label={t('common.delete')} onClick={() => onDelete(exp.id)}>
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M3 6h18" /><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" /><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6" /></svg>
              </button>
            </div>
          </td>
        </tr>
      ))}
    </>
  );
}
