# downstream

A simulated dependency the sample app calls on every `/work` request. Exists so
network chaos on the sample app has a real downstream to degrade.

- **Port:** 6000
- `GET /compute` — sleeps a small random time, occasionally 500s
- `GET /health`, `GET /metrics`

## Config (env)
| Var | Default | Meaning |
|---|---|---|
| `PORT` | 6000 | listen port |
| `BASE_DELAY_MS` | 20 | base compute delay (ms); actual = base + up to base random |
| `ERROR_RATE` | 0.02 | fraction of requests that return 500 |

## Build & run standalone
```bash
docker build -t sre-demo-downstream .
docker run --rm -p 6000:6000 sre-demo-downstream
curl localhost:6000/compute
```
