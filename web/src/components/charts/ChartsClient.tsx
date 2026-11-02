'use client';

import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  BarElement,
  PointElement,
  LineElement,
  ArcElement,
  RadialLinearScale,
  Tooltip,
  Legend,
  Filler,
} from 'chart.js';
import { Bar, Line, Doughnut, Radar } from 'react-chartjs-2';
import { useEffect, useMemo, useState } from 'react';
import { useI18n } from '@/i18n/I18nProvider';
import { ExpenseAPI } from '@/lib/expenses';
import { formatDateShortByLocale, formatMoney } from '@/lib/format';
import type { Expense } from '@/lib/types';

ChartJS.register(
  CategoryScale,
  LinearScale,
  BarElement,
  PointElement,
  LineElement,
  ArcElement,
  RadialLinearScale,
  Tooltip,
  Legend,
  Filler,
);

// Categorical palette for the charts. The page itself stays strictly
// monochrome; the charts are the one place where colour carries meaning, so
// the hues are muted, evenly separated and legible on both the light and the
// dark theme.
const PALETTE = [
  '#3d6fa5', // steel blue
  '#c4763f', // terracotta
  '#4f9263', // sage
  '#b4526c', // rose
  '#7c63ab', // violet
  '#2f8f96', // teal
  '#a8873a', // ochre
  '#6b7280', // slate
];

/** Single-series accent (trend line / radar). */
const ACCENT = '#3d6fa5';

const isDark = () =>
  typeof window !== 'undefined' && !!window.matchMedia?.('(prefers-color-scheme: dark)').matches;

const axisColor = () => (isDark() ? '#a1a1aa' : '#6b7280');
const gridColor = () => (isDark() ? '#262626' : '#e5e7eb');
const surfaceColor = () => (isDark() ? '#0a0a0a' : '#ffffff');

export default function ChartsClient() {
  const { t, locale } = useI18n();
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const [errorMsg, setErrorMsg] = useState('');
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setStatus('loading');
    setErrorMsg('');

    ExpenseAPI.getAllExpenses()
      .then((list) => {
        if (cancelled) return;
        setExpenses(list);
        setStatus('ready');
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        // Surface the real reason instead of silently rendering an empty state.
        const msg = err instanceof Error ? err.message : String(err);
        setErrorMsg(msg);
        setExpenses([]);
        setStatus('error');
      });

    return () => {
      cancelled = true;
    };
  }, [reloadKey]);

  const { byType, byDate, byWeekday } = useMemo(() => {
    const typeMap = new Map<string, number>();
    const dateMap = new Map<string, number>();
    const weekday = new Array(7).fill(0);
    for (const e of expenses) {
      const amt = Math.abs(Number(e.amount) || 0);
      typeMap.set(e.type, (typeMap.get(e.type) || 0) + amt);
      dateMap.set(e.date, (dateMap.get(e.date) || 0) + amt);
      const d = new Date(e.date);
      if (!Number.isNaN(d.getTime())) weekday[d.getDay()] += amt;
    }
    const byType = [...typeMap.entries()].sort((a, b) => b[1] - a[1]);
    const byDate = [...dateMap.entries()].sort((a, b) => (a[0] < b[0] ? -1 : 1));
    return { byType, byDate, byWeekday: weekday };
  }, [expenses]);

  const moneyTooltip = {
    callbacks: {
      label: (ctx: { parsed?: { y?: number | null; x?: number | null } }) =>
        formatMoney((ctx.parsed?.y ?? ctx.parsed?.x) ?? 0),
    },
  };

  const baseOpts: any = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false }, tooltip: moneyTooltip },
    scales: {
      x: { ticks: { color: axisColor() }, grid: { color: gridColor() } },
      y: { ticks: { color: axisColor() }, grid: { color: gridColor() }, beginAtZero: true },
    },
  };

  const barData = {
    labels: byType.map(([type]) => type),
    datasets: [
      {
        label: t('chart.categoryAnalysis'),
        data: byType.map(([, sum]) => sum),
        // One colour per category, matching the doughnut chart below so the
        // two views of the same data read identically.
        backgroundColor: byType.map((_, i) => PALETTE[i % PALETTE.length]),
        borderRadius: 4,
        borderSkipped: false,
      },
    ],
  };

  const doughnutData = {
    labels: byType.map(([type]) => type),
    datasets: [
      {
        data: byType.map(([, sum]) => sum),
        backgroundColor: byType.map((_, i) => PALETTE[i % PALETTE.length]),
        borderColor: surfaceColor(),
        borderWidth: 2,
        hoverOffset: 6,
      },
    ],
  };

  const lineData = {
    labels: byDate.map(([date]) => formatDateShortByLocale(date, locale)),
    datasets: [
      {
        label: t('chart.trendAnalysis'),
        data: byDate.map(([, sum]) => sum),
        borderColor: ACCENT,
        backgroundColor: 'rgba(61, 111, 165, 0.14)',
        fill: true,
        tension: 0.3,
        pointRadius: 2,
        pointBackgroundColor: ACCENT,
        pointHoverRadius: 4,
        borderWidth: 2,
      },
    ],
  };

  const radarData = {
    labels: [
      t('common.sunday'), t('common.monday'), t('common.tuesday'), t('common.wednesday'),
      t('common.thursday'), t('common.friday'), t('common.saturday'),
    ],
    datasets: [
      {
        label: t('chart.weekdayAnalysis'),
        data: byWeekday,
        borderColor: ACCENT,
        backgroundColor: 'rgba(61, 111, 165, 0.18)',
        pointBackgroundColor: ACCENT,
        borderWidth: 2,
        fill: true,
      },
    ],
  };

  const radarOpts: any = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false }, tooltip: moneyTooltip },
    scales: {
      r: {
        ticks: { color: axisColor(), backdropColor: 'transparent' },
        grid: { color: gridColor() },
        angleLines: { color: gridColor() },
        beginAtZero: true,
      },
    },
  };

  if (status === 'loading') {
    return (
      <div className="container page">
        <div className="empty-state section">
          <p className="muted">{t('app.loading')}</p>
        </div>
      </div>
    );
  }

  if (status === 'error') {
    return (
      <div className="container page">
        <div className="empty-state section">
          <h3>{t('common.loadFailed')}</h3>
          {errorMsg ? <p className="muted">{errorMsg}</p> : null}
          <button className="btn btn--primary" onClick={() => setReloadKey((k) => k + 1)}>
            {t('common.retry')}
          </button>
        </div>
      </div>
    );
  }

  if (expenses.length === 0) {
    return (
      <div className="container page">
        <div className="empty-state section">
          <h3>{t('expense.noRecords')}</h3>
          <p>{t('expense.noDataMessage')}</p>
          <button className="btn" onClick={() => setReloadKey((k) => k + 1)}>
            {t('common.retry')}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="container page">
      <div className="charts-grid">
        <ChartCard title={t('chart.categoryAnalysis')}>
          <div className="chart-box"><Bar data={barData} options={baseOpts} /></div>
        </ChartCard>
        <ChartCard title={t('chart.categoryPercentage')}>
          <div className="chart-box">
            <Doughnut
              data={doughnutData}
              options={{
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                  legend: {
                    position: 'right' as const,
                    labels: {
                      color: axisColor(),
                      boxWidth: 12,
                      boxHeight: 12,
                      padding: 12,
                      usePointStyle: true,
                      pointStyle: 'circle',
                    },
                  },
                  tooltip: {
                    callbacks: {
                      label: (ctx: { parsed?: number; label?: string }) =>
                        `${ctx.label ?? ''}: ${formatMoney(ctx.parsed ?? 0)}`,
                    },
                  },
                },
              }}
            />
          </div>
        </ChartCard>
        <ChartCard title={t('chart.trendAnalysis')}>
          <div className="chart-box"><Line data={lineData} options={baseOpts} /></div>
        </ChartCard>
        <ChartCard title={t('chart.weekdayAnalysis')}>
          <div className="chart-box"><Radar data={radarData} options={radarOpts} /></div>
        </ChartCard>
      </div>
    </div>
  );
}

function ChartCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="card">
      <h2 className="card__title">{title}</h2>
      {children}
    </section>
  );
}
