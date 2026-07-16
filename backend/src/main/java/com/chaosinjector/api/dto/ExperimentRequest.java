package com.chaosinjector.api.dto;

import java.util.Map;

import com.chaosinjector.experiment.ScenarioType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request to create/validate an experiment (spec §10). Common numeric fields
 * are bean-validated here; scenario-specific {@code parameters} are validated by
 * the matching injector at VALIDATING time.
 *
 * @param name           optional friendly name
 * @param connectionId   registered Docker daemon connection id
 * @param containerId    target container id/name
 * @param healthCheckUrl optional HTTP(S) endpoint for availability/latency
 * @param scenario       the chaos scenario to inject
 * @param parameters     scenario-specific parameters (see spec §7)
 * @param durationSeconds how long the fault stays applied (hard-capped by config)
 * @param baselineSeconds pre-injection sampling window
 * @param sampleIntervalMs metrics sampling interval
 */
public record ExperimentRequest(
        String name,

        @NotBlank(message = "connectionId is required")
        String connectionId,

        @NotBlank(message = "containerId is required")
        String containerId,

        String healthCheckUrl,

        @NotNull(message = "scenario is required")
        ScenarioType scenario,

        Map<String, Object> parameters,

        @Min(value = 1, message = "durationSeconds must be at least 1")
        int durationSeconds,

        @Min(value = 0, message = "baselineSeconds must be >= 0")
        int baselineSeconds,

        @Min(value = 100, message = "sampleIntervalMs must be at least 100")
        int sampleIntervalMs) {

    public Map<String, Object> parametersOrEmpty() {
        return parameters == null ? Map.of() : parameters;
    }
}
