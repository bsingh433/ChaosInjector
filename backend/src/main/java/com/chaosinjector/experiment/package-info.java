/**
 * Experiment domain and orchestration: the {@code Experiment} aggregate, the
 * lifecycle state machine (CREATED → VALIDATING → BASELINE → INJECTING →
 * ACTIVE → REVERTING → COMPLETED, plus FAILED/ABORTED), the experiment service
 * that drives it end to end with a bounded-duration watchdog and guaranteed
 * revert, and the in-memory experiment store. See chaos_injector_spec.md §6.2.
 */
package com.chaosinjector.experiment;
