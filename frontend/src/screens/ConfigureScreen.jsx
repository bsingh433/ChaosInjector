import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client.js';
import { useAppState } from '../state/AppState.jsx';
import ScenarioForm from '../components/ScenarioForm.jsx';

// Pick a target container + scenario, tune parameters, review, and inject.
// Spec §11.2.
export default function ConfigureScreen() {
  const { connectionId, containerId, setContainerId, healthUrl, setHealthUrl } = useAppState();
  const navigate = useNavigate();

  const [containers, setContainers] = useState([]);
  const [scenarios, setScenarios] = useState([]);
  const [filter, setFilter] = useState('');
  const [selected, setSelected] = useState(null); // scenario type
  const [params, setParams] = useState({});
  const [duration, setDuration] = useState(60);
  const [baseline, setBaseline] = useState(15);
  const [interval, setIntervalMs] = useState(1000);
  const [ack, setAck] = useState(false);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!connectionId) return;
    api.listContainers(connectionId).then(setContainers).catch((e) => setError(e.message));
    api.scenarios().then(setScenarios).catch((e) => setError(e.message));
  }, [connectionId]);

  const scenario = useMemo(() => scenarios.find((s) => s.type === selected), [scenarios, selected]);

  function selectScenario(s) {
    setSelected(s.type);
    const defaults = {};
    s.params.forEach((p) => {
      if (p.defaultValue !== null && p.defaultValue !== undefined) defaults[p.name] = p.defaultValue;
    });
    setParams(defaults);
  }

  const filtered = containers.filter(
    (c) =>
      !filter ||
      (c.name && c.name.includes(filter)) ||
      (c.image && c.image.includes(filter)),
  );

  async function inject() {
    setBusy(true);
    setError(null);
    try {
      const res = await api.createExperiment({
        connectionId,
        containerId,
        healthCheckUrl: healthUrl || null,
        scenario: selected,
        parameters: params,
        durationSeconds: Number(duration),
        baselineSeconds: Number(baseline),
        sampleIntervalMs: Number(interval),
      });
      navigate(`/run/${res.experimentId}`);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  if (!connectionId) {
    return (
      <section className="screen">
        <h1>Configure experiment</h1>
        <p className="muted">Connect to a Docker daemon first.</p>
        <button className="btn" onClick={() => navigate('/connect')}>
          Go to Connect
        </button>
      </section>
    );
  }

  const canInject = containerId && selected && ack && !busy;

  return (
    <section className="screen">
      <h1>Configure experiment</h1>

      <div className="card">
        <h3>Target</h3>
        <input
          className="search"
          placeholder="Filter containers by name or image…"
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
        />
        <div className="container-list">
          {filtered.length === 0 && <p className="muted">No running containers found.</p>}
          {filtered.map((c) => (
            <button
              key={c.id}
              className={`container-item ${containerId === c.id ? 'selected' : ''}`}
              onClick={() => setContainerId(c.id)}
            >
              <strong>{c.name || c.id.slice(0, 12)}</strong>
              <span className="muted">{c.image}</span>
              <span className="badge">{c.status}</span>
            </button>
          ))}
        </div>
        <label className="field">
          <span>Health-check URL (optional)</span>
          <input
            placeholder="http://localhost:8081/health"
            value={healthUrl}
            onChange={(e) => setHealthUrl(e.target.value)}
          />
        </label>
      </div>

      <div className="card">
        <h3>Scenario</h3>
        <div className="scenario-grid">
          {scenarios.map((s) => (
            <button
              key={s.type}
              className={`card scenario-card ${selected === s.type ? 'selected' : ''}`}
              onClick={() => selectScenario(s)}
            >
              <h3>{s.label}</h3>
              <p className="muted">{s.description}</p>
            </button>
          ))}
        </div>
        {scenario && <ScenarioForm scenario={scenario} values={params} onChange={setParams} />}
      </div>

      <div className="card">
        <h3>Timing</h3>
        <div className="row">
          <label className="field">
            <span>Duration (s)</span>
            <input type="number" min="1" value={duration} onChange={(e) => setDuration(e.target.value)} />
          </label>
          <label className="field">
            <span>Baseline (s)</span>
            <input type="number" min="0" value={baseline} onChange={(e) => setBaseline(e.target.value)} />
          </label>
          <label className="field">
            <span>Sample interval (ms)</span>
            <input type="number" min="100" value={interval} onChange={(e) => setIntervalMs(e.target.value)} />
          </label>
        </div>
      </div>

      <div className="card review">
        {selected && containerId && (
          <p>
            You are about to inject <strong>{scenario?.label}</strong> into{' '}
            <strong>{containers.find((c) => c.id === containerId)?.name || containerId.slice(0, 12)}</strong>{' '}
            for <strong>{duration}s</strong>.
          </p>
        )}
        <label className="field field--inline">
          <input type="checkbox" checked={ack} onChange={(e) => setAck(e.target.checked)} />
          <span>I understand this will disrupt the target.</span>
        </label>
        <button className="btn btn--danger" disabled={!canInject} onClick={inject}>
          {busy ? 'Starting…' : 'Inject Chaos'}
        </button>
        {error && <p className="error">{error}</p>}
      </div>
    </section>
  );
}
