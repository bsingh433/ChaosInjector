package com.chaosinjector.api.dto;

import java.util.List;

import com.chaosinjector.experiment.ScenarioType;

/**
 * A scenario and its parameter schema. The UI renders the parameter form
 * dynamically from this (spec §11.2).
 */
public record ScenarioDto(ScenarioType type, String label, String description, List<ParamSpec> params) {

    /**
     * One tunable parameter.
     *
     * @param name        parameter key
     * @param type        "int" | "number" | "string" | "enum"
     * @param label       human label
     * @param required    whether it must be provided
     * @param defaultValue default value (may be null)
     * @param min         numeric minimum (may be null)
     * @param max         numeric maximum (may be null)
     * @param options     allowed values for enum types (may be null)
     * @param dependsOnMode when set, only applies for this scenario mode
     */
    public record ParamSpec(
            String name,
            String type,
            String label,
            boolean required,
            Object defaultValue,
            Double min,
            Double max,
            List<String> options,
            String dependsOnMode) {
    }
}
