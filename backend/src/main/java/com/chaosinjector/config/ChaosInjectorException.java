package com.chaosinjector.config;

/**
 * Base for all typed ChaosInjector errors. Every subclass carries a stable
 * machine-readable {@link #code()} and a user-facing message, which the API
 * layer maps to the {@code {code, message, details}} error shape (spec §15).
 */
public abstract class ChaosInjectorException extends RuntimeException {

    private final Object details;

    protected ChaosInjectorException(String message) {
        this(message, null, null);
    }

    protected ChaosInjectorException(String message, Throwable cause) {
        this(message, cause, null);
    }

    protected ChaosInjectorException(String message, Throwable cause, Object details) {
        super(message, cause);
        this.details = details;
    }

    /** Stable error code, e.g. {@code CONNECTION_ERROR}. */
    public abstract String code();

    /** Optional structured details (may be null). */
    public Object details() {
        return details;
    }
}
