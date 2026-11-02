/**
 * Next.js configuration for the Home Finance Tracker web frontend.
 *
 * The previous Vue + Vite app proxied "/api" to the Go backend on :3010.
 * We reproduce the same behaviour with server-side rewrites so the browser
 * can keep calling a relative "/api" base while the request is forwarded to
 * the local backend in development.
 *
 * @type {import('next').NextConfig}
 */
const BACKEND_TARGET = process.env.API_TARGET || 'http://localhost:3010';

/**
 * Production builds are exported as static files: the Go backend serves them
 * directly, so a release is a single binary plus a folder of assets — no
 * Node.js runtime required on the server.
 *
 * The dev server keeps running as a normal Next.js app and proxies "/api" to
 * the Go backend through the rewrite below.
 */
const isStaticExport = process.env.NEXT_OUTPUT === 'export';

const nextConfig = {
  reactStrictMode: true,
  // Keep the public path at the site root, matching the previous SPA.
  basePath: '',
  ...(isStaticExport ? { output: 'export' } : {}),
  ...(isStaticExport
    ? {}
    : {
        async rewrites() {
          return [
            {
              source: '/api/:path*',
              destination: `${BACKEND_TARGET}/api/:path*`,
            },
          ];
        },
      }),
};

export default nextConfig;
