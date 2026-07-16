package com.chaosinjector.engine;

import com.chaosinjector.experiment.ScenarioType;

/**
 * One chaos scenario (spec §6.3, §7). Implementations use only their own
 * primitives via the {@link com.chaosinjector.target.TargetAdapter}. {@link
 * #revert} must be idempotent and leave the target exactly as it was.
 */
public interface ChaosInjector {

    ScenarioType type();

    /** @throws com.chaosinjector.config.Errors.ValidationError on bad params/preconditions. */
    void validate(ExperimentContext ctx);

    /** Apply the fault. */
    InjectionHandle inject(ExperimentContext ctx);

    /** Undo the fault. Idempotent; safe to call after a partial inject. */
    void revert(ExperimentContext ctx, InjectionHandle handle);
}
