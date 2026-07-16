import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client.js';
import { useAppState } from '../state/AppState.jsx';

// Connect to the local Docker daemon (or a remote TLS daemon). Spec §11.1.
export default function ConnectScreen() {
  const { setConnectionId } = useAppState();
  const navigate = useNavigate();

  const [mode, setMode] = useState('socket'); // 'socket' | 'tcp'
  const [host, setHost] = useState('unix:///var/run/docker.sock');
  const [certPath, setCertPath] = useState('');
  const [tlsVerify, setTlsVerify] = useState(false);
  const [status, setStatus] = useState(null);
  const [busy, setBusy] = useState(false);

  async function connect(e) {
    e.preventDefault();
    setBusy(true);
    setStatus(null);
    try {
      const payload =
        mode === 'socket'
          ? { host: 'unix:///var/run/docker.sock', tlsVerify: false }
          : { host, certPath: certPath || null, tlsVerify };
      const res = await api.connect(payload);
      setConnectionId(res.connectionId);
      setStatus({ ok: true, msg: 'Connected' });
      navigate('/configure');
    } catch (err) {
      setStatus({ ok: false, msg: err.message });
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="screen">
      <h1>Connect</h1>
      <p className="muted">Connect to the Docker daemon hosting your application under test.</p>

      <form className="card form" onSubmit={connect}>
        <label className="field">
          <span>Connection type</span>
          <select value={mode} onChange={(e) => setMode(e.target.value)}>
            <option value="socket">Local socket (unix:///var/run/docker.sock)</option>
            <option value="tcp">Remote TCP</option>
          </select>
        </label>

        {mode === 'tcp' && (
          <>
            <label className="field">
              <span>Host</span>
              <input
                value={host === 'unix:///var/run/docker.sock' ? '' : host}
                placeholder="tcp://host:2376"
                onChange={(e) => setHost(e.target.value)}
              />
            </label>
            <label className="field">
              <span>TLS cert directory (optional)</span>
              <input
                value={certPath}
                placeholder="/path/to/certs"
                onChange={(e) => setCertPath(e.target.value)}
              />
            </label>
            <label className="field field--inline">
              <input type="checkbox" checked={tlsVerify} onChange={(e) => setTlsVerify(e.target.checked)} />
              <span>Verify TLS</span>
            </label>
          </>
        )}

        <button className="btn btn--primary" disabled={busy} type="submit">
          {busy ? 'Connecting…' : 'Test & Connect'}
        </button>

        {status && (
          <div className="status-line">
            <span className="status-dot" data-ok={status.ok} />
            <span>{status.msg}</span>
          </div>
        )}
      </form>
    </section>
  );
}
