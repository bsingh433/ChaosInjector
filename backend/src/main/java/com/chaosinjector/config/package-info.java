/**
 * Cross-cutting configuration: typed configuration properties (with
 * {@code ${ENV_VAR}} interpolation, secrets never logged), the typed exception
 * hierarchy (ConnectionError, ValidationError, TargetNotFoundError,
 * InjectionError, RevertError, MetricsError), and the API error mapping to the
 * {@code {code, message, details}} shape. See chaos_injector_spec.md §13, §15.
 */
package com.chaosinjector.config;
