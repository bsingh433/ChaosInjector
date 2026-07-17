"""Sample instrumented backend for the Chaos + SRE-Agent demo.

Exposes Prometheus metrics at /metrics so the SRE Agent can diagnose the impact
of chaos injected by ChaosInjector — without being told what was injected.

Two real dependencies make chaos observable:
  * downstream service (HTTP)  -> /work
  * MongoDB (CRUD)             -> /items

Endpoints:
  GET    /            liveness
  GET    /health      health (also pings MongoDB)
  GET    /work        work that calls the downstream service
  POST   /items       create a document {name, value}
  GET    /items       read all documents
  GET    /items/<id>  read one
  PUT    /items/<id>  update
  DELETE /items/<id>  delete
  GET    /metrics     Prometheus exposition

A background load generator (SELF_LOAD=true) exercises /work AND the CRUD path so
app, downstream, and DB metrics always have signal without external traffic.
"""
import os
import random
import threading
import time
from contextlib import contextmanager

import requests
from bson import ObjectId
from bson.errors import InvalidId
from flask import Flask, Response, jsonify, request
from prometheus_client import (
    CONTENT_TYPE_LATEST,
    Counter,
    Histogram,
    generate_latest,
)
from pymongo import MongoClient
from pymongo.errors import PyMongoError

app = Flask(__name__)

PORT = int(os.environ.get("PORT", "5000"))
DOWNSTREAM_URL = os.environ.get("DOWNSTREAM_URL", "http://downstream:6000/compute")
SELF_LOAD = os.environ.get("SELF_LOAD", "true").lower() == "true"
MONGO_URI = os.environ.get("MONGO_URI", "mongodb://mongo:27017")
MONGO_DB = os.environ.get("MONGO_DB", "sampleapp")
MONGO_COLLECTION = os.environ.get("MONGO_COLLECTION", "items")

# Fail fast under chaos so timeouts are visible rather than hanging forever.
mongo = MongoClient(MONGO_URI, serverSelectionTimeoutMS=2000)
collection = mongo[MONGO_DB][MONGO_COLLECTION]

# --- Metrics ---
REQUESTS = Counter(
    "http_requests_total", "HTTP requests", ["method", "endpoint", "status"]
)
LATENCY = Histogram(
    "http_request_duration_seconds", "Request latency (s)", ["endpoint"]
)
DOWNSTREAM_LATENCY = Histogram(
    "downstream_request_duration_seconds", "Downstream call latency (s)"
)
DOWNSTREAM_ERRORS = Counter(
    "downstream_errors_total", "Failed downstream calls", ["reason"]
)
DB_OP_LATENCY = Histogram(
    "db_operation_duration_seconds", "MongoDB operation latency (s)", ["operation"]
)
DB_ERRORS = Counter(
    "db_errors_total", "MongoDB operation errors", ["operation", "reason"]
)


@contextmanager
def db_op(operation):
    """Time a DB operation and record failures as metrics."""
    start = time.time()
    try:
        yield
    except PyMongoError as e:
        DB_ERRORS.labels(operation, type(e).__name__).inc()
        raise
    finally:
        DB_OP_LATENCY.labels(operation).observe(time.time() - start)


def _doc(d):
    return {"id": str(d["_id"]), "name": d.get("name"), "value": d.get("value")}


# --- Basic endpoints ---
@app.route("/")
def index():
    with LATENCY.labels("/").time():
        REQUESTS.labels("GET", "/", "200").inc()
        return jsonify(service="sample-app", status="ok")


@app.route("/health")
def health():
    status = "200"
    try:
        with db_op("ping"):
            mongo.admin.command("ping")
    except PyMongoError:
        status = "503"
    REQUESTS.labels("GET", "/health", status).inc()
    return jsonify(status="ok" if status == "200" else "degraded",
                   mongo=(status == "200")), int(status)


@app.route("/work")
def work():
    start = time.time()
    status = "200"
    try:
        d_start = time.time()
        try:
            resp = requests.get(DOWNSTREAM_URL, timeout=2.0)
            DOWNSTREAM_LATENCY.observe(time.time() - d_start)
            if resp.status_code >= 500:
                DOWNSTREAM_ERRORS.labels("downstream_5xx").inc()
                status = "502"
        except requests.exceptions.Timeout:
            DOWNSTREAM_LATENCY.observe(time.time() - d_start)
            DOWNSTREAM_ERRORS.labels("timeout").inc()
            status = "504"
        except requests.exceptions.RequestException:
            DOWNSTREAM_ERRORS.labels("connection").inc()
            status = "502"
        _ = sum(i * i for i in range(2000))  # a little local CPU work
        return jsonify(result="done", downstream_status=status), int(status)
    finally:
        LATENCY.labels("/work").observe(time.time() - start)
        REQUESTS.labels("GET", "/work", status).inc()


# --- MongoDB CRUD ---
@app.route("/items", methods=["POST"])
def create_item():
    body = request.get_json(silent=True) or {}
    doc = {"name": body.get("name"), "value": body.get("value")}
    try:
        with db_op("create"):
            res = collection.insert_one(doc)
        doc["_id"] = res.inserted_id
        REQUESTS.labels("POST", "/items", "201").inc()
        return jsonify(_doc(doc)), 201
    except PyMongoError as e:
        REQUESTS.labels("POST", "/items", "503").inc()
        return jsonify(error=str(e)), 503


@app.route("/items", methods=["GET"])
def list_items():
    try:
        with db_op("read"):
            docs = [_doc(d) for d in collection.find().limit(100)]
        REQUESTS.labels("GET", "/items", "200").inc()
        return jsonify(items=docs, count=len(docs)), 200
    except PyMongoError as e:
        REQUESTS.labels("GET", "/items", "503").inc()
        return jsonify(error=str(e)), 503


@app.route("/items/<id>", methods=["GET"])
def get_item(id):
    try:
        oid = ObjectId(id)
    except InvalidId:
        return jsonify(error="invalid id"), 400
    try:
        with db_op("read_one"):
            d = collection.find_one({"_id": oid})
        if not d:
            REQUESTS.labels("GET", "/items/id", "404").inc()
            return jsonify(error="not found"), 404
        REQUESTS.labels("GET", "/items/id", "200").inc()
        return jsonify(_doc(d)), 200
    except PyMongoError as e:
        REQUESTS.labels("GET", "/items/id", "503").inc()
        return jsonify(error=str(e)), 503


@app.route("/items/<id>", methods=["PUT"])
def update_item(id):
    try:
        oid = ObjectId(id)
    except InvalidId:
        return jsonify(error="invalid id"), 400
    body = request.get_json(silent=True) or {}
    update = {k: v for k, v in body.items() if k in ("name", "value")}
    try:
        with db_op("update"):
            res = collection.update_one({"_id": oid}, {"$set": update})
        if res.matched_count == 0:
            REQUESTS.labels("PUT", "/items/id", "404").inc()
            return jsonify(error="not found"), 404
        REQUESTS.labels("PUT", "/items/id", "200").inc()
        return jsonify(id=id, updated=update), 200
    except PyMongoError as e:
        REQUESTS.labels("PUT", "/items/id", "503").inc()
        return jsonify(error=str(e)), 503


@app.route("/items/<id>", methods=["DELETE"])
def delete_item(id):
    try:
        oid = ObjectId(id)
    except InvalidId:
        return jsonify(error="invalid id"), 400
    try:
        with db_op("delete"):
            res = collection.delete_one({"_id": oid})
        REQUESTS.labels("DELETE", "/items/id", "200").inc()
        return jsonify(deleted=res.deleted_count), 200
    except PyMongoError as e:
        REQUESTS.labels("DELETE", "/items/id", "503").inc()
        return jsonify(error=str(e)), 503


@app.route("/metrics")
def metrics():
    return Response(generate_latest(), mimetype=CONTENT_TYPE_LATEST)


# --- Background load generator ---
def _self_load():
    time.sleep(4)  # let Mongo/downstream come up
    base = f"http://127.0.0.1:{PORT}"
    while True:
        try:
            requests.get(f"{base}/work", timeout=3.0)
            # CRUD cycle: create -> read -> update -> delete
            r = requests.post(f"{base}/items",
                              json={"name": "load", "value": random.randint(1, 100)},
                              timeout=3.0)
            if r.status_code == 201:
                item_id = r.json().get("id")
                requests.get(f"{base}/items", timeout=3.0)
                if item_id:
                    requests.put(f"{base}/items/{item_id}",
                                 json={"value": random.randint(1, 100)}, timeout=3.0)
                    requests.delete(f"{base}/items/{item_id}", timeout=3.0)
        except requests.exceptions.RequestException:
            pass
        time.sleep(random.uniform(0.4, 0.9))


if __name__ == "__main__":
    if SELF_LOAD:
        threading.Thread(target=_self_load, daemon=True).start()
    app.run(host="0.0.0.0", port=PORT, threaded=True)
