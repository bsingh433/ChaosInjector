package com.chaosinjector.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/**
 * Exercises {@link HealthProbe} against an in-JVM HTTP server (no Docker).
 */
class HealthProbeTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", ex -> {
            byte[] body = "ok".getBytes();
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.createContext("/fail", ex -> {
            ex.sendResponseHeaders(503, -1);
            ex.close();
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void reachableHealthyEndpoint() {
        HealthResult r = new HealthProbe(2000).probe("http://127.0.0.1:" + port + "/health");
        assertThat(r.reachable()).isTrue();
        assertThat(r.httpStatus()).isEqualTo(200);
        assertThat(r.healthy()).isTrue();
        assertThat(r.latencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void reachableButUnhealthyStatus() {
        HealthResult r = new HealthProbe(2000).probe("http://127.0.0.1:" + port + "/fail");
        assertThat(r.reachable()).isTrue();
        assertThat(r.httpStatus()).isEqualTo(503);
        assertThat(r.healthy()).isFalse();
    }

    @Test
    void unreachableEndpoint() {
        // Nothing listening on this port.
        HealthResult r = new HealthProbe(500).probe("http://127.0.0.1:1/health");
        assertThat(r.reachable()).isFalse();
        assertThat(r.httpStatus()).isNull();
        assertThat(r.healthy()).isFalse();
    }
}
