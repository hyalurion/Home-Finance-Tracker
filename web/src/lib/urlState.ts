/**
 * Filter / pagination state mirrored into the address bar.
 *
 * The original Vue app wrote its search params and page number into the URL
 * query string (and read them back on mount), so a filtered view could be
 * bookmarked, shared or survived a reload. The Next.js port keeps the exact
 * same parameter names and behaviour.
 */

export interface ExpenseUrlState {
  page: number;
  keyword: string;
  type: string;
  month: string;
  minAmount: number | null;
  maxAmount: number | null;
  sortOption: string;
}

export type ExpenseSearchFilters = Omit<ExpenseUrlState, 'page'>;

export const DEFAULT_EXPENSE_URL_STATE: ExpenseUrlState = {
  page: 1,
  keyword: '',
  type: '',
  month: '',
  minAmount: null,
  maxAmount: null,
  sortOption: 'dateDesc',
};

const PARAM = {
  page: 'page',
  keyword: 'keyword',
  type: 'type',
  month: 'month',
  minAmount: 'minAmount',
  maxAmount: 'maxAmount',
  sort: 'sort',
} as const;

const toNumber = (raw: string | null): number | null => {
  if (raw === null || raw === '') return null;
  const n = parseFloat(raw);
  return Number.isFinite(n) ? n : null;
};

/** Read the current filter / page state from the address bar. */
export function readExpenseStateFromUrl(search?: string): ExpenseUrlState {
  if (typeof window === 'undefined') return { ...DEFAULT_EXPENSE_URL_STATE };

  const sp = new URLSearchParams(search ?? window.location.search);
  const pageRaw = parseInt(sp.get(PARAM.page) || '', 10);

  return {
    page: Number.isFinite(pageRaw) && pageRaw > 0 ? pageRaw : 1,
    keyword: sp.get(PARAM.keyword) || '',
    type: sp.get(PARAM.type) || '',
    month: sp.get(PARAM.month) || '',
    minAmount: toNumber(sp.get(PARAM.minAmount)),
    maxAmount: toNumber(sp.get(PARAM.maxAmount)),
    sortOption: sp.get(PARAM.sort) || DEFAULT_EXPENSE_URL_STATE.sortOption,
  };
}

/** Write the filter / page state back into the address bar (no reload). */
export function writeExpenseStateToUrl(state: ExpenseUrlState): void {
  if (typeof window === 'undefined') return;

  const url = new URL(window.location.href);
  const apply = (key: string, value: string | number | null | undefined) => {
    if (value === null || value === undefined || value === '') {
      url.searchParams.delete(key);
    } else {
      url.searchParams.set(key, String(value));
    }
  };

  apply(PARAM.page, state.page > 1 ? state.page : null);
  apply(PARAM.keyword, state.keyword);
  apply(PARAM.type, state.type);
  apply(PARAM.month, state.month);
  apply(PARAM.minAmount, state.minAmount);
  apply(PARAM.maxAmount, state.maxAmount);
  apply(PARAM.sort, state.sortOption && state.sortOption !== 'dateDesc' ? state.sortOption : null);

  window.history.replaceState({}, '', url.toString());
}

/** True when two filter objects describe the same query. */
export function sameFilters(a: ExpenseSearchFilters, b: ExpenseSearchFilters): boolean {
  return (
    a.keyword === b.keyword &&
    a.type === b.type &&
    a.month === b.month &&
    (a.minAmount ?? null) === (b.minAmount ?? null) &&
    (a.maxAmount ?? null) === (b.maxAmount ?? null) &&
    a.sortOption === b.sortOption
  );
}
