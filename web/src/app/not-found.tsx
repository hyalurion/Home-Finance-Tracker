import Link from 'next/link';

export default function NotFound() {
  return (
    <div className="container page">
      <div className="empty-state" style={{ marginTop: 40 }}>
        <h3>404</h3>
        <p>The page you&apos;re looking for doesn&apos;t exist.</p>
        <Link href="/" className="btn btn--primary">{('Back to Home')}</Link>
      </div>
    </div>
  );
}
