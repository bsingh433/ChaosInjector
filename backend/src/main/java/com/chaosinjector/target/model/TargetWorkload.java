package com.chaosinjector.target.model;

/**
 * A workload the operator can target. On Docker this is a container; a future
 * K8s adapter maps a pod to the same shape.
 */
public record TargetWorkload(String id, String name, String image, String status) {
}
