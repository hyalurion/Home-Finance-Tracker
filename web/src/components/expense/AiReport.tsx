'use client';

import { useEffect, useMemo, useState } from 'react';
import { marked } from 'marked';
import { useI18n } from '@/i18n/I18nProvider';
import { Dialog } from '@/components/ui/Dialog';
import { Select } from '@/components/ui/Select';
import { useToast } from '@/components/ui/ToastProvider';
import { ExpenseAPI } from '@/lib/expenses';
import { buildReportPrompt, buildFilterDescription, buildAIDataFilename } from '@/lib/aiRecord';
import { EXPENSE_TYPES, type Expense } from '@/lib/types';

interface Props {
  open: boolean;
  onClose: () => void;
}

const MONTHS = ['01', '02', '03', '04', '05', '06', '07', '08', '09', '10', '11', '12'];

export function AiReport({ open, onClose }: Props) {
  const { t, locale } = useI18n();
  const toast = useToast();

  // Filter state. The backend expects `year` as a 4-digit string, `month` as a
  // 2-digit string and `types` as a comma separated list — anything else is
  // silently dropped by parseAIDataFilters and the export would contain the
  // unfiltered dataset.
  const [year, setYear] = useState('');
  const [month, setMonth] = useState('');
  const [types, setTypes] = useState<string[]>([]);

  const [allExpenses, setAllExpenses] = useState<Expense[]>([]);
  const [loading, setLoading] = useState(false);

  const [prompt, setPrompt] = useState('');
  const [reply, setReply] = useState('');
  const [content, setContent] = useState('');
  const [fileName, setFileName] = useState('');
  const [downloading, setDownloading] = useState(false);

  const now = useMemo(() => new Date(), []);

  const yearOptions = useMemo(() => {
    const out: { value: string; label: string }[] = [];
    for (let i = 0; i < 10; i += 1) {
      const y = String(now.getFullYear() - i);
      out.push({ value: y, label: `${y}` });
    }
    return out;
  }, [now]);

  const monthOptions = useMemo(
    () => MONTHS.map((m) => ({ value: m, label: `${parseInt(m, 10)}月` })),
    [],
  );

  // Load the full dataset so the filter preview can show what will be
  // exported. The very same data feeds the statistics block in the prompt.
  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setLoading(true);

    ExpenseAPI.getAllExpenses()
      .then((list) => {
        if (!cancelled) setAllExpenses(list);
      })
      .catch(() => {
        if (!cancelled) setAllExpenses([]);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [open]);

  // Reset the transient prompt / reply state each time the dialog opens.
  useEffect(() => {
    if (open) return;
    setPrompt('');
    setReply('');
    setContent('');
  }, [open]);

  /** The rows that match the current filter — mirrors the backend query. */
  const filtered = useMemo(() => {
    return allExpenses.filter((e) => {
      const date = typeof e.date === 'string' ? e.date : '';
      if (year && !date.startsWith(`${year}-`)) return false;
      if (month && !date.includes(`-${month}-`)) return false;
      if (types.length > 0 && !types.includes(e.type)) return false;
      return true;
    });
  }, [allExpenses, year, month, types]);

  const stats = useMemo(() => {
    const amounts = filtered
      .map((e) => Number(e.amount))
      .filter((a) => !Number.isNaN(a))
      .sort((a, b) => a - b);

    const totalAmount = amounts.reduce((sum, a) => sum + a, 0);
    const typeDistribution: Record<string, number> = {};
    for (const e of filtered) {
      typeDistribution[e.type] = (typeDistribution[e.type] || 0) + 1;
    }

    const mid = Math.floor(amounts.length / 2);
    const median =
      amounts.length === 0
        ? 0
        : amounts.length % 2 === 1
          ? amounts[mid]
          : (amounts[mid - 1] + amounts[mid]) / 2;

    return {
      totalCount: filtered.length,
      totalAmount,
      averageAmount: amounts.length ? totalAmount / amounts.length : 0,
      medianAmount: median,
      minAmount: amounts.length ? amounts[0] : 0,
      maxAmount: amounts.length ? amounts[amounts.length - 1] : 0,
      typeDistribution,
    };
  }, [filtered]);

  const conditions = {
    year: year || undefined,
    month: month || undefined,
    types,
  };

  const toggleType = (tp: string) => {
    setTypes((prev) => (prev.includes(tp) ? prev.filter((x) => x !== tp) : [...prev, tp]));
  };

  const selectCurrentMonth = () => {
    setYear(String(now.getFullYear()));
    setMonth(String(now.getMonth() + 1).padStart(2, '0'));
  };

  const selectLastMonth = () => {
    const d = new Date(now.getFullYear(), now.getMonth() - 1, 1);
    setYear(String(d.getFullYear()));
    setMonth(String(d.getMonth() + 1).padStart(2, '0'));
  };

  const selectThisYear = () => {
    setYear(String(now.getFullYear()));
    setMonth('');
  };

  const clearFilters = () => {
    setYear('');
    setMonth('');
    setTypes([]);
  };

  const downloadData = async () => {
    setDownloading(true);
    const fn = buildAIDataFilename(conditions.year, conditions.month, types);
    setFileName(fn);

    const params = new URLSearchParams();
    params.set('lang', locale);
    if (conditions.year) params.set('year', conditions.year);
    if (conditions.month) params.set('month', conditions.month);
    if (types.length) params.set('types', types.join(','));

    try {
      const res = await fetch(`/api/export/ai-data?${params.toString()}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = fn;
      document.body.appendChild(a); // required for Firefox
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
      toast.show(t('ai.report.downloadButton') + ' ✓');
    } catch {
      toast.show(t('export.failed'), 'error');
    } finally {
      setDownloading(false);
    }
  };

  const generatePrompt = () => {
    const desc = buildFilterDescription({ totalCount: stats.totalCount }, conditions);
    setPrompt(
      buildReportPrompt({
        xlsxFileName: fileName || buildAIDataFilename(conditions.year, conditions.month, types),
        stats: {
          totalCount: stats.totalCount,
          totalAmount: stats.totalAmount,
          averageAmount: stats.averageAmount,
          medianAmount: stats.medianAmount,
          amountRange: `${stats.minAmount.toFixed(2)} – ${stats.maxAmount.toFixed(2)}`,
          typeDistribution: stats.typeDistribution,
        },
        filterDescription: desc,
      }),
    );
  };

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(prompt);
      toast.show(t('ai.report.copyButton') + ' ✓');
    } catch {
      toast.show(t('ai.report.copyButton'));
    }
  };

  const render = () => {
    if (!reply.trim()) {
      toast.show(t('ai.report.replyPlaceholder'), 'error');
      return;
    }
    setContent(marked.parse(reply) as string);
    toast.show(t('ai.report.pasteButton') + ' ✓');
  };

  const money = (v: number) => `${v.toFixed(2)}`;

  return (
    <Dialog
      open={open}
      title={t('ai.report.title')}
      onClose={onClose}
      width={720}
      footer={<button className="btn" onClick={onClose}>{t('common.cancel')}</button>}
    >
      {/* ---------------- filter ---------------- */}
      <div className="field">
        <label className="field__label">{t('ai.report.filterTitle')}</label>

        <div className="row" style={{ gap: 10, flexWrap: 'wrap' }}>
          <div style={{ minWidth: 140 }}>
            <label className="field__label">{t('expense.search.year')}</label>
            <Select value={year} onChange={setYear} options={yearOptions} emptyOptionLabel={t('expense.search.allYear')} />
          </div>
          <div style={{ minWidth: 140 }}>
            <label className="field__label">{t('expense.search.month')}</label>
            <Select value={month} onChange={setMonth} options={monthOptions} emptyOptionLabel={t('expense.search.allMonth')} />
          </div>
        </div>

        <div className="btn-group" style={{ marginTop: 12, flexWrap: 'wrap' }}>
          <button className="btn btn--sm" onClick={selectCurrentMonth}>{t('ai.report.currentMonth')}</button>
          <button className="btn btn--sm" onClick={selectLastMonth}>{t('ai.report.lastMonth')}</button>
          <button className="btn btn--sm" onClick={selectThisYear}>{t('ai.report.thisYear')}</button>
          <button className="btn btn--sm btn--ghost" onClick={clearFilters}>{t('ai.report.clearFilters')}</button>
        </div>

        <div className="field" style={{ marginTop: 14 }}>
          <label className="field__label">{t('ai.report.types')}</label>
          <div className="check-list">
            {EXPENSE_TYPES.map((tp) => (
              <label key={tp} className={`check-item ${types.includes(tp) ? 'is-active' : ''}`}>
                <input
                  type="checkbox"
                  className="check-item__input"
                  checked={types.includes(tp)}
                  onChange={() => toggleType(tp)}
                />
                <span className="check-item__label">{tp}</span>
              </label>
            ))}
          </div>
        </div>
      </div>

      {/* ---------------- statistics preview ---------------- */}
      <div className="field">
        <div className="row" style={{ justifyContent: 'space-between', marginBottom: 8 }}>
          <label className="field__label" style={{ margin: 0 }}>{t('ai.report.statsPreview')}</label>
          {filtered.length > 0 ? (
            <span className="tag tag--solid">{t('ai.report.recordsCount', { count: filtered.length })}</span>
          ) : null}
        </div>

        {loading ? (
          <p className="muted">{t('ai.report.loading')}</p>
        ) : (
          <div className="stats-grid stats-grid--six">
            <div className="stat">
              <div className="stat__value">{stats.totalCount}</div>
              <div className="stat__label">{t('ai.report.totalCount')}</div>
            </div>
            <div className="stat">
              <div className="stat__value">{money(stats.totalAmount)}</div>
              <div className="stat__label">{t('ai.report.totalAmount')}</div>
            </div>
            <div className="stat">
              <div className="stat__value">{money(stats.averageAmount)}</div>
              <div className="stat__label">{t('ai.report.averageAmount')}</div>
            </div>
            <div className="stat">
              <div className="stat__value">{money(stats.medianAmount)}</div>
              <div className="stat__label">{t('ai.report.medianAmount')}</div>
            </div>
            <div className="stat">
              <div className="stat__value">{money(stats.minAmount)}</div>
              <div className="stat__label">{t('ai.report.minAmount')}</div>
            </div>
            <div className="stat">
              <div className="stat__value">{money(stats.maxAmount)}</div>
              <div className="stat__label">{t('ai.report.maxAmount')}</div>
            </div>
          </div>
        )}
      </div>

      <div className="btn-group" style={{ marginBottom: 16 }}>
        <button className="btn" onClick={downloadData} disabled={downloading}>
          {downloading ? t('ai.report.downloading') : t('ai.report.downloadButton')}
        </button>
        <button className="btn btn--primary" onClick={generatePrompt}>{t('ai.report.generateButton')}</button>
      </div>

      {prompt ? (
        <div className="field">
          <label className="field__label">{t('ai.report.promptLabel')}</label>
          <textarea className="textarea" readOnly value={prompt} style={{ fontFamily: 'var(--font-mono)', fontSize: 12 }} />
          <button className="btn btn--sm" style={{ marginTop: 8 }} onClick={copy}>{t('ai.report.copyButton')}</button>
        </div>
      ) : null}

      {prompt ? (
        <div className="field">
          <label className="field__label">{t('ai.report.replyLabel')}</label>
          <textarea className="textarea" placeholder={t('ai.report.replyPlaceholder')} value={reply} onChange={(e) => setReply(e.target.value)} />
          <button className="btn btn--primary btn--sm" style={{ marginTop: 8 }} onClick={render}>{t('ai.report.pasteButton')}</button>
        </div>
      ) : null}

      {content ? (
        <div className="field">
          <label className="field__label">{t('ai.report.emptyContent')}</label>
          <div className="markdown" dangerouslySetInnerHTML={{ __html: content }} />
        </div>
      ) : (
        !prompt ? (
          <ol className="ai-steps">
            <li>{t('ai.report.hint1', { file: fileName || t('ai.report.defaultFileName') })}</li>
            <li>{t('ai.report.hint2')}</li>
            <li>{t('ai.report.hint3')}</li>
            <li>{t('ai.report.hint4')}</li>
          </ol>
        ) : null
      )}
    </Dialog>
  );
}
