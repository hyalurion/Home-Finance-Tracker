'use client';

import { useEffect, useRef, useState, type ReactNode } from 'react';

interface DialogProps {
  open: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
  width?: number;
}

/** Must match the exit animation duration declared in globals.css. */
const EXIT_DURATION = 180;

type Phase = 'closed' | 'open' | 'closing';

/**
 * Modal dialog with an enter and an exit animation.
 *
 * The panel is kept mounted while the exit animation plays (phase "closing")
 * and is only unmounted afterwards, so closing no longer snaps away without a
 * transition.
 */
export function Dialog({ open, title, onClose, children, footer, width = 460 }: DialogProps) {
  const [phase, setPhase] = useState<Phase>(open ? 'open' : 'closed');
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Drive the open/close lifecycle. On close we hold the "closing" phase for
  // the length of the exit animation before unmounting.
  useEffect(() => {
    if (open) {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
        timerRef.current = null;
      }
      setPhase('open');
      return;
    }

    setPhase((p) => (p === 'closed' ? 'closed' : 'closing'));
    timerRef.current = setTimeout(() => {
      timerRef.current = null;
      setPhase('closed');
    }, EXIT_DURATION);

    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
        timerRef.current = null;
      }
    };
  }, [open]);

  // Escape to close.
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  // Lock the page behind the dialog while it is on screen.
  useEffect(() => {
    if (!open) return;
    const previous = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = previous;
    };
  }, [open]);

  if (!open && phase === 'closed') return null;

  // While `open` is true we always show the "in" variant; the "out" variant is
  // used only while the exit animation is playing. Switching the animation name
  // restarts the animation, so re-opening always plays the enter transition.
  const entering = open;
  const overlayClass = entering ? 'dialog-overlay--in' : 'dialog-overlay--out';
  const panelClass = entering ? 'dialog--in' : 'dialog--out';

  return (
    <div className={`dialog-overlay ${overlayClass}`} onClick={onClose}>
      <div
        className={`dialog ${panelClass}`}
        style={width ? { maxWidth: width } : undefined}
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        <div className="dialog__header">
          <h3 className="dialog__title">{title}</h3>
          <button className="dialog__close" onClick={onClose} aria-label="Close">
            ×
          </button>
        </div>
        <div className="dialog__body">{children}</div>
        {footer ? <div className="dialog__footer">{footer}</div> : null}
      </div>
    </div>
  );
}
