package com.chaosinjector.api.dto;

/**
 * Request to register a Docker daemon connection (spec §8.2).
 *
 * @param host      daemon endpoint; null/blank uses the configured default
 * @param certPath  TLS cert directory for a remote TLS daemon, or null
 * @param tlsVerify whether to verify TLS
 */
public record ConnectRequest(String host, String certPath, boolean tlsVerify) {
}
