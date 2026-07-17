# CLAUDE.md — ChaosInjector

Operating guide for **Claude Code** building this project. This file is durable
project memory: read it fully before acting, and keep it in sync if conventions
change. The authoritative, detailed requirements live in
**`chaos_injector_spec.md`** (referenced below as *SPEC §n*) — when this file
and the spec disagree, the spec wins for *scenario behaviour, API shape, and
output/report format*; this file wins for *build process and conventions*.

> **Scope note:** This file governs the **ChaosInjector application** only. The
> `sre-demo/` directory is a **separate sub-project** (a Chaos + Observability +
> SRE-Agent demo) with its **own** operating guide and spec — see
> `sre-demo/CLAUDE.md`, `sre-demo/sre_demo_spec.md`, and
> `sre-demo/IMPLEMENTATION_PLAN.md`. Do not apply this ChaosInjector guide to
> work under `sre-demo/`.

## What you are building
**ChaosInjector** — a chaos-engineering tool that injects a fault into a
running application and reports the observed impact. A **React** UI drives a
**Java + Spring Boot** engine that injects one of four scenarios (Network
Failure, CPU Overhead, Memory Overhead, Service Unavailable) into a container
on the **local Docker daemon**, samples metrics before/during/after, and shows
the impact. ChaosInjector **injects and observes only — it never fixes,
self-heals, or remediates the target.**

## Non-negotiable invariants (violating any of these is a defect)
1. **Every injection is bounded and reversible.** Each experiment has a hard
   `durationSeconds`; a server-side watchdog reverts even if the client
   disconnects. `revert()` is **idempotent** and runs on completion, abort,
   backend exception, and JVM shutdown. After REVERTING the target must be
   byte-for-byte as before (no leftover helper containers, exec processes,
   cgroup changes, or netem qdiscs). A failed revert is escalated loudly
   (*SPEC §12, §15*) — never hidden.
2. **One engine.** The React UI holds **zero** chaos logic. All behaviour lives
   in the Spring Boot backend behind the REST + SSE API (*SPEC §10*). The UI
   renders scenario schemas, sends requests, and visualises the SSE stream.
3. **Pluggable target layer.** All target interaction goes through the
   `TargetAdapter` interface (*SPEC §6.4*). MVP ships **only**
   `DockerTargetAdapter`; the engine and API must never reference Docker types
   directly, so K8s/OpenShift adapters drop in later without touching them.
4. **Own primitives, local daemon.** Inject with our own primitives against the
   **local** `unix:///var/run/docker.sock` (*SPEC §7, §8*): `tc/netem` via a
   helper container joined to the target's netns; `stress-ng` (with a portable
   fallback) via `docker exec` inside the target; `pause`/`stop`/network-
   disconnect for unavailability. Do not orchestrate third-party chaos
   frameworks in MVP.
5. **Helper cleanup is guaranteed.** Helper containers are labelled
   `chaosinjector.experiment=<id>`; they are force-removed on revert **and**
   swept on backend startup (orphan sweep). The target image is never modified.
6. **Two projects, one JAR.** `frontend/` and `backend/` are independent dev
   projects (own IDE sessions, own build). The release build bundles
   `frontend/dist` into the backend's `static/` to produce a single runnable
   fat JAR (*SPEC §5, §17*). Do not merge them into one project.
7. **Impact is measured, not asserted.** Report values come from real samples —
   `docker stats` + health-probe — tagged by phase (BASELINE/ACTIVE/POST), per
   *SPEC §9*. No fabricated or hardcoded metrics.
8. **Secrets.** Support `${ENV_VAR}` interpolation in config strings. Never
   hardcode credentials or TLS material; never log resolved secrets.

## Tech stack (do not substitute)
**Backend:** Java 17 · Spring Boot 3.x · Spring Web (REST) + SSE ·
`docker-java` · Jakarta Bean Validation · Maven (wrapper `./mvnw`) · JUnit 5 ·
Mockito · **Testcontainers** for integration tests against a real daemon.
**Frontend:** React 18 (JavaScript) · Vite · React Router · `fetch` +
`EventSource` · Recharts (time-series). Full module map: *SPEC §17*.

## Commands (use these; don't invent your own)
```bash
# Backend (own IDE session) — from backend/
./mvnw spring-boot:run                 # run API on :8080 (dev)
./mvnw test                            # unit + integration (needs Docker for IT)
./mvnw -Pprod package                  # build frontend + bundle SPA → single fat JAR

# Frontend (own IDE session) — from frontend/
npm install
npm run dev                            # Vite dev server on :5173, proxies /api → :8080
npm run build                          # production build → frontend/dist
npm test                               # component tests

# Release artifact
java -jar backend/target/chaosinjector.jar   # serves API + SPA on :8080
```
Format/lint: `spotless`/`checkstyle` (backend) and ESLint/Prettier (frontend)
if configured. Type-hint/JavaDoc public APIs.

## Conventions
- **Package root** `com.chaosinjector`, sub-packages per *SPEC §17*
  (`api`, `experiment`, `engine`, `target`, `metrics`, `config`).
- **Scenario engine:** one class per scenario implementing the `ChaosInjector`
  interface (`type` / `validate` / `inject` → `InjectionHandle` / `revert`)
  from *SPEC §6.3*. `InjectionHandle` must persist everything needed to undo
  the fault so revert never relies on in-flight memory alone.
- **State machine:** experiment transitions exactly per *SPEC §6.2*
  (`CREATED→VALIDATING→BASELINE→INJECTING→ACTIVE→REVERTING→COMPLETED`, plus
  `FAILED`/`ABORTED`). REVERTING is always attempted from any active/failed
  state.
- **API:** REST paths + SSE event types exactly per *SPEC §10*. One ACTIVE
  experiment per target container (`409` on overlap).
- **Typed exceptions only** (`config` package): `ConnectionError`,
  `ValidationError`, `TargetNotFoundError`, `InjectionError`, `RevertError`,
  `MetricsError` — each carrying a user-facing message and mapped to the
  `{code, message, details}` API error shape.
- **Config** via `application.yml` + env vars (*SPEC §13*); `${ENV_VAR}`
  interpolation; secrets never logged.
- **Logging:** SLF4J; every phase transition and injected/reverted command is
  logged with the experiment id (audit trail, *SPEC §12*).
- **Version** single-sourced in the backend `pom.xml`; surfaced by a
  `/api/version` or actuator info endpoint.

## Build plan — work phase by phase
Track these as todos. **After each phase, run its verification and do not start
the next until it is green.** Each phase leaves both projects buildable and
tests passing.

**Phase 0 — Scaffold (two projects).** Create `backend/` (Spring Boot + Maven,
package skeleton from *SPEC §17*, `application.yml`, health/version endpoint,
`./mvnw`) and `frontend/` (Vite + React 18, router, three empty screens, dev
proxy `/api`→`:8080`). Wire the `-Pprod` Maven profile that runs the Vite build
and copies `frontend/dist` → `backend/.../static`.
*Verify:* `./mvnw spring-boot:run` serves the health endpoint; `npm run dev`
serves the shell and proxies `/api`; `./mvnw -Pprod package` yields a JAR that
serves both API and SPA on `:8080`.

**Phase 1 — Domain, config & exceptions.** `experiment` domain types
(`Experiment`, `Target`, `ScenarioType`, parameter DTOs), the state-machine
enum (*SPEC §6.2*), the typed exception hierarchy, config properties with
`${ENV_VAR}` interpolation, and Bean Validation on request DTOs.
*Verify:* unit tests for state-machine legal/illegal transitions, config
interpolation, and DTO validation (param ranges from *SPEC §7*) pass.

**Phase 2 — Target adapter (Docker).** `TargetAdapter` interface (*SPEC §6.4*)
+ `DockerTargetAdapter` over `docker-java`: verify connection, list/describe
containers, `pause`/`unpause`/`stop`/`start`, `execDetached`, `runHelper`
(netns-shared, labelled), `sampleStats`, network connect/disconnect. Engine/API
depend only on the interface (invariant 3).
*Verify:* Testcontainers IT — connect to the daemon, list a running container,
sample stats, run+remove a labelled helper. Orphan-sweep removes stray helpers.

**Phase 3 — Metrics collector & ImpactReport.** `metrics` package: periodic
sampler (stats + health probe) tagging samples by phase, and the `ImpactReport`
computation (baseline vs active means/deltas, availability, latency p50/p95,
recovery time, verdict) per *SPEC §9*.
*Verify:* unit tests over synthetic sample series assert every derived field;
an IT samples a real container's stats.

**Phase 4 — Chaos injectors (the four scenarios).** Implement each
`ChaosInjector` per *SPEC §7*: Network Failure (netem helper: latency / loss /
bandwidth / partition), CPU Overhead (stress-ng + fallback), Memory Overhead
(stress-ng `--vm` + fallback), Service Unavailable (pause / stop /
network-off). Each with a real `revert()`.
*Verify:* Testcontainers IT per scenario proves **inject → observe measurable
effect → revert → target back to original state** (invariant 1). Assert no
labelled helper/exec/qdisc remains after revert.

**Phase 5 — Experiment service & orchestration.** `experiment` service drives
the state machine end to end: validate → baseline → inject → hold for duration
(watchdog) → revert → report; abort path; single-active-per-target guard;
guaranteed revert on error/shutdown; audit logging.
*Verify:* IT runs a full experiment on a real container and asserts phase order,
auto-revert at duration, abort-reverts-immediately, and a populated report.

**Phase 6 — REST + SSE API.** `api` controllers + DTOs for every endpoint in
*SPEC §10*, the SSE stream (`phase`/`sample`/`log`/`completed`/`error`), the
scenario-schema endpoint that drives the UI form, and the `{code,message,
details}` error mapping.
*Verify:* MockMvc/WebTestClient tests cover connect, list containers,
scenarios, validate, create→stream→completed, and abort; overlap returns `409`.

**Phase 7 — React UI.** The three screens (*SPEC §11*): Connect (daemon +
optional TLS), Configure (container picker, health URL, scenario cards with
schema-driven param forms, common controls, review + acknowledge), Run/Result
(live phase indicator, live charts, ABORT, ImpactReport). REST + `EventSource`
clients. No chaos logic in the UI (invariant 2).
*Verify:* component tests for the dynamic scenario form and SSE-driven
dashboard; manual smoke: connect → configure → inject → live phases → report.

**Phase 8 — End-to-end & packaging.** Full flow through the release JAR against
a real target container on the local daemon; the ChaosInjector helper image
(iproute2 + stress-ng) defined and referenced by config; optional Dockerfile
for running ChaosInjector itself (socket bind-mount).
*Verify:* `./mvnw -Pprod package` → `java -jar` serves UI+API; a real
experiment for each scenario shows live metrics and a correct ImpactReport, and
the target is verifiably unchanged afterward.

**Phase 9 — Docs.** `README.md`: prerequisites (local Docker), the two-project
dev workflow, the single-JAR release build/run, the helper image, a walkthrough
per scenario, config reference, and the safety/rollback model.
*Verify:* a new reader can build both projects, produce the JAR, and run one
scenario end to end from the README alone.

## Definition of done
All of *SPEC §18* (MVP acceptance criteria) holds; both projects build; the
`-Pprod` JAR serves UI+API; every scenario injects, reports real impact, and
**verifiably reverts** leaving the target unchanged; the full test suite
(including Testcontainers ITs) is green.

## When unsure
For exact scenario parameters/behaviour, the API/SSE shape, the state machine,
the `AnnotationRecord`-equivalent report fields, or the target-adapter contract,
consult `chaos_injector_spec.md` rather than guessing. If a genuine ambiguity
remains after reading the spec, state the assumption in a code comment and keep
moving — do not block.
