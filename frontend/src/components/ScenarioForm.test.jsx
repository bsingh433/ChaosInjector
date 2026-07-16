import { render, screen } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import ScenarioForm from './ScenarioForm.jsx';

const networkScenario = {
  type: 'NETWORK_FAILURE',
  label: 'Network Failure',
  params: [
    { name: 'mode', type: 'enum', label: 'Mode', required: true, defaultValue: 'LATENCY',
      options: ['LATENCY', 'PACKET_LOSS'], dependsOnMode: null },
    { name: 'latencyMs', type: 'int', label: 'Latency (ms)', required: false, defaultValue: 300,
      dependsOnMode: 'LATENCY' },
    { name: 'lossPercent', type: 'number', label: 'Packet loss (%)', required: false, defaultValue: 20,
      dependsOnMode: 'PACKET_LOSS' },
  ],
};

function Harness({ initial }) {
  const [values, setValues] = useState(initial);
  return <ScenarioForm scenario={networkScenario} values={values} onChange={setValues} />;
}

describe('ScenarioForm', () => {
  it('shows only params for the current mode', () => {
    render(<Harness initial={{ mode: 'LATENCY' }} />);
    expect(screen.getByText(/Latency \(ms\)/)).toBeInTheDocument();
    expect(screen.queryByText(/Packet loss/)).not.toBeInTheDocument();
  });

  it('switches visible params when mode changes', () => {
    render(<Harness initial={{ mode: 'PACKET_LOSS' }} />);
    expect(screen.getByText(/Packet loss/)).toBeInTheDocument();
    expect(screen.queryByText(/Latency \(ms\)/)).not.toBeInTheDocument();
  });
});
