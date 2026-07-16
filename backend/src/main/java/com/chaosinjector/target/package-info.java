/**
 * Target abstraction: the {@code TargetAdapter} interface (the pluggable seam
 * for future Kubernetes/OpenShift adapters) and the MVP {@code
 * DockerTargetAdapter} built on docker-java. The engine and API depend only on
 * the interface. See chaos_injector_spec.md §6.4, §8.
 */
package com.chaosinjector.target;
