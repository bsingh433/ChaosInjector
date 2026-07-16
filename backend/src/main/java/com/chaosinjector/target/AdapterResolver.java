package com.chaosinjector.target;

/**
 * Resolves a registered connection id to its {@link TargetAdapter}. Implemented
 * by {@link ConnectionRegistry}; injected into the engine so orchestration is
 * testable with a stub resolver.
 */
public interface AdapterResolver {

    /** @throws com.chaosinjector.config.Errors.ConnectionError if unknown. */
    TargetAdapter resolve(String connectionId);
}
