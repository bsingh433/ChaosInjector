// Renders the computed ImpactReport: per-metric before/during deltas,
// availability, recovery time, and the qualitative verdict. Spec §11.3.
function Row({ label, cmp, unit }) {
  if (!cmp) return null;
  const up = cmp.delta > 0;
  return (
    <tr>
      <td>{label}</td>
      <td>{cmp.baselineMean.toFixed(1)}{unit}</td>
      <td>{cmp.activeMean.toFixed(1)}{unit}</td>
      <td className={up ? 'up' : 'down'}>
        {up ? '▲' : '▼'} {cmp.delta.toFixed(1)}{unit}
      </td>
    </tr>
  );
}

export default function ImpactReportView({ report }) {
  if (!report) return null;
  const avail =
    report.availabilityDuringActive === null || report.availabilityDuringActive === undefined
      ? '—'
      : `${(report.availabilityDuringActive * 100).toFixed(0)}%`;
  const recovery =
    report.recoverySeconds === null || report.recoverySeconds === undefined
      ? '—'
      : `${report.recoverySeconds.toFixed(1)}s`;

  return (
    <div className="card report">
      <h3>Impact report</h3>
      <p className="verdict">{report.verdict}</p>

      <div className="report-stats">
        <div className="stat">
          <span className="stat-value">{avail}</span>
          <span className="stat-label">Availability (active)</span>
        </div>
        <div className="stat">
          <span className="stat-value">{recovery}</span>
          <span className="stat-label">Recovery time</span>
        </div>
        <div className="stat">
          <span className="stat-value">{report.latencyActiveP95?.toFixed?.(0) ?? '—'}ms</span>
          <span className="stat-label">p95 latency (active)</span>
        </div>
      </div>

      <table className="report-table">
        <thead>
          <tr>
            <th>Metric</th>
            <th>Baseline</th>
            <th>During</th>
            <th>Δ</th>
          </tr>
        </thead>
        <tbody>
          <Row label="CPU" cmp={report.cpu} unit="%" />
          <Row label="Memory" cmp={report.memoryPercent} unit="%" />
          <Row label="Health latency" cmp={report.latencyMs} unit="ms" />
        </tbody>
      </table>
      <p className="muted">
        {report.baselineSamples} baseline / {report.activeSamples} active samples
      </p>
    </div>
  );
}
