package com.chaosinjector.api.dto;

import java.time.Instant;

import com.chaosinjector.experiment.Experiment;
import com.chaosinjector.experiment.ExperimentState;
import com.chaosinjector.experiment.ScenarioType;
import com.chaosinjector.metrics.ImpactReport;

/** Experiment status + report snapshot returned by the API (spec §10). */
public record ExperimentResponse(
        String id,
        String name,
        String connectionId,
        String containerId,
        String healthCheckUrl,
        ScenarioType scenario,
        ExperimentState state,
        int durationSeconds,
        int baselineSeconds,
        Instant createdAt,
        Instant updatedAt,
        String errorCode,
        String errorMessage,
        ImpactReport report) {

    public static ExperimentResponse from(Experiment e) {
        return new ExperimentResponse(
                e.getId(), e.getName(), e.getConnectionId(), e.getContainerId(), e.getHealthCheckUrl(),
                e.getScenario(), e.getState(), e.getDurationSeconds(), e.getBaselineSeconds(),
                e.getCreatedAt(), e.getUpdatedAt(), e.getErrorCode(), e.getErrorMessage(), e.getReport());
    }
}
