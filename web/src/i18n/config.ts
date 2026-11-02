/**
 * i18n configuration.
 *
 * Preserves the language set and detection logic of the original Vue app
 * (client/src/locales/i18n.js) but without the vue-i18n runtime.
 */

export type LocaleCode =
  | 'en-US'
  | 'id-ID'
  | 'ja-JP'
  | 'ko-KR'
  | 'ms-MY'
  | 'th-TH'
  | 'vi-VN'
  | 'zh-CN'
  | 'zh-HK'
  | 'zh-MO'
  | 'zh-SG'
  | 'zh-TW';

export interface LanguageMeta {
  code: LocaleCode;
  label: string;
  shortLabel: string;
}

export const SUPPORTED_LANGUAGES: LanguageMeta[] = [
  { code: 'en-US', label: 'English', shortLabel: 'EN' },
  { code: 'id-ID', label: 'Indonesia', shortLabel: 'ID' },
  { code: 'ja-JP', label: '日本語', shortLabel: 'JP' },
  { code: 'ko-KR', label: '한국어', shortLabel: 'KO' },
  { code: 'ms-MY', label: 'Melayu', shortLabel: 'MS' },
  { code: 'th-TH', label: 'ไทย', shortLabel: 'TH' },
  { code: 'vi-VN', label: 'Tiếng Việt', shortLabel: 'VI' },
  { code: 'zh-CN', label: '简体中文', shortLabel: 'CN' },
  { code: 'zh-HK', label: '繁體中文(香港)', shortLabel: 'HK' },
  { code: 'zh-MO', label: '繁體中文(澳門)', shortLabel: 'MO' },
  { code: 'zh-SG', label: '简体中文(新加坡)', shortLabel: 'SG' },
  { code: 'zh-TW', label: '繁體中文(台灣)', shortLabel: 'TW' },
];

export const SUPPORTED_CODES: LocaleCode[] = SUPPORTED_LANGUAGES.map((l) => l.code);
export const DEFAULT_LOCALE: LocaleCode = 'en-US';
export const FALLBACK_LOCALE: LocaleCode = 'en-US';
export const STORAGE_KEY = 'hft-lang';

/** Detect the best matching supported locale from a browser language string. */
export function detectLocale(browserLang: string | null | undefined): LocaleCode {
  if (!browserLang) return DEFAULT_LOCALE;
  const lang = browserLang.toLowerCase();

  if ((SUPPORTED_CODES as string[]).includes(lang)) return lang as LocaleCode;

  // Region-aware fallbacks for zh / ja / ko / id / ms / th / vi.
  if (lang.startsWith('zh')) {
    if (lang.includes('tw') || lang.includes('hant')) return 'zh-TW';
    if (lang.includes('hk')) return 'zh-HK';
    if (lang.includes('mo')) return 'zh-MO';
    if (lang.includes('sg')) return 'zh-SG';
    return 'zh-CN';
  }
  if (lang.startsWith('ja')) return 'ja-JP';
  if (lang.startsWith('ko')) return 'ko-KR';
  if (lang.startsWith('id')) return 'id-ID';
  if (lang.startsWith('ms')) return 'ms-MY';
  if (lang.startsWith('th')) return 'th-TH';
  if (lang.startsWith('vi')) return 'vi-VN';

  return DEFAULT_LOCALE;
}

/** Read the persisted locale, falling back to browser detection. */
export function getInitialLocale(): LocaleCode {
  if (typeof window === 'undefined') return DEFAULT_LOCALE;
  const stored = window.localStorage.getItem(STORAGE_KEY);
  if (stored && (SUPPORTED_CODES as string[]).includes(stored)) {
    return stored as LocaleCode;
  }
  return detectLocale(window.navigator.language);
}
