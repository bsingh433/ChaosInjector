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
| A | Observability stack + apps + database | completed (static-verified; run on user machine) |
| B | SRE Agent — read-only RCA (pluggable LLM) | completed (unit-verified; live LLM run on user machine) |
| C | SRE Agent — gated reversible remediation | not-started |
| D | Packaging, overall compose, docs | not-started |

Update this table when a phase's tasks are all `completed`.

---

## Phase A — Observability stack + apps + database

| ID | Task | Status | Verification / notes |
|----|------|--------|----------------------|
| A1 | `mongo/` — Dockerfile (`FROM mongo:7`) + `init/seed.js` (create `sampleapp` DB, `items` collection, seed docs) + README | completed | files created |
| A2 | `downstream/` — Flask `/compute` (random delay, small error rate) + `/metrics` + requirements + Dockerfile + README | completed | py_compile OK |
| A3 | `sample-app/` — Flask app: `/`, `/health` (pings Mongo), `/work` (calls downstream), `/metrics` | completed | py_compile OK |
| A4 | `sample-app/` — MongoDB CRUD (`POST/GET/GET id/PUT/DELETE /items`) via pymongo | completed | py_compile OK; manual CRUD test pending on user machine |
| A5 | `sample-app/` — metrics: http_*, downstream_*, `db_operation_duration_seconds`, `db_errors_total` | completed | py_compile OK |
| A6 | `sample-app/` — self-load loop exercising `/work` + CRUD; requirements; Dockerfile; README | completed | py_compile OK |
| A7 | `prometheus/` — `prometheus.yml` (jobs: sample-app, downstream, cadvisor, self; 5s interval) + Dockerfile + README | completed | YAML valid |
| A8 | `grafana/` — provisioning (datasource + dashboards loader) + `chaos-overview.json` dashboard + Dockerfile + README | completed | dashboard JSON + provisioning YAML valid |
| A9 | Root `docker-compose.yml` — services: mongo, downstream, sample-app, cadvisor, prometheus, grafana; network; volumes; ports; `depends_on`; mem_limit | completed | `docker compose config` passes |
| A10 | Root `.env.example` (LLM provider + Azure/OpenAI/Anthropic creds placeholders + demo toggles) | completed | Azure api-version default 2025-04-01-preview; no real secrets |
| A11 | Root `README.md` — run sequence (compose up → URLs → inject chaos → observe) | completed | doc written |
| A12 | **Phase A verification gate** | completed | static checks pass here (syntax/JSON/YAML/compose config); full `docker compose up` + CRUD + Grafana + chaos-moves-metrics to be run on the user's machine |

---

## Phase B — SRE Agent: read-only RCA (pluggable LLM)

| ID | Task | Status | Verification / notes |
|----|------|--------|----------------------|
| B1 | `sre-agent/` Maven scaffold (pom.xml, Java 17, main class, application.yml, Dockerfile skeleton) | completed | `mvn compile` OK |
| B2 | Provider-neutral LLM layer: `LlmClient` interface + `LlmRequest`/`LlmResponse`/`ToolCall`/`ToolSpec` types | completed | Messages.java + LlmClient; unit-tested |
| B3 | Tool framework: provider-neutral tool definition (name, description, JSON-schema, handler) + registry | completed | Tool + ToolRegistry |
| B4 | `AzureResponsesLlmClient` — Responses API (`/openai/responses?api-version=2025-04-01-preview`, model in body, `api-key`), tool-call translation | completed | mocked-HTTP unit test passes; live test on user machine |
| B5 | `OpenAiResponsesLlmClient` — `api.openai.com/v1/responses` (shares shape with Azure) | completed | shares ResponsesApiLlmClient; factory-tested |
| B6 | `AnthropicMessagesClient` — `api.anthropic.com/v1/messages` tool blocks | completed | mocked-HTTP unit test passes |
| B7 | Config-driven provider selection (`llm.provider`, `llm.model`, per-provider creds via env; `${ENV_VAR}`; never log secrets) | completed | LlmClientFactory + application.yml; unit-tested |
| B8 | Read-only tools: `prometheus_instant`, `prometheus_range`, `list_targets`, `container_stats`, `recent_changes`, `container_logs` | completed | PrometheusClient + DockerFacade + tools; compile OK; live on user machine |
| B9 | Agent loop: gather → reason (LLM tool-calls) → validate → structured RCA output (schema from spec §6.6) | completed | RcaAgent + RcaReport; loop unit-tested (scripted LLM + fake tool) |
| B10 | Entry points: CLI (`--analyze --window`) + HTTP endpoint (`POST /api/analyze`, `GET /api/health`) | completed | AnalyzeController + CliRunner |
| B11 | `sre-agent/` Dockerfile (multi-stage) + README | completed | files created |
| B12 | **Phase B verification gate** | completed | 9 unit tests green (adapters, provider selection, agent loop). Live RCA against a real injected fault + provider switch to be run on the user's machine (needs an Azure/OpenAI/Anthropic key). |

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
- 2026-07-17 — Phase A implemented (mongo, downstream, sample-app+CRUD,
  prometheus, grafana, overall compose, .env.example, README). Static checks
  pass (py_compile, dashboard JSON, all YAML, `docker compose config`). Full
  `docker compose up` verification pending on the user's machine.
- 2026-07-17 — Phase A verified on the user's Mac (Grafana panels live);
  fixes applied: host port 5001 (macOS AirPlay), cAdvisor /dev/kmsg for
  Docker Desktop.
- 2026-07-17 — Phase B implemented: provider-neutral LLM layer (Azure Responses
  default + OpenAI + Anthropic adapters, config-selected), read-only Prometheus
  + Docker tools, agentic RCA loop with structured RcaReport, HTTP + CLI entry.
  9 unit tests green (mvn test). Live LLM RCA + provider switch pending on the
  user's machine (requires an LLM API key).
