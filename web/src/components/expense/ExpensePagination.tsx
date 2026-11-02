'use client';

import { useI18n } from '@/i18n/I18nProvider';

interface Props {
  currentPage: number;
  totalPages: number;
  onChange: (page: number) => void;
}

export function ExpensePagination({ currentPage, totalPages, onChange }: Props) {
  const { t } = useI18n();
  if (totalPages <= 1) return null;

  const maxVisible = typeof window !== 'undefined' && window.innerWidth < 768 ? 3 : 7;
  const pages: (number | '...')[] = [];
  const half = Math.floor(maxVisible / 2);
  const start = Math.max(1, currentPage - half);
  const end = Math.min(totalPages, start + maxVisible - 1);

  if (start > 1) {
    pages.push(1);
    if (start > 2) pages.push('...');
  }
  for (let i = Math.max(1, start); i <= end; i += 1) pages.push(i);
  if (end < totalPages) {
    if (end < totalPages - 1) pages.push('...');
    pages.push(totalPages);
  }

  return (
    <div className="pagination section" role="navigation" aria-label="Pagination">
      <button className="btn btn--sm" disabled={currentPage <= 1} onClick={() => onChange(currentPage - 1)}>
        {t('app.previousPage')}
      </button>
      <div className="pagination__pages">
        {pages.map((p, idx) =>
          p === '...' ? (
            <span key={`e${idx}`} className="pagination__ellipsis">…</span>
          ) : (
            <button
              key={p}
              className={`pagination__page ${p === currentPage ? 'is-active' : ''}`}
              onClick={() => onChange(p)}
              aria-current={p === currentPage ? 'page' : undefined}
            >
              {p}
            </button>
          ),
        )}
      </div>
      <button className="btn btn--sm" disabled={currentPage >= totalPages} onClick={() => onChange(currentPage + 1)}>
        {t('app.nextPage')}
      </button>
    </div>
  );
}
