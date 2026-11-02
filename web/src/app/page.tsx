'use client';

import { useCallback, useMemo, useState } from 'react';
import { useI18n } from '@/i18n/I18nProvider';
import { usePageMeta } from '@/components/PageMeta';
import { useToast } from '@/components/ui/ToastProvider';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { ExpenseAPI } from '@/lib/expenses';
import type { Expense } from '@/lib/types';
import type { ExpenseDraft } from '@/lib/aiRecord';
import { ExpenseList } from '@/components/expense/ExpenseList';
import { SpendingLimit } from '@/components/expense/SpendingLimit';
import { ExportImport } from '@/components/expense/ExportImport';
import { ExpenseFormDialog, type ExpenseFormValue } from '@/components/expense/ExpenseFormDialog';
import { AiSmartRecord } from '@/components/expense/AiSmartRecord';
import { AiReport } from '@/components/expense/AiReport';
import { MultiRecordDialog } from '@/components/expense/MultiRecordDialog';

const LARGE_EXPENSE_THRESHOLD = 500;

export default function HomePage() {
  const { t } = useI18n();
  const toast = useToast();
  usePageMeta({ title: t('app.title'), showBack: false });

  const [refreshKey, setRefreshKey] = useState(0);
  const [total, setTotal] = useState(0);

  const [addOpen, setAddOpen] = useState(false);
  const [addInitial, setAddInitial] = useState<Expense | undefined>(undefined);
  const [editOpen, setEditOpen] = useState(false);
  const [editing, setEditing] = useState<Expense | null>(null);
  const [deleteId, setDeleteId] = useState<string | null>(null);
  const [largeWarn, setLargeWarn] = useState<null | (() => void)>(null);

  const [aiAddOpen, setAiAddOpen] = useState(false);
  const [aiReportOpen, setAiReportOpen] = useState(false);
  const [multiOpen, setMultiOpen] = useState(false);
  const [multiRecords, setMultiRecords] = useState<ExpenseDraft[]>([]);

  const refresh = () => setRefreshKey((k) => k + 1);

  // Stable callbacks so ExpenseList's data-fetch effect doesn't re-run on every
  // HomePage render (which previously caused "Maximum update depth exceeded").
  const onLoaded = useCallback((n: number) => setTotal(n), []);
  const onDelete = useCallback((id: string) => setDeleteId(id), []);

  // Ask for confirmation when a single expense exceeds the threshold.
  const guardLarge = (amount: number, proceed: () => void) => {
    if (amount > LARGE_EXPENSE_THRESHOLD) {
      setLargeWarn(proceed);
    } else {
      proceed();
    }
  };

  const submitAdd = (value: ExpenseFormValue) => {
    guardLarge(Number(value.amount), async () => {
      try {
        await ExpenseAPI.addExpense({
          type: value.type,
          amount: Number(value.amount),
          date: value.date,
          remark: value.remark,
        });
        toast.show(t('expense.addSuccess', { count: 1 }));
        setAddOpen(false);
        setAddInitial(undefined);
        refresh();
      } catch {
        toast.show(t('expense.updateFailed'), 'error');
      }
    });
  };

  const openEdit = useCallback((exp: Expense) => {
    setEditing(exp);
    setEditOpen(true);
  }, []);

  const submitEdit = (value: ExpenseFormValue) => {
    if (!editing) return;
    guardLarge(Number(value.amount), async () => {
      try {
        await ExpenseAPI.updateExpense(editing.id, {
          type: value.type,
          amount: Number(value.amount),
          date: value.date,
          remark: value.remark,
        });
        toast.show(t('expense.updateSuccess'));
        setEditOpen(false);
        setEditing(null);
        refresh();
      } catch {
        toast.show(t('expense.updateFailed'), 'error');
      }
    });
  };

  const confirmDelete = async () => {
    if (!deleteId) return;
    try {
      await ExpenseAPI.deleteExpense(deleteId);
      toast.show(t('expense.deleteSuccess'));
      refresh();
    } catch {
      toast.show(t('expense.deleteFailed'), 'error');
    } finally {
      setDeleteId(null);
    }
  };

  const onAiParsed = (records: ExpenseDraft[]) => {
    if (records.length === 1) {
      const r = records[0];
      setAddInitial({ id: '', type: r.type, amount: r.amount, date: r.date, remark: r.remark });
      setAddOpen(true);
      toast.show('AI ' + t('ai.smartRecord.submit'));
    } else {
      setMultiRecords(records);
      setMultiOpen(true);
    }
  };

  const submitMulti = async (records: ExpenseDraft[]) => {
    try {
      for (const r of records) {
        await ExpenseAPI.addExpense({ type: r.type, amount: r.amount, date: r.date, remark: r.remark });
      }
      toast.show(`${records.length} ${t('expense.addSuccess', { count: records.length })}`);
      setMultiOpen(false);
      setMultiRecords([]);
      refresh();
    } catch {
      toast.show(t('expense.updateFailed'), 'error');
    }
  };

  const functionGroups = useMemo(
    () => [
      { key: 'primary', items: [
        { label: t('expense.addRecord'), onClick: () => { setAddInitial(undefined); setAddOpen(true); }, icon: '+' },
        { label: t('chart.title'), href: '/charts', icon: '◔' },
      ] },
      { key: 'ai', items: [
        { label: t('ai.smartRecord.title'), onClick: () => setAiAddOpen(true), icon: '✦' },
        { label: t('ai.report.title'), onClick: () => setAiReportOpen(true), icon: '❝' },
      ] },
    ],
    [t],
  );

  return (
    <div className="container page">
      {/* Function groups — desktop grid / mobile selector */}
      <section className="function-grid desktop-only">
        {functionGroups.map((group) => (
          <div className="card" key={group.key}>
            <h2 className="card__title">{t(group.key === 'primary' ? 'function.primary' : 'function.aiFeatures')}</h2>
            <div className="row" style={{ flexWrap: 'wrap', gap: 10 }}>
              {group.items.map((item) => (
                item.href ? (
                  <a key={item.label} href={item.href} className="btn btn--primary btn--lg">
                    <span aria-hidden style={{ fontSize: 16 }}>{item.icon}</span> {item.label}
                  </a>
                ) : (
                  <button key={item.label} className="btn btn--primary btn--lg" onClick={item.onClick}>
                    <span aria-hidden style={{ fontSize: 16 }}>{item.icon}</span> {item.label}
                  </button>
                )
              ))}
            </div>
          </div>
        ))}
      </section>

      <div className="mobile-only section">
        <div className="stack" style={{ gap: 10 }}>
          <button className="btn btn--primary btn--lg btn--block" onClick={() => { setAddInitial(undefined); setAddOpen(true); }}>
            {t('expense.addRecord')}
          </button>
          <a href="/charts" className="btn btn--lg btn--block">{t('chart.title')}</a>
          <button className="btn btn--lg btn--block" onClick={() => setAiAddOpen(true)}>{t('ai.smartRecord.title')}</button>
          <button className="btn btn--lg btn--block" onClick={() => setAiReportOpen(true)}>{t('ai.report.title')}</button>
        </div>
      </div>

      <SpendingLimit refreshKey={refreshKey} />

      <ExpenseList
        refreshKey={refreshKey}
        onEdit={openEdit}
        onDelete={onDelete}
        onLoaded={onLoaded}
      />

      <ExportImport canExport={total > 0} onImported={refresh} />

      {/* Dialogs */}
      <ExpenseFormDialog
        open={addOpen}
        mode="add"
        initial={addInitial}
        onClose={() => { setAddOpen(false); setAddInitial(undefined); }}
        onSubmit={submitAdd}
      />

      <ExpenseFormDialog
        open={editOpen}
        mode="edit"
        initial={editing || undefined}
        onClose={() => { setEditOpen(false); setEditing(null); }}
        onSubmit={submitEdit}
      />

      <ConfirmDialog
        open={deleteId !== null}
        title={t('expense.deleteConfirm')}
        message={t('expense.deleteMessage')}
        danger
        confirmLabel={t('common.delete')}
        onConfirm={confirmDelete}
        onClose={() => setDeleteId(null)}
      />

      <AiSmartRecord open={aiAddOpen} onClose={() => setAiAddOpen(false)} onParsed={onAiParsed} />
      <AiReport open={aiReportOpen} onClose={() => setAiReportOpen(false)} />
      <MultiRecordDialog open={multiOpen} records={multiRecords} onClose={() => setMultiOpen(false)} onSubmit={submitMulti} />

      <ConfirmDialog
        open={largeWarn !== null}
        title={t('expense.largeExpense.warning')}
        message={t('expense.largeExpense.warning')}
        confirmLabel={t('expense.largeExpense.confirm')}
        onConfirm={() => { largeWarn?.(); setLargeWarn(null); }}
        onClose={() => setLargeWarn(null)}
      />
    </div>
  );
}
