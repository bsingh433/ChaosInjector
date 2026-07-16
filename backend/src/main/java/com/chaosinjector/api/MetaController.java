package com.chaosinjector.api;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Meta endpoints: liveness and build/version info. Exposed under {@code /api}
 * so the SPA (and smoke checks) can confirm the backend is up and which
 * version is running.
 */
@RestController
@RequestMapping("/api")
public class MetaController {

    private final String version;

    public MetaController(@Value("${chaosinjector.version:0.1.0}") String version) {
        this.version = version;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/version")
    public Map<String, String> version() {
        return Map.of("name", "ChaosInjector", "version", version);
    }
}
