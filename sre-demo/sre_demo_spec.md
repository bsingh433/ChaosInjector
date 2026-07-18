# Chaos + Observability + SRE Agent — Demo Specification

**Status:** Draft for review · **Version:** 0.1 · **Date:** 2026-07-17

A self-contained local environment that closes the loop from **fault → observation
→ automated root-cause analysis → fix**:

1. A sample backend application (instrumented for metrics) runs in Docker.
2. **Prometheus + Grafana** observe it.
3. **ChaosInjector** (existing project) injects a fault into the app.
4. An **SRE Agent** — powered by a **configurable LLM** (default: **GPT‑5 on
   Azure OpenAI via the Responses API**; also OpenAI direct and Anthropic/Claude)
   — inspects the observability data, performs **Root Cause Analysis (RCA)**,
   produces fix instructions, and can apply **gated, reversible** remediations.

ChaosInjector is the **ground truth**: the SRE Agent is **not told** what was
injected — it must discover the cause from metrics alone. That makes this both a
demo and a test harness for the agent's RCA accuracy.

---

## 1. Goals

1. Stand up Prometheus + Grafana + a sample app locally with **one command**.
2. Make all four ChaosInjector scenarios **observable** in metrics.
3. An SRE Agent that reads observability data, produces a **structured RCA** with
   evidence and confidence, and gives **fix instructions**.
4. The agent can **propose and, after human confirmation, apply reversible
   remediations** (restart, unpause, reconnect network, abort the active chaos).
5. **Strict component separation** — each part is an independent folder with its
   own `Dockerfile` and README, ready to be extracted into its own Git repo.
6. **One top-level `docker-compose.yml`** that deploys every part together, each
   in **its own container**.

## 2. Non-goals (this demo)

- Production hardening, multi-node clusters, HA Prometheus/Grafana.
- Kubernetes/OpenShift (Docker only — mirrors ChaosInjector's MVP scope).
- Real alerting pipelines (PagerDuty/Alertmanager). Trigger is manual/CLI here.
- Log aggregation stacks (Loki/ELK). The agent reads container logs directly.
- Persisting RCA history / learning loop (future).

---

## 3. The end-to-end loop

```
          ┌──────────────────── one Docker daemon ─────────────────────┐
          │                                                            │
          │   sample-app ──HTTP──▶ downstream          cAdvisor        │
          │   (Flask,/metrics)     (Flask,/metrics)   (per-container   │
          │        ▲  │                                 CPU/mem)        │
          │        │  └──────── Prometheus ◀── scrapes all ────────────┤
          │        │                 │                                 │
          │        │             Grafana (dashboards)                  │
          │        │                                                   │
          │   ChaosInjector ──inject fault──▶ sample-app container      │
          │   (existing app)                                           │
          └────────────────────────────────────────────────────────────┘
                    ▲                              │
      queries Prometheus + Docker + logs          │ ground truth
                    │                               ▼
              SRE Agent (Azure GPT‑5) ──▶ RCA report + fix instructions
                    │                               + gated remediation
                    └── proposes reversible action → human approves → applies
```

**Chaos → observable signal (§7)** is what makes the RCA possible without telling
the agent anything.

---

## 4. Component inventory

Every component is an independent unit: its own folder, its own `Dockerfile`
(where it ships an image), its own README. The rightmost column is the future
standalone repo.

| Component | Folder | Tech | Ships a Dockerfile? | Future repo |
|---|---|---|---|---|
| Sample app | `sample-app/` | Python + Flask + prometheus_client + pymongo | ✅ own image | `sample-app` |
| Downstream dependency | `downstream/` | Python + Flask | ✅ own image | `downstream-service` |
| MongoDB | `mongo/` | MongoDB (official image + init) | ✅ extends `mongo` | `sample-app-db` |
| Prometheus | `prometheus/` | Prometheus + config | ✅ extends `prom/prometheus` | `observability-prometheus` |
| Grafana | `grafana/` | Grafana + provisioning + dashboards | ✅ extends `grafana/grafana` | `observability-grafana` |
| cAdvisor | *(none — compose only)* | `gcr.io/cadvisor/cadvisor` | ❌ used as-is | *n/a* |
| SRE Agent | `sre-agent/` | **Java 17 + Maven, pluggable LLM (Azure Responses API / OpenAI / Anthropic)** | ✅ own image | `sre-agent` |
| ChaosInjector | *(existing repo)* | Java + Spring (already built) | ✅ (already has one) | `ChaosInjector` |
| Orchestration | `sre-demo/` (root) | `docker-compose.yml` | ❌ compose only | `sre-demo` (umbrella) |

> **Separation rule:** no component imports code or files from a sibling. They
> communicate only over the network (HTTP / PromQL / Docker API). A component's
> folder is fully buildable on its own with `docker build .`.

---

## 5. Folder layout

```
sre-demo/                          # umbrella (this spec + the overall compose)
├── sre_demo_spec.md               # this file
├── README.md                      # run-the-whole-thing guide
├── docker-compose.yml             # OVERALL: brings up every service, each in its own container
├── .env.example                   # Azure creds + config template (never commit real secrets)
│
├── sample-app/                    # → future repo
│   ├── app.py
│   ├── requirements.txt
│   ├── Dockerfile
│   └── README.md
│
├── downstream/                    # → future repo
│   ├── app.py
│   ├── requirements.txt
│   ├── Dockerfile
│   └── README.md
│
├── mongo/                         # → future repo
│   ├── Dockerfile                 # FROM mongo + COPY init script
│   ├── init/seed.js               # creates DB/collection + seed docs (optional)
│   └── README.md
│
├── prometheus/                    # → future repo
│   ├── prometheus.yml
│   ├── Dockerfile                 # FROM prom/prometheus + COPY prometheus.yml
│   └── README.md
│
├── grafana/                       # → future repo
│   ├── provisioning/
│   │   ├── datasources/datasource.yml
│   │   └── dashboards/dashboards.yml
│   ├── dashboards/chaos-overview.json
│   ├── Dockerfile                 # FROM grafana/grafana + COPY provisioning + dashboards
│   └── README.md
│
└── sre-agent/                     # → future repo
    ├── pom.xml
    ├── src/main/java/com/sreagent/...
    ├── src/main/resources/application.yml
    ├── Dockerfile                 # multi-stage: build jar → JRE runtime
    └── README.md
```

When each folder becomes its own repo, the overall `docker-compose.yml` switches
each service from `build: ./<folder>` to `image: <registry>/<component>:<tag>`.
Both forms are documented in the compose file.

---

## 6. Components in detail

### 6.1 `sample-app/` — instrumented backend (the chaos target)

Python + Flask, exposes Prometheus metrics, and talks to **MongoDB** (via
`pymongo`) for CRUD. This is the container ChaosInjector targets.

**Endpoints**
| Method | Path | Purpose |
|---|---|---|
| GET | `/` | liveness JSON |
| GET | `/health` | health check (ChaosInjector probe URL); also pings MongoDB |
| GET | `/work` | does a unit of work; **calls the downstream service** |
| POST | `/items` | **create** a document (`{name, value}`) |
| GET | `/items` | **read** all documents (list) |
| GET | `/items/<id>` | **read** one document |
| PUT | `/items/<id>` | **update** a document |
| DELETE | `/items/<id>` | **delete** a document |
| GET | `/metrics` | Prometheus exposition |

**MongoDB CRUD:** the five `/items` routes are full Create/Read/Update/Delete
against a Mongo collection (`items`), using `pymongo`. Each DB operation is timed
and counted (see DB metrics below), so **database slowness or failures are
observable** — and diagnosable by the SRE Agent — just like the downstream calls.

**Behaviour:** a background load generator (`SELF_LOAD=true`) continuously
exercises both `/work` (→ downstream) **and** the CRUD path (create → read →
update → delete) so app, downstream, and DB metrics all have signal without
external traffic.

**Config (env):** `PORT` (5000), `DOWNSTREAM_URL`, `SELF_LOAD`,
`MONGO_URI` (e.g. `mongodb://mongo:27017`), `MONGO_DB` (`sampleapp`),
`MONGO_COLLECTION` (`items`).
**Container:** `container_name: sample-app`, `mem_limit: 512m` (so memory% and
OOM are meaningful and ChaosInjector's `percentOfLimit` works). `depends_on: mongo`.
**Dockerfile:** `python:3.11-slim` → install requirements → run app.

### 6.2 `downstream/` — simulated dependency

Python + Flask. `GET /compute` sleeps a small random time and returns 200 (rarely
5xx). Exposes `/metrics`. Exists so `sample-app`'s outbound call has a real
dependency to degrade under **network failure** chaos.

**Config (env):** `PORT` (6000), `BASE_DELAY_MS`, `ERROR_RATE`.
**Dockerfile:** same base as sample-app.

### 6.3 `prometheus/` — metrics store

Own image extending `prom/prometheus`, baking in `prometheus.yml`. Scrapes:

| Job | Target | Gives |
|---|---|---|
| `sample-app` | `sample-app:5000/metrics` | request rate, errors, latency, `up` |
| `downstream` | `downstream:6000/metrics` | downstream latency/errors |
| `cadvisor` | `cadvisor:8080/metrics` | per-container CPU / memory |
| `prometheus` | `localhost:9090` | self |

Scrape interval 5s (fast enough to see chaos quickly). Port 9090.

### 6.4 `grafana/` — dashboards

Own image extending `grafana/grafana`, with **provisioned** Prometheus datasource
and a **Chaos Overview** dashboard (no manual setup). Panels:

- App request rate & error rate (`/work`)
- App p95 latency (`http_request_duration_seconds`)
- Downstream p95 latency (`downstream_request_duration_seconds`)
- Target availability (`up{job="sample-app"}`)
- Container CPU (`container_cpu_usage_seconds_total` for sample-app)
- Container memory (`container_memory_usage_bytes` vs limit)

Port 3000, anonymous/admin login `admin/admin` (demo only).

### 6.5 cAdvisor — container metrics (off-the-shelf)

Used directly from `gcr.io/cadvisor/cadvisor` in the overall compose (privileged,
with the standard host mounts). No custom folder — it needs no configuration.
Provides the per-container CPU/memory that expose **CPU** and **Memory** chaos.

### 6.6 `sre-agent/` — the RCA + remediation agent

**Java 17 + Maven.** The agent runs a provider-neutral agentic loop: gather
evidence via read-only tools → reason with the configured LLM → validate → emit a
structured RCA → optionally propose a gated fix.

#### 6.6.1 Model-agnostic LLM layer

The LLM is **pluggable**. The engine talks only to a provider-neutral interface;
each vendor is a thin adapter selected by configuration. The default is **GPT‑5
on Azure OpenAI (Responses API)**, but the same agent runs against OpenAI-direct
or Anthropic/Claude by changing config only — no code change.

```
        Agent loop (provider-neutral)
                  │  LlmRequest{ system, messages, tools }
                  ▼
        ┌──────── LlmClient (interface) ────────┐
        │  chat(LlmRequest) -> LlmResponse       │
        │   LlmResponse = text  |  toolCalls[]    │
        └───────────────────────────────────────┘
             ▲            ▲              ▲
   AzureResponsesClient  OpenAiResponsesClient  AnthropicMessagesClient
   (default)             (api.openai.com)       (api.anthropic.com)
```

- **`LlmClient` interface** — one method: given a system prompt, the running
  message list, and the tool catalog, return either assistant text or a list of
  tool calls to execute. The agent loop, tools, and RCA schema are identical
  across providers.
- **Tool-calling is normalized.** Tools are defined **once** in provider-neutral
  form (name, description, JSON-Schema parameters, handler). Each adapter
  translates to/from that provider's wire format (Responses API `tools` +
  `function_call` items; Anthropic `tools` + `tool_use`/`tool_result` blocks).
- **Implementation:** thin HTTP adapters built on `java.net.http.HttpClient` +
  Jackson — **not** vendor SDKs — so every provider is uniform and we control the
  exact request shape (important for the Azure Responses API, below). Adding a
  provider = one new `LlmClient` implementation.

**Provider selection (config):**
```yaml
llm:
  provider: azure-responses        # azure-responses | openai-responses | openai-chat | anthropic-messages
  model: gpt-5                     # model/deployment name sent IN THE REQUEST BODY
  temperature: 0.2
  maxOutputTokens: 4000
```

**Azure OpenAI — Responses API (default).** The endpoint carries **no model name**;
the model is a field in the request body:
```
POST ${AZURE_OPENAI_ENDPOINT}/openai/responses?api-version=${AZURE_OPENAI_API_VERSION}
Header: api-key: ${AZURE_OPENAI_API_KEY}        # or  Authorization: Bearer <AAD token>
Body:   { "model": "${llm.model}", "input": [...], "tools": [...] }
```
| Var | Meaning |
|---|---|
| `AZURE_OPENAI_ENDPOINT` | `https://<resource>.openai.azure.com` |
| `AZURE_OPENAI_API_VERSION` | e.g. `2025-04-01-preview` (Responses API) |
| `AZURE_OPENAI_API_KEY` | key **or** Azure AD (`DefaultAzureCredential`) bearer token |

**OpenAI direct — Responses API.** `POST https://api.openai.com/v1/responses`,
`Authorization: Bearer ${OPENAI_API_KEY}`, model in body. Same request shape as
Azure (both are the Responses API), so the Azure and OpenAI adapters share most
code.

**OpenAI-compatible Chat Completions (OpenAI classic / Groq / others).**
`POST {baseUrl}/chat/completions`, `Authorization: Bearer <key>`, model in body,
OpenAI-style `tools` + `tool_calls`. Works with any OpenAI-compatible endpoint by
setting the base URL — notably **Groq** (`https://api.groq.com/openai/v1`, a
tool-capable model such as `llama-3.3-70b-versatile`), as well as OpenAI's own
Chat Completions API and gateways like Together/Fireworks/Ollama. Groq does
**not** implement the Responses API, so it must use this `openai-chat` provider.
Config: `OPENAI_CHAT_BASE_URL` (default `https://api.openai.com/v1`) and a key
(`OPENAI_CHAT_API_KEY`, falling back to `OPENAI_API_KEY`).

**Anthropic / Claude — Messages API.** `POST https://api.anthropic.com/v1/messages`,
`x-api-key: ${ANTHROPIC_API_KEY}`, `anthropic-version` header, model in body,
Anthropic-style tool blocks.

**Secrets:** all keys/tokens via env only (`${ENV_VAR}`); never hardcoded, never
logged. Only the selected provider's credentials need to be present.

**Read-only tools (evidence gathering):**
| Tool | Does |
|---|---|
| `prometheus_instant(promql)` | instant PromQL query |
| `prometheus_range(promql, minutes, step)` | range query for a time window |
| `list_targets()` | scrape targets + `up` health |
| `container_stats(name)` | live CPU/mem/restart from Docker |
| `recent_changes()` | container start times, restart counts, image, docker events (the "what changed" signal) |
| `container_logs(name, tail)` | recent logs of a container |

**Gated remediation tools (reversible; require human confirmation before executing):**
| Tool | Reversible action |
|---|---|
| `restart_container(name)` | restart the target |
| `unpause_container(name)` | undo a pause |
| `reconnect_networks(name)` | reattach a disconnected container |
| `abort_chaos()` | call ChaosInjector's abort API to end the active experiment |

**Confirmation gating:** when GPT‑5 requests a remediation tool, the agent
**pauses** and asks a human (CLI prompt, or an `--auto-approve` flag for demos).
Read-only tools run without prompting. Every proposed and executed action is
logged (audit trail).

**RCA output (structured):**
```json
{
  "incidentWindow": {"fromIso": "...", "toIso": "..."},
  "timeline": ["t0: latency normal", "t1: cpu spike on sample-app", ...],
  "hypotheses": [
    {"cause": "CPU saturation on sample-app",
     "confidence": 0.86,
     "evidence": ["container_cpu_usage ~100% since t1", "p95 latency 5x baseline"],
     "refutedBy": []}
  ],
  "recommendedFixes": [
    {"summary": "Relieve CPU pressure",
     "instructions": ["...human-readable steps..."],
     "proposedAction": {"tool": "abort_chaos", "reversible": true}}
  ],
  "verdict": "CPU overhead on sample-app degraded request latency; app stayed up."
}
```

**Grounding rules (anti-hallucination):** every hypothesis must cite tool
evidence; the agent must be able to answer "insufficient signal"; the model
proposes remediation, deterministic Java code executes it (only after approval).

**Dockerfile:** multi-stage (Maven build → JRE runtime). Mounts the Docker socket
so its Docker-based tools work; reaches Prometheus over the compose network.

### 6.7 ChaosInjector (existing) — fault injection

Already built (separate repo). In this demo it is the fault source and the RCA
ground truth. It connects to the same Docker daemon and targets `sample-app`.
It is included in the overall compose as an **optional** service (build from its
repo/image, mount the Docker socket) so the whole loop can come up together; it
can equally be run on its own as today.

### 6.8 `mongo/` — document store (sample-app's database)

MongoDB, the database backing the sample app's CRUD endpoints. Ships its own
image extending the official `mongo` image, optionally with an init script that
creates the database/collection and seeds a few documents.

- **Image:** `FROM mongo:7` + `COPY init/seed.js /docker-entrypoint-initdb.d/`
  (Mongo runs any `*.js`/`*.sh` in that directory on first startup to create the
  `sampleapp` DB, the `items` collection, and seed data).
- **Port:** 27017 (exposed on the compose network; optionally to the host for
  inspection with `mongosh`/Compass).
- **Persistence:** a named volume for `/data/db` so data survives restarts.
- **Auth:** demo runs without auth by default; `MONGO_INITDB_ROOT_USERNAME` /
  `MONGO_INITDB_ROOT_PASSWORD` supported via env for a secured variant.
- **Container:** `container_name: mongo`.

Because the sample app calls Mongo on every CRUD op, MongoDB is a **second
observable dependency** (alongside `downstream`): network/CPU chaos on the app,
or pausing Mongo itself, shows up as rising DB-operation latency or DB errors.

| ChaosInjector scenario | What the SRE Agent observes | Primary metric |
|---|---|---|
| **CPU Overhead** | sample-app CPU near 100%, request latency rises | `container_cpu_usage_seconds_total` |
| **Memory Overhead** | memory climbs toward limit; possible OOM restart | `container_memory_usage_bytes`, restart count |
| **Network Failure** | downstream **and DB** call latency/errors spike | `downstream_request_duration_seconds`, `db_operation_duration_seconds`, `*_errors_total` |
| **Service Unavailable** | scrape target down; availability 0 | `up{job="sample-app"} == 0` |
| **Mongo paused/stopped** (bonus) | DB ops time out; DB errors spike; `/health` degrades | `db_operation_duration_seconds`, `db_errors_total` |

The agent correlates these with **`recent_changes()`** (restart counts, container
state) to reach a cause without being told which scenario ran. With Mongo as a
dependency, the agent can also distinguish "app is slow" from "the database is
slow/unreachable" — a realistic RCA branch.

---

## 8. Metrics catalog

**sample-app exposes:** `http_requests_total{method,endpoint,status}`,
`http_request_duration_seconds{endpoint}` (histogram),
`downstream_request_duration_seconds` (histogram),
`downstream_errors_total{reason}`,
`db_operation_duration_seconds{operation}` (histogram; operation ∈
create/read/read_one/update/delete/ping),
`db_errors_total{operation,reason}`.
**downstream exposes:** request count + latency histogram, error counter.
**cAdvisor exposes:** `container_cpu_usage_seconds_total`,
`container_memory_usage_bytes`, `container_spec_memory_limit_bytes`,
`container_network_*`, restart info — all labelled by container.
**Prometheus exposes:** `up{job=...}` per target.

---

## 9. Orchestration — the overall `docker-compose.yml`

One compose file at the demo root brings up **every service in its own
container** on a shared user-defined network:

| Service | Image / build | Ports | Notes |
|---|---|---|---|
| `mongo` | `build: ./mongo` | 27017 | `container_name: mongo`; named volume for `/data/db` |
| `sample-app` | `build: ./sample-app` | 5000 | `container_name: sample-app`, `mem_limit: 512m`, `depends_on: [mongo, downstream]` |
| `downstream` | `build: ./downstream` | 6000 | |
| `prometheus` | `build: ./prometheus` | 9090 | |
| `grafana` | `build: ./grafana` | 3000 | depends_on prometheus |
| `cadvisor` | `gcr.io/cadvisor/cadvisor` | 8080 | privileged + host mounts |
| `sre-agent` | `build: ./sre-agent` | 8085 | mounts `/var/run/docker.sock`; selected LLM provider creds via `.env` |
| `chaosinjector` | build from ChaosInjector repo (optional) | 8080→8081 | mounts `/var/run/docker.sock` |

- **Separation preserved:** each `build:` context is a single component folder;
  swapping to `image:` (post-repo-split) is documented inline.
- **Docker socket:** `sre-agent` and `chaosinjector` mount the daemon socket to
  read container state / inject faults. On Windows Docker Desktop the standard
  `/var/run/docker.sock` bind works via the WSL2 backend; ChaosInjector also
  auto-detects the named pipe when run natively.
- **Secrets:** Azure OpenAI credentials come from a root `.env` (git-ignored);
  `.env.example` documents the keys.
- **Port conflict note:** cAdvisor and ChaosInjector both default to 8080 — the
  compose remaps host ports (e.g. ChaosInjector → 8081) to avoid clashes.

Bring-up order via `depends_on`: mongo + downstream → sample-app → cadvisor →
prometheus → grafana → sre-agent.

---

## 10. Configuration & secrets

- All service config via env vars; a root `.env` (from `.env.example`) holds the
  LLM provider selection, the chosen provider's credentials, and demo toggles.
- **LLM is provider-agnostic (§6.6.1).** `llm.provider` picks the adapter; only
  that provider's credentials need to be set:
  - `azure-responses` → `AZURE_OPENAI_ENDPOINT`, `AZURE_OPENAI_API_VERSION`,
    `AZURE_OPENAI_API_KEY` (or Azure AD); `llm.model` = deployment name.
  - `openai-responses` → `OPENAI_API_KEY`; `llm.model` = e.g. `gpt-5`.
  - `openai-chat` → `OPENAI_CHAT_BASE_URL` + `OPENAI_CHAT_API_KEY` (or
    `OPENAI_API_KEY`); OpenAI-compatible Chat Completions. For **Groq**: base URL
    `https://api.groq.com/openai/v1`, `llm.model` a tool-capable Groq model.
  - `anthropic-messages` → `ANTHROPIC_API_KEY`, `ANTHROPIC_VERSION`;
    `llm.model` = e.g. a Claude model id.
- **No secrets in Git, images, logs, or the spec.** `${ENV_VAR}` interpolation
  only; resolved secrets are never logged.
- Grafana/Prometheus use demo credentials only; documented as non-production.

## 11. Safety & blast radius (SRE Agent)

Mirrors ChaosInjector's invariants:
1. **Read-only by default** — diagnosis needs only read access.
2. **Remediation is gated + reversible** — restart / unpause / reconnect / abort
   chaos, each behind explicit human confirmation; no destructive actions.
3. **Model proposes, typed code disposes** — GPT‑5 never executes anything; it
   emits a tool request that Java code runs after approval.
4. **Full audit** of every tool call and every applied action.

---

## 12. Build plan — phase by phase (verify each before the next)

**Phase A — Observability stack + apps + database.** `mongo/`, `sample-app/`
(incl. MongoDB CRUD + DB metrics), `downstream/`, `prometheus/`, `grafana/`, and
the overall compose (minus the agent). *Verify:* `docker compose up` brings up
all; the CRUD endpoints work against Mongo; Grafana shows live app/DB panels;
injecting chaos via ChaosInjector visibly moves the metrics (incl. DB latency).

**Phase B — SRE Agent read-only RCA.** `sre-agent/` Java project: the
provider-neutral `LlmClient` layer with the Azure Responses API adapter first
(then OpenAI + Anthropic adapters behind the same interface), read-only tools,
agentic loop, structured RCA output. *Verify:* with a known injected fault, the
agent independently produces an RCA whose top hypothesis matches the injected
scenario, with cited evidence — and switching `llm.provider` runs the same flow
on another provider.

**Phase C — Gated remediation.** Add the reversible remediation tools + human
confirmation + audit log. *Verify:* the agent proposes a correct reversible fix;
on approval it executes and the metrics recover; declining leaves state untouched.

**Phase D — Packaging & docs.** Each component's Dockerfile + README; the overall
compose with both `build:` and `image:` forms; root README with the full run
sequence; `.env.example`. *Verify:* a clean `docker compose up --build` stands up
the entire loop on a fresh machine.

---

## 13. Acceptance criteria

1. One `docker compose up --build` starts every component, each in its own
   container.
2. Grafana shows the sample-app under load; all four chaos scenarios visibly move
   the dashboards.
3. The SRE Agent, given no hint, produces a structured RCA correctly identifying
   the injected scenario for each of the four, with cited metric evidence.
4. The agent proposes a reversible fix and, on confirmation, applies it and the
   metrics recover; declining changes nothing.
5. Every component folder builds independently (`docker build .`) and is
   repo-extractable with its own Dockerfile + README.
6. No secrets in the repo; Azure creds injected via env.

---

## 14. Open questions for reviewer

1. **LLM providers to ship in v1:** default `azure-responses` (GPT‑5 via the
   Responses API) plus `openai-responses` and `anthropic-messages`. Confirm this
   set; more adapters are additive. Azure auth supports API key and Azure AD.
2. **ChaosInjector in the overall compose:** include it as a service (one-command
   everything) or keep it run-separately and only wire the network? Default:
   include as an optional service.
3. **Agent trigger:** CLI command (`analyze --window 10m`) for the demo, or an
   HTTP endpoint the agent exposes? Default: CLI + a thin HTTP endpoint.
4. **cAdvisor on Windows Docker Desktop:** it usually works but host mounts can
   be finicky; acceptable to fall back to ChaosInjector/Docker stats if a metric
   is missing? Default: yes, agent degrades gracefully.
