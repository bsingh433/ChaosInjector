package com.sreagent.tools.readonly;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.sreagent.config.AgentProperties;
import com.sreagent.docker.DockerFacade;
import com.sreagent.tools.Tool;

/** Read-only Docker tools: container stats, recent changes, logs. */
public final class DockerTools {

    private DockerTools() {
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties,
                "required", required, "additionalProperties", false);
    }

    @Component
    public static class ContainerStats implements Tool {
        private final DockerFacade docker;
        private final String defaultTarget;

        public ContainerStats(DockerFacade docker, AgentProperties props) {
            this.docker = docker;
            this.defaultTarget = props.getDefaultTarget();
        }

        @Override
        public String name() {
            return "container_stats";
        }

        @Override
        public String description() {
            return "Live CPU%, memory usage/limit, restart count, and running/paused state "
                    + "for a container (from Docker directly). Use for CPU/memory diagnosis.";
        }

        @Override
        public Object parametersSchema() {
            return objectSchema(Map.of("name", Map.of("type", "string",
                    "description", "container name (default: the target under test)")), List.of());
        }

        @Override
        public String execute(JsonNode args) {
            return docker.stats(args.path("name").asText(defaultTarget));
        }
    }

    @Component
    public static class RecentChanges implements Tool {
        private final DockerFacade docker;

        public RecentChanges(DockerFacade docker) {
            this.docker = docker;
        }

        @Override
        public String name() {
            return "recent_changes";
        }

        @Override
        public String description() {
            return "List running containers with image, status, restart count, and start time — "
                    + "the 'what changed' signal (recent restarts / new starts).";
        }

        @Override
        public Object parametersSchema() {
            return objectSchema(Map.of(), List.of());
        }

        @Override
        public String execute(JsonNode args) {
            return docker.describeAll();
        }
    }

    @Component
    public static class ContainerLogs implements Tool {
        private final DockerFacade docker;
        private final String defaultTarget;

        public ContainerLogs(DockerFacade docker, AgentProperties props) {
            this.docker = docker;
            this.defaultTarget = props.getDefaultTarget();
        }

        @Override
        public String name() {
            return "container_logs";
        }

        @Override
        public String description() {
            return "Recent stdout/stderr log lines from a container. Use to spot errors/exceptions.";
        }

        @Override
        public Object parametersSchema() {
            return objectSchema(Map.of(
                    "name", Map.of("type", "string", "description", "container name (default: target)"),
                    "tail", Map.of("type", "integer", "description", "number of recent lines (default 100)")
            ), List.of());
        }

        @Override
        public String execute(JsonNode args) {
            return docker.logs(args.path("name").asText(defaultTarget), args.path("tail").asInt(100));
        }
    }
}
