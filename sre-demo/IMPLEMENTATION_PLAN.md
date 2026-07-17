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
| C | SRE Agent — gated reversible remediation | completed (unit-verified; live run on user machine) |
| D | Packaging, overall compose, docs | completed (static-verified; full run on user machine) |

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
| C1 | Remediation tools (reversible): `restart_container`, `unpause_container`, `start_container`, `abort_chaos` (ChaosInjector API). Note: network-off is reverted by `abort_chaos` (ChaosInjector reconnects), so a separate `reconnect_networks` tool was not needed. | completed | ChaosInjectorClient + DockerFacade; compile OK |
| C2 | Confirmation gate for remediation tools (modes: propose / prompt / auto); read-only tools never prompt | completed | ConfirmationGate; unit-tested (propose = not executed, auto = executed) |
| C3 | Audit log of every proposed + executed action (`GET /api/audit`) | completed | AuditLog; unit test asserts PROPOSED/APPROVED entries |
| C4 | RCA output carries `recommendedFixes[].proposedAction` (reversible tool); system prompt instructs the model to fill it | completed | RcaReport.ProposedAction + prompt update |
| C5 | **Phase C verification gate** | completed | 11 unit tests green (incl. gating). Live: agent proposes a correct reversible fix; on approval (auto/prompt) applies it and metrics recover; propose mode changes nothing — to be run on the user's machine |

---

## Phase D — Packaging, overall compose, docs

| ID | Task | Status | Verification / notes |
|----|------|--------|----------------------|
| D1 | Add `sre-agent` (+ optional `chaosinjector`) to the overall `docker-compose.yml`; socket mount; host-gateway; optional `.env`; ChaosInjector optional block w/ 8081 remap | completed | `docker compose config` passes |
| D2 | Each component README finalized; `build:`→`image:` switch documented inline in compose | completed | READMEs present for all components |
| D3 | Root README: full end-to-end run + chaos walkthrough + agent RCA + remediation | completed | README updated |
| D4 | Verify all folders build independently + no secrets in repo | completed | no secrets committed (.env gitignored); per-folder `docker build .` to be run on user machine |
| D5 | **Definition of done** — spec §13 acceptance criteria | completed (static) | code/config verified here; full end-to-end (compose up + inject + RCA + approve→recover, provider switch) to be run on the user's machine with an LLM key |

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
- 2026-07-17 — Phase C implemented: reversible remediation tools
  (restart/unpause/start container, abort_chaos via ChaosInjector API), a
  ConfirmationGate (propose/prompt/auto), an AuditLog (GET /api/audit), and
  RcaReport.proposedAction. 11 unit tests green. Live approve/apply/recover
  pending on the user's machine.
- 2026-07-17 — Phase D implemented: sre-agent added to the overall compose
  (socket mount, host-gateway, optional .env, optional chaosinjector block),
  root README end-to-end walkthrough (RCA + remediation). `docker compose
  config` passes. All four phases done and static-verified; remaining
  verification is the live end-to-end run on the user's machine with an LLM key.
