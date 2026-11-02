'use client';

import dynamic from 'next/dynamic';
import { usePageMeta } from '@/components/PageMeta';
import { useI18n } from '@/i18n/I18nProvider';

// Load the Chart.js bundle only on the client and only when this route is
// visited — keeps it out of the first-screen payload for every other page.
const ChartsClient = dynamic(() => import('@/components/charts/ChartsClient'), {
  ssr: false,
  loading: () => <div className="container page"><p className="muted">Loading…</p></div>,
});

export default function ChartsPage() {
  const { t } = useI18n();
  usePageMeta({ title: t('chart.title'), showBack: true, backHref: '/' });
  return <ChartsClient />;
}
