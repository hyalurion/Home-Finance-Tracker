/**
 * Generic fetch wrapper for the Go backend.
 *
 * The Vue app used axios with a "/api" base proxied to the backend. Here we
 * keep the same relative "/api" base; Next.js rewrites (next.config.mjs)
 * forward it to the Go server in development. No axios dependency is needed.
 */

const BASE = '/api';

export class ApiError extends Error {
  status?: number;
  constructor(message: string, status?: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    headers: { 'Content-Type': 'application/json', ...(init?.headers || {}) },
    ...init,
  });

  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    try {
      const body = await res.json();
      if (body?.message) message = body.message;
    } catch {
      /* ignore parse errors */
    }
    throw new ApiError(message, res.status);
  }

  // Some endpoints return no body (204). Guard against that.
  const contentType = res.headers.get('content-type') || '';
  if (!contentType.includes('application/json')) {
    return undefined as T;
  }
  return (await res.json()) as T;
}

/** Build a query string from a params object, skipping empty values. */
export function buildQuery(params: Record<string, string | number | null | undefined>): string {
  const usp = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== null && value !== undefined && value !== '') {
      usp.append(key, String(value));
    }
  });
  const s = usp.toString();
  return s ? `?${s}` : '';
}

export { BASE, request };
