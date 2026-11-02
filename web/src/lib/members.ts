/**
 * Membership API client (get-or-create user + avatar).
 *
 * The Go backend mounts the member routes under the "/api/members" group and
 * registers them with an extra "/members" segment:
 *
 *   POST /api/members/members              -> get or create
 *   GET  /api/members/members/:username    -> read
 *   PUT  /api/members/members/:username/avatar -> update avatar
 *
 * The Android client (MemberApi.kt) calls the very same paths, so these are
 * the canonical ones. Every handler answers with a { success, data } envelope
 * which is unwrapped here.
 */

import { request } from './api';
import type { MemberUser } from './types';

/** Unwrap the { success, data } envelope used by the member handlers. */
async function requestMember<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await request<{ success?: boolean; data?: T }>(path, init);
  if (res && typeof res === 'object' && 'data' in res) {
    return res.data as T;
  }
  return res as unknown as T;
}

export const MemberAPI = {
  async getOrCreateUser(userData: { username: string }): Promise<MemberUser> {
    return requestMember<MemberUser>('/members/members', {
      method: 'POST',
      body: JSON.stringify(userData),
    });
  },

  async getUser(username: string): Promise<MemberUser> {
    return requestMember<MemberUser>(`/members/members/${encodeURIComponent(username)}`);
  },

  async updateAvatar(username: string, avatar: string): Promise<unknown> {
    return requestMember(`/members/members/${encodeURIComponent(username)}/avatar`, {
      method: 'PUT',
      body: JSON.stringify({ avatar }),
    });
  },
};
