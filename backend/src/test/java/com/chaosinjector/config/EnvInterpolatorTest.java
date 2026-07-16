package com.chaosinjector.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

class EnvInterpolatorTest {

    private final EnvInterpolator interp = new EnvInterpolator(
            Map.of("DB_PASS", "s3cr3t", "HOST", "db.internal")::get);

    @Test
    void replacesKnownVariables() {
        assertThat(interp.interpolate("tcp://${HOST}:2376")).isEqualTo("tcp://db.internal:2376");
        assertThat(interp.interpolate("${DB_PASS}")).isEqualTo("s3cr3t");
    }

    @Test
    void leavesPlainStringsUnchanged() {
        assertThat(interp.interpolate("no-placeholders")).isEqualTo("no-placeholders");
        assertThat(interp.interpolate("")).isEmpty();
        assertThat(interp.interpolate(null)).isNull();
    }

    @Test
    void failsFastOnMissingVariable() {
        assertThatThrownBy(() -> interp.interpolate("${NOT_SET}"))
                .isInstanceOf(Errors.ValidationError.class)
                .hasMessageContaining("NOT_SET");
    }
}
