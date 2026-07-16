package com.chaosinjector.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards non-API, non-asset routes to the SPA entry point so client-side
 * routing (React Router) works when the app is served from the bundled JAR.
 * API calls live under {@code /api/**} and are handled by REST controllers;
 * everything else falls through to {@code index.html}.
 */
@Controller
public class SpaForwardController {

    // Match top-level and nested paths that are not API calls and not files
    // (no dot in the last segment). Static assets under /assets/** are served
    // directly by Spring's resource handler and never reach this mapping.
    @GetMapping(value = {"/", "/{path:^(?!api$|assets$)[^.]*}", "/{path:^(?!api$)[^.]*}/**"})
    public String forward() {
        return "forward:/index.html";
    }
}
