# ChaosInjector — MVP Specification

**Status:** Draft for review · **Version:** 0.1 · **Date:** 2026-07-16

ChaosInjector is a tool for performing **chaos engineering** on running
applications. A user selects a chaos scenario from a web UI, points it at a
running application, and ChaosInjector injects the fault, measures the
application's behaviour before/during/after, and reports the observed impact.

> ChaosInjector **injects and observes** chaos. It does **not** fix,
> self-heal, or remediate the application under test. Surfacing the effect of
> failure is the entire product.

---

## 1. Goals (MVP)

1. A **React** single-page UI to configure and launch a chaos experiment.
2. Four chaos scenarios: **Network Failure**, **CPU Overhead**,
   **Memory (RAM) Overhead**, **Service Unavailable**.
3. A form to describe the **target application** (a container running on a
   Docker daemon).
4. **Automatic, bounded, reversible** injection — every experiment has a
   duration and is automatically rolled back; a user can abort early.
5. **Impact reporting** — sample native platform metrics (`docker stats`) plus
   an application health-check endpoint before, during, and after the
   experiment, and present the deltas.

## 2. Non-goals (MVP — see §18 Roadmap)

- Kubernetes / OpenShift targets. The target layer is designed as a pluggable
  interface (§6.4) so these can be added later, but only Docker ships in MVP.
- Orchestrating third-party chaos frameworks (Pumba, Chaos Mesh, Litmus). MVP
  injects using its **own primitives**.
- Scheduled / recurring experiments, experiment pipelines, GameDay campaigns.
- Multi-user accounts, RBAC, SSO. MVP is a single-operator localhost tool.
- Prometheus / APM scraping, distributed tracing, alerting integrations.
- Automatic remediation or rollback of the *application's* own state.

## 3. Personas & primary use case

**Persona — Reliability Engineer / Developer ("the operator").** Runs
ChaosInjector locally (or on a bastion host) with access to a Docker daemon
that hosts the application under test.

**Primary flow.**
1. Operator opens the UI and connects to a Docker daemon.
2. Operator selects a target container and provides a health-check URL.
3. Operator picks one chaos scenario and its parameters (intensity, duration).
4. Operator reviews a summary and clicks **Inject**.
5. ChaosInjector records a baseline, injects the fault, streams live
   metrics/status to the UI, then automatically reverts at the end (or on abort).
6. The UI shows a result report: what was injected, whether the app stayed
   healthy, and the measured impact (CPU/mem/latency/availability deltas).

---

## 4. High-level architecture

```
┌─────────────────────────────┐        ┌──────────────────────────────────────┐
│         React SPA           │  HTTP  │            Spring Boot API             │
│  (Vite build, served static)│◄──────►│  Controllers · Experiment Service      │
│  - Target form              │  REST  │  - Experiment state machine            │
│  - Scenario picker          │  + SSE │  - In-memory experiment store          │
│  - Live impact dashboard    │◄──────►│                                        │
└─────────────────────────────┘  live  │  ┌──────────────┐  ┌───────────────┐  │
                                  feed  │  │ Chaos Engine │  │ Metrics       │  │
                                        │  │ (injectors)  │  │ Collector     │  │
                                        │  └──────┬───────┘  └──────┬────────┘  │
                                        │         │ TargetAdapter   │           │
                                        └─────────┼─────────────────┼───────────┘
                                                  │ docker-java     │ stats/exec
                                                  ▼                 ▼
                                        ┌───────────────────────────────────────┐
                                        │      Docker daemon (target host)       │
                                        │   app container(s)  +  helper containers│
                                        └───────────────────────────────────────┘
```

- **React SPA** — the only UI. Talks to the backend over REST for
  commands and a live stream (Server-Sent Events) for progress/metrics.
- **Spring Boot API** — the single engine. All business logic lives here; the
  UI holds none. Exposes REST + SSE.
- **Chaos Engine** — one **Injector** implementation per scenario, each behind
  a common `ChaosInjector` interface (`inject()` / `revert()`).
- **Target Adapter** — abstracts *how we reach the workload*. MVP ships
  `DockerTargetAdapter` (docker-java); the interface is the seam for future
  K8s/OpenShift adapters.
- **Metrics Collector** — samples `docker stats` and probes the health URL,
  producing a time series per experiment phase.

---

## 5. Tech stack (do not substitute without updating this spec)

**Backend**
- Java 17 (LTS) · Spring Boot 3.x
- `docker-java` (Docker Engine API client, `com.github.docker-java`)
- Spring Web (REST) + SSE for live updates
- Bean Validation (Jakarta) for request DTOs
- Build: Maven (`chaosinjector-server`)
- Tests: JUnit 5, Mockito; integration tests with Testcontainers (a real
  Docker daemon) for injector/adapter behaviour

**Frontend**
- React 18 (JavaScript) · Vite build
- React Router (screens) · fetch/EventSource for API + live stream
- A lightweight charts lib for time-series (e.g. Recharts)
- Build output served as static assets by Spring Boot (single deployable),
  with a Vite dev proxy for local development

**Repository layout — two independent projects, one deployable**
- `frontend/` — the React + Vite project. Opened and debugged as its own
  project in its own IDE session; has its own `package.json`, dev server, and
  tests.
- `backend/` — the Spring Boot + Maven project. Opened and debugged as its own
  project in its own IDE session; has its own `pom.xml` and tests.
- The two are **decoupled at development time** (run separately, see below) but
  produce a **single deployable JAR** for release:
  - **Development:** run `backend` (Spring Boot on `:8080`) and `frontend`
    (Vite dev server on `:5173`) independently. Vite proxies `/api/*` to the
    backend so each can be launched/debugged in its own IDE.
  - **Release build:** the Maven build runs the Vite production build and
    copies `frontend/dist` into the backend's `src/main/resources/static`, so
    the final Spring Boot **fat JAR serves both the API and the SPA** — one
    artifact, `java -jar chaosinjector.jar`.
- Optionally a Dockerfile so ChaosInjector itself can run as a container, but
  the primary deployment target is **running the JAR directly on the local
  machine** (see §8).

---

## 6. Domain model & core concepts

### 6.1 Entities

| Entity | Meaning |
|---|---|
| **Target** | The application under test: a connection to a Docker daemon + a selected container + an optional health-check URL. |
| **ChaosScenario** | One of the four fault types, each with a typed parameter set. |
| **Experiment** | A single run: `Target` + `ChaosScenario` + parameters + lifecycle state + collected metrics + result. |
| **ImpactReport** | The computed before/during/after comparison for one experiment. |

### 6.2 Experiment lifecycle (state machine)

```
CREATED ─► VALIDATING ─► BASELINE ─► INJECTING ─► ACTIVE ─► REVERTING ─► COMPLETED
                │            │           │           │           │
                └────────────┴───────────┴───────────┴───────────┴──► FAILED
                                                      │
                                    (user abort) ─────┴──► REVERTING ─► ABORTED
```

- **VALIDATING** — daemon reachable, container exists & running, params in
  range, health URL reachable (warn, not fail, if unreachable).
- **BASELINE** — collect metrics for `baselineSeconds` with no fault applied.
- **INJECTING** — apply the fault (start helper container / exec / pause).
- **ACTIVE** — fault in place for `durationSeconds`; metrics stream live.
- **REVERTING** — undo the fault; **always attempted**, even after FAILED.
- **COMPLETED / ABORTED / FAILED** — terminal. Report is available.

**Invariant — every injection is reversible and bounded.** No scenario may
leave the target altered after REVERTING. Revert is idempotent and runs on
normal completion, on user abort, and on backend error/shutdown.

### 6.3 Chaos Injector interface (conceptual)

```java
interface ChaosInjector {
    ScenarioType type();
    void validate(ExperimentContext ctx);   // params + target preconditions
    InjectionHandle inject(ExperimentContext ctx);   // apply fault
    void revert(InjectionHandle handle);             // undo; idempotent
}
```

`InjectionHandle` records everything needed to undo the fault (helper
container IDs, prior container state, cgroup values, etc.) so revert never
depends on in-flight memory alone.

### 6.4 Target Adapter interface (the future-proofing seam)

```java
interface TargetAdapter {
    void verifyConnection();
    List<TargetWorkload> listWorkloads();      // containers now; pods later
    WorkloadState describe(String id);
    void pause(String id); void unpause(String id);
    void stop(String id);  void start(String id);
    String execDetached(String id, String[] cmd, Caps caps);
    String runHelper(HelperSpec spec);         // sidecar in target's netns
    Stats sampleStats(String id);              // one docker-stats sample
}
```

MVP implements `DockerTargetAdapter`. K8s/OpenShift adapters (roadmap) satisfy
the same interface, so the Chaos Engine and API never change.

---

## 7. Chaos scenarios (detailed)

All scenarios share common parameters: `durationSeconds` (required, 1–3600),
`baselineSeconds` (default 15). Each defines its own typed parameters, the
injection primitive, the revert, and the impact we expect to surface.

### 7.1 Network Failure

Degrade or sever the target container's network using Linux traffic control
(`tc` / `netem`). Because the target image may not contain `tc`, we run a
**helper container** that shares the target's network namespace
(`--net=container:<id>`, cap `NET_ADMIN`) using a small image that ships
`iproute2`. The helper applies `netem` rules to the target's interface.

**Modes (`mode`):**
| Mode | Effect | Key params |
|---|---|---|
| `LATENCY` | Add delay (± jitter) to egress | `latencyMs`, `jitterMs` |
| `PACKET_LOSS` | Drop a % of packets | `lossPercent` (0–100) |
| `BANDWIDTH` | Throttle throughput | `rateKbit` |
| `PARTITION` | Drop all traffic (full outage) | — |

**Inject:** helper runs `tc qdisc add dev <iface> root netem …`.
**Revert:** helper runs `tc qdisc del dev <iface> root`; remove helper container.
**Expected impact:** increased health-check latency / timeouts; elevated error
rate; possible dependency failures inside the app.

### 7.2 CPU Overhead

Saturate CPU inside the target so the app competes for cycles.

**Params:** `workers` (number of CPU-burn threads, default = target vCPUs),
`loadPercent` (target utilisation, default 100).
**Inject (primary):** `docker exec` a bounded CPU-burn command in the target
(prefer `stress-ng --cpu <workers> --cpu-load <loadPercent>`; if `stress-ng`
absent, fall back to a portable busy-loop shell/`sh` process). Process is
started detached and tracked by PID/exec-ID.
**Revert:** kill the burn process(es); if `stress-ng` was used it exits on its
own at duration, but revert still force-kills to guarantee cleanup.
**Expected impact:** CPU% approaching 100%, higher request latency, possible
throttling if a CPU cgroup limit exists.

> Note: MVP injects load **inside** the target container so the impact is
> realistic. A future toggle may instead *constrain* the container's CPU quota
> via `docker update --cpus` (cgroup throttling) — recorded in roadmap.

### 7.3 Memory (RAM) Overhead

Consume memory inside the target to induce pressure / OOM behaviour.

**Params:** `sizeMb` (bytes to allocate) **or** `percentOfLimit` (share of the
container memory limit), `holdSeconds` (usually = duration).
**Inject:** `docker exec` `stress-ng --vm 1 --vm-bytes <sizeMb>m --vm-hold`
(fallback: a small allocator process). The allocator touches pages so memory
is actually resident.
**Revert:** kill the allocator process; memory is reclaimed by the kernel.
**Expected impact:** memory usage climbs toward the limit; on a constrained
container the kernel OOM-killer may terminate a process (surfaced as a
container restart / health failure — exactly the effect we want to show).

### 7.4 Service Unavailable

Make the whole target unreachable for the duration, then restore it.

**Modes (`mode`):**
| Mode | Effect | Revert |
|---|---|---|
| `PAUSE` (default) | `docker pause` freezes all processes | `docker unpause` |
| `STOP` | `docker stop` (graceful) | `docker start` |
| `NETWORK_OFF` | Disconnect container from its networks | reconnect networks |

**Expected impact:** health check fails / times out for the duration;
availability drops to 0%; on restore, recovery time is measurable.

> `PAUSE` is the default because it is the fastest, cleanest, fully reversible
> way to simulate an unavailable service without losing container state.

---

## 8. Deployment topology & target connection (Docker)

### 8.1 MVP deployment topology (the concrete setup)

ChaosInjector runs on the **operator's local machine**, where a single Docker
engine is running. The **application under test runs as another container on
that same Docker engine.** ChaosInjector reaches it through the **same local
Docker daemon**:

```
┌──────────────────────── local machine ────────────────────────┐
│                                                                │
│   java -jar chaosinjector.jar   ── unix:///var/run/docker.sock ─┼─┐
│   (backend + bundled SPA, :8080)                               │ │
│                                                                │ │
│                      ┌──────────── Docker engine ─────────────┐│ │
│                      │  [ target app container ]  ◄───────────┼┼─┘ inject
│                      │  [ chaosinjector helper container(s) ] ││    + observe
│                      └────────────────────────────────────────┘│
└────────────────────────────────────────────────────────────────┘
```

Because ChaosInjector and the target share **one local daemon**, everything
the injectors need works directly and locally: `docker exec` into the target
(CPU/RAM), a helper container joined to the target's network namespace
(`--net=container:<id>`) for network faults, and `pause`/`stop`/network-
disconnect for unavailability. No remote transport is required for MVP.

ChaosInjector is expected to run **natively as the JAR** (not itself
containerised) with read/write access to `/var/run/docker.sock`. Running it in
a container is possible by bind-mounting the socket, but is not the default.

### 8.2 Docker daemon connection

The operator configures **one** Docker daemon connection per session:

- **Local socket** — `unix:///var/run/docker.sock` (**default; the MVP path**).
- **Remote TCP** — `tcp://host:2375` (plain) or `tcp://host:2376` with
  **TLS** (`ca.pem`, `cert.pem`, `key.pem` paths or uploads). Supported by the
  adapter but secondary to the local-socket flow above.

The UI lists the daemon's containers (id, name, image, status) so the operator
selects the target by clicking, rather than typing an id. A **health-check
URL** (any HTTP(S) endpoint that returns the app's health) is entered
separately and used for availability/latency measurement.

**Preconditions checked at VALIDATING:** daemon ping succeeds; selected
container exists and is `running`; scenario-specific caps are available (e.g.
ability to run a helper with `NET_ADMIN` for Network Failure).

---

## 9. Metrics & impact measurement

**Collector** samples on a fixed interval (`sampleIntervalMs`, default 1000):

- **Container stats** (docker-java stats stream): `cpuPercent`,
  `memUsageBytes`, `memLimitBytes`, `memPercent`, `netRxBytes`/`netTxBytes`,
  `blkRead`/`blkWrite`, `pids`, plus `restartCount` from container inspect.
- **Health probe:** HTTP GET the health URL → `httpStatus`, `latencyMs`,
  `reachable` (bool). Timeout = `probeTimeoutMs` (default 3000).

Samples are tagged with the **phase** (`BASELINE` / `ACTIVE` / `POST`) and a
timestamp. The **ImpactReport** computes, per metric:

- Baseline mean, active mean, delta and % change.
- Availability = fraction of `reachable` probes during ACTIVE.
- Latency p50/p95 per phase.
- `recoverySeconds` = time from revert to first healthy probe.
- A qualitative verdict per scenario (e.g. "app degraded but stayed
  available", "app went down and recovered in 4.2s", "no measurable impact").

All raw samples are retained for the experiment so the UI can chart them.

---

## 10. API specification (REST + SSE)

Base path `/api`. JSON. Errors use a common `{ code, message, details }` shape.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/targets/connect` | Validate & register a Docker daemon connection; returns a `connectionId`. |
| `GET`  | `/api/targets/{connectionId}/containers` | List containers on that daemon. |
| `GET`  | `/api/scenarios` | List scenarios + their parameter schemas (drives the dynamic form). |
| `POST` | `/api/experiments/validate` | Dry-run validation of a full experiment request. |
| `POST` | `/api/experiments` | Create & start an experiment → `{ experimentId }`. |
| `GET`  | `/api/experiments/{id}` | Current state + latest report snapshot. |
| `GET`  | `/api/experiments/{id}/stream` | **SSE** live feed: phase changes + metric samples. |
| `POST` | `/api/experiments/{id}/abort` | Abort → triggers REVERTING. |
| `GET`  | `/api/experiments` | List recent experiments (history, in-memory). |

**Concurrency guard (MVP):** at most **one ACTIVE experiment per target
container** (reject overlapping injections with `409 CONFLICT`). A global
single-active-experiment option is configurable.

**SSE event types:** `phase` (`{state}`), `sample`
(`{phase, ts, metrics}`), `log` (`{level, message}`), `completed`
(`{report}`), `error` (`{code, message}`).

---

## 11. UI specification (React)

Three primary screens plus a persistent connection indicator.

### 11.1 Connect screen
- Form: socket vs TCP, host, TLS files. **Test connection** button →
  `POST /targets/connect`. On success, show daemon info and proceed.

### 11.2 Configure experiment screen
- **Target section:** searchable container list (name/image/status), select
  one; health-check URL field; optional friendly experiment name.
- **Scenario section:** four cards (Network Failure, CPU Overhead, Memory
  Overhead, Service Unavailable). Selecting one reveals its parameter form,
  rendered dynamically from `/scenarios` schema, with sane defaults and
  min/max hints.
- **Common controls:** `durationSeconds`, `baselineSeconds`,
  `sampleIntervalMs`.
- **Review & Inject:** a summary panel ("You are about to add 300ms latency to
  `payments-api` for 60s") and a prominent **Inject Chaos** button, with an
  explicit "this will disrupt the target" acknowledgement.

### 11.3 Run / result screen
- **Live phase indicator** (BASELINE → INJECTING → ACTIVE → REVERTING …) driven
  by SSE.
- **Live charts:** CPU%, memory%, health latency, availability — baseline band
  vs active band shown distinctly.
- **Big red ABORT button** while ACTIVE.
- **On completion — ImpactReport:** what was injected, per-metric
  before/during/after deltas, availability %, recovery time, and the
  qualitative verdict. Option to view raw samples and to run again.

The UI holds **no** chaos logic — it renders schemas, sends requests, and
visualises the SSE stream.

---

## 12. Safety, guardrails & rollback

1. **Bounded duration** — every experiment has a hard `durationSeconds`; a
   server-side watchdog reverts even if the client disconnects.
2. **Guaranteed revert** — revert runs on completion, abort, backend
   exception, and JVM shutdown hook. Revert is idempotent.
3. **Helper cleanup** — helper containers are labelled
   (`chaosinjector.experiment=<id>`) and force-removed on revert and on
   startup (orphan sweep).
4. **Blast-radius limits** — one target container per experiment; parameter
   ranges are validated and clamped.
5. **Explicit confirmation** — the UI requires acknowledging disruption before
   injecting.
6. **Dry-run** — `/experiments/validate` lets the operator confirm
   preconditions without injecting.
7. **Audit log** — every phase transition and injected/reverted command is
   logged with the experiment id.

---

## 13. Configuration

Backend config via `application.yml` / env vars:

- `chaosinjector.docker.defaultHost`
- `chaosinjector.experiment.maxDurationSeconds` (hard cap, default 3600)
- `chaosinjector.experiment.singleActiveGlobal` (bool)
- `chaosinjector.metrics.sampleIntervalMs`, `probeTimeoutMs`
- `chaosinjector.helper.image` (the iproute2/stress-ng helper image name)
- Secrets (TLS material, remote creds) are never logged; support `${ENV_VAR}`
  interpolation.

## 14. Persistence (MVP)

In-memory experiment store (recent-N history, configurable). No database in
MVP. Persistence to a store is a roadmap item so history survives restarts.

## 15. Error handling

- Typed backend exceptions → consistent API error payloads:
  `ConnectionError`, `ValidationError`, `TargetNotFoundError`,
  `InjectionError`, `RevertError`, `MetricsError`.
- A `RevertError` is escalated (logged loudly, surfaced in the report) because
  it may mean the target is left altered — the one condition the tool must
  never hide.

## 16. Non-functional requirements

- **Reversibility** is the top invariant (see §12).
- Metrics sampling adds negligible load to the target.
- Backend responsive during an active experiment (injection/metrics run on
  separate threads; SSE non-blocking).
- Cross-platform Docker support (Linux daemon primary; Docker Desktop on
  Mac/Windows best-effort).

## 17. Project structure

Two **independent** top-level projects (`backend/` and `frontend/`), each
opened and debugged in its own IDE session. The Maven build in `backend/`
bundles the built frontend into the release JAR (§5).

```
chaosinjector/
├── chaos_injector_spec.md          # this file
├── README.md
│
├── backend/                        # Spring Boot + Maven — own IDE project
│   ├── pom.xml
│   ├── src/main/java/com/chaosinjector/
│   │   ├── api/                    # controllers, DTOs, SSE
│   │   ├── experiment/             # state machine, service, store
│   │   ├── engine/                 # ChaosInjector impls (4 scenarios)
│   │   ├── target/                 # TargetAdapter + DockerTargetAdapter
│   │   ├── metrics/                # collector, ImpactReport
│   │   └── config/                 # properties, exceptions
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── static/                 # frontend/dist copied here at release build
│   └── src/test/java/...           # JUnit + Testcontainers
│
└── frontend/                       # React + Vite — own IDE project
    ├── package.json
    ├── vite.config.js              # dev proxy /api → backend :8080
    └── src/
        ├── screens/                # Connect, Configure, Run/Result
        ├── components/             # scenario forms, charts
        └── api/                    # REST + SSE clients
```

**Dev:** `cd backend && ./mvnw spring-boot:run` and, separately,
`cd frontend && npm run dev` — two processes, two IDE sessions.
**Release:** `cd backend && ./mvnw -Pprod package` runs the frontend build,
copies `frontend/dist` into `static/`, and produces the single fat JAR.

## 18. MVP acceptance criteria

1. Operator connects to a Docker daemon (local socket) from the UI and sees the
   container list.
2. Each of the four scenarios can be injected against a running container with
   configurable parameters and a set duration.
3. Every experiment records a baseline, streams live metrics to the UI, and is
   **automatically and verifiably reverted** at the end (target unchanged).
4. Abort mid-experiment reverts immediately.
5. The Run/Result screen shows an ImpactReport with before/during/after CPU,
   memory, latency, availability, recovery time, and a verdict.
6. No chaos logic in the React app; CLI-less single-JAR + SPA deployable.
7. Integration tests (Testcontainers) prove inject→observe→revert for all four
   scenarios leaves the target in its original state.

## 19. Future roadmap (post-MVP)

- **Kubernetes** and **OpenShift** target adapters (pod/deployment targeting,
  scale-to-zero for unavailability, SCC handling on OpenShift).
- Additional scenarios: disk I/O stress, clock skew, dependency/DNS failure,
  process kill, container restart storms.
- Orchestrate mature frameworks (Pumba, Chaos Mesh, Litmus) as an alternative
  engine behind the same interface.
- Prometheus / APM metric sources for richer impact analysis.
- Persistent experiment history (database), scheduled & recurring experiments,
  experiment templates and GameDay campaigns.
- Multi-user auth, RBAC, and audit trails.

---

## Decisions confirmed

- **Backend language:** Java + Spring Boot. ✅
- **Deployment:** single fat JAR serving the bundled SPA, run natively on the
  local machine against the local Docker socket. ✅
- **Repository layout:** two independent dev projects (`frontend/`, `backend/`)
  that build into that one JAR. ✅
- **Topology:** ChaosInjector and the target container share one local Docker
  daemon (§8.1). ✅

- **Helper image:** ChaosInjector maintains a small helper image (iproute2 +
  stress-ng); injectors pull/run it so the target image stays untouched. ✅
- **CPU/RAM injection stance:** inject load *inside* the target container for
  realistic impact (§7.2/§7.3); cgroup-throttle mode is a roadmap toggle. ✅
