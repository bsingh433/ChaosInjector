package com.chaosinjector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ChaosInjector — entry point.
 *
 * <p>Boots the Spring context that hosts the whole engine: the REST + SSE API,
 * the experiment state machine, the chaos injectors, the Docker target adapter,
 * and the metrics collector. In a release build the compiled React SPA is served
 * from {@code static/} by the same process (single deployable JAR).
 */
@SpringBootApplication
public class ChaosInjectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChaosInjectorApplication.class, args);
    }
}
