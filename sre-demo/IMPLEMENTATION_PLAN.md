# Implementation Plan — Chaos + Observability + SRE Agent Demo

Living checklist for building the system described in `sre_demo_spec.md`.
**Update the `Status` of each task as work proceeds** so the build can be resumed
after any interruption by reading this file.

**Status legend:** `not-started` · `in-progress` · `completed`
(a task may also carry a short note, e.g. "completed — verified locally", or
"blocked — needs X").

**Confirmed decisions**
- Repo location: under `sre-demo/` in the ChaosInjector repo (splittable later).
- LLM: provider-agnostic; default **Azure OpenAI Responses API**, model in body.
- **Azure API version: `2025-04-01-preview`** (confirmed).
- Also ship `openai-responses` and `anthropic-messages` adapters.
- SRE Agent: Java 17 + Maven; read-only RCA + gated reversible remediation.
- Strict separation: each component = own folder + own Dockerfile + README.

**Sandbox caveat:** this build environment cannot pull Docker images or run
`docker compose`, so verification here is **config-lint + code-compile + unit
tests**; the full `docker compose up` runs on the user's machine. Tasks note
which verification applies.

---

## Status summary (rollup)

| Phase | Description | Status |
|---|---|---|
| A | Observability stack + apps + database | not-started |
| B | SRE Agent — read-only RCA (pluggable LLM) | not-started |
| C | SRE Agent — gated reversible remediation | not-started |
| D | Packaging, overall compose, docs | not-started |

Update this table when a phase's tasks are all `completed`.

---

## Phase A — Observability stack + apps + database

| ID | Task | Status | Verification / notes |
|----|------|--------|----------------------|
| A1 | `mongo/` — Dockerfile (`FROM mongo:7`) + `init/seed.js` (create `sampleapp` DB, `items` collection, seed docs) + README | not-started | `docker build` on user machine; seed runs on first start |
| A2 | `downstream/` — Flask `/compute` (random delay, small error rate) + `/metrics` + requirements + Dockerfile + README | not-started | `python -m py_compile`; runs on user machine |
| A3 | `sample-app/` — Flask app: `/`, `/health` (pings Mongo), `/work` (calls downstream), `/metrics` | not-started | py_compile |
| A4 | `sample-app/` — MongoDB CRUD (`POST/GET/GET id/PUT/DELETE /items`) via pymongo | not-started | py_compile; manual CRUD test on user machine |
| A5 | `sample-app/` — metrics: http_*, downstream_*, `db_operation_duration_seconds`, `db_errors_total` | not-started | py_compile; visible in `/metrics` |
| A6 | `sample-app/` — self-load loop exercising `/work` + CRUD; requirements; Dockerfile; README | not-started | py_compile |
| A7 | `prometheus/` — `prometheus.yml` (jobs: sample-app, downstream, cadvisor, self; 5s interval) + Dockerfile + README | not-started | `promtool check config` if available; else YAML lint |
| A8 | `grafana/` — provisioning (datasource + dashboards loader) + `chaos-overview.json` dashboard + Dockerfile + README | not-started | JSON valid; dashboard loads on user machine |
| A9 | Root `docker-compose.yml` — services: mongo, downstream, sample-app, cadvisor, prometheus, grafana; network; volumes; ports; `depends_on`; mem_limit | not-started | `docker compose config` (lint) if available; else YAML review |
| A10 | Root `.env.example` (LLM provider + Azure/OpenAI/Anthropic creds placeholders + demo toggles) | not-started | no real secrets |
| A11 | Root `README.md` — run sequence (compose up → URLs → inject chaos → observe) | not-started | doc review |
| A12 | **Phase A verification gate** | not-started | Stack builds; CRUD works against Mongo; Grafana panels live; chaos moves metrics (run on user machine) |

---

## Phase B — SRE Agent: read-only RCA (pluggable LLM)

| ID | Task | Status | Verification / notes |
|----|------|--------|----------------------|
| B1 | `sre-agent/` Maven scaffold (pom.xml, Java 17, main class, application.yml, Dockerfile skeleton) | not-started | `mvn compile` |
| B2 | Provider-neutral LLM layer: `LlmClient` interface + `LlmRequest`/`LlmResponse`/`ToolCall`/`ToolSpec` types | not-started | compile + unit tests |
| B3 | Tool framework: provider-neutral tool definition (name, description, JSON-schema, handler) + registry | not-started | unit tests |
| B4 | `AzureResponsesLlmClient` — Responses API (`/openai/responses?api-version=2025-04-01-preview`, model in body, `api-key`/AAD), tool-call translation | not-started | unit test with mocked HTTP; live test on user machine |
| B5 | `OpenAiResponsesLlmClient` — `api.openai.com/v1/responses` (shares shape with Azure) | not-started | unit test (mocked HTTP) |
| B6 | `AnthropicMessagesClient` — `api.anthropic.com/v1/messages` tool blocks | not-started | unit test (mocked HTTP) |
| B7 | Config-driven provider selection (`llm.provider`, `llm.model`, per-provider creds via env; `${ENV_VAR}`; never log secrets) | not-started | unit tests for selection + interpolation |
| B8 | Read-only tools: `prometheus_instant`, `prometheus_range`, `list_targets`, `container_stats`, `recent_changes`, `container_logs` | not-started | unit tests w/ fake Prometheus/Docker; live on user machine |
| B9 | Agent loop: gather → reason (LLM tool-calls) → validate → structured RCA output (schema from spec §6.6) | not-started | unit test w/ stubbed LlmClient returning scripted tool calls |
| B10 | Entry points: CLI (`analyze --window 10m`) + thin HTTP endpoint (`POST /analyze`) | not-started | compile; manual on user machine |
| B11 | `sre-agent/` Dockerfile (multi-stage) + README | not-started | build on user machine |
| B12 | **Phase B verification gate** | not-started | Agent produces RCA whose top hypothesis matches the injected fault, with cited evidence; provider switch works (user machine) |

---

## Phase C — SRE Agent: gated reversible remediation

| ID | Task | Status | Verification / notes |
|----|------|--------|----------------------|
| C1 | Remediation tools (reversible): `restart_container`, `unpause_container`, `reconnect_networks`, `abort_chaos` (ChaosInjector API) | not-started | unit tests w/ fake Docker / ChaosInjector client |
| C2 | Human confirmation gate for remediation tools (CLI prompt + `--auto-approve` flag); read-only tools never prompt | not-started | unit tests |
| C3 | Audit log of every proposed + executed action | not-started | unit test asserts audit entries |
| C4 | RCA output carries `recommendedFixes[].proposedAction` (reversible tool) | not-started | unit test |
| C5 | **Phase C verification gate** | not-started | Agent proposes a correct reversible fix; on approval applies it and metrics recover; declining changes nothing (user machine) |

---

## Phase D — Packaging, overall compose, docs

| ID | Task | Status | Verification / notes |
|----|------|--------|----------------------|
| D1 | Add `sre-agent` (+ optional `chaosinjector`) to the overall `docker-compose.yml`; socket mount; port remaps (avoid 8080 clash); `.env` wiring | not-started | `docker compose config` |
| D2 | Each component README finalized; document `build:`→`image:` switch for repo-split | not-started | doc review |
| D3 | Root README: full end-to-end run + each chaos scenario walkthrough + expected RCA | not-started | doc review |
| D4 | Verify all folders build independently (`docker build .` each) + no secrets in repo | not-started | user machine |
| D5 | **Definition of done** — spec §13 acceptance criteria all hold | not-started | user machine end-to-end |

---

## Change log

- 2026-07-17 — plan created; awaiting user confirmation to start Phase A.
