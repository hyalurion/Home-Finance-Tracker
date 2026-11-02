/**
 * Shared TypeScript types for the Home Finance Tracker web frontend.
 * These mirror the data shapes returned by the Go backend (server-go).
 */

export interface Expense {
  id: string;
  type: string;
  amount: number;
  date: string; // YYYY-MM-DD
  remark?: string;
  time?: string;
  version?: number;
  updatedAt?: number;
  deletedAt?: string | null;
}

export interface ByDateGroup {
  date: string;
  count: number;
  totalAmount: number;
  expenses: Expense[];
}

export interface Statistics {
  count: number;
  totalAmount: number;
  averageAmount: number;
  medianAmount: number;
  minAmount: number;
  maxAmount: number;
  typeDistribution: Record<string, number>;
}

export interface ExpenseMeta {
  uniqueTypes: string[];
  availableMonths: string[];
}

export interface ByDateResponse {
  data: ByDateGroup[];
  total: number;
  meta?: ExpenseMeta;
}

export interface StatisticsResponse {
  count?: number;
  totalAmount?: number;
  averageAmount?: number;
  medianAmount?: number;
  minAmount?: number;
  maxAmount?: number;
  typeDistribution?: Record<string, number>;
  error?: string;
}

export interface DeletedExpensesResponse {
  expenses: Expense[];
  count: number;
}

export interface MemberUser {
  username: string;
  avatar?: string;
}

export interface ExpenseFilters {
  keyword?: string;
  type?: string;
  month?: string;
  minAmount?: number | null;
  maxAmount?: number | null;
  sort?: string;
}

export const EXPENSE_TYPES: string[] = [
  '日常用品', '奢侈品', '通讯费用', '食品', '零食糖果', '冷饮', '方便食品',
  '纺织品', '饮品', '调味品', '交通出行', '餐饮', '医疗费用', '水果', '其他',
  '水产品', '乳制品', '礼物人情', '旅行度假', '政务', '水电煤气', '美容美发',
  '豆制品', '个护美妆', '电子产品', '家用电器', '五金', '服装',
];
