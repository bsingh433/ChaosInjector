# CLAUDE.md — SRE Demo (sre-demo/)

Operating guide for **Claude Code** building the **Chaos + Observability + SRE
Agent demo** under `sre-demo/`. This is a **separate sub-project** from the
ChaosInjector application.

## Scope & authority (read this first)
- **This sub-project is governed by its own docs, not the repo-root
  `CLAUDE.md`.** The root `CLAUDE.md` / `chaos_injector_spec.md` describe the
  ChaosInjector Java/Spring app; they do **not** apply here.
- **Authoritative for design:** [`sre_demo_spec.md`](./sre_demo_spec.md).
- **Authoritative for build tracking:** [`IMPLEMENTATION_PLAN.md`](./IMPLEMENTATION_PLAN.md).
  Update each task's `Status` (`not-started` → `in-progress` → `completed`) as
  you work, so the build is resumable after any interruption.
- If this file and the spec disagree, the **spec wins** for architecture,
  component behaviour, and config shape; **this file wins** for build process
  and conventions.

## What is being built
A local, one-command demo that closes the loop **fault → observe → RCA → fix**:
an instrumented **sample app** (Flask + MongoDB CRUD) and a **downstream**
service, observed by **Prometheus + Grafana** (+ cAdvisor); **ChaosInjector**
injects a fault; a Java **SRE Agent** reads the metrics, performs RCA, and
proposes **gated, reversible** fixes. ChaosInjector is the ground truth — the
agent is **not told** what was injected.

## Non-negotiable conventions
1. **Strict component separation.** Each component (`sample-app/`, `downstream/`,
   `mongo/`, `prometheus/`, `grafana/`, `sre-agent/`) is a self-contained folder
   with its **own Dockerfile and README**, buildable on its own, and destined to
   become its **own Git repo**. No component imports code/files from a sibling —
   they communicate only over the network (HTTP / PromQL / Docker API).
2. **One overall `docker-compose.yml`** at the demo root brings up every service,
   each in **its own container**. Document both `build: ./<folder>` (now) and the
   `image:` form (after repo-split).
3. **Model-agnostic LLM.** The SRE Agent talks only to a provider-neutral
   `LlmClient` interface; providers are thin HTTP adapters selected by config.
   Default **Azure OpenAI Responses API** (model in request body, api-version
   **`2025-04-01-preview`**); also OpenAI-direct and Anthropic/Claude. Adding a
   provider = one new adapter, no engine change.
4. **Secrets via env only.** `${ENV_VAR}` interpolation; never hardcode or log
   keys/tokens. Only the selected LLM provider's credentials need to be present.
5. **SRE Agent safety (mirrors ChaosInjector's ethos).** Read-only by default;
   remediation is **gated + reversible** (restart / unpause / reconnect / abort
   chaos) behind human confirmation; the model proposes, typed Java code
   executes; full audit trail.
6. **Spec-first.** Do not implement ahead of the spec + plan. Finalize spec
   changes, then build phase by phase, updating the plan's statuses.

## Tech stack (per component)
- `sample-app/`, `downstream/` — Python 3.11 + Flask + `prometheus_client`;
  sample-app also uses `pymongo`.
- `mongo/` — official `mongo` image + init/seed script.
- `prometheus/`, `grafana/` — official images + baked config / provisioning.
- `sre-agent/` — Java 17 + Maven; thin HTTP LLM adapters (`java.net.http` +
  Jackson — **not** vendor SDKs); Docker + Prometheus client tools.

## Build phases (see IMPLEMENTATION_PLAN.md for task-level status)
- **A** — observability stack + apps + MongoDB
- **B** — SRE Agent read-only RCA (pluggable LLM)
- **C** — SRE Agent gated reversible remediation
- **D** — packaging, overall compose, docs

Each phase has a verification gate; do not start the next until the current one
is green.

## Build/verification environment note
The cloud build sandbox **cannot pull Docker images or run `docker compose`**.
Verify here with config-lint + code-compile + unit tests; the full
`docker compose up` runs on the user's machine. Note which verification applies
per task.
