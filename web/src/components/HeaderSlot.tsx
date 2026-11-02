'use client';

import { usePageMetaState } from '@/components/PageMeta';
import { Header } from '@/components/Header';

/**
 * Bridges the shared PageMeta context (set by individual pages via usePageMeta)
 * into the persistent header rendered by the root layout. Must be a Client
 * Component because it reads a client context during SSR/prerender.
 */
export function HeaderSlot() {
  const meta = usePageMetaState();
  if (!meta) return null;
  return (
    <Header title={meta.title} showBack={meta.showBack} backHref={meta.backHref} />
  );
}
