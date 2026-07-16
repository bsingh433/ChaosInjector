import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api, streamExperiment } from '../api/client.js';
import PhaseIndicator from '../components/PhaseIndicator.jsx';
import MetricsCharts from '../components/MetricsCharts.jsx';
import ImpactReportView from '../components/ImpactReportView.jsx';

const TERMINAL = ['COMPLETED', 'ABORTED', 'FAILED'];

// Live run view: phase stepper, live charts from the SSE sample stream, an
// ABORT control, and the impact report on completion. Spec §11.3.
export default function RunScreen() {
  const { experimentId } = useParams();
  const navigate = useNavigate();

  const [state, setState] = useState('VALIDATING');
  const [samples, setSamples] = useState([]);
  const [logs, setLogs] = useState([]);
  const [report, setReport] = useState(null);
  const [errorMsg, setErrorMsg] = useState(null);
  const esRef = useRef(null);

  useEffect(() => {
    if (!experimentId) return undefined;

    // Seed from current status (in case we connect after some events).
    api.getExperiment(experimentId).then((e) => {
      setState(e.state);
      if (e.report) setReport(e.report);
    });

    const es = streamExperiment(experimentId, {
      onPhase: (ev) => setState(ev.state),
      onSample: (ev) => setSamples((prev) => [...prev, ev.sample]),
      onLog: (ev) => setLogs((prev) => [...prev, ev.message]),
      onCompleted: (ev) => {
        setState(ev.state);
        setReport(ev.report);
        es.close();
      },
      onError: (ev) => {
        setErrorMsg(ev.message || 'Experiment failed');
        setState('FAILED');
        es.close();
      },
    });
    esRef.current = es;
    return () => es.close();
  }, [experimentId]);

  const running = !TERMINAL.includes(state);

  async function abort() {
    await api.abortExperiment(experimentId);
  }

  if (!experimentId) {
    return (
      <section className="screen">
        <h1>Run &amp; result</h1>
        <p className="muted">Start an experiment from the Configure screen.</p>
        <button className="btn" onClick={() => navigate('/configure')}>
          Go to Configure
        </button>
      </section>
    );
  }

  return (
    <section className="screen">
      <div className="run-header">
        <h1>Run &amp; result</h1>
        {running && (
          <button className="btn btn--danger" onClick={abort}>
            ⨯ Abort
          </button>
        )}
      </div>

      <PhaseIndicator state={state} />

      {errorMsg && <p className="error">{errorMsg}</p>}

      <MetricsCharts samples={samples} />

      {report && <ImpactReportView report={report} />}

      {logs.length > 0 && (
        <div className="card">
          <h4>Log</h4>
          <ul className="logs">
            {logs.map((l, i) => (
              <li key={i}>{l}</li>
            ))}
          </ul>
        </div>
      )}

      {!running && (
        <button className="btn" onClick={() => navigate('/configure')}>
          Run another experiment
        </button>
      )}
    </section>
  );
}
