package com.sreagent.tools.readonly;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.sreagent.prometheus.PrometheusClient;
import com.sreagent.tools.Tool;

/** Read-only Prometheus tools: instant query, range query, target health. */
public final class PrometheusTools {

    private PrometheusTools() {
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties,
                "required", required, "additionalProperties", false);
    }

    @Component
    public static class Instant implements Tool {
        private final PrometheusClient prom;

        public Instant(PrometheusClient prom) {
            this.prom = prom;
        }

        @Override
        public String name() {
            return "prometheus_instant";
        }

        @Override
        public String description() {
            return "Run an instant PromQL query and return the current value(s). "
                    + "Use for point-in-time checks like up{job=\"sample-app\"}.";
        }

        @Override
        public Object parametersSchema() {
            return objectSchema(Map.of("query", Map.of("type", "string",
                    "description", "PromQL expression")), List.of("query"));
        }

        @Override
        public String execute(JsonNode args) {
            return prom.instant(args.path("query").asText(""));
        }
    }

    @Component
    public static class Range implements Tool {
        private final PrometheusClient prom;

        public Range(PrometheusClient prom) {
            this.prom = prom;
        }

        @Override
        public String name() {
            return "prometheus_range";
        }

        @Override
        public String description() {
            return "Run a PromQL query over a recent time window and return a time series. "
                    + "Use to see how a metric changed (e.g. latency or CPU over the last N minutes).";
        }

        @Override
        public Object parametersSchema() {
            return objectSchema(Map.of(
                    "query", Map.of("type", "string", "description", "PromQL expression"),
                    "minutes", Map.of("type", "integer", "description", "look-back window in minutes (default 15)"),
                    "stepSeconds", Map.of("type", "integer", "description", "resolution in seconds (default 15)")
            ), List.of("query"));
        }

        @Override
        public String execute(JsonNode args) {
            return prom.range(args.path("query").asText(""),
                    args.path("minutes").asInt(15), args.path("stepSeconds").asInt(15));
        }
    }

    @Component
    public static class Targets implements Tool {
        private final PrometheusClient prom;

        public Targets(PrometheusClient prom) {
            this.prom = prom;
        }

        @Override
        public String name() {
            return "list_targets";
        }

        @Override
        public String description() {
            return "List Prometheus scrape targets and their health (up/down). "
                    + "Use to check whether a service's metrics endpoint is reachable.";
        }

        @Override
        public Object parametersSchema() {
            return objectSchema(Map.of(), List.of());
        }

        @Override
        public String execute(JsonNode args) {
            return prom.targets();
        }
    }
}
