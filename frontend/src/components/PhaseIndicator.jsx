const PHASES = ['VALIDATING', 'BASELINE', 'INJECTING', 'ACTIVE', 'REVERTING'];
const TERMINAL = { COMPLETED: 'ok', ABORTED: 'warn', FAILED: 'danger' };

// Horizontal stepper of the experiment lifecycle, driven by the current state.
export default function PhaseIndicator({ state }) {
  const activeIdx = PHASES.indexOf(state);
  const terminal = TERMINAL[state];

  return (
    <div className="phases">
      {PHASES.map((p, i) => {
        const done = terminal ? true : i < activeIdx;
        const current = p === state;
        return (
          <div key={p} className={`phase-step ${done ? 'done' : ''} ${current ? 'current' : ''}`}>
            <span className="phase-dot" />
            <span className="phase-label">{p}</span>
          </div>
        );
      })}
      {terminal && <div className={`phase-terminal ${terminal}`}>{state}</div>}
    </div>
  );
}
