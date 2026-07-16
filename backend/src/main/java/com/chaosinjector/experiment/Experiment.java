package com.chaosinjector.experiment;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The experiment aggregate: identity, target reference, chosen scenario and
 * parameters, timing, and current lifecycle {@link ExperimentState}. State
 * changes go through {@link #transitionTo} which enforces the legal-transition
 * rules (spec §6.2); illegal transitions throw and are treated as defects.
 *
 * <p>Not thread-safe on its own; the experiment service serialises access per
 * experiment.
 */
public class Experiment {

    private final String id;
    private final String name;
    private final String connectionId;
    private final String containerId;
    private final String healthCheckUrl;
    private final ScenarioType scenario;
    private final Map<String, Object> parameters;
    private final int durationSeconds;
    private final int baselineSeconds;
    private final int sampleIntervalMs;
    private final Instant createdAt;

    private volatile ExperimentState state = ExperimentState.CREATED;
    private volatile Instant updatedAt;
    private volatile String errorCode;
    private volatile String errorMessage;

    public Experiment(String name, String connectionId, String containerId, String healthCheckUrl,
                      ScenarioType scenario, Map<String, Object> parameters,
                      int durationSeconds, int baselineSeconds, int sampleIntervalMs) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.connectionId = connectionId;
        this.containerId = containerId;
        this.healthCheckUrl = healthCheckUrl;
        this.scenario = scenario;
        this.parameters = parameters == null ? Map.of() : new HashMap<>(parameters);
        this.durationSeconds = durationSeconds;
        this.baselineSeconds = baselineSeconds;
        this.sampleIntervalMs = sampleIntervalMs;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Move to {@code next}, enforcing the state machine.
     *
     * @throws IllegalStateException if the transition is not legal from the
     *     current state.
     */
    public synchronized void transitionTo(ExperimentState next) {
        if (!state.canTransitionTo(next)) {
            throw new IllegalStateException(
                    "Illegal experiment transition " + state + " -> " + next + " (experiment " + id + ")");
        }
        this.state = next;
        this.updatedAt = Instant.now();
    }

    /** Record a failure reason (does not itself change state). */
    public synchronized void recordError(String code, String message) {
        this.errorCode = code;
        this.errorMessage = message;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public String getContainerId() {
        return containerId;
    }

    public String getHealthCheckUrl() {
        return healthCheckUrl;
    }

    public ScenarioType getScenario() {
        return scenario;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public int getBaselineSeconds() {
        return baselineSeconds;
    }

    public int getSampleIntervalMs() {
        return sampleIntervalMs;
    }

    public ExperimentState getState() {
        return state;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
