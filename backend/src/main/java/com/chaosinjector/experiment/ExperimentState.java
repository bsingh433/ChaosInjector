package com.chaosinjector.experiment;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Experiment lifecycle states and the legal transitions between them
 * (chaos_injector_spec.md §6.2):
 *
 * <pre>
 * CREATED → VALIDATING → BASELINE → INJECTING → ACTIVE → REVERTING → COMPLETED
 *                                                             ↘ ABORTED
 *   any active state → FAILED
 * </pre>
 *
 * REVERTING is always reachable from an in-flight state so the guaranteed-revert
 * invariant can be honoured on completion, abort, or error.
 */
public enum ExperimentState {
    CREATED,
    VALIDATING,
    BASELINE,
    INJECTING,
    ACTIVE,
    REVERTING,
    COMPLETED,
    ABORTED,
    FAILED;

    private static final Map<ExperimentState, Set<ExperimentState>> TRANSITIONS =
            new EnumMap<>(ExperimentState.class);

    static {
        TRANSITIONS.put(CREATED, EnumSet.of(VALIDATING, FAILED));
        TRANSITIONS.put(VALIDATING, EnumSet.of(BASELINE, FAILED));
        // Abort during BASELINE still routes through REVERTING for uniform cleanup.
        TRANSITIONS.put(BASELINE, EnumSet.of(INJECTING, REVERTING, FAILED));
        TRANSITIONS.put(INJECTING, EnumSet.of(ACTIVE, REVERTING, FAILED));
        TRANSITIONS.put(ACTIVE, EnumSet.of(REVERTING, FAILED));
        TRANSITIONS.put(REVERTING, EnumSet.of(COMPLETED, ABORTED, FAILED));
        TRANSITIONS.put(COMPLETED, EnumSet.noneOf(ExperimentState.class));
        TRANSITIONS.put(ABORTED, EnumSet.noneOf(ExperimentState.class));
        TRANSITIONS.put(FAILED, EnumSet.noneOf(ExperimentState.class));
    }

    /** @return true if this state may legally transition to {@code next}. */
    public boolean canTransitionTo(ExperimentState next) {
        return TRANSITIONS.getOrDefault(this, Collections.emptySet()).contains(next);
    }

    /** @return true once no further transitions are possible. */
    public boolean isTerminal() {
        return TRANSITIONS.getOrDefault(this, Collections.emptySet()).isEmpty();
    }

    /** States in which a fault may be (partially) applied and must be reverted. */
    public boolean isActiveInjectionState() {
        return this == INJECTING || this == ACTIVE;
    }
}
