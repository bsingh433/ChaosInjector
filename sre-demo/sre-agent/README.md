# sre-agent

LLM-powered root-cause-analysis agent. Reads Prometheus + Docker (read-only),
reasons with a **configurable LLM**, and produces a structured RCA. It is **not**
told what chaos was injected — it discovers the cause from the metrics.

- **Port:** 8085 (HTTP service)
- **LLM:** provider-agnostic via a thin `LlmClient` layer. Default **Azure OpenAI
  Responses API** (`api-version 2025-04-01-preview`, model in the request body);
  also `openai-responses`, `anthropic-messages`, and `openai-chat` (any
  OpenAI-compatible Chat Completions endpoint, e.g. **Groq**). Switch with
  `LLM_PROVIDER`.

## Tools
**Read-only:** `prometheus_instant`, `prometheus_range`, `list_targets`,
`container_stats`, `recent_changes`, `container_logs`.

**Gated reversible remediation:** `restart_container`, `unpause_container`,
`start_container`, `abort_chaos` (ends the active ChaosInjector experiment).
These require human approval per `REMEDIATION_MODE`:
- `propose` (default) — the agent proposes but never executes; the action is
  recorded and returned in `recommendedFixes[].proposedAction`.
- `prompt` — asks for `y/N` on the console (CLI mode).
- `auto` — executes without asking (demo only).

Every proposed/executed action is written to an audit log — `GET /api/audit`.

## Configure (env)
```
LLM_PROVIDER=azure-responses            # azure-responses | openai-responses | anthropic-messages | openai-chat
LLM_MODEL=gpt-5
AZURE_OPENAI_ENDPOINT=https://<resource>.openai.azure.com
AZURE_OPENAI_API_VERSION=2025-04-01-preview
AZURE_OPENAI_API_KEY=...                # or OPENAI_API_KEY / ANTHROPIC_API_KEY for other providers
PROMETHEUS_URL=http://localhost:9090    # http://prometheus:9090 inside compose
DOCKER_HOST=unix:///var/run/docker.sock
DEFAULT_TARGET=sample-app
```
Only the selected provider's credentials are needed. Secrets via env only.

**Using a Groq API key** (`openai-chat` provider — OpenAI-compatible Chat
Completions, `Authorization: Bearer`):
```
LLM_PROVIDER=openai-chat
LLM_MODEL=llama-3.3-70b-versatile       # a Groq model that supports tool use
OPENAI_CHAT_BASE_URL=https://api.groq.com/openai/v1
OPENAI_CHAT_API_KEY=gsk_...             # your Groq key (falls back to OPENAI_API_KEY)
```
The same provider also drives OpenAI's classic Chat Completions API and other
compatible gateways — just point `OPENAI_CHAT_BASE_URL` at them.

> **Groq free tier is token-rate-limited** (~12k tokens/min). The agent
> automatically retries `429` responses honouring the server's `Retry-After`,
> so a transient limit self-heals. If an RCA persistently trips the limit,
> shorten the window (`windowMinutes`), lower `LLM_MAX_OUTPUT_TOKENS`, or
> upgrade your Groq tier.

## Run

**HTTP service**
```bash
mvn spring-boot:run          # or: java -jar target/sre-agent.jar
curl -X POST "http://localhost:8085/api/analyze?windowMinutes=10&target=sample-app"
```

**One-shot CLI**
```bash
mvn -q package -DskipTests
java -jar target/sre-agent.jar --analyze --window=10 --target=sample-app
```

**Docker** (mount the socket so the Docker tools work)
```bash
docker build -t sre-demo-sre-agent .
docker run --rm -p 8085:8085 --env-file ../.env \
  -e PROMETHEUS_URL=http://host.docker.internal:9090 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  sre-demo-sre-agent
```

## Tests
```bash
mvn test    # LLM adapters (mocked HTTP), provider selection, agent loop
```
