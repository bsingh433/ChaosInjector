// Phase 0 stub. Full implementation (container picker, health-check URL,
// scenario cards with schema-driven param forms, review + acknowledge) lands in
// Phase 7 (spec §11.2).
export default function ConfigureScreen() {
  const scenarios = [
    'Network Failure',
    'CPU Overhead',
    'Memory Overhead',
    'Service Unavailable',
  ];
  return (
    <section className="screen">
      <h1>Configure experiment</h1>
      <p className="muted">Pick a target container and a chaos scenario.</p>
      <div className="scenario-grid">
        {scenarios.map((name) => (
          <div className="card scenario-card" key={name}>
            <h3>{name}</h3>
            <p className="muted">Coming in Phase 7</p>
          </div>
        ))}
      </div>
    </section>
  );
}
