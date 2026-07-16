package com.chaosinjector.api;

import java.io.IOException;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the bundled SPA from {@code classpath:/static/} with a single-page-app
 * fallback: real files (JS/CSS/assets) are served as-is with their correct
 * content type, and any other non-API path falls back to {@code index.html} so
 * client-side routing (React Router deep links) works.
 *
 * <p>This replaces the earlier forwarding controller, which incorrectly matched
 * {@code /assets/*.js} and returned index.html, producing a blank page.
 */
@Component
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver());
    }

    /**
     * Returns the requested static resource when it exists; otherwise falls back
     * to index.html for client routes. Never falls back for {@code api/*} or
     * {@code actuator/*} paths (those must 404 if unmatched, not return HTML).
     */
    static final class SpaFallbackResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = location.createRelative(resourcePath);
            if (requested.exists() && requested.isReadable()) {
                return requested;
            }
            if (resourcePath.startsWith("api/") || resourcePath.startsWith("actuator/")) {
                return null;
            }
            Resource index = location.createRelative("index.html");
            return index.exists() ? index : null;
        }
    }
}
