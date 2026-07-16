package com.chaosinjector.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.chaosinjector.experiment.ScenarioType;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.Validation;

class ExperimentRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void validRequestHasNoViolations() {
        ExperimentRequest req = new ExperimentRequest("net-test", "conn-1", "web-1",
                "http://localhost:8081/health", ScenarioType.NETWORK_FAILURE,
                Map.of("mode", "LATENCY", "latencyMs", 300), 60, 15, 1000);
        Set<ConstraintViolation<ExperimentRequest>> v = validator.validate(req);
        assertThat(v).isEmpty();
    }

    @Test
    void missingRequiredFieldsAreReported() {
        ExperimentRequest req = new ExperimentRequest(null, "  ", "", null, null,
                null, 0, -1, 50);
        Set<ConstraintViolation<ExperimentRequest>> v = validator.validate(req);
        assertThat(v).extracting(ConstraintViolation::getMessage)
                .anyMatch(m -> m.contains("connectionId"))
                .anyMatch(m -> m.contains("containerId"))
                .anyMatch(m -> m.contains("scenario"))
                .anyMatch(m -> m.contains("durationSeconds"))
                .anyMatch(m -> m.contains("sampleIntervalMs"));
    }
}
