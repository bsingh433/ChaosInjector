package com.sreagent.prometheus;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sreagent.config.AgentProperties;
import com.sreagent.llm.HttpExecutor;

/** Thin read-only Prometheus HTTP API client (instant/range queries + targets). */
@Component
public class PrometheusClient {

    private static final int MAX_RESULT_CHARS = 6000;

    private final HttpExecutor http;
    private final ObjectMapper mapper;
    private final String baseUrl;

    public PrometheusClient(HttpExecutor http, ObjectMapper mapper, AgentProperties props) {
        this.http = http;
        this.mapper = mapper;
        this.baseUrl = props.getPrometheusUrl().replaceAll("/+$", "");
    }

    public String instant(String promql) {
        String url = baseUrl + "/api/v1/query?query=" + enc(promql);
        return summarise(http.get(url, Map.of()));
    }

    public String range(String promql, int minutes, int stepSeconds) {
        long end = Instant.now().getEpochSecond();
        long start = end - Math.max(1, minutes) * 60L;
        int step = Math.max(5, stepSeconds);
        String url = baseUrl + "/api/v1/query_range?query=" + enc(promql)
                + "&start=" + start + "&end=" + end + "&step=" + step;
        return summarise(http.get(url, Map.of()));
    }

    public String targets() {
        return summarise(http.get(baseUrl + "/api/v1/targets?state=any", Map.of()));
    }

    private String summarise(HttpExecutor.HttpResult res) {
        if (!res.ok()) {
            return "prometheus HTTP " + res.status() + ": " + truncate(res.body());
        }
        try {
            JsonNode root = mapper.readTree(res.body());
            JsonNode data = root.path("data");
            String out = data.isMissingNode() ? root.toString() : data.toString();
            return truncate(out);
        } catch (Exception e) {
            return truncate(res.body());
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= MAX_RESULT_CHARS ? s
                : s.substring(0, MAX_RESULT_CHARS) + "…(truncated)";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
