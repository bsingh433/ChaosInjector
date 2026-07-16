/**
 * Chaos engine: one {@code ChaosInjector} implementation per scenario (Network
 * Failure, CPU Overhead, Memory Overhead, Service Unavailable), each with a
 * real, idempotent {@code revert()}. Injectors use their own primitives via the
 * {@link com.chaosinjector.target.TargetAdapter} and never reference Docker
 * types directly. See chaos_injector_spec.md §7.
 */
package com.chaosinjector.engine;
