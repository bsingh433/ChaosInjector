package com.chaosinjector.metrics;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Probes a user-supplied HTTP(S) health endpoint, returning reachability,
 * status, and latency (spec §9). Direct connections only (no proxy) so
 * localhost targets are reached as-is.
 */
public class HealthProbe {

    private final HttpClient client;
    private final Duration timeout;

    public HealthProbe(int timeoutMs) {
        this.timeout = Duration.ofMillis(timeoutMs);
        this.client = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * GET {@code url} once. A response (any status) counts as reachable; a
     * timeout or connection error counts as unreachable.
     */
    public HealthResult probe(String url) {
        long start = System.nanoTime();
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeout)
                    .GET()
                    .build();
            HttpResponse<Void> res = client.send(req, HttpResponse.BodyHandlers.discarding());
            long ms = (System.nanoTime() - start) / 1_000_000;
            return new HealthResult(true, res.statusCode(), ms);
        } catch (Exception e) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            return new HealthResult(false, null, ms);
        }
    }
}
