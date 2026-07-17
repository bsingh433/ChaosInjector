"""Downstream dependency for the Chaos + SRE-Agent demo.

A deliberately simple service the sample app calls on every /work request. Its
latency and error rate make **network chaos** (injected on the sample app)
observable: netem delay on the app's egress shows up as elevated
downstream_request_duration_seconds on the app side.

Endpoints:
  GET /compute  -> sleeps a small random time, occasionally returns 500
  GET /health   -> health check
  GET /metrics  -> Prometheus exposition
"""
import os
import random
import time

from flask import Flask, Response, jsonify
from prometheus_client import (
    CONTENT_TYPE_LATEST,
    Counter,
    Histogram,
    generate_latest,
)

app = Flask(__name__)

PORT = int(os.environ.get("PORT", "6000"))
BASE_DELAY_MS = int(os.environ.get("BASE_DELAY_MS", "20"))
ERROR_RATE = float(os.environ.get("ERROR_RATE", "0.02"))

REQUESTS = Counter("downstream_requests_total", "Downstream requests", ["status"])
LATENCY = Histogram("downstream_compute_duration_seconds", "Compute latency (s)")


@app.route("/health")
def health():
    return jsonify(status="ok"), 200


@app.route("/compute")
def compute():
    with LATENCY.time():
        delay = (BASE_DELAY_MS + random.uniform(0, BASE_DELAY_MS)) / 1000.0
        time.sleep(delay)
        if random.random() < ERROR_RATE:
            REQUESTS.labels("500").inc()
            return jsonify(error="downstream failure"), 500
        REQUESTS.labels("200").inc()
        return jsonify(result="ok", delay_ms=round(delay * 1000, 1)), 200


@app.route("/metrics")
def metrics():
    return Response(generate_latest(), mimetype=CONTENT_TYPE_LATEST)


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=PORT, threaded=True)
