/**
 * Expense API client. Preserves the same endpoint contract as the original
 * Vue app (client/src/api/expenses.js) so it works against the same Go backend.
 */

import { buildQuery, request } from './api';
import type {
  ByDateResponse,
  DeletedExpensesResponse,
  Expense,
  ExpenseFilters,
  Statistics,
  StatisticsResponse,
} from './types';

export const ExpenseAPI = {
  /** Paginated, server-filtered list grouped by date. */
  async getExpensesByDate(
    page = 1,
    limit = 10,
    filters: ExpenseFilters = {},
  ): Promise<ByDateResponse> {
    const query = buildQuery({
      page,
      limit,
      keyword: filters.keyword,
      type: filters.type,
      month: filters.month,
      minAmount: filters.minAmount,
      maxAmount: filters.maxAmount,
      sort: filters.sort,
    });
    const data = await request<any>(`/expenses/by-date${query}`);
    return {
      data: Array.isArray(data?.data) ? data.data : [],
      total: data?.total || 0,
      meta: data?.meta,
    };
  },

  /** Statistics for the current filter (used by the stats bar + charts). */
  async getStatistics(filters: ExpenseFilters = {}): Promise<Statistics> {
    const query = buildQuery({
      keyword: filters.keyword,
      type: filters.type,
      month: filters.month,
      minAmount: filters.minAmount,
      maxAmount: filters.maxAmount,
    });
    const data = (await request<StatisticsResponse>(`/expenses/statistics${query}`)) || {};
    return {
      count: data.count || 0,
      totalAmount: data.totalAmount || 0,
      averageAmount: data.averageAmount || 0,
      medianAmount: data.medianAmount || 0,
      minAmount: data.minAmount || 0,
      maxAmount: data.maxAmount || 0,
      typeDistribution: data.typeDistribution || {},
    };
  },

  /** All expenses (used for the charts, the monthly budget progress and the AI export). */
  async getAllExpenses(limit = 2000): Promise<Expense[]> {
    try {
      const data = await request<any>(`/expenses${buildQuery({ limit })}`);
      if (data && Array.isArray(data.data) && data.data.length > 0) return data.data;
      if (Array.isArray(data) && data.length > 0) return data;
      // An empty collection response is treated as "not loaded yet" and falls
      // through to the grouped endpoint below.
    } catch {
      // Same here: fall through to the grouped endpoint.
    }
    return loadAllExpensesByDate();
  },

  async addExpense(payload: Partial<Expense>): Promise<unknown> {
    return request('/expenses', {
      method: 'POST',
      body: JSON.stringify(normalize(payload)),
    });
  },

  async updateExpense(id: string, payload: Partial<Expense>): Promise<unknown> {
    return request(`/expenses/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: JSON.stringify(normalize(payload)),
    });
  },

  async deleteExpense(id: string): Promise<unknown> {
    return request(`/expenses/${encodeURIComponent(id)}`, { method: 'DELETE' });
  },

  // ----- Recycle bin (soft-deleted) -----

  async getDeletedExpenses(): Promise<DeletedExpensesResponse> {
    const data = await request<any>('/expenses/deleted');
    const payload = data?.data ?? data ?? {};
    const expenses = Array.isArray(payload.expenses) ? payload.expenses : [];
    return { expenses, count: payload.count ?? expenses.length };
  },

  async restoreExpense(id: string): Promise<unknown> {
    return request(`/expenses/${encodeURIComponent(id)}/restore`, { method: 'POST' });
  },

  async restoreExpensesBatch(ids: string[]): Promise<unknown> {
    return request('/expenses/restore/batch', {
      method: 'POST',
      body: JSON.stringify({ ids }),
    });
  },

  async restoreAllExpenses(): Promise<unknown> {
    return request('/expenses/restore/all', { method: 'POST' });
  },

  async hardDeleteExpense(id: string): Promise<unknown> {
    return request(`/expenses/${encodeURIComponent(id)}/hard`, { method: 'DELETE' });
  },

  async permanentDeleteBatch(ids: string[]): Promise<unknown> {
    return request('/expenses/deleted/batch', {
      method: 'DELETE',
      body: JSON.stringify({ ids }),
    });
  },

  async permanentDeleteAll(): Promise<unknown> {
    return request('/expenses/deleted/all', { method: 'DELETE' });
  },
};

/** Normalize a payload the way the Vue app did before sending it. */
function normalize(payload: Partial<Expense>): Record<string, unknown> {
  const out: Record<string, unknown> = { ...payload };
  if (payload.amount !== undefined) {
    out.amount = parseFloat(parseFloat(String(payload.amount)).toFixed(2));
  }
  if (payload.remark === undefined) out.remark = '';
  return out;
}

/**
 * Fallback loader for the full dataset.
 *
 * The collection route ("/api/expenses") and the grouped route
 * ("/api/expenses/by-date") are two separate handlers on the Go side. When the
 * collection route is unavailable we page through the grouped route — the very
 * endpoint the home page list uses — and flatten the date groups back into a
 * flat expense list, so the charts / AI export / budget bar still get data.
 */
async function loadAllExpensesByDate(maxPages = 60): Promise<Expense[]> {
  const out: Expense[] = [];
  const PAGE_SIZE = 100;

  for (let page = 1; page <= maxPages; page += 1) {
    const res = await request<any>(`/expenses/by-date${buildQuery({ page, limit: PAGE_SIZE })}`);
    const groups: any[] = Array.isArray(res?.data) ? res.data : [];
    if (groups.length === 0) break;

    let received = 0;
    for (const group of groups) {
      const items: any[] = Array.isArray(group?.expenses) ? group.expenses : [];
      received += items.length;
      for (const item of items) out.push(item as Expense);
    }

    // The backend keeps date groups intact, so a page may hold more than
    // PAGE_SIZE records. Stop as soon as we have everything the server knows.
    const total = Number(res?.total || 0);
    if (total > 0 && out.length >= total) break;
    if (received < PAGE_SIZE) break;
  }

  return out;
}
