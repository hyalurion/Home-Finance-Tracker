'use client';

import { useEffect, useMemo, useState } from 'react';
import { useI18n } from '@/i18n/I18nProvider';
import { useUser } from '@/components/UserContext';
import { ExpenseAPI } from '@/lib/expenses';
import { currentMonth, formatMoney, formatMonthLabelByLocale } from '@/lib/format';
import type { Expense } from '@/lib/types';

export function SpendingLimit({ refreshKey }: { refreshKey: number }) {
  const { t, locale } = useI18n();
  const { spending, setSpending } = useUser();
  const [monthTotal, setMonthTotal] = useState(0);
  const [editing, setEditing] = useState(false);
  const [localLimit, setLocalLimit] = useState(String(spending.monthlyLimit));
  const [localThreshold, setLocalThreshold] = useState(String(Math.round(spending.warningThreshold * 100)));

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const all = await ExpenseAPI.getAllExpenses();
        if (cancelled) return;
        const cm = currentMonth();
        const total = all
          .filter((e: Expense) => (e.date || '').startsWith(cm))
          .reduce((sum, e) => sum + Math.abs(Number(e.amount) || 0), 0);
        setMonthTotal(total);
      } catch {
        // Backend unreachable — keep the previous month total; do not throw.
      }
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [refreshKey]);

  useEffect(() => {
    setLocalLimit(String(spending.monthlyLimit));
    setLocalThreshold(String(Math.round(spending.warningThreshold * 100)));
  }, [spending.monthlyLimit, spending.warningThreshold]);

  /**
   * All budget metrics, derived the same way the original Vue app did:
   *  - percentage  = monthTotal / monthlyLimit
   *  - remaining   = monthlyLimit - monthTotal (never negative)
   *  - dailyAverage    = monthTotal / days elapsed this month
   *  - recommendedDaily= remaining / days left this month
   */
  const metrics = useMemo(() => {
    const limit = spending.monthlyLimit;
    const now = new Date();
    const currentDay = now.getDate();
    const daysInMonth = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate();
    const remainingDays = Math.max(daysInMonth - currentDay, 0);

    const remaining = Math.max(limit - monthTotal, 0);
    const overAmount = Math.max(monthTotal - limit, 0);
    const percentage = limit > 0 ? (monthTotal / limit) * 100 : 0;

    const dailyAverage = currentDay > 0 ? monthTotal / currentDay : 0;
    const recommendedDaily = remainingDays > 0 ? remaining / remainingDays : 0;

    const enabled = spending.isLimitEnabled && limit > 0;
    const over = enabled && monthTotal > limit;
    const near = enabled && !over && monthTotal >= limit * spending.warningThreshold;

    return {
      enabled,
      percentage,
      remaining,
      overAmount,
      dailyAverage,
      recommendedDaily,
      remainingDays,
      status: !enabled
        ? ('disabled' as const)
        : over
          ? ('overLimit' as const)
          : near
            ? ('nearLimit' as const)
            : ('normal' as const),
    };
  }, [monthTotal, spending]);

  const save = () => {
    setSpending({
      monthlyLimit: Number(localLimit) || 0,
      warningThreshold: Math.max(10, Math.min(100, Number(localThreshold) || 80)) / 100,
    });
    setEditing(false);
  };

  const barColor = metrics.status === 'overLimit' ? 'var(--danger)' : 'var(--accent)';
  const pctColor = metrics.status === 'overLimit' ? 'var(--danger)' : 'var(--text-muted)';

  // The recommendation is only "comfortable" when it is not far below what is
  // already being spent per day — flag it in red so the user can react.
  const recommendedTight =
    metrics.recommendedDaily > 0 &&
    metrics.dailyAverage > 0 &&
    metrics.recommendedDaily < metrics.dailyAverage * 0.8;

  const monthLabel = formatMonthLabelByLocale(currentMonth(), locale);

  return (
    <section className="card section" aria-label={t('spending.monthlyProgress')}>
      <div className="row" style={{ justifyContent: 'space-between', marginBottom: editing ? 16 : 0 }}>
        <div className="row" style={{ gap: 10, alignItems: 'baseline' }}>
          <h2 className="card__title" style={{ margin: 0 }}>{t('spending.monthlyProgress')}</h2>
          <span className="muted" style={{ fontSize: 13 }}>{monthLabel}</span>
        </div>
        {!editing ? (
          <button className="btn btn--ghost btn--sm" onClick={() => setEditing(true)}>
            {spending.isLimitEnabled ? t('spending.settings.title') : t('spending.enablePrompt.button')}
          </button>
        ) : null}
      </div>

      {!spending.isLimitEnabled && !editing ? (
        <div className="muted" style={{ fontSize: 14 }}>
          {t('spending.status.disabled')}
        </div>
      ) : null}

      {spending.isLimitEnabled && !editing ? (
        <>
          {/* spent / limit + percentage */}
          <div className="row" style={{ justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 10 }}>
            <span className="num" style={{ fontSize: 22, fontWeight: 600 }}>{formatMoney(monthTotal)}</span>
            <span className="muted" style={{ fontSize: 13 }}>/ {formatMoney(spending.monthlyLimit)}</span>
            <span className="num" style={{ fontSize: 14, fontWeight: 600, color: pctColor }}>
              {Math.round(metrics.percentage)}%
            </span>
          </div>

          <div className="progress-track">
            <div
              className="progress-fill"
              style={{ width: `${Math.min(metrics.percentage, 100)}%`, background: barColor }}
            />
          </div>

          <div className="row" style={{ justifyContent: 'space-between', marginTop: 12, fontSize: 14 }}>
            <span className="muted">
              {metrics.status === 'overLimit'
                ? t('spending.status.overLimit')
                : metrics.status === 'nearLimit'
                  ? t('spending.status.nearLimit')
                  : t('spending.status.normal')}
            </span>
            <span className="muted">
              {t('spending.usedPercentage')}: {Math.round(metrics.percentage)}%
            </span>
          </div>

          {/* budget breakdown */}
          <div className="spending-details">
            {metrics.status === 'overLimit' ? (
              <div className="spending-details__row">
                <span className="spending-details__label">{t('spending.exceeded')}</span>
                <span className="num spending-details__value" style={{ color: 'var(--danger)' }}>
                  {formatMoney(metrics.overAmount)}
                </span>
              </div>
            ) : null}

            <div className="spending-details__row">
              <span className="spending-details__label">{t('spending.remainingQuota')}</span>
              <span className="num spending-details__value">{formatMoney(metrics.remaining)}</span>
            </div>

            <div className="spending-details__row">
              <span className="spending-details__label">{t('spending.dailyAverage')}</span>
              <span className="num spending-details__value">{formatMoney(metrics.dailyAverage)}</span>
            </div>

            <div className="spending-details__row">
              <span className="spending-details__label">{t('spending.recommendedDaily')}</span>
              <span
                className="num spending-details__value"
                style={{ color: recommendedTight ? 'var(--danger)' : undefined }}
              >
                {formatMoney(metrics.recommendedDaily)}
              </span>
            </div>
          </div>
        </>
      ) : null}

      {editing ? (
        <>
          <div className="field" style={{ marginTop: 12 }}>
            <label className="field__label">{t('spending.settings.monthlyLimit')}</label>
            <input className="input" type="number" min="0" step="0.01" placeholder={t('spending.settings.enterLimit')} value={localLimit} onChange={(e) => setLocalLimit(e.target.value)} />
          </div>
          <div className="field">
            <label className="field__label">{t('spending.settings.warningThreshold')} (%)</label>
            <input className="input" type="number" min="10" max="100" step="1" placeholder={t('spending.settings.enterThreshold')} value={localThreshold} onChange={(e) => setLocalThreshold(e.target.value)} />
          </div>
          <label className="row" style={{ gap: 8, marginBottom: 16, fontSize: 14 }}>
            <input type="checkbox" checked={spending.isLimitEnabled} onChange={(e) => setSpending({ isLimitEnabled: e.target.checked })} />
            {t('spending.settings.on')}
          </label>
          <div className="row" style={{ justifyContent: 'flex-end', gap: 10 }}>
            <button className="btn" onClick={() => setEditing(false)}>{t('common.cancel')}</button>
            <button className="btn btn--primary" onClick={save}>{t('common.confirm')}</button>
          </div>
        </>
      ) : null}
    </section>
  );
}
