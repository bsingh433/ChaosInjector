package com.chaosinjector.api.dto;

/** Uniform API error body (spec §15). */
public record ApiError(String code, String message, Object details) {
}
