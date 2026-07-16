package com.chaosinjector.engine;

import java.util.Arrays;
import java.util.Map;

import com.chaosinjector.config.Errors;

/**
 * Typed access to the loosely-typed scenario {@code parameters} map (JSON
 * numbers arrive as Integer/Double/Long). Throws {@link Errors.ValidationError}
 * on bad values so validation fails fast at VALIDATING.
 */
final class Params {

    private Params() {
    }

    static String str(Map<String, Object> p, String key, String def) {
        Object v = p.get(key);
        return v == null ? def : v.toString();
    }

    static int intVal(Map<String, Object> p, String key, int def) {
        Object v = p.get(key);
        if (v == null) {
            return def;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (NumberFormatException e) {
            throw new Errors.ValidationError("Parameter '" + key + "' must be an integer, got: " + v);
        }
    }

    static double dbl(Map<String, Object> p, String key, double def) {
        Object v = p.get(key);
        if (v == null) {
            return def;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(v.toString().trim());
        } catch (NumberFormatException e) {
            throw new Errors.ValidationError("Parameter '" + key + "' must be a number, got: " + v);
        }
    }

    static <E extends Enum<E>> E enumVal(Map<String, Object> p, String key, Class<E> type, E def) {
        Object v = p.get(key);
        if (v == null) {
            return def;
        }
        try {
            return Enum.valueOf(type, v.toString().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new Errors.ValidationError("Parameter '" + key + "' must be one of "
                    + Arrays.toString(type.getEnumConstants()) + ", got: " + v);
        }
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new Errors.ValidationError(message);
        }
    }
}
