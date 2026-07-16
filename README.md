# ChaosInjector

Inject a chaos scenario into a running application and see the impact.

ChaosInjector is a chaos-engineering tool: from a React UI you pick a fault,
point it at a container on your local Docker daemon, and ChaosInjector injects
the fault, measures the app's behaviour before / during / after, and reports the
impact — then **automatically reverts**. It injects and observes only; it never
tries to fix, self-heal, or remediate the target.

**MVP scenarios:** Network Failure · CPU Overhead · Memory Overhead · Service
Unavailable. **MVP target:** a container on the local Docker daemon (the target
layer is pluggable for Kubernetes/OpenShift later).

See [`chaos_injector_spec.md`](chaos_injector_spec.md) for the full spec and
[`CLAUDE.md`](CLAUDE.md) for the build plan.

---

## How it works

```
React SPA  ──REST + SSE──▶  Spring Boot engine  ──docker-java──▶  local Docker daemon
 (forms, live charts)        (state machine, injectors,             ├─ target app container
                             metrics, target adapter)               └─ helper container(s)
```

The React UI holds no chaos logic — it renders scenario schemas, sends requests,
and visualises a live Server-Sent-Events stream. All behaviour lives in the
backend behind the `TargetAdapter` interface (the seam where K8s/OpenShift plug
in later).

Every experiment is **bounded and reversible**: a hard duration, a server-side
watchdog, and a guaranteed idempotent revert on completion, abort, error, or
shutdown. Helper containers are labelled and swept on revert and on reconnect.

---

## Prerequisites

- **Java 17+** (the backend targets Java 17; JDK 21 works too)
- **Node 20+** and **npm** (for the frontend build)
- **Docker** running locally, with access to `/var/run/docker.sock`
- Maven is provided via the wrapper (`./mvnw`) — no separate install needed

The two projects are independent and can be opened in separate IDE sessions:

| Path | Project |
|------|---------|
| `backend/` | Spring Boot + Maven engine + REST/SSE API |
| `frontend/` | React + Vite UI |

---

## Quickstart (single JAR)

Build the frontend and backend into one runnable JAR and start it:

```bash
cd backend
./mvnw -Pprod clean package        # builds the React app and bundles it into the JAR
java -jar target/chaosinjector.jar # serves UI + API on http://localhost:8080
```

Open <http://localhost:8080> and:

1. **Connect** — use the local socket (default) and click *Test & Connect*.
2. **Configure** — pick a target container, optionally add a health-check URL,
   choose a scenario, tune its parameters and the duration, acknowledge the
   disruption, and click *Inject Chaos*.
3. **Run** — watch the live phases and charts; the impact report appears when
   the experiment finishes and the target is reverted. Use **Abort** to stop
   early (it reverts immediately).

### Build the helper image (needed for Network / CPU / Memory scenarios)

Network faults (and the CPU/RAM `stress-ng` path) run a small helper image
against the target. Build it once:

```bash
docker build -t chaosinjector/helper:latest helper/
```

Override the image name via `chaosinjector.helper.image` if you publish your own.
The **Service Unavailable** scenario needs no helper image.

---

## Development (two projects, hot reload)

Run the backend and frontend separately, each in its own IDE session:

```bash
# terminal 1 — backend API on :8080
cd backend && ./mvnw spring-boot:run

# terminal 2 — Vite dev server on :5173 (proxies /api -> :8080)
cd frontend && npm install && npm run dev
```

Open <http://localhost:5173>.

### Tests

```bash
cd backend && ./mvnw test     # unit + integration; Docker-dependent ITs
                              # self-skip when no daemon/image is available
cd frontend && npm test       # component tests (Vitest)
```

Integration tests (`DockerTargetAdapterTest`, `EndToEndTest`) use Testcontainers
and run for real when a Docker daemon and a pullable base image are available;
otherwise they skip so the suite stays green everywhere.

---

## Optional: run ChaosInjector itself as a container

```bash
docker build -t chaosinjector:latest .
docker run --rm -p 8080:8080 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  chaosinjector:latest
```

The primary supported deployment is still running the JAR directly on the host.

---

## Scenarios

| Scenario | What it does | Key parameters | Reverted by |
|----------|--------------|----------------|-------------|
| **Network Failure** | `tc/netem` via a NET_ADMIN helper in the target's netns | `mode` (LATENCY / PACKET_LOSS / BANDWIDTH / PARTITION), `latencyMs`, `jitterMs`, `lossPercent`, `rateKbit`, `iface` | delete qdisc + sweep helpers |
| **CPU Overhead** | `stress-ng` (busy-loop fallback) exec'd inside the target | `workers`, `loadPercent` | kill the load process |
| **Memory Overhead** | `stress-ng --vm` (tmpfs fallback) inside the target | `sizeMb` **or** `percentOfLimit` | free the memory |
| **Service Unavailable** | pause / stop / disconnect the container | `mode` (PAUSE / STOP / NETWORK_OFF), `stopTimeoutSeconds` | unpause / start / reconnect |

Common controls for every scenario: `durationSeconds`, `baselineSeconds`,
`sampleIntervalMs`.

**Impact** is measured by sampling `docker stats` (CPU %, memory %, network,
restarts) plus the health-check URL (status, latency, availability) across
BASELINE → ACTIVE → POST, and reported as before/during deltas, availability,
p95 latency, recovery time, and a qualitative verdict.

---

## Configuration

Backend config lives in `backend/src/main/resources/application.yml` (override
via env vars; `${ENV_VAR}` interpolation is supported and secrets are never
logged):

| Key | Default | Meaning |
|-----|---------|---------|
| `chaosinjector.docker.default-host` | `unix:///var/run/docker.sock` | Daemon endpoint used when none is supplied |
| `chaosinjector.experiment.max-duration-seconds` | `3600` | Hard cap on any experiment's duration |
| `chaosinjector.experiment.single-active-global` | `false` | Global vs per-target single-active guard |
| `chaosinjector.experiment.post-window-seconds` | `5` | Post-revert sampling window (recovery) |
| `chaosinjector.metrics.sample-interval-ms` | `1000` | Default metrics sampling interval |
| `chaosinjector.metrics.probe-timeout-ms` | `3000` | Health-probe timeout |
| `chaosinjector.helper.image` | `chaosinjector/helper:latest` | Helper image injectors run |

---

## Safety model

1. **Bounded** — every experiment has a hard `durationSeconds`; a watchdog
   reverts even if the client disconnects.
2. **Guaranteed revert** — runs on completion, abort, backend error, and JVM
   shutdown; idempotent.
3. **Helper cleanup** — helpers are labelled and force-removed on revert and
   swept on reconnect (orphan recovery).
4. **One experiment per target** — overlapping injections are rejected (HTTP
   409).
5. **Explicit confirmation** — the UI requires acknowledging disruption before
   injecting, and offers a dry-run *Validate*.
6. **Loud revert failures** — a failed revert is surfaced in the report, never
   hidden.

---

## API summary

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/targets/connect` | Register a daemon connection → `{connectionId}` |
| GET | `/api/targets/{id}/containers` | List containers |
| GET | `/api/scenarios` | Scenario + parameter schemas |
| POST | `/api/experiments/validate` | Dry-run validation |
| POST | `/api/experiments` | Create & start → `{experimentId}` |
| GET | `/api/experiments/{id}` | Status + report snapshot |
| GET | `/api/experiments/{id}/stream` | Live SSE feed (phase / sample / log / completed / error) |
| POST | `/api/experiments/{id}/abort` | Abort (reverts immediately) |
| GET | `/api/experiments` | Recent experiments |

---

## Project layout

```
chaosinjector/
├── backend/    Spring Boot + Maven (api, experiment, engine, target, metrics, config)
├── frontend/   React + Vite (screens, components, api client)
├── helper/     Dockerfile for the iproute2 + stress-ng helper image
├── Dockerfile  Optional: run ChaosInjector itself as a container
├── chaos_injector_spec.md
└── CLAUDE.md
```
