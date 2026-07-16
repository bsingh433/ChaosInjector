package com.chaosinjector.engine;

import java.util.Map;

import com.chaosinjector.target.TargetAdapter;

/**
 * Everything an injector needs to apply/revert a fault for one experiment. The
 * adapter is the only way injectors touch the target (invariant: no Docker types
 * in the engine).
 *
 * @param experimentId   used to label helper containers for cleanup
 * @param containerId    the target
 * @param parameters     scenario-specific parameters
 * @param durationSeconds how long the fault stays applied
 * @param adapter        target adapter
 * @param helperImage    image used for helper containers (network/tc, etc.)
 */
public record ExperimentContext(
        String experimentId,
        String containerId,
        Map<String, Object> parameters,
        int durationSeconds,
        TargetAdapter adapter,
        String helperImage) {

    public Map<String, String> helperLabels() {
        return Map.of(Labels.EXPERIMENT, experimentId);
    }
}
