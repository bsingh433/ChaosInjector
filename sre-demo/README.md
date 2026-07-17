# Chaos + Observability + SRE Agent — Demo

A local, one-command environment that closes the loop **fault → observe → RCA →
fix**. See [`sre_demo_spec.md`](./sre_demo_spec.md) for the full design and
[`IMPLEMENTATION_PLAN.md`](./IMPLEMENTATION_PLAN.md) for build status.

> **Component separation:** every part (`sample-app/`, `downstream/`, `mongo/`,
> `prometheus/`, `grafana/`, `sre-agent/`) is a self-contained folder with its own
> Dockerfile + README, destined to become its own repo. The root
> `docker-compose.yml` wires them together, each in its own container.

## What's here (Phase A)

| Service | URL | Purpose |
|---|---|---|
| sample-app | http://localhost:5001 | instrumented backend (chaos target); Mongo CRUD at `/items` (host 5001 → container 5000; 5000 avoided because macOS AirPlay uses it) |
| downstream | http://localhost:6000 | dependency the app calls on `/work` |
| mongo | localhost:27017 | database backing the CRUD endpoints |
| cadvisor | http://localhost:8080 | per-container CPU/memory metrics |
| prometheus | http://localhost:9090 | scrapes everything (5s) |
| grafana | http://localhost:3000 | **Chaos Overview** dashboard (admin/admin) |
| sre-agent | http://localhost:8085 | LLM-powered RCA + gated remediation |

## Run it

```bash
cd sre-demo
cp .env.example .env          # adjust if you like
docker compose up --build     # first build pulls base images
```

Then:
- Grafana → http://localhost:3000 (admin/admin) → **Chaos Overview**. The
  sample-app's self-load generator means the panels move immediately.
- Try the CRUD API (host port **5001**):
  ```bash
  curl -X POST localhost:5001/items -H 'content-type: application/json' -d '{"name":"x","value":42}'
  curl localhost:5001/items
  ```

> **macOS note:** the app is mapped to host **5001** because macOS Control Center
> (AirPlay Receiver) occupies **5000**. When you point ChaosInjector's health-check
> at the app, use `http://localhost:5001/health`. To use 5000 instead, turn off
> *System Settings → General → AirDrop & Handoff → AirPlay Receiver* and change the
> port mapping back to `5000:5000`.

## Inject chaos and watch

Run **ChaosInjector** (from the ChaosInjector app), connect to the local Docker
daemon, target the **`sample-app`** container, and inject a scenario. Watch the
Chaos Overview dashboard react:

| Scenario | What you'll see |
|---|---|
| CPU Overhead | `sample-app` container CPU spikes; app latency rises |
| Memory Overhead | container memory climbs toward the 512m limit; possible restart |
| Network Failure | downstream **and DB** p95 latency spike; errors rise |
| Service Unavailable | `up{job="sample-app"}` drops to 0 |

(For network/CPU/memory scenarios, build the ChaosInjector helper image first:
`docker build -t chaosinjector/helper:latest <chaosinjector-repo>/helper/`.)

## Run the SRE Agent (RCA)

The agent reads Prometheus + Docker and does root-cause analysis with an LLM. It
is **not told** what was injected — it discovers the cause. Configure your LLM in
`.env` (default provider Azure OpenAI Responses API; also OpenAI / Anthropic):

```bash
# .env (only the selected provider's creds are needed)
LLM_PROVIDER=azure-responses
LLM_MODEL=gpt-5
AZURE_OPENAI_ENDPOINT=https://<resource>.openai.azure.com
AZURE_OPENAI_API_VERSION=2025-04-01-preview
AZURE_OPENAI_API_KEY=...
```

Inject a fault from ChaosInjector, wait ~30s for metrics to move, then ask the
agent (it comes up with the stack, on port 8085):

```bash
curl -s -X POST "http://localhost:8085/api/analyze?windowMinutes=10&target=sample-app" | jq
```

You get a structured RCA — timeline, ranked hypotheses with cited metric
evidence, a plain-language verdict, and a **proposed reversible fix**. Compare its
top hypothesis to the scenario you actually injected — that's the agent grading
itself against ground truth.

### Remediation (gated, reversible)
`REMEDIATION_MODE` controls whether the agent may act:
- `propose` (default) — proposes a fix (`restart_container` / `unpause_container` /
  `abort_chaos` …) but never executes; see it in `recommendedFixes[].proposedAction`.
- `auto` — executes the reversible fix (e.g. `abort_chaos` ends the experiment and
  ChaosInjector reverts); the metrics then recover.
- `prompt` — asks for approval on the console (CLI mode).

Every proposed/executed action is audited: `curl http://localhost:8085/api/audit`.

One-shot CLI instead of HTTP:
```bash
cd sre-agent && java -jar target/sre-agent.jar --analyze --window=10 --target=sample-app
```

## Tear down
```bash
docker compose down          # add -v to also drop the mongo volume
```

## Notes
- `sample-app` has `mem_limit: 512m` so memory chaos and OOM are meaningful.
- cAdvisor needs the host mounts in the compose file; on Docker Desktop this
  works via the WSL2 backend.
