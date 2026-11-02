'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import { useI18n } from '@/i18n/I18nProvider';
import { Select } from '@/components/ui/Select';
import { formatMonthLabelByLocale } from '@/lib/format';
import type { ExpenseSearchFilters } from '@/lib/urlState';

interface Props {
  uniqueTypes: string[];
  availableMonths: string[];
  locale: string;
  /** Values restored from the address bar — seeds the form on first render. */
  initial?: Partial<ExpenseSearchFilters>;
  onSearch: (filters: ExpenseSearchFilters) => void;
}

export function ExpenseSearch({ uniqueTypes, availableMonths, locale, initial, onSearch }: Props) {
  const { t } = useI18n();
  const [keyword, setKeyword] = useState(initial?.keyword ?? '');
  const [type, setType] = useState(initial?.type ?? '');
  const [month, setMonth] = useState(initial?.month ?? '');
  const [minAmount, setMinAmount] = useState(initial?.minAmount != null ? String(initial.minAmount) : '');
  const [maxAmount, setMaxAmount] = useState(initial?.maxAmount != null ? String(initial.maxAmount) : '');
  const [sortOption, setSortOption] = useState(initial?.sortOption ?? 'dateDesc');

  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Keep the form in sync with values restored from the address bar. The
  // signature guard makes sure we only apply genuinely new values.
  const appliedInitial = useRef<string>('');
  useEffect(() => {
    const next = {
      keyword: initial?.keyword ?? '',
      type: initial?.type ?? '',
      month: initial?.month ?? '',
      minAmount: initial?.minAmount ?? null,
      maxAmount: initial?.maxAmount ?? null,
      sortOption: initial?.sortOption ?? 'dateDesc',
    };
    const signature = JSON.stringify(next);
    if (signature === appliedInitial.current) return;
    appliedInitial.current = signature;

    setKeyword(next.keyword);
    setType(next.type);
    setMonth(next.month);
    setMinAmount(next.minAmount != null ? String(next.minAmount) : '');
    setMaxAmount(next.maxAmount != null ? String(next.maxAmount) : '');
    setSortOption(next.sortOption);
  }, [initial]);

  const monthOptions = useMemo(
    () =>
      availableMonths.map((m) => ({
        value: m,
        label: formatMonthLabelByLocale(m, locale),
      })),
    [availableMonths, locale],
  );

  const typeOptions = useMemo(
    () => uniqueTypes.map((tp) => ({ value: tp, label: tp })),
    [uniqueTypes],
  );

  const sortOptions = useMemo(
    () => [
      { value: 'dateDesc', label: t('expense.sort.dateDesc') },
      { value: 'dateAsc', label: t('expense.sort.dateAsc') },
      { value: 'amountDesc', label: t('expense.sort.amountDesc') },
      { value: 'amountAsc', label: t('expense.sort.amountAsc') },
    ],
    [t],
  );

  const emit = () => {
    const min = minAmount !== '' ? Number(minAmount) : undefined;
    const max = maxAmount !== '' ? Number(maxAmount) : undefined;
    onSearch({
      keyword,
      type,
      month,
      minAmount: min !== undefined && !Number.isNaN(min) ? min : null,
      maxAmount: max !== undefined && !Number.isNaN(max) ? max : null,
      sortOption,
    });
  };

  // Debounced emit on any change.
  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(emit, 250);
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [keyword, type, month, minAmount, maxAmount, sortOption]);

  const reset = () => {
    setKeyword('');
    setType('');
    setMonth('');
    setMinAmount('');
    setMaxAmount('');
    setSortOption('dateDesc');
  };

  const activeCount =
    (keyword ? 1 : 0) + (type ? 1 : 0) + (month ? 1 : 0) + (minAmount || maxAmount ? 1 : 0);

  return (
    <section className="card section" aria-label={t('expense.search.title')}>
      <div className="row" style={{ justifyContent: 'space-between', marginBottom: 16 }}>
        <h2 className="card__title" style={{ margin: 0 }}>{t('expense.search.title')}</h2>
        {activeCount > 0 ? (
          <button className="btn btn--ghost btn--sm" onClick={reset}>{t('expense.search.reset')}</button>
        ) : null}
      </div>

      <div className="search-grid">
        <div className="field">
          <label className="field__label">{t('expense.search.month')}</label>
          <Select value={month} onChange={setMonth} options={monthOptions} emptyOptionLabel={t('expense.search.allMonth')} />
        </div>

        <div className="field">
          <label className="field__label">{t('expense.search.type')}</label>
          <Select value={type} onChange={setType} options={typeOptions} emptyOptionLabel={t('expense.search.allType')} />
        </div>

        <div className="field">
          <label className="field__label">{t('expense.search.sort')}</label>
          <Select value={sortOption} onChange={setSortOption} options={sortOptions} />
        </div>

        <div className="field">
          <label className="field__label">{t('expense.search.amountRange')}</label>
          <div className="row" style={{ gap: 8 }}>
            <input
              className="input"
              type="number"
              min={0}
              step="0.01"
              placeholder={t('expense.search.minAmountPlaceholder')}
              value={minAmount}
              onChange={(e) => setMinAmount(e.target.value)}
            />
            <span className="muted">–</span>
            <input
              className="input"
              type="number"
              min={0}
              step="0.01"
              placeholder={t('expense.search.maxAmountPlaceholder')}
              value={maxAmount}
              onChange={(e) => setMaxAmount(e.target.value)}
            />
          </div>
        </div>

        <div className="field">
          <label className="field__label" htmlFor="expense-keyword">{t('expense.search.keyword')}</label>
          <input
            id="expense-keyword"
            className="input"
            type="text"
            placeholder={t('expense.search.keywordPlaceholder')}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
          />
        </div>
      </div>
    </section>
  );
}
