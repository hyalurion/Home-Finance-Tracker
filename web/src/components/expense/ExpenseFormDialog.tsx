'use client';

import { useEffect, useState } from 'react';
import { useI18n } from '@/i18n/I18nProvider';
import { Dialog } from '@/components/ui/Dialog';
import { Select } from '@/components/ui/Select';
import { EXPENSE_TYPES, type Expense } from '@/lib/types';

export interface ExpenseFormValue {
  type: string;
  amount: string;
  date: string;
  remark: string;
}

interface Props {
  open: boolean;
  mode: 'add' | 'edit';
  initial?: Expense;
  onClose: () => void;
  onSubmit: (value: ExpenseFormValue) => void;
}

export function ExpenseFormDialog({ open, mode, initial, onClose, onSubmit }: Props) {
  const { t } = useI18n();
  const [type, setType] = useState('');
  const [amount, setAmount] = useState('');
  const [date, setDate] = useState('');
  const [remark, setRemark] = useState('');
  const [errors, setErrors] = useState<{ type?: string; amount?: string; date?: string }>({});

  // Reset form whenever the dialog opens.
  useEffect(() => {
    if (!open) return;
    setType(initial?.type || '');
    setAmount(initial?.amount != null ? String(initial.amount) : '');
    setDate(initial?.date || new Date().toISOString().slice(0, 10));
    setRemark(initial?.remark || '');
    setErrors({});
  }, [open, initial]);

  const validate = (): boolean => {
    const e: { type?: string; amount?: string; date?: string } = {};
    if (!type) e.type = t('expense.selectType');
    if (!amount) {
      e.amount = t('expense.inputAmount');
    } else {
      const a = Number(String(amount).replace(',', '.'));
      if (Number.isNaN(a) || a <= 0 || !/^\d+(\.\d{1,2})?$/.test(amount)) {
        e.amount = t('expense.invalidAmountFormat');
      }
    }
    if (!date) e.date = t('expense.selectDate');
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const submit = () => {
    if (!validate()) return;
    onSubmit({ type, amount, date, remark });
  };

  const typeOptions = EXPENSE_TYPES.map((tp) => ({ value: tp, label: tp }));

  return (
    <Dialog
      open={open}
      title={mode === 'add' ? t('expense.addDialogTitle') : t('expense.edit')}
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose}>{t('common.cancel')}</button>
          <button className="btn btn--primary" onClick={submit}>{t('common.confirm')}</button>
        </>
      }
    >
      <div className="field">
        <label className={`field__label ${errors.type ? 'field__label--error' : ''}`}>{t('expense.type')}</label>
        <Select value={type} onChange={setType} options={typeOptions} emptyOptionLabel={t('expense.selectType')} className={errors.type ? 'select--error' : ''} />
        {errors.type ? <span className="field__error">{errors.type}</span> : null}
      </div>

      <div className="field">
        <label className={`field__label ${errors.amount ? 'field__label--error' : ''}`}>{t('expense.amount')}</label>
        <input
          className={`input ${errors.amount ? 'input--error' : ''}`}
          type="number"
          step="0.01"
          min="0"
          placeholder="0"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
        />
        {errors.amount ? <span className="field__error">{errors.amount}</span> : null}
      </div>

      <div className="field">
        <label className={`field__label ${errors.date ? 'field__label--error' : ''}`}>{t('expense.date')}</label>
        <input
          className={`input ${errors.date ? 'input--error' : ''}`}
          type="date"
          value={date}
          onChange={(e) => setDate(e.target.value)}
        />
        {errors.date ? <span className="field__error">{errors.date}</span> : null}
      </div>

      <div className="field">
        <label className="field__label">{t('expense.remark')}</label>
        <textarea
          className="textarea"
          placeholder={t('expense.enterRemark')}
          value={remark}
          onChange={(e) => setRemark(e.target.value)}
        />
      </div>
    </Dialog>
  );
}
