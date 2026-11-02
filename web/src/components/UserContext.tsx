'use client';

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react';
import { MemberAPI } from '@/lib/members';

interface SpendingSettings {
  monthlyLimit: number;
  isLimitEnabled: boolean;
  warningThreshold: number;
}

interface UserContextValue {
  username: string;
  isLoggedIn: boolean;
  login: (username: string) => void;
  logout: () => void;
  avatar: string;
  setAvatar: (base64: string, forUsername?: string) => void;
  spending: SpendingSettings;
  setSpending: (next: Partial<SpendingSettings>) => void;
}

const DEFAULT_SPENDING: SpendingSettings = {
  monthlyLimit: 0,
  isLimitEnabled: false,
  warningThreshold: 0.8,
};

const STORAGE_USER = 'username';
const STORAGE_SPENDING = 'homemoney-spending-settings';

const UserContext = createContext<UserContextValue | null>(null);

export function UserProvider({ children }: { children: ReactNode }) {
  const [username, setUsername] = useState('');
  const [avatar, setAvatarState] = useState('');
  const [spending, setSpendingState] = useState<SpendingSettings>(DEFAULT_SPENDING);

  // Hydrate the profile from localStorage on the client.
  useEffect(() => {
    const u = window.localStorage.getItem(STORAGE_USER) || '';
    setUsername(u);
    const saved = window.localStorage.getItem(STORAGE_SPENDING);
    if (saved) {
      try {
        const s = JSON.parse(saved);
        setSpendingState({
          monthlyLimit: Number(s.monthlyLimit) || 0,
          isLimitEnabled: Boolean(s.isLimitEnabled),
          warningThreshold: Number(s.warningThreshold) || 0.8,
        });
      } catch {
        /* ignore */
      }
    }
  }, []);

  /**
   * Load the avatar of the signed-in user.
   *
   * The locally cached copy is used right away; when it is missing (a fresh
   * browser, cleared cache or a login performed after the app was mounted) the
   * avatar is fetched from the backend and cached again. The effect depends on
   * `username` so logging in also triggers the lookup.
   */
  useEffect(() => {
    if (!username) {
      setAvatarState('');
      return;
    }

    let cancelled = false;
    const local = window.localStorage.getItem(`avatar-${username}`) || '';
    if (local) {
      setAvatarState(local);
      return;
    }

    setAvatarState('');
    MemberAPI.getUser(username)
      .then((member) => {
        if (cancelled) return;
        const remote = member?.avatar || '';
        if (!remote) return;
        setAvatarState(remote);
        window.localStorage.setItem(`avatar-${username}`, remote);
      })
      .catch(() => {
        /* The backend may be unreachable — the placeholder stays in place. */
      });

    return () => {
      cancelled = true;
    };
  }, [username]);

  const login = useCallback((name: string) => {
    const clean = name.trim();
    if (!clean) return;
    window.localStorage.setItem(STORAGE_USER, clean);
    setUsername(clean);
  }, []);

  const logout = useCallback(() => {
    window.localStorage.removeItem(STORAGE_USER);
    setUsername('');
    setAvatarState('');
  }, []);

  const setAvatar = useCallback(
    (base64: string, forUsername?: string) => {
      setAvatarState(base64);
      // `forUsername` allows a caller that just signed in to store the avatar
      // under the right key — the state update for `username` has not been
      // applied yet at that point.
      const key = forUsername || username;
      if (key) window.localStorage.setItem(`avatar-${key}`, base64);
    },
    [username],
  );

  const setSpending = useCallback((next: Partial<SpendingSettings>) => {
    setSpendingState((prev) => {
      const merged = { ...prev, ...next };
      window.localStorage.setItem(STORAGE_SPENDING, JSON.stringify(merged));
      return merged;
    });
  }, []);

  const value: UserContextValue = {
    username,
    isLoggedIn: Boolean(username),
    login,
    logout,
    avatar,
    setAvatar,
    spending,
    setSpending,
  };

  return <UserContext.Provider value={value}>{children}</UserContext.Provider>;
}

export function useUser(): UserContextValue {
  const ctx = useContext(UserContext);
  if (!ctx) {
    return {
      username: '',
      isLoggedIn: false,
      login: () => {},
      logout: () => {},
      avatar: '',
      setAvatar: () => {},
      spending: DEFAULT_SPENDING,
      setSpending: () => {},
    };
  }
  return ctx;
}
