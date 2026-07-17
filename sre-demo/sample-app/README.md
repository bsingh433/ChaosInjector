# sample-app

The instrumented backend under test — the container ChaosInjector targets. Talks
to the `downstream` service (HTTP) and to `MongoDB` (CRUD), and exposes Prometheus
metrics so the SRE Agent can diagnose chaos impact.

- **Port:** 5000

## Endpoints
| Method | Path | Purpose |
|---|---|---|
| GET | `/` | liveness |
| GET | `/health` | health (pings MongoDB) |
| GET | `/work` | work that calls the downstream service |
| POST | `/items` | create `{name, value}` |
| GET | `/items` | read all |
| GET | `/items/<id>` | read one |
| PUT | `/items/<id>` | update |
| DELETE | `/items/<id>` | delete |
| GET | `/metrics` | Prometheus exposition |

## Metrics
`http_requests_total`, `http_request_duration_seconds`,
`downstream_request_duration_seconds`, `downstream_errors_total`,
`db_operation_duration_seconds{operation}`, `db_errors_total{operation,reason}`.

## Config (env)
| Var | Default | Meaning |
|---|---|---|
| `PORT` | 5000 | listen port |
| `DOWNSTREAM_URL` | `http://downstream:6000/compute` | downstream endpoint |
| `MONGO_URI` | `mongodb://mongo:27017` | Mongo connection |
| `MONGO_DB` | `sampleapp` | database |
| `MONGO_COLLECTION` | `items` | collection |
| `SELF_LOAD` | `true` | run the background load generator |

## Build & run standalone
Needs a reachable Mongo + downstream (use the overall compose for the full setup):
```bash
docker build -t sre-demo-sample-app .
docker run --rm -p 5000:5000 \
  -e MONGO_URI=mongodb://host.docker.internal:27017 \
  -e DOWNSTREAM_URL=http://host.docker.internal:6000/compute \
  sre-demo-sample-app
curl localhost:5000/items
```
