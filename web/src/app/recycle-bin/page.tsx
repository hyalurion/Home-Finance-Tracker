'use client';

import { useEffect, useMemo, useState } from 'react';
import { useI18n } from '@/i18n/I18nProvider';
import { usePageMeta } from '@/components/PageMeta';
import { useToast } from '@/components/ui/ToastProvider';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { ExpenseAPI } from '@/lib/expenses';
import { formatMoney } from '@/lib/format';
import type { Expense } from '@/lib/types';

const RETENTION_DAYS = 30;

function daysUntilPermanent(deletedAt?: string | null): number | null {
  if (!deletedAt) return null;
  const ts = Number(deletedAt);
  const d = Number.isNaN(ts) ? new Date(deletedAt).getTime() : ts;
  if (Number.isNaN(d)) return null;
  const diff = RETENTION_DAYS - (Date.now() - d) / 86400000;
  return Math.ceil(diff);
}

export default function RecycleBinPage() {
  const { t } = useI18n();
  const toast = useToast();
  usePageMeta({ title: t('recycleBin.title'), showBack: true, backHref: '/' });

  const [items, setItems] = useState<Expense[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState(false);
  const [confirm, setConfirm] = useState<null | 'one' | 'selected' | 'all'>(null);
  const [targetId, setTargetId] = useState<string | null>(null);

  const load = () => {
    ExpenseAPI.getDeletedExpenses()
      .then((res) => setItems(res.expenses))
      .catch(() => setItems([]));
  };

  useEffect(() => {
    load();
  }, []);

  const allSelected = items.length > 0 && selected.size === items.length;
  const selectedCount = selected.size;

  const toggle = (id: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };
  const toggleAll = () => {
    setSelected(allSelected ? new Set() : new Set(items.map((i) => i.id)));
  };

  const selectedIds = useMemo(() => items.filter((i) => selected.has(i.id)).map((i) => i.id), [items, selected]);

  const doRestore = async (ids: string[]) => {
    setBusy(true);
    try {
      if (ids.length === 1) await ExpenseAPI.restoreExpense(ids[0]);
      else await ExpenseAPI.restoreExpensesBatch(ids);
      toast.show(t('recycleBin.restoreSuccess'));
      setSelected(new Set());
      load();
    } catch {
      toast.show(t('recycleBin.operationFailed'), 'error');
    } finally {
      setBusy(false);
    }
  };

  const doPermanent = async (ids: string[]) => {
    setBusy(true);
    try {
      if (ids.length === 1) await ExpenseAPI.hardDeleteExpense(ids[0]);
      else await ExpenseAPI.permanentDeleteBatch(ids);
      toast.show(t('recycleBin.deleteSuccess'));
      setSelected(new Set());
      load();
    } catch {
      toast.show(t('recycleBin.operationFailed'), 'error');
    } finally {
      setBusy(false);
    }
  };

  const doRestoreAll = async () => {
    setBusy(true);
    try {
      await ExpenseAPI.restoreAllExpenses();
      toast.show(t('recycleBin.restoreAllSuccess'));
      setSelected(new Set());
      load();
    } catch {
      toast.show(t('recycleBin.operationFailed'), 'error');
    } finally {
      setBusy(false);
    }
  };

  const doEmptyAll = async () => {
    setBusy(true);
    try {
      await ExpenseAPI.permanentDeleteAll();
      toast.show(t('recycleBin.deleteAllSuccess'));
      setSelected(new Set());
      load();
    } catch {
      toast.show(t('recycleBin.operationFailed'), 'error');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="container page">
      {items.length === 0 ? (
        <div className="empty-state section">
          <h3>{t('recycleBin.empty')}</h3>
          <p>{t('recycleBin.emptyHint')}</p>
        </div>
      ) : (
        <>
          <div className="row section" style={{ justifyContent: 'space-between', flexWrap: 'wrap', gap: 10 }}>
            <label className="row" style={{ gap: 8 }}>
              <input type="checkbox" checked={allSelected} onChange={toggleAll} />
              {selectedCount > 0 ? t('recycleBin.selectedCount', { count: selectedCount }) : t('recycleBin.totalCount', { count: items.length })}
            </label>
            <div className="btn-group">
              <button className="btn btn--sm" onClick={() => doRestore(selectedIds)} disabled={selectedCount === 0 || busy}>{t('recycleBin.restoreSelected')}</button>
              <button className="btn btn--sm btn--danger" onClick={() => { setConfirm('selected'); }} disabled={selectedCount === 0 || busy}>{t('recycleBin.deleteSelected')}</button>
              <button className="btn btn--sm" onClick={doRestoreAll} disabled={busy}>{t('recycleBin.restoreAll')}</button>
              <button className="btn btn--sm btn--danger" onClick={() => setConfirm('all')} disabled={busy}>{t('recycleBin.emptyBin')}</button>
            </div>
          </div>

          <div className="table-scroll">
            <table className="table">
              <thead>
                <tr>
                  <th style={{ width: 40 }}></th>
                  <th>{t('common.date')}</th>
                  <th>{t('expense.columns.name')}</th>
                  <th className="num">{t('expense.amount')}</th>
                  <th>{t('recycleBin.deletedAt')}</th>
                  <th>{t('recycleBin.expiresIn', { days: '—' }).split('{days}')[0]}</th>
                  <th style={{ textAlign: 'right' }}>{t('common.action')}</th>
                </tr>
              </thead>
              <tbody>
                {items.map((it) => {
                  const left = daysUntilPermanent(it.deletedAt);
                  return (
                    <tr key={it.id}>
                      <td><input type="checkbox" checked={selected.has(it.id)} onChange={() => toggle(it.id)} /></td>
                      <td className="faint">{it.date}</td>
                      <td><span className="tag">{it.type}</span></td>
                      <td className="num">{formatMoney(it.amount)}</td>
                      <td className="muted">{it.deletedAt ? new Date(Number(it.deletedAt) || it.deletedAt).toLocaleString() : '—'}</td>
                      <td className="muted">
                        {left === null ? '—' : left <= 0 ? t('recycleBin.expiredToday') : t('recycleBin.expiresIn', { days: left })}
                      </td>
                      <td>
                        <div className="table__actions">
                          <button className="btn btn--sm" onClick={() => doRestore([it.id])} disabled={busy}>{t('recycleBin.restore')}</button>
                          <button className="btn btn--sm btn--danger" onClick={() => { setTargetId(it.id); setConfirm('one'); }} disabled={busy}>{t('recycleBin.permanentDelete')}</button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </>
      )}

      <ConfirmDialog
        open={confirm === 'one'}
        title={t('recycleBin.permanentDeleteTitle')}
        message={t('recycleBin.permanentDeleteMessage')}
        danger
        confirmLabel={t('common.delete')}
        onConfirm={() => { if (targetId) doPermanent([targetId]); setConfirm(null); setTargetId(null); }}
        onClose={() => { setConfirm(null); setTargetId(null); }}
      />
      <ConfirmDialog
        open={confirm === 'selected'}
        title={t('recycleBin.deleteSelectedTitle')}
        message={t('recycleBin.deleteSelectedMessage', { count: selectedCount })}
        danger
        confirmLabel={t('common.delete')}
        onConfirm={() => { doPermanent(selectedIds); setConfirm(null); }}
        onClose={() => setConfirm(null)}
      />
      <ConfirmDialog
        open={confirm === 'all'}
        title={t('recycleBin.deleteAllTitle')}
        message={t('recycleBin.deleteAllMessage', { count: items.length })}
        danger
        confirmLabel={t('common.delete')}
        onConfirm={() => { doEmptyAll(); setConfirm(null); }}
        onClose={() => setConfirm(null)}
      />
    </div>
  );
}
