'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useI18n } from '@/i18n/I18nProvider';
import { ExpenseAPI } from '@/lib/expenses';
import {
  DEFAULT_EXPENSE_URL_STATE,
  readExpenseStateFromUrl,
  sameFilters,
  writeExpenseStateToUrl,
  type ExpenseSearchFilters,
} from '@/lib/urlState';
import type { ByDateGroup, Expense, Statistics } from '@/lib/types';
import { ExpenseSearch } from './ExpenseSearch';
import { ExpenseStats } from './ExpenseStats';
import { ExpenseTable } from './ExpenseTable';
import { ExpensePagination } from './ExpensePagination';

interface Props {
  refreshKey: number;
  onEdit: (expense: Expense) => void;
  onDelete: (id: string) => void;
  onLoaded?: (total: number) => void;
}

const PAGE_SIZE = 10;

const EMPTY_STATS: Statistics = {
  count: 0,
  totalAmount: 0,
  averageAmount: 0,
  medianAmount: 0,
  minAmount: 0,
  maxAmount: 0,
  typeDistribution: {},
};

export function ExpenseList({ refreshKey, onEdit, onDelete, onLoaded }: Props) {
  const { t, locale } = useI18n();
  const [filters, setFilters] = useState<ExpenseSearchFilters>({
    keyword: '',
    type: '',
    month: '',
    minAmount: null,
    maxAmount: null,
    sortOption: 'dateDesc',
  });
  const [page, setPage] = useState(1);
  const [groups, setGroups] = useState<ByDateGroup[]>([]);
  const [total, setTotal] = useState(0);
  const [stats, setStats] = useState<Statistics>(EMPTY_STATS);
  const [uniqueTypes, setUniqueTypes] = useState<string[]>([]);
  const [availableMonths, setAvailableMonths] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);

  // The filters restored from the address bar. Kept in its own state so the
  // search form can be seeded with them without re-seeding on every render.
  const [initialSearch, setInitialSearch] = useState<ExpenseSearchFilters>({
    keyword: '',
    type: '',
    month: '',
    minAmount: null,
    maxAmount: null,
    sortOption: 'dateDesc',
  });

  // Fetching only starts once the address bar has been read, so the very first
  // request already carries the restored filters (no duplicate round-trip).
  const [ready, setReady] = useState(false);

  // Keep the latest onLoaded in a ref so its (often inline) identity never
  // destabilises fetchData. Otherwise the fetch effect re-runs on every render
  // and triggers "Maximum update depth exceeded".
  const onLoadedRef = useRef(onLoaded);
  onLoadedRef.current = onLoaded;

  // ---- restore the state from the address bar (once, on mount) ------------
  useEffect(() => {
    const fromUrl = readExpenseStateFromUrl();
    const restored: ExpenseSearchFilters = {
      keyword: fromUrl.keyword,
      type: fromUrl.type,
      month: fromUrl.month,
      minAmount: fromUrl.minAmount,
      maxAmount: fromUrl.maxAmount,
      sortOption: fromUrl.sortOption || DEFAULT_EXPENSE_URL_STATE.sortOption,
    };
    setFilters(restored);
    setInitialSearch(restored);
    setPage(fromUrl.page);
    setReady(true);
  }, []);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const res = await ExpenseAPI.getExpensesByDate(page, PAGE_SIZE, {
        keyword: filters.keyword,
        type: filters.type,
        month: filters.month,
        minAmount: filters.minAmount ?? undefined,
        maxAmount: filters.maxAmount ?? undefined,
        sort: filters.sortOption,
      });
      setGroups(res.data);
      setTotal(res.total);
      if (res.meta) {
        setUniqueTypes(res.meta.uniqueTypes || []);
        if (res.meta.availableMonths?.length) {
          setAvailableMonths([...res.meta.availableMonths].sort((a, b) => b.localeCompare(a)));
        }
      }
      onLoadedRef.current?.(res.total);
    } catch {
      // Network/API failures must not throw (avoids unhandled rejections and
      // the render loop); the empty state covers the no-data case.
    } finally {
      setLoading(false);
    }
    // onLoaded is intentionally excluded — see onLoadedRef above.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, filters]);

  const fetchStats = useCallback(async () => {
    try {
      const s = await ExpenseAPI.getStatistics({
        keyword: filters.keyword,
        type: filters.type,
        month: filters.month,
        minAmount: filters.minAmount ?? undefined,
        maxAmount: filters.maxAmount ?? undefined,
      });
      setStats(s);
    } catch {
      // Keep the previous statistics when the request fails.
    }
  }, [filters]);

  // fetchData re-runs whenever page / filters change; refreshKey forces a
  // reload after an add / edit / delete without duplicating the mount fetch.
  useEffect(() => {
    if (!ready) return;
    fetchData();
  }, [ready, fetchData, refreshKey]);

  useEffect(() => {
    if (!ready) return;
    fetchStats();
  }, [ready, fetchStats]);

  // ---- mirror the state back into the address bar ------------------------
  useEffect(() => {
    if (!ready) return;
    const timer = setTimeout(() => {
      writeExpenseStateToUrl({ page, ...filters });
    }, 200);
    return () => clearTimeout(timer);
  }, [ready, page, filters]);

  const handleSearch = (next: ExpenseSearchFilters) => {
    // Keep the previous object when nothing actually changed, so we do not
    // trigger an extra request for an identical query.
    setFilters((prev) => (sameFilters(prev, next) ? prev : next));
    setPage(1);
  };

  const handleSort = (field: 'date' | 'amount') => {
    setFilters((prev) => {
      const current = prev.sortOption;
      let nextSort = current;
      if (field === 'date') {
        nextSort = current === 'dateAsc' ? 'dateDesc' : 'dateAsc';
      } else {
        nextSort = current === 'amountAsc' ? 'amountDesc' : 'amountAsc';
      }
      return { ...prev, sortOption: nextSort };
    });
    setPage(1);
  };

  const totalPages = Math.ceil(total / PAGE_SIZE) || 1;
  const hasData = groups.length > 0;

  const searchProps = useMemo(
    () => ({ uniqueTypes, availableMonths, locale, initial: initialSearch }),
    [uniqueTypes, availableMonths, locale, initialSearch],
  );

  return (
    <>
      <ExpenseSearch {...searchProps} onSearch={handleSearch} />

      {hasData ? (
        <>
          <ExpenseStats stats={stats} />
          <ExpenseTable
            groups={groups}
            sortOption={filters.sortOption}
            onSort={handleSort}
            onEdit={onEdit}
            onDelete={onDelete}
          />
          <ExpensePagination currentPage={page} totalPages={totalPages} onChange={setPage} />
        </>
      ) : (
        <div className="empty-state section">
          <h3>{t('expense.empty.title')}</h3>
          <p>{t('expense.empty.description')}</p>
        </div>
      )}

      {loading ? <p className="muted section" style={{ textAlign: 'center' }}>{t('app.loading')}</p> : null}
    </>
  );
}
