package com.chaosinjector.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * Guards against the blank-screen regression: static assets must be served as
 * themselves (never index.html), API paths must not fall back to HTML, and
 * unknown client routes must fall back to index.html.
 */
class SpaFallbackResolverTest {

    private final WebConfig.SpaFallbackResolver resolver = new WebConfig.SpaFallbackResolver();
    private final Resource staticRoot = new ClassPathResource("/static-test/");

    @Test
    void existingAssetIsServedAsItself() throws IOException {
        Resource r = resolver.getResource("assets/app.js", staticRoot);
        assertThat(r).isNotNull();
        assertThat(r.getFilename()).isEqualTo("app.js");
        assertThat(new String(r.getInputStream().readAllBytes())).contains("console.log");
    }

    @Test
    void unknownClientRouteFallsBackToIndex() throws IOException {
        Resource r = resolver.getResource("configure", staticRoot);
        assertThat(r).isNotNull();
        assertThat(r.getFilename()).isEqualTo("index.html");
    }

    @Test
    void apiPathDoesNotFallBackToHtml() throws IOException {
        Resource r = resolver.getResource("api/experiments/123", staticRoot);
        assertThat(r).isNull();
    }
}
