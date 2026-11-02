'use client';

import { useEffect, useState } from 'react';
import { useI18n } from '@/i18n/I18nProvider';
import { Dialog } from '@/components/ui/Dialog';
import { Select } from '@/components/ui/Select';
import { EXPENSE_TYPES } from '@/lib/types';
import type { ExpenseDraft } from '@/lib/aiRecord';

interface Props {
  open: boolean;
  records: ExpenseDraft[];
  onClose: () => void;
  onSubmit: (records: ExpenseDraft[]) => void;
}

const typeOptions = EXPENSE_TYPES.map((tp) => ({ value: tp, label: tp }));

export function MultiRecordDialog({ open, records, onClose, onSubmit }: Props) {
  const { t } = useI18n();
  const [items, setItems] = useState<ExpenseDraft[]>([]);

  useEffect(() => {
    if (open) setItems(records.map((r) => ({ ...r })));
  }, [open, records]);

  const allSelected = items.length > 0 && items.every((r) => r.selected);
  const selectedCount = items.filter((r) => r.selected).length;

  const toggleAll = (checked: boolean) => {
    setItems((prev) => prev.map((r) => ({ ...r, selected: checked })));
  };
  const update = (idx: number, key: keyof ExpenseDraft, value: string) => {
    setItems((prev) => prev.map((r, i) => (i === idx ? { ...r, [key]: value } : r)));
  };

  return (
    <Dialog
      open={open}
      title={t('ai.smartRecord.title')}
      onClose={onClose}
      width={560}
      footer={
        <>
          <button className="btn" onClick={onClose}>{t('common.cancel')}</button>
          <button className="btn btn--primary" onClick={() => onSubmit(items.filter((r) => r.selected))} disabled={selectedCount === 0}>
            {t('common.submit')} ({selectedCount}/{items.length})
          </button>
        </>
      }
    >
      {items.length === 0 ? (
        <p className="muted">{t('expense.noRecords')}</p>
      ) : (
        <>
          <label className="row" style={{ gap: 8, marginBottom: 16, fontSize: 14 }}>
            <input type="checkbox" checked={allSelected} onChange={(e) => toggleAll(e.target.checked)} />
            {t('common.selectAll')}
          </label>
          <div className="stack" style={{ gap: 12 }}>
            {items.map((rec, idx) => (
              <div className="record-card" key={idx}>
                <div className="row" style={{ justifyContent: 'space-between', marginBottom: 8 }}>
                  <label className="row" style={{ gap: 8, fontSize: 14 }}>
                    <input type="checkbox" checked={rec.selected} onChange={(e) => update(idx, 'selected', e.target.checked ? 'true' : 'false')} />
                    {t('expense.record')} {idx + 1}
                  </label>
                </div>
                <div className="search-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))' }}>
                  <Select value={rec.type} onChange={(v) => update(idx, 'type', v)} options={typeOptions} />
                  <input className="input" type="text" placeholder="0" value={rec.amount} onChange={(e) => update(idx, 'amount', e.target.value)} />
                  <input className="input" type="date" value={rec.date} onChange={(e) => update(idx, 'date', e.target.value)} />
                </div>
                <input className="input" style={{ marginTop: 8 }} placeholder={t('expense.enterRemark')} value={rec.remark} onChange={(e) => update(idx, 'remark', e.target.value)} />
              </div>
            ))}
          </div>
        </>
      )}
    </Dialog>
  );
}
