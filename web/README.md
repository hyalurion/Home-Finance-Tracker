# Home Finance Tracker — Web Frontend

A modern, minimalist **black-and-white** personal finance tracker. This is the
Next.js reimplementation of the original Vue 3 + Vite SPA, rebuilt to improve
maintainability, design clarity, responsiveness, and first-screen performance.

The app is exported as **static files** (`web/out/`) and served directly by the
Go backend, so a release is one binary plus a folder of assets — no Node.js
runtime is needed on the server.

---

## What changed vs. the original SPA

| Concern | Before (Vue 3 + Vite) | After (Next.js 16, App Router) |
| --- | --- | --- |
| Framework | Vue 3 SPA, client-only | Next.js 16 App Router, React 19, TypeScript |
| Styling | Glassmorphism / liquid-glass, green accent | Monochrome design system — hairline borders, whitespace, no blur |
| i18n | vue-i18n + 12 locale files | Lightweight custom `I18nProvider` (same 12 locale JSON files reused) |
| HTTP | axios | Native `fetch` wrapper (`lib/api.ts`) |
| Charts | bundled eagerly | `chart.js` + `react-chartjs-2`, dynamically imported (`ssr: false`) |
| Build | Vite | Turbopack (Next build) — static prerender of all routes |
| Routing | vue-router | File-based routing under `src/app` |

The data contract with the Go backend (`server-go`, served at `/api`) is
preserved exactly: same endpoints, same request/response shapes, same soft-delete
recycle-bin model.

---

## Design system (Requirement 2)

A single token file (`src/app/globals.css`) drives the whole look:

- **Monochrome palette** — near-black `#0a0a0a` text/accents on white `#fff`;
  a restrained red `#b42318` is the only chromatic color and is reserved for
  destructive actions.
- **Automatic dark mode** via `prefers-color-scheme` (no toggle needed).
- **No decorative glass** — the sticky header uses a solid `var(--bg)`
  background and a 1px hairline border instead of `backdrop-filter` blur.
- **Typography & hierarchy** — system font stack, tight letter-spacing on
  headings, clear spacing scale, cards delimited by borders rather than heavy
  shadows.
- **Responsive** — a single `768px` breakpoint collapses nav, grids, and tables
  to mobile layouts.

---

## Project structure

```
web/
├── next.config.mjs          # static export + dev /api rewrite (API_TARGET)
├── src/
│   ├── app/
│   │   ├── layout.tsx       # Root layout: providers + persistent header
│   │   ├── page.tsx         # Home: search, stats, table, form, AI, import/export
│   │   ├── charts/page.tsx  # Charts (client-only, lazy)
│   │   ├── membership/      # Login/register + avatar cropper
│   │   ├── recycle-bin/     # Soft-delete recovery + permanent purge
│   │   ├── not-found.tsx    # 404
│   │   └── globals.css      # Monochrome design tokens + primitives
│   ├── components/
│   │   ├── Header.tsx, HeaderSlot.tsx, UserContext.tsx, PageMeta.tsx
│   │   ├── ui/              # ToastProvider, Dialog, Select, ConfirmDialog
│   │   ├── expense/         # ExpenseSearch, ExpenseStats, ExpenseTable, …
│   │   ├── avatar/          # AvatarCropper (canvas, zoom + pan)
│   │   └── charts/ChartsClient.tsx
│   ├── i18n/                # I18nProvider + 12 locale JSON files
│   └── lib/                 # types, api, expenses, members, aiRecord, format, urlState
└── out/                     # static export consumed by the Go server
```

---

## Getting started

### Prerequisites
- Node.js (the project is built/tested on Node 20+)
- The Go backend running on `http://localhost:3010` (or override `API_TARGET`)

### Install
```bash
cd web
npm install
```

### Develop
```bash
npm run dev
# http://localhost:3000
```

### Build & run in production

```bash
# Static export — this is what the Go backend serves
npm run build:static      # -> web/out/

# Or as a standalone Next.js server (proxies /api to the backend)
npm run build
npm run start
# http://localhost:3000
```

### Deploy with the Go backend

```bash
cd web && npm ci && npm run build:static
cd ../server-go && go build -o server.exe ./cmd/server && ./server.exe
# http://localhost:3010  -> serves both the API and the frontend
```

The backend looks for the export in `../web/out` (running from `server-go/`)
and `web/out` (running from a release package root); `WEB_DIST` overrides it.

### Point at a different backend
```bash
API_TARGET=http://192.168.1.41:3010 npm run dev
```

In development all `/api/*` requests are rewritten server-side to the backend,
so the browser always uses a relative base. In the exported build the frontend
and the API share one origin, so the calls are plain same-origin requests.

---

## Internationalization

12 languages are supported and reused verbatim from the original locale files
(`src/i18n/locales/*.json`). Locale is detected on first visit (browser +
stored preference) and can be switched from the header dropdown. Translation
lookups use dot-paths with `{param}` interpolation and fall back to `en-US`.

---

## Pages & features

- **Home (`/`)** — expense search/filter (mirrored into the address bar, so a
  filtered view can be bookmarked or shared), 5-stat summary, date-grouped
  table with sorting & pagination, add/edit form with validation, monthly
  spending limit progress (daily average, recommended daily, available quota,
  percentage), AI smart record, AI report (Markdown), import/export.
- **Charts (`/charts`)** — bar / line / doughnut / radar visualisations in a
  muted categorical palette, loaded lazily to keep the home bundle small.
- **Membership (`/membership`)** — username login/register (letters only,
  1–20 chars) with an avatar cropper (circular mask, 1–3x zoom, drag to pan)
  that uploads a 256px PNG to the backend; the avatar is restored from the
  server on login.
- **Recycle Bin (`/recycle-bin`)** — restore or permanently delete soft-deleted
  entries, with select-all and a 30-day retention countdown.
- **404** — branded not-found page.

---

## Performance notes (Requirement 4)

- All routes are **statically prerendered** at build time (no per-request SSR).
- Chart.js is **dynamically imported with `ssr: false`**, so it never enters the
  initial home bundle.
- The design system is plain CSS (no runtime UI framework), keeping JS minimal.
- Images are served as inline/SVG or downscaled client-side; no large assets.
- Native `fetch` replaces axios, removing a dependency and its polyfills.

---

## Backend contract (preserved)

| Endpoint | Purpose |
| --- | --- |
| `/api/expenses/by-date` | Paginated, server-side filtered expense list (date groups) |
| `/api/expenses/statistics` | Aggregate statistics for the current view |
| `/api/expenses` | Create / update / soft-delete (recycle) |
| `/api/expenses/deleted` | List recycle-bin items |
| `/api/members/members` | Create-or-get a member (`POST`), read (`GET /:username`), avatar (`PUT /:username/avatar`) |
| `/api/export/ai-data` | Filtered xlsx export for the AI Q&A (`year`, `month`, `types`) |

Soft-delete uses a `deletedAt` tombstone; the recycle bin exposes restore and
permanent purge. This matches the original client behaviour exactly.
