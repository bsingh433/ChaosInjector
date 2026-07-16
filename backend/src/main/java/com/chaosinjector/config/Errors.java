package com.chaosinjector.config;

/**
 * The typed exception hierarchy (spec §15). Grouped here as nested classes so
 * the seven error types read as one contract. Each fixes its own stable code.
 */
public final class Errors {

    private Errors() {
    }

    /** Docker daemon unreachable / transport failure. */
    public static class ConnectionError extends ChaosInjectorException {
        public ConnectionError(String message) {
            super(message);
        }

        public ConnectionError(String message, Throwable cause) {
            super(message, cause);
        }

        @Override
        public String code() {
            return "CONNECTION_ERROR";
        }
    }

    /** Request/config validation failed (bad params, missing fields). */
    public static class ValidationError extends ChaosInjectorException {
        public ValidationError(String message) {
            super(message);
        }

        public ValidationError(String message, Object details) {
            super(message, null, details);
        }

        @Override
        public String code() {
            return "VALIDATION_ERROR";
        }
    }

    /** The requested target workload does not exist or is not running. */
    public static class TargetNotFoundError extends ChaosInjectorException {
        public TargetNotFoundError(String message) {
            super(message);
        }

        @Override
        public String code() {
            return "TARGET_NOT_FOUND";
        }
    }

    /** A fault could not be applied. */
    public static class InjectionError extends ChaosInjectorException {
        public InjectionError(String message) {
            super(message);
        }

        public InjectionError(String message, Throwable cause) {
            super(message, cause);
        }

        @Override
        public String code() {
            return "INJECTION_ERROR";
        }
    }

    /**
     * A fault could not be fully reverted. This is escalated loudly because it
     * may mean the target is left altered — the one condition the tool must
     * never hide (spec §15).
     */
    public static class RevertError extends ChaosInjectorException {
        public RevertError(String message) {
            super(message);
        }

        public RevertError(String message, Throwable cause) {
            super(message, cause);
        }

        @Override
        public String code() {
            return "REVERT_ERROR";
        }
    }

    /** Another experiment is already active on the target (maps to HTTP 409). */
    public static class ConflictError extends ChaosInjectorException {
        public ConflictError(String message) {
            super(message);
        }

        @Override
        public String code() {
            return "CONFLICT";
        }
    }

    /** Metrics collection or health probing failed. */
    public static class MetricsError extends ChaosInjectorException {
        public MetricsError(String message) {
            super(message);
        }

        public MetricsError(String message, Throwable cause) {
            super(message, cause);
        }

        @Override
        public String code() {
            return "METRICS_ERROR";
        }
    }
}
