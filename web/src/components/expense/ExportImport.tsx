'use client';

import { useRef } from 'react';
import Link from 'next/link';
import { useI18n } from '@/i18n/I18nProvider';
import { useToast } from '@/components/ui/ToastProvider';

interface Props {
  canExport: boolean;
  onImported: () => void;
}

export function ExportImport({ canExport, onImported }: Props) {
  const { t, locale } = useI18n();
  const toast = useToast();
  const fileRef = useRef<HTMLInputElement>(null);

  const exportExcel = async () => {
    try {
      const res = await fetch(`/api/export/excel?lang=${locale}`);
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      const now = new Date();
      const ts = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}${String(now.getHours()).padStart(2, '0')}${String(now.getMinutes()).padStart(2, '0')}${String(now.getSeconds()).padStart(2, '0')}`;
      a.download = `expenses_${ts}.xlsx`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch {
      toast.show(t('export.failed'), 'error');
    }
  };

  const onFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const form = new FormData();
    form.append('file', file);
    try {
      const res = await fetch('/api/import/excel', { method: 'POST', body: form });
      if (!res.ok) throw new Error('HTTP ' + res.status);
      toast.show(t('import.success'));
      onImported();
    } catch {
      toast.show(t('import.failed'), 'error');
    } finally {
      if (fileRef.current) fileRef.current.value = '';
    }
  };

  return (
    <div className="btn-group section" role="group" aria-label="Data actions">
      <input ref={fileRef} type="file" accept=".xlsx,.xls" style={{ display: 'none' }} onChange={onFile} />
      <button className="btn" onClick={() => fileRef.current?.click()}>{t('import.title')}</button>
      <button className="btn btn--primary" onClick={exportExcel} disabled={!canExport}>{t('export.allData')}</button>
      <Link href="/recycle-bin" className="btn">{t('common.trash')}</Link>
    </div>
  );
}
