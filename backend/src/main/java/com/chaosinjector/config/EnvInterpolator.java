package com.chaosinjector.config;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {@code ${ENV_VAR}} placeholders in runtime-supplied strings (e.g. a
 * connection payload's host or TLS paths) so secrets need never be typed in
 * plain (spec §13). A missing variable fails fast with a {@link
 * Errors.ValidationError}; resolved values are never logged by this class.
 */
public final class EnvInterpolator {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");

    private final Function<String, String> lookup;

    public EnvInterpolator() {
        this(System::getenv);
    }

    /** Package/test constructor allowing an injected environment source. */
    public EnvInterpolator(Function<String, String> lookup) {
        this.lookup = lookup;
    }

    /**
     * @return {@code value} with every {@code ${VAR}} replaced by its
     *     environment value. Returns null/blank inputs unchanged.
     * @throws Errors.ValidationError if a referenced variable is not set.
     */
    public String interpolate(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        Matcher m = PLACEHOLDER.matcher(value);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            String resolved = lookup.apply(name);
            if (resolved == null) {
                throw new Errors.ValidationError(
                        "Environment variable '" + name + "' referenced in config is not set");
            }
            m.appendReplacement(out, Matcher.quoteReplacement(resolved));
        }
        m.appendTail(out);
        return out.toString();
    }
}
