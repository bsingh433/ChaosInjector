package com.sreagent.tools.remediation;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.sreagent.config.AgentProperties;
import com.sreagent.docker.DockerFacade;
import com.sreagent.remediation.ChaosInjectorClient;
import com.sreagent.tools.Tool;

/**
 * Reversible remediation tools. All require human confirmation before executing
 * (the {@link com.sreagent.remediation.ConfirmationGate} enforces this in the
 * agent loop). No destructive actions.
 */
public final class RemediationTools {

    private RemediationTools() {
    }

    private static Map<String, Object> nameSchema() {
        return Map.of("type", "object", "properties",
                Map.of("name", Map.of("type", "string", "description", "container name (default: target)")),
                "required", List.of(), "additionalProperties", false);
    }

    private abstract static class ContainerTool implements Tool {
        final DockerFacade docker;
        final String defaultTarget;

        ContainerTool(DockerFacade docker, AgentProperties props) {
            this.docker = docker;
            this.defaultTarget = props.getDefaultTarget();
        }

        @Override
        public boolean requiresConfirmation() {
            return true;
        }

        @Override
        public Object parametersSchema() {
            return nameSchema();
        }

        String target(JsonNode args) {
            return args.path("name").asText(defaultTarget);
        }
    }

    @Component
    public static class RestartContainer extends ContainerTool {
        public RestartContainer(DockerFacade docker, AgentProperties props) {
            super(docker, props);
        }

        @Override
        public String name() {
            return "restart_container";
        }

        @Override
        public String description() {
            return "Restart the target container (reversible). Use to recover from an OOM/hung state.";
        }

        @Override
        public String execute(JsonNode args) {
            return docker.restart(target(args));
        }
    }

    @Component
    public static class UnpauseContainer extends ContainerTool {
        public UnpauseContainer(DockerFacade docker, AgentProperties props) {
            super(docker, props);
        }

        @Override
        public String name() {
            return "unpause_container";
        }

        @Override
        public String description() {
            return "Unpause the target container (reversible). Use if the container was paused.";
        }

        @Override
        public String execute(JsonNode args) {
            return docker.unpause(target(args));
        }
    }

    @Component
    public static class StartContainer extends ContainerTool {
        public StartContainer(DockerFacade docker, AgentProperties props) {
            super(docker, props);
        }

        @Override
        public String name() {
            return "start_container";
        }

        @Override
        public String description() {
            return "Start the target container if it is stopped (reversible).";
        }

        @Override
        public String execute(JsonNode args) {
            return docker.start(target(args));
        }
    }

    @Component
    public static class AbortChaos implements Tool {
        private final ChaosInjectorClient chaos;

        public AbortChaos(ChaosInjectorClient chaos) {
            this.chaos = chaos;
        }

        @Override
        public String name() {
            return "abort_chaos";
        }

        @Override
        public String description() {
            return "Abort the active ChaosInjector experiment (reversible) — the cleanest fix for "
                    + "any injected fault; ChaosInjector reverts it (unpause/reconnect/kill load).";
        }

        @Override
        public boolean requiresConfirmation() {
            return true;
        }

        @Override
        public Object parametersSchema() {
            return Map.of("type", "object", "properties", Map.of(),
                    "required", List.of(), "additionalProperties", false);
        }

        @Override
        public String execute(JsonNode args) {
            return chaos.abortActive(null);
        }
    }
}
