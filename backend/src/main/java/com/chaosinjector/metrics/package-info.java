/**
 * Metrics and impact: the periodic collector (docker stats + health probe)
 * that tags samples by phase (BASELINE/ACTIVE/POST), and the ImpactReport
 * computation (baseline-vs-active deltas, availability, latency percentiles,
 * recovery time, verdict). All values come from real samples. See
 * chaos_injector_spec.md §9.
 */
package com.chaosinjector.metrics;
