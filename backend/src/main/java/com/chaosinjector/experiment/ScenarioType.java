package com.chaosinjector.experiment;

/**
 * The chaos scenarios supported by the MVP. See chaos_injector_spec.md §7.
 */
public enum ScenarioType {
    NETWORK_FAILURE,
    CPU_OVERHEAD,
    MEMORY_OVERHEAD,
    SERVICE_UNAVAILABLE
}
