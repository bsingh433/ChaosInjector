package com.chaosinjector.target;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConnectionRegistryHostTest {

    private static final String UNIX = "unix:///var/run/docker.sock";

    @Test
    void windowsUsesNamedPipe() {
        assertThat(ConnectionRegistry.resolveLocalHost("Windows 11", UNIX))
                .isEqualTo("npipe:////./pipe/docker_engine");
        assertThat(ConnectionRegistry.resolveLocalHost("Windows Server 2022", UNIX))
                .isEqualTo("npipe:////./pipe/docker_engine");
    }

    @Test
    void linuxAndMacUseUnixSocket() {
        assertThat(ConnectionRegistry.resolveLocalHost("Linux", UNIX)).isEqualTo(UNIX);
        assertThat(ConnectionRegistry.resolveLocalHost("Mac OS X", UNIX)).isEqualTo(UNIX);
    }
}
