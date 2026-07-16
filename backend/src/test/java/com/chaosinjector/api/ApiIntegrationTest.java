package com.chaosinjector.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import com.chaosinjector.support.FakeTargetAdapter;
import com.chaosinjector.target.ConnectionRegistry;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "chaosinjector.experiment.post-window-seconds=0")
class ApiIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ConnectionRegistry connections;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String registerFakeConnection() {
        FakeTargetAdapter adapter = new FakeTargetAdapter().withWorkload("c1", "web", "nginx");
        return connections.register(adapter);
    }

    @Test
    void listsScenarios() {
        ResponseEntity<List> res = rest.getForEntity(url("/api/scenarios"), List.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).hasSize(4);
    }

    @Test
    void validateThenRunToCompletion() {
        String conn = registerFakeConnection();
        Map<String, Object> req = Map.of(
                "connectionId", conn, "containerId", "c1",
                "scenario", "SERVICE_UNAVAILABLE", "parameters", Map.of("mode", "PAUSE"),
                "durationSeconds", 1, "baselineSeconds", 0, "sampleIntervalMs", 100);

        ResponseEntity<Map> validate = rest.postForEntity(url("/api/experiments/validate"), req, Map.class);
        assertThat(validate.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(validate.getBody()).containsEntry("valid", true);

        ResponseEntity<Map> created = rest.postForEntity(url("/api/experiments"), req, Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = (String) created.getBody().get("experimentId");
        assertThat(id).isNotBlank();

        await().atMost(Duration.ofSeconds(15)).until(() -> {
            ResponseEntity<Map> got = rest.getForEntity(url("/api/experiments/" + id), Map.class);
            return "COMPLETED".equals(got.getBody().get("state"))
                    || "FAILED".equals(got.getBody().get("state"))
                    || "ABORTED".equals(got.getBody().get("state"));
        });

        ResponseEntity<Map> done = rest.getForEntity(url("/api/experiments/" + id), Map.class);
        assertThat(done.getBody().get("state")).isEqualTo("COMPLETED");
        assertThat(done.getBody().get("report")).isNotNull();
    }

    @Test
    void sseStreamDeliversPhaseAndCompletedEvents() throws Exception {
        String conn = registerFakeConnection();
        Map<String, Object> req = Map.of(
                "connectionId", conn, "containerId", "c1",
                "scenario", "SERVICE_UNAVAILABLE", "parameters", Map.of("mode", "PAUSE"),
                "durationSeconds", 1, "baselineSeconds", 0, "sampleIntervalMs", 100);
        ResponseEntity<Map> created = rest.postForEntity(url("/api/experiments"), req, Map.class);
        String id = (String) created.getBody().get("experimentId");

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest streamReq = HttpRequest.newBuilder(URI.create(url("/api/experiments/" + id + "/stream")))
                .header("Accept", "text/event-stream")
                .timeout(Duration.ofSeconds(15))
                .GET().build();
        HttpResponse<Stream<String>> res = client.send(streamReq, HttpResponse.BodyHandlers.ofLines());
        String body;
        try (Stream<String> lines = res.body()) {
            body = lines.collect(Collectors.joining("\n"));
        }

        assertThat(body).contains("event:phase");
        assertThat(body).contains("event:completed");
    }

    @Test
    void overlappingExperimentsReturn409() {
        String conn = registerFakeConnection();
        Map<String, Object> longRun = Map.of(
                "connectionId", conn, "containerId", "c1",
                "scenario", "SERVICE_UNAVAILABLE", "parameters", Map.of("mode", "PAUSE"),
                "durationSeconds", 30, "baselineSeconds", 0, "sampleIntervalMs", 200);

        ResponseEntity<Map> first = rest.postForEntity(url("/api/experiments"), longRun, Map.class);
        String id = (String) first.getBody().get("experimentId");
        try {
            ResponseEntity<Map> second = rest.postForEntity(url("/api/experiments"), longRun, Map.class);
            assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(second.getBody().get("code")).isEqualTo("CONFLICT");
        } finally {
            rest.postForEntity(url("/api/experiments/" + id + "/abort"), null, Void.class);
        }
    }

    @Test
    void invalidRequestReturns400() {
        ResponseEntity<Map> res = rest.postForEntity(url("/api/experiments"),
                Map.of("durationSeconds", 10), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().get("code")).isEqualTo("VALIDATION_ERROR");
    }
}
