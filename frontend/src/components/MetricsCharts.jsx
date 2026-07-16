import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

// Live line charts for CPU%, memory%, and health latency, built from the
// stream of samples. Each point is tagged with its phase for context.
function Chart({ title, data, dataKey, unit, color }) {
  return (
    <div className="chart">
      <h4>{title}</h4>
      <ResponsiveContainer width="100%" height={160}>
        <LineChart data={data} margin={{ top: 5, right: 10, bottom: 5, left: -10 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#2a2f3a" />
          <XAxis dataKey="t" tick={{ fontSize: 10, fill: '#9aa3af' }} />
          <YAxis tick={{ fontSize: 10, fill: '#9aa3af' }} unit={unit} />
          <Tooltip
            contentStyle={{ background: '#1a1d24', border: '1px solid #2a2f3a' }}
            labelStyle={{ color: '#e6e8eb' }}
          />
          <Line type="monotone" dataKey={dataKey} stroke={color} dot={false} isAnimationActive={false} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

export default function MetricsCharts({ samples }) {
  const data = samples.map((s, i) => ({
    t: i,
    phase: s.phase,
    cpu: s.stats ? Number(s.stats.cpuPercent?.toFixed?.(1) ?? s.stats.cpuPercent) : null,
    mem: s.stats ? Number(s.stats.memoryPercent?.toFixed?.(1) ?? s.stats.memoryPercent) : null,
    latency: s.health && s.health.reachable ? s.health.latencyMs : null,
  }));

  return (
    <div className="charts">
      <Chart title="CPU %" data={data} dataKey="cpu" unit="%" color="#f5a623" />
      <Chart title="Memory %" data={data} dataKey="mem" unit="%" color="#3fb950" />
      <Chart title="Health latency (ms)" data={data} dataKey="latency" unit="ms" color="#58a6ff" />
    </div>
  );
}
