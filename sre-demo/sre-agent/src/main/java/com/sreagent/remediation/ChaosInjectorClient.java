package com.sreagent.remediation;

import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sreagent.config.AgentProperties;
import com.sreagent.llm.HttpExecutor;

/**
 * Talks to the ChaosInjector API to end an active experiment (the cleanest
 * reversible remediation: aborting reverts whatever fault was injected).
 */
@Component
public class ChaosInjectorClient {

    private static final Set<String> TERMINAL = Set.of("COMPLETED", "ABORTED", "FAILED");

    private final HttpExecutor http;
    private final ObjectMapper mapper;
    private final String baseUrl;

    public ChaosInjectorClient(HttpExecutor http, ObjectMapper mapper, AgentProperties props) {
        this.http = http;
        this.mapper = mapper;
        this.baseUrl = props.getChaosinjectorUrl().replaceAll("/+$", "");
    }

    /** Abort the first non-terminal experiment (optionally for a specific container). */
    public String abortActive(String containerFilter) {
        HttpExecutor.HttpResult list = http.get(baseUrl + "/api/experiments", Map.of());
        if (!list.ok()) {
            return "error: ChaosInjector unreachable (HTTP " + list.status() + ")";
        }
        try {
            JsonNode arr = mapper.readTree(list.body());
            for (JsonNode exp : arr) {
                String state = exp.path("state").asText("");
                if (TERMINAL.contains(state)) {
                    continue;
                }
                if (containerFilter != null && !containerFilter.isBlank()
                        && !containerFilter.equals(exp.path("containerId").asText(""))) {
                    continue;
                }
                String id = exp.path("id").asText("");
                HttpExecutor.HttpResult ab = http.post(
                        baseUrl + "/api/experiments/" + id + "/abort", Map.of(), "");
                return ab.ok()
                        ? "aborted active experiment " + id + " (was " + state + ")"
                        : "error: abort failed (HTTP " + ab.status() + ")";
            }
            return "no active experiment to abort";
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }
}
