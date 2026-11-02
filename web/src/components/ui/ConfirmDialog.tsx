'use client';

import { useI18n } from '@/i18n/I18nProvider';
import { Dialog } from './Dialog';

interface Props {
  open: boolean;
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  danger?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}

export function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel,
  cancelLabel,
  danger,
  onConfirm,
  onClose,
}: Props) {
  const { t } = useI18n();
  return (
    <Dialog
      open={open}
      title={title}
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose}>{cancelLabel || t('common.cancel')}</button>
          <button
            className={danger ? 'btn btn--danger' : 'btn btn--primary'}
            onClick={() => {
              onConfirm();
              onClose();
            }}
          >
            {confirmLabel || t('common.confirm')}
          </button>
        </>
      }
    >
      <p>{message}</p>
    </Dialog>
  );
}
