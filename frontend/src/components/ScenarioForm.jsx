// Renders a scenario's parameter form from its schema (ParamSpec[]). Params
// with `dependsOnMode` only show when the current `mode` value matches.
export default function ScenarioForm({ scenario, values, onChange }) {
  const currentMode = values.mode;

  function setField(name, raw, type) {
    let v = raw;
    if (type === 'int') v = raw === '' ? '' : parseInt(raw, 10);
    else if (type === 'number') v = raw === '' ? '' : parseFloat(raw);
    onChange({ ...values, [name]: v });
  }

  return (
    <div className="param-form">
      {scenario.params
        .filter((p) => !p.dependsOnMode || p.dependsOnMode === currentMode)
        .map((p) => (
          <label className="field" key={p.name}>
            <span>
              {p.label}
              {p.required && <em className="req"> *</em>}
            </span>
            {p.type === 'enum' ? (
              <select
                value={values[p.name] ?? p.defaultValue ?? ''}
                onChange={(e) => setField(p.name, e.target.value, p.type)}
              >
                {p.options.map((o) => (
                  <option key={o} value={o}>
                    {o}
                  </option>
                ))}
              </select>
            ) : (
              <input
                type={p.type === 'int' || p.type === 'number' ? 'number' : 'text'}
                value={values[p.name] ?? p.defaultValue ?? ''}
                min={p.min ?? undefined}
                max={p.max ?? undefined}
                onChange={(e) => setField(p.name, e.target.value, p.type)}
              />
            )}
          </label>
        ))}
    </div>
  );
}
