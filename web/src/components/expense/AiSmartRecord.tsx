'use client';

import { useEffect, useState } from 'react';
import { useI18n } from '@/i18n/I18nProvider';
import { Dialog } from '@/components/ui/Dialog';
import { useToast } from '@/components/ui/ToastProvider';
import { buildAddRecordPrompt, parseAiReply, type ExpenseDraft } from '@/lib/aiRecord';

interface Props {
  open: boolean;
  onClose: () => void;
  onParsed: (records: ExpenseDraft[]) => void;
}

export function AiSmartRecord({ open, onClose, onParsed }: Props) {
  const { t } = useI18n();
  const toast = useToast();
  const [prompt, setPrompt] = useState('');
  const [reply, setReply] = useState('');
  const [parsing, setParsing] = useState(false);

  useEffect(() => {
    if (open) {
      setPrompt(buildAddRecordPrompt());
      setReply('');
    }
  }, [open]);

  const copy = async (text: string, label: string) => {
    try {
      await navigator.clipboard.writeText(text);
      toast.show(`${label} ✓`);
    } catch {
      toast.show(`${label}`); // still show; selection fallback is acceptable
    }
  };

  const submit = () => {
    setParsing(true);
    const result = parseAiReply(reply);
    setParsing(false);
    if (!result.ok) {
      toast.show(result.message || t('ai.smartRecord.parsing'), 'error');
      return;
    }
    onParsed(result.records);
    onClose();
  };

  return (
    <Dialog
      open={open}
      title={t('ai.smartRecord.title')}
      onClose={onClose}
      width={520}
      footer={
        <>
          <button className="btn" onClick={onClose}>{t('common.cancel')}</button>
          <button className="btn btn--primary" onClick={submit} disabled={parsing}>
            {parsing ? t('ai.smartRecord.parsing') : t('ai.smartRecord.submit')}
          </button>
        </>
      }
    >
      <ol className="ai-steps">
        <li>{t('ai.smartRecord.step1')}</li>
        <li>{t('ai.smartRecord.step2')}</li>
        <li>{t('ai.smartRecord.step3')}</li>
      </ol>

      <div className="field">
        <label className="field__label">{t('ai.smartRecord.promptLabel')}</label>
        <textarea className="textarea" readOnly value={prompt} style={{ fontFamily: 'var(--font-mono)', fontSize: 12 }} />
        <button className="btn btn--sm" style={{ marginTop: 8 }} onClick={() => copy(prompt, t('ai.smartRecord.copyButton'))}>
          {t('ai.smartRecord.copyButton')}
        </button>
      </div>

      <div className="field">
        <label className="field__label">{t('ai.smartRecord.replyLabel')}</label>
        <textarea
          className="textarea"
          placeholder={t('ai.smartRecord.replyPlaceholder')}
          value={reply}
          onChange={(e) => setReply(e.target.value)}
        />
      </div>
    </Dialog>
  );
}
