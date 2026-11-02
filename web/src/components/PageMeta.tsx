'use client';

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';

interface PageMetaState {
  title: string;
  showBack: boolean;
  backHref: string;
}

interface PageMetaContextValue extends PageMetaState {
  set: (meta: Partial<PageMetaState>) => void;
}

const PageMetaContext = createContext<PageMetaContextValue | null>(null);

export function PageMetaProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<PageMetaState>({
    title: 'Home Finance Tracker',
    showBack: false,
    backHref: '/',
  });

  // Stable setter: functional update means its identity never changes,
  // so consumers depending on `set` won't re-run their effects on every state change.
  const set = useCallback((m: Partial<PageMetaState>) => {
    setState((prev) => ({ ...prev, ...m }));
  }, []);

  const value = useMemo<PageMetaContextValue>(() => ({ ...state, set }), [state, set]);

  return <PageMetaContext.Provider value={value}>{children}</PageMetaContext.Provider>;
}

/** Pages call this to configure the shared header (title, back button). */
export function usePageMeta(meta: Partial<PageMetaState>) {
  const ctx = useContext(PageMetaContext);
  const set = ctx?.set;
  const { title, showBack, backHref } = meta;
  useEffect(() => {
    set?.({ title, showBack, backHref });
  }, [set, title, showBack, backHref]);
}

export function usePageMetaState(): PageMetaContextValue | null {
  return useContext(PageMetaContext);
}
