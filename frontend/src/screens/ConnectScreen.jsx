import { useEffect, useState } from 'react';
import { api } from '../api/client.js';

// Phase 0 stub: confirms the backend is reachable and shows its version.
// The full daemon-connection form (socket/TCP/TLS) arrives in Phase 7 (spec §11.1).
export default function ConnectScreen() {
  const [status, setStatus] = useState('checking…');
  const [version, setVersion] = useState(null);

  useEffect(() => {
    api
      .version()
      .then((v) => {
        setVersion(v);
        setStatus('backend reachable');
      })
      .catch((e) => setStatus(`backend unreachable: ${e.message}`));
  }, []);

  return (
    <section className="screen">
      <h1>Connect</h1>
      <p className="muted">
        Connect to the local Docker daemon hosting your application under test.
      </p>
      <div className="card">
        <div className="status-line">
          <span className="status-dot" data-ok={!!version} />
          <span>{status}</span>
        </div>
        {version && (
          <p className="muted">
            {version.name} v{version.version}
          </p>
        )}
      </div>
    </section>
  );
}
