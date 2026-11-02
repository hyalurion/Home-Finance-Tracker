import type { Metadata, Viewport } from 'next';
import './globals.css';
import { I18nProvider } from '@/i18n/I18nProvider';
import { UserProvider } from '@/components/UserContext';
import { ToastProvider } from '@/components/ui/ToastProvider';
import { PageMetaProvider } from '@/components/PageMeta';
import { HeaderSlot } from '@/components/HeaderSlot';

export const metadata: Metadata = {
  title: {
    default: 'Home Finance Tracker',
    template: '%s · Home Finance Tracker',
  },
  description: 'A minimalist personal finance tracker — record, analyse and manage your spending.',
};

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  themeColor: '#ffffff',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <I18nProvider>
          <UserProvider>
            <ToastProvider>
              <PageMetaProvider>
                <HeaderSlot />
                <main>{children}</main>
              </PageMetaProvider>
            </ToastProvider>
          </UserProvider>
        </I18nProvider>
      </body>
    </html>
  );
}
