'use client';

/**
 * Lightweight i18n provider. Reuses the exact locale JSON files from the
 * original app (client/src/locales/*.json) so no translation work is lost.
 *
 * The provider is intentionally tiny — no external i18n dependency — which
 * keeps the client bundle small and the first screen fast.
 */

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { DEFAULT_LOCALE, FALLBACK_LOCALE, SUPPORTED_CODES, STORAGE_KEY, getInitialLocale, type LocaleCode } from './config';

import enUS from './locales/en-US.json';
import idID from './locales/id-ID.json';
import jaJP from './locales/ja-JP.json';
import koKR from './locales/ko-KR.json';
import msMY from './locales/ms-MY.json';
import thTH from './locales/th-TH.json';
import viVN from './locales/vi-VN.json';
import zhCN from './locales/zh-CN.json';
import zhHK from './locales/zh-HK.json';
import zhMO from './locales/zh-MO.json';
import zhSG from './locales/zh-SG.json';
import zhTW from './locales/zh-TW.json';

const MESSAGES: Record<LocaleCode, Record<string, unknown>> = {
  'en-US': enUS as Record<string, unknown>,
  'id-ID': idID as Record<string, unknown>,
  'ja-JP': jaJP as Record<string, unknown>,
  'ko-KR': koKR as Record<string, unknown>,
  'ms-MY': msMY as Record<string, unknown>,
  'th-TH': thTH as Record<string, unknown>,
  'vi-VN': viVN as Record<string, unknown>,
  'zh-CN': zhCN as Record<string, unknown>,
  'zh-HK': zhHK as Record<string, unknown>,
  'zh-MO': zhMO as Record<string, unknown>,
  'zh-SG': zhSG as Record<string, unknown>,
  'zh-TW': zhTW as Record<string, unknown>,
};

export interface TParams {
  [key: string]: string | number;
}

function lookup(messages: Record<string, unknown>, key: string): string | undefined {
  // Dot-path lookup, e.g. "expense.addDialogTitle".
  const parts = key.split('.');
  let node: unknown = messages;
  for (const part of parts) {
    if (node && typeof node === 'object' && part in (node as Record<string, unknown>)) {
      node = (node as Record<string, unknown>)[part];
    } else {
      return undefined;
    }
  }
  return typeof node === 'string' ? node : undefined;
}

function interpolate(template: string, params?: TParams): string {
  if (!params) return template;
  return template.replace(/\{(\w+)\}/g, (match, name: string) => {
    return params[name] !== undefined ? String(params[name]) : match;
  });
}

interface I18nContextValue {
  locale: LocaleCode;
  setLocale: (code: LocaleCode) => void;
  t: (key: string, params?: TParams) => string;
}

const I18nContext = createContext<I18nContextValue | null>(null);

export function I18nProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<LocaleCode>(DEFAULT_LOCALE);

  // On the client, sync with stored / detected language.
  useEffect(() => {
    setLocaleState(getInitialLocale());
  }, []);

  // Reflect the active language on <html lang> for accessibility + SEO.
  useEffect(() => {
    if (typeof document !== 'undefined') {
      document.documentElement.lang = locale;
    }
  }, [locale]);

  const setLocale = useCallback((code: LocaleCode) => {
    if (!(SUPPORTED_CODES as string[]).includes(code)) return;
    setLocaleState(code);
    try {
      window.localStorage.setItem(STORAGE_KEY, code);
      window.localStorage.setItem('appLang', code);
    } catch {
      /* ignore storage errors (private mode, etc.) */
    }
  }, []);

  const t = useCallback(
    (key: string, params?: TParams): string => {
      const primary = lookup(MESSAGES[locale], key);
      const fallback = primary ?? lookup(MESSAGES[FALLBACK_LOCALE], key);
      if (fallback === undefined) return key;
      return interpolate(fallback, params);
    },
    [locale],
  );

  const value = useMemo<I18nContextValue>(() => ({ locale, setLocale, t }), [locale, setLocale, t]);

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18nContextValue {
  const ctx = useContext(I18nContext);
  if (!ctx) {
    // Safe fallback so components never crash when used outside the provider.
    return {
      locale: DEFAULT_LOCALE,
      setLocale: () => {},
      t: (key: string, params?: TParams) => interpolate(key, params),
    };
  }
  return ctx;
}
