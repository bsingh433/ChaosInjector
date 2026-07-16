// Thin REST + SSE client for the ChaosInjector backend. All endpoints live
// under /api (proxied to :8080 in dev, same-origin in the bundled JAR).

const BASE = '/api';

async function request(path, options = {}) {
  const res = await fetch(`${BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) {
    let body;
    try {
      body = await res.json();
    } catch {
      body = { message: res.statusText };
    }
    const err = new Error(body.message || `Request failed: ${res.status}`);
    err.code = body.code;
    err.details = body.details;
    err.status = res.status;
    throw err;
  }
  return res.status === 204 ? null : res.json();
}

export const api = {
  health: () => request('/health'),
  version: () => request('/version'),

  // Endpoints below are implemented in later build phases (spec §10).
  connect: (payload) =>
    request('/targets/connect', { method: 'POST', body: JSON.stringify(payload) }),
  listContainers: (connectionId) =>
    request(`/targets/${connectionId}/containers`),
  scenarios: () => request('/scenarios'),
  validateExperiment: (payload) =>
    request('/experiments/validate', { method: 'POST', body: JSON.stringify(payload) }),
  createExperiment: (payload) =>
    request('/experiments', { method: 'POST', body: JSON.stringify(payload) }),
  getExperiment: (id) => request(`/experiments/${id}`),
  abortExperiment: (id) => request(`/experiments/${id}/abort`, { method: 'POST' }),
};

// Subscribe to an experiment's live SSE feed. Returns the EventSource so the
// caller can close() it. Handlers: { onPhase, onSample, onLog, onCompleted, onError }.
export function streamExperiment(id, handlers = {}) {
  const es = new EventSource(`${BASE}/experiments/${id}/stream`);
  const bind = (name, fn) =>
    fn && es.addEventListener(name, (e) => fn(JSON.parse(e.data)));
  bind('phase', handlers.onPhase);
  bind('sample', handlers.onSample);
  bind('log', handlers.onLog);
  bind('completed', handlers.onCompleted);
  bind('error', handlers.onError);
  return es;
}
