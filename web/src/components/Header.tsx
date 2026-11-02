'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';
import { useI18n } from '@/i18n/I18nProvider';
import { SUPPORTED_LANGUAGES } from '@/i18n/config';
import { useUser } from './UserContext';

const NAV = [
  { href: '/', key: 'app.title' },
  { href: '/charts', key: 'chart.title' },
  { href: '/membership', key: 'membership.title' },
];

const DEFAULT_AVATAR =
  'data:image/svg+xml;charset=UTF-8,' +
  encodeURIComponent(
    `<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80" viewBox="0 0 80 80">
      <circle cx="40" cy="40" r="40" fill="#e5e7eb"/>
      <circle cx="40" cy="31" r="11" fill="#9ca3af"/>
      <path d="M18 62.5 C18 53.4 25.7 47 40 47 C54.3 47 62 53.4 62 62.5 V68 C62 74.2 57.2 78 51 78 H29 C22.8 78 18 74.2 18 68 V62.5 Z" fill="#9ca3af"/>
    </svg>`,
  );

export function Header({ title, showBack = false, backHref = '/' }: { title: string; showBack?: boolean; backHref?: string }) {
  const { t, locale, setLocale } = useI18n();
  const { avatar } = useUser();
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  const current = SUPPORTED_LANGUAGES.find((l) => l.code === locale);

  useEffect(() => {
    const onClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('click', onClick);
    return () => document.removeEventListener('click', onClick);
  }, []);

  return (
    <header className="app-header">
      {showBack ? (
        <Link href={backHref} className="icon-btn" aria-label={t('app.backToHome')}>
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M15 18l-6-6 6-6" />
          </svg>
        </Link>
      ) : null}

      <Link href="/" className="app-header__title">{title}</Link>

      <nav className="app-header__spacer desktop-only">
        <div className="row" style={{ gap: 4 }}>
          {NAV.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className={`btn btn--ghost btn--sm ${pathname === item.href ? 'is-active' : ''}`}
              style={pathname === item.href ? { background: 'var(--surface-3)' } : undefined}
            >
              {t(item.key)}
            </Link>
          ))}
        </div>
      </nav>

      <div className="app-header__right">
        <div className="language-dropdown" ref={ref}>
          <button className="btn btn--sm" onClick={() => setOpen((v) => !v)} aria-haspopup="true" aria-expanded={open}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="10" />
              <line x1="2" y1="12" x2="22" y2="12" />
              <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z" />
            </svg>
            <span>{current?.shortLabel}</span>
          </button>
          {open ? (
            <div className="language-menu">
              {SUPPORTED_LANGUAGES.map((l) => (
                <button
                  key={l.code}
                  className={`language-item ${l.code === locale ? 'is-active' : ''}`}
                  onClick={() => {
                    setLocale(l.code);
                    setOpen(false);
                  }}
                >
                  {l.label}
                  {l.code === locale ? <span className="check">✓</span> : null}
                </button>
              ))}
            </div>
          ) : null}
        </div>

        <Link href="/membership" aria-label={t('membership.title')}>
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={avatar || DEFAULT_AVATAR} alt="avatar" className="header-avatar" />
        </Link>
      </div>
    </header>
  );
}
