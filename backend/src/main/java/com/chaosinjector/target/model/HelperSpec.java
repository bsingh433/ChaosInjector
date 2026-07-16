package com.chaosinjector.target.model;

import java.util.List;
import java.util.Map;

/**
 * Specification for a helper container the engine runs against a target — e.g.
 * an iproute2 container joined to the target's network namespace to apply
 * {@code tc/netem} rules. Helpers are always labelled so they can be swept on
 * revert and on startup (spec §12, invariant "helper cleanup is guaranteed").
 *
 * @param image           helper image
 * @param targetContainerId if set, share this container's network namespace
 *                          ({@code --net=container:<id>})
 * @param cmd             command to run
 * @param capAdd          Linux capabilities to add (e.g. NET_ADMIN)
 * @param labels          labels applied to the helper (must include the
 *                        experiment label)
 * @param autoRemove      whether the daemon removes the helper on exit
 */
public record HelperSpec(
        String image,
        String targetContainerId,
        List<String> cmd,
        List<String> capAdd,
        Map<String, String> labels,
        boolean autoRemove) {
}
