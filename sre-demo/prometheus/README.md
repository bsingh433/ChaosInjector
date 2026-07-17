# prometheus

Prometheus with the demo scrape config baked in.

- **Port:** 9090 (UI at `http://localhost:9090`)
- **Scrape interval:** 5s (fast, so chaos shows up quickly)
- **Jobs:** `sample-app` (:5000), `downstream` (:6000), `cadvisor` (:8080),
  `prometheus` (self)

Service DNS names (`sample-app`, `downstream`, `cadvisor`) resolve on the shared
compose network — this image is meant to run via the overall `docker-compose.yml`.

## Build standalone
```bash
docker build -t sre-demo-prometheus .
```

## Useful queries
```promql
up{job="sample-app"}
histogram_quantile(0.95, sum(rate(http_request_duration_seconds_bucket{job="sample-app"}[1m])) by (le))
histogram_quantile(0.95, sum(rate(db_operation_duration_seconds_bucket[1m])) by (le, operation))
rate(container_cpu_usage_seconds_total{name="sample-app"}[1m])
```
