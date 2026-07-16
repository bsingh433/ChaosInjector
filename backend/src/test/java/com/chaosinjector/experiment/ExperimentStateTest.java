package com.chaosinjector.experiment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ExperimentStateTest {

    @Test
    void happyPathTransitionsAreLegal() {
        assertThat(ExperimentState.CREATED.canTransitionTo(ExperimentState.VALIDATING)).isTrue();
        assertThat(ExperimentState.VALIDATING.canTransitionTo(ExperimentState.BASELINE)).isTrue();
        assertThat(ExperimentState.BASELINE.canTransitionTo(ExperimentState.INJECTING)).isTrue();
        assertThat(ExperimentState.INJECTING.canTransitionTo(ExperimentState.ACTIVE)).isTrue();
        assertThat(ExperimentState.ACTIVE.canTransitionTo(ExperimentState.REVERTING)).isTrue();
        assertThat(ExperimentState.REVERTING.canTransitionTo(ExperimentState.COMPLETED)).isTrue();
    }

    @Test
    void abortAndFailurePathsAreLegal() {
        assertThat(ExperimentState.ACTIVE.canTransitionTo(ExperimentState.REVERTING)).isTrue();
        assertThat(ExperimentState.REVERTING.canTransitionTo(ExperimentState.ABORTED)).isTrue();
        assertThat(ExperimentState.INJECTING.canTransitionTo(ExperimentState.FAILED)).isTrue();
        assertThat(ExperimentState.BASELINE.canTransitionTo(ExperimentState.REVERTING)).isTrue();
    }

    @Test
    void illegalTransitionsAreRejected() {
        assertThat(ExperimentState.CREATED.canTransitionTo(ExperimentState.ACTIVE)).isFalse();
        assertThat(ExperimentState.COMPLETED.canTransitionTo(ExperimentState.ACTIVE)).isFalse();
        assertThat(ExperimentState.ACTIVE.canTransitionTo(ExperimentState.COMPLETED)).isFalse();
    }

    @Test
    void terminalStatesAreTerminal() {
        assertThat(ExperimentState.COMPLETED.isTerminal()).isTrue();
        assertThat(ExperimentState.ABORTED.isTerminal()).isTrue();
        assertThat(ExperimentState.FAILED.isTerminal()).isTrue();
        assertThat(ExperimentState.ACTIVE.isTerminal()).isFalse();
    }

    @Test
    void aggregateEnforcesTransitionsAndRejectsIllegalOnes() {
        Experiment exp = new Experiment("t", "conn", "cid", null,
                ScenarioType.CPU_OVERHEAD, Map.of("workers", 2), 30, 10, 1000);
        assertThat(exp.getState()).isEqualTo(ExperimentState.CREATED);

        exp.transitionTo(ExperimentState.VALIDATING);
        exp.transitionTo(ExperimentState.BASELINE);
        assertThat(exp.getState()).isEqualTo(ExperimentState.BASELINE);

        assertThatThrownBy(() -> exp.transitionTo(ExperimentState.COMPLETED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Illegal experiment transition");
    }
}
