/**
 * AI record helper (pure local string logic). Ported from
 * client/src/api/aiRecord.js — no network, no API key. The web app builds a
 * prompt the user copies into any AI chat app, then pastes the reply back.
 */

import { EXPENSE_TYPES } from './types';

const todayIso = (): string => {
  const d = new Date();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
};

const buildRecordPrompt = (): string => {
  const schema = [
    '每条记录必须包含以下字段：',
    '{',
    '  "type": "消费类型", // 只能从下面这个固定列表里选一个：',
    ...EXPENSE_TYPES.map((t) => `\`${t}\``),
    '  "amount": 123.45, // 纯数字，不要带货币符号、千分位逗号或单位',
    '  "date": "2026-10-02", // 日期，格式 YYYY-MM-DD',
    '  "remark": "消费内容描述" // 必须包含所购物品或服务的名称',
    '}',
  ].join('\n');

  const rules = [
    '1. 多笔消费就返回一个 JSON 数组；只有一笔也返回数组（一个元素）。',
    `2. 内容里没写日期的，统一用今天日期 ${todayIso()}。`,
    '3. amount 只输出阿拉伯数字，例如 56.8。',
    '4. 原文里多个金额（比如多件商品各自单价）时，按笔数拆成多条记录。',
    '5. 只输出 JSON，不要输出任何解释文字、Markdown 代码围栏或注释。',
  ].join('\n');

  return [
    '你是一个消费记录解析助手。',
    '接下来我会先给你这段提取规则，再附上需要解析的消费内容（可能是文字描述，也可能是一张收据、小票或账单截图）。',
    '请把消费内容里出现的每一笔消费都提取出来。',
    '',
    schema,
    '',
    '规则：',
    rules,
  ].join('\n');
};

export const buildAddRecordPrompt = (): string => buildRecordPrompt();

export const buildReportPrompt = ({
  xlsxFileName = 'expenses.xlsx',
  stats = null,
  filterDescription = '',
}: {
  xlsxFileName?: string;
  stats?: StatisticsLike | null;
  filterDescription?: string;
} = {}): string => {
  const n = (v: unknown): string =>
    v === null || v === undefined || v === '' ? '-' : String(v);
  const f = (v: unknown): string =>
    typeof v === 'number' && !Number.isNaN(v) ? v.toFixed(2) : n(v);

  const basic = stats
    ? [
        '## 汇总数据（记账应用已预计算，供你交叉校验）',
        `- 总记录数：${n(stats.totalCount)} 条`,
        `- 总金额：${f(stats.totalAmount)} 元`,
        `- 平均金额：${f(stats.averageAmount)} 元`,
        `- 中位数：${f(stats.medianAmount)} 元`,
        `- 金额范围：${n(stats.amountRange)}`,
        '',
        '## 消费类型分布',
        stats.typeDistribution && typeof stats.typeDistribution === 'object'
          ? Object.entries(stats.typeDistribution)
              .map(([type, count]) => `- ${type}：${count} 条`)
              .join('\n')
          : '- 暂无数据',
        '',
      ].join('\n')
    : '';

  return [
    '你是一个专业的消费分析助手。用户已经把自己的消费数据导出成 Excel 文件，并把它作为附件上传给了你。',
    '',
    `数据文件：\`${n(xlsxFileName)}\``,
    '',
    '工作表「明细」是逐条消费记录，工作表「统计」是汇总数据（两个表的数据应当一致，可以互相校验）。',
    '请先打开这个文件确认数据范围，再阅读下面的说明。',
    '',
    '## 数据范围',
    filterDescription || '- 未指定筛选条件（包含全部数据）',
    '',
    basic,
    '## 接下来怎么做',
    '上面只是数据说明，我还没有提问。',
    '请你先简要说明读到的这份数据的概况（总记录数、总金额、时间跨度、消费类型分布），',
    '这轮不要下结论，等我说出问题再作答。',
    '我之后会把问题作为新的消息发给你，照常回答即可。',
    '',
    '## 输出要求',
    '1. 用 Markdown 正文输出，不要用代码块包裹。',
    '2. 用表格或列表呈现数据与结论，保证可读性。',
    '3. 只依据这份 Excel 里真实存在的数据作答，不要编造数字。',
    '4. 聚焦消费分析本身，不要推荐其他记账工具或软件。',
  ].join('\n');
};

interface StatisticsLike {
  totalCount?: number;
  totalAmount?: number;
  averageAmount?: number;
  medianAmount?: number;
  amountRange?: string;
  typeDistribution?: Record<string, number>;
}

const stripCodeFence = (raw: string): string => {
  const s = String(raw || '').trim();
  const m = s.match(/^```[a-zA-Z0-9_+-]*\s*([\s\S]*?)\s*```$/);
  return m ? m[1] : s;
};

const sliceBalanced = (s: string, open: string, close: string): string | null => {
  const start = s.indexOf(open);
  if (start === -1) return null;
  let depth = 0;
  let inStr = false;
  let escaped = false;
  for (let i = start; i < s.length; i += 1) {
    const ch = s[i];
    if (inStr) {
      if (escaped) escaped = false;
      else if (ch === '\\') escaped = true;
      else if (ch === '"') inStr = false;
      continue;
    }
    if (ch === '"') inStr = true;
    else if (ch === open) depth += 1;
    else if (ch === close) {
      depth -= 1;
      if (depth === 0) return s.slice(start, i + 1);
    }
  }
  return null;
};

const normalizeRecord = (item: any): ExpenseDraft | null => {
  if (!item || typeof item !== 'object') return null;
  const amountRaw = item.amount ?? item.Amount ?? item.money ?? item.price ?? item.total;
  const amount = parseFloat(String(amountRaw).replace(/[^0-9.\-]/g, ''));
  if (Number.isNaN(amount) || amount <= 0) return null;

  const rawDate = item.date ?? item.Date ?? '';
  let date = '';
  if (typeof rawDate === 'string' && rawDate.trim()) {
    const iso = rawDate.trim().match(/(\d{4})[-/年](\d{1,2})[-/月](\d{1,2})/);
    date = iso
      ? `${iso[1]}-${iso[2].padStart(2, '0')}-${iso[3].padStart(2, '0')}`
      : rawDate.trim().slice(0, 10);
  }

  const type = String(item.type ?? item.Type ?? '').trim();
  const remark = String(item.remark ?? item.Remark ?? item.note ?? item.description ?? '').trim();

  return {
    type: EXPENSE_TYPES.includes(type) ? type : type || '其他',
    amount,
    date: date || todayIso(),
    remark,
    selected: true,
  };
};

export interface ExpenseDraft {
  type: string;
  amount: number;
  date: string;
  remark: string;
  selected: boolean;
}

export const parseAiReply = (raw: string): { ok: boolean; records: ExpenseDraft[]; message?: string } => {
  const text = stripCodeFence(raw);
  if (!text) return { ok: false, records: [], message: '粘贴的内容是空的' };

  let candidate = text.trim();
  let parsed: any = null;

  if (candidate.startsWith('[') || candidate.startsWith('{')) {
    try {
      parsed = JSON.parse(candidate);
    } catch {
      parsed = null;
    }
  }

  if (parsed === null) {
    const arr = sliceBalanced(candidate, '[', ']');
    const obj = arr === null ? sliceBalanced(candidate, '{', '}') : arr;
    const chunk = arr || obj;
    if (chunk) {
      try {
        parsed = JSON.parse(chunk);
      } catch {
        parsed = null;
      }
    }
  }

  if (parsed === null) {
    return {
      ok: false,
      records: [],
      message: '没能从粘贴内容里解析出 JSON，请让 AI 只输出纯 JSON 后重试',
    };
  }

  const list = Array.isArray(parsed) ? parsed : [parsed];
  const records = list.map(normalizeRecord).filter(Boolean) as ExpenseDraft[];

  if (records.length === 0) {
    return {
      ok: false,
      records: [],
      message: '解析结果里没有有效的消费记录（需要 amount 为大于 0 的数字）',
    };
  }
  return { ok: true, records };
};

/** Mirror the backend's deterministic export filename rule. */
export const buildAIDataFilename = (year?: string, month?: string, types?: string[]): string => {
  let base = 'expenses';
  if (year && month) base = `expenses_${year}-${month}`;
  else if (year) base = `expenses_${year}`;
  else if (types && types.length) base = `expenses_types_${types.length}`;
  return `${base}.xlsx`;
};

/** Build the human-readable filter description for the report prompt. */
export const buildFilterDescription = (
  stats: { totalCount?: number } | null,
  conditions: { year?: string; month?: string; types?: string[] },
): string => {
  const c: string[] = [];
  if (conditions.year && conditions.month) {
    c.push(`- **时间范围**：${conditions.year}年${parseInt(conditions.month, 10)}月`);
  } else if (conditions.year) {
    c.push(`- **时间范围**：${conditions.year}年全年度`);
  } else if (conditions.month) {
    c.push(`- **时间范围**：所有年份的${parseInt(conditions.month, 10)}月`);
  } else {
    c.push('- **时间范围**：所有时间');
  }
  if (conditions.types && conditions.types.length > 0) {
    c.push(`- **消费类型**：${conditions.types.join('、')}`);
  } else {
    c.push('- **消费类型**：全部类型（共不超过28种）');
  }
  c.push(`- **数据量**：共${stats?.totalCount || 0}条记录`);
  return c.join('\n');
};
