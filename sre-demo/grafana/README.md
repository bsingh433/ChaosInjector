# grafana

Grafana with the Prometheus datasource and the **Chaos Overview** dashboard
provisioned automatically — no manual setup.

- **Port:** 3000 (`http://localhost:3000`, login `admin` / `admin`)
- **Datasource:** Prometheus at `http://prometheus:9090` (uid `prometheus`)
- **Dashboard:** `Chaos Overview` — app availability, request rate, app/downstream/DB
  p95 latency, error rates, and sample-app container CPU/memory.

Meant to run via the overall `docker-compose.yml` (needs the `prometheus`
service on the same network).

## Build standalone
```bash
docker build -t sre-demo-grafana .
```
