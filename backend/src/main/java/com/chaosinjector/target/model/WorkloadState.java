package com.chaosinjector.target.model;

import java.util.List;

/**
 * Detailed state of a single workload, used for precondition checks and revert
 * bookkeeping (e.g. which networks a container was attached to before a
 * network-off experiment).
 */
public record WorkloadState(
        String id,
        String name,
        String image,
        String status,
        boolean running,
        boolean paused,
        long restartCount,
        Long memoryLimitBytes,
        List<String> networks) {
}
