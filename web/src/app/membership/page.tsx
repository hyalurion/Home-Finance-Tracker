'use client';

import { useRef, useState } from 'react';
import { useI18n } from '@/i18n/I18nProvider';
import { usePageMeta } from '@/components/PageMeta';
import { useUser } from '@/components/UserContext';
import { useToast } from '@/components/ui/ToastProvider';
import { AvatarCropper } from '@/components/avatar/AvatarCropper';
import { MemberAPI } from '@/lib/members';

/** Maximum accepted upload size, in MB (same limit as the original app). */
const MAX_AVATAR_MB = 10;

const DEFAULT_AVATAR =
  'data:image/svg+xml;charset=UTF-8,' +
  encodeURIComponent(
    `<svg xmlns="http://www.w3.org/2000/svg" width="120" height="120" viewBox="0 0 80 80"><circle cx="40" cy="40" r="40" fill="#e5e7eb"/><circle cx="40" cy="31" r="11" fill="#9ca3af"/><path d="M18 62.5 C18 53.4 25.7 47 40 47 C54.3 47 62 53.4 62 62.5 V68 C62 74.2 57.2 78 51 78 H29 C22.8 78 18 74.2 18 68 V62.5 Z" fill="#9ca3af"/></svg>`,
  );

export default function MembershipPage() {
  const { t } = useI18n();
  const { username, isLoggedIn, login, logout, avatar, setAvatar } = useUser();
  const toast = useToast();
  const [name, setName] = useState('');
  const [busy, setBusy] = useState(false);
  const [cropImage, setCropImage] = useState<HTMLImageElement | null>(null);
  const [cropOpen, setCropOpen] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  usePageMeta({ title: t('membership.title'), showBack: true, backHref: '/' });

  const submit = async () => {
    if (!name.trim()) {
      toast.show(t('membership.error.requiredUsername'), 'error');
      return;
    }
    if (!/^[A-Za-z]+$/.test(name.trim())) {
      toast.show(t('membership.error.usernameFormat'), 'error');
      return;
    }
    if (name.trim().length < 1 || name.trim().length > 20) {
      toast.show(t('membership.error.usernameLength'), 'error');
      return;
    }
    setBusy(true);
    try {
      // The backend returns the stored member, including an already uploaded
      // avatar — use it so the header shows the real picture right away.
      const member = await MemberAPI.getOrCreateUser({ username: name.trim() });
      const uname = name.trim();
      login(uname);
      if (member?.avatar) setAvatar(member.avatar, uname);
      toast.show(t('membership.success.login'));
    } catch {
      toast.show(t('membership.error.loginFailed'), 'error');
    } finally {
      setBusy(false);
    }
  };

  // Pick a file, validate it and hand it over to the cropper.
  const onFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (file.size > MAX_AVATAR_MB * 1024 * 1024) {
      toast.show(t('avatar.error.sizeLimit', { maxSize: MAX_AVATAR_MB }), 'error');
      if (fileRef.current) fileRef.current.value = '';
      return;
    }
    if (!file.type.startsWith('image/')) {
      toast.show(t('avatar.error.invalidType'), 'error');
      if (fileRef.current) fileRef.current.value = '';
      return;
    }

    const reader = new FileReader();
    reader.onload = () => {
      const img = new Image();
      img.onload = () => {
        setCropImage(img);
        setCropOpen(true);
      };
      img.onerror = () => toast.show(t('avatar.error.invalidType'), 'error');
      img.src = reader.result as string;
    };
    reader.readAsDataURL(file);

    // Reset the input so picking the same file again still fires onChange.
    if (fileRef.current) fileRef.current.value = '';
  };

  // The cropper handed back the finished square PNG.
  const onCropConfirm = async (dataUrl: string) => {
    setCropOpen(false);
    setCropImage(null);

    setAvatar(dataUrl, username);
    if (!username) return;

    try {
      await MemberAPI.updateAvatar(username, dataUrl);
      toast.show(t('membership.success.avatarUploaded'));
    } catch {
      toast.show(t('avatar.error.uploadFailed'), 'error');
    }
  };

  return (
    <div className="container page" style={{ maxWidth: 560 }}>
      <div className="card">
        <h2 className="card__title">{t('membership.title')}</h2>

        {!isLoggedIn ? (
          <>
            <div className="field">
              <label className="field__label">{t('membership.username')}</label>
              <input
                className="input"
                placeholder={t('membership.usernamePlaceholder')}
                value={name}
                onChange={(e) => setName(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && submit()}
              />
            </div>
            <button className="btn btn--primary btn--block btn--lg" onClick={submit} disabled={busy}>
              {t('membership.loginOrRegister')}
            </button>
          </>
        ) : (
          <>
            <div className="row" style={{ gap: 16, alignItems: 'center', marginBottom: 20 }}>
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={avatar || DEFAULT_AVATAR} alt="avatar" className="profile-avatar" />
              <div>
                <div style={{ fontWeight: 600, fontSize: 18 }}>{username}</div>
                <div className="muted" style={{ fontSize: 13 }}>{t('membership.title')}</div>
              </div>
            </div>
            <input ref={fileRef} type="file" accept="image/*" style={{ display: 'none' }} onChange={onFile} />
            <div className="btn-group">
              <button className="btn" onClick={() => fileRef.current?.click()}>{t('avatar.placeholder')}</button>
              <button className="btn btn--danger" onClick={() => { logout(); toast.show(t('membership.success.logout')); }}>
                {t('membership.logout')}
              </button>
            </div>
          </>
        )}
      </div>

      <AvatarCropper
        open={cropOpen}
        image={cropImage}
        onCancel={() => { setCropOpen(false); setCropImage(null); }}
        onConfirm={onCropConfirm}
      />
    </div>
  );
}
