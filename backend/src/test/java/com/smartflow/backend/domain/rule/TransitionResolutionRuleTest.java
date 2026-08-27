package com.smartflow.backend.domain.rule;

import com.smartflow.backend.domain.enums.Priority;
import com.smartflow.backend.domain.rule.TransitionResolutionRule.CandidateTransition;
import com.smartflow.backend.domain.rule.TransitionResolutionRule.ResolutionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §6.5 - "Conditions simples basées sur la catégorie, la priorité, le service ou une
 * valeur du formulaire" : which configured Transition applies among several sharing the
 * same (fromStep, action). Pure - no Spring, no database.
 */
class TransitionResolutionRuleTest {

    private final TransitionResolutionRule rule = new TransitionResolutionRule();

    private static final ResolutionContext NO_CONTEXT = new ResolutionContext(null, null, Map.of());

    @Test
    @DisplayName("a single unconditioned candidate always matches")
    void singleUnconditionedCandidateMatches() {
        CandidateTransition candidate = unconditioned(1L, 10L);

        Optional<CandidateTransition> resolved = rule.resolve(List.of(candidate), NO_CONTEXT);

        assertThat(resolved).contains(candidate);
    }

    @Test
    @DisplayName("no candidates at all resolves to nothing")
    void noCandidatesResolvesToEmpty() {
        assertThat(rule.resolve(List.of(), NO_CONTEXT)).isEmpty();
    }

    @Test
    @DisplayName("a conditioned candidate matching the request's priority wins over the unconditioned default")
    void matchingPriorityConditionWinsOverDefault() {
        CandidateTransition critical = withPriority(1L, 10L, Priority.CRITICAL);
        CandidateTransition fallback = unconditioned(2L, 20L);
        ResolutionContext context = new ResolutionContext(Priority.CRITICAL, null, Map.of());

        Optional<CandidateTransition> resolved = rule.resolve(List.of(fallback, critical), context);

        assertThat(resolved).contains(critical);
    }

    @Test
    @DisplayName("when the priority condition does not match, the unconditioned default applies")
    void nonMatchingPriorityConditionFallsBackToDefault() {
        CandidateTransition critical = withPriority(1L, 10L, Priority.CRITICAL);
        CandidateTransition fallback = unconditioned(2L, 20L);
        ResolutionContext context = new ResolutionContext(Priority.LOW, null, Map.of());

        Optional<CandidateTransition> resolved = rule.resolve(List.of(fallback, critical), context);

        assertThat(resolved).contains(fallback);
    }

    @Test
    @DisplayName("a conditioned candidate that matches nothing and no default present resolves to nothing")
    void nonMatchingConditionWithNoDefaultResolvesToEmpty() {
        CandidateTransition critical = withPriority(1L, 10L, Priority.CRITICAL);
        ResolutionContext context = new ResolutionContext(Priority.LOW, null, Map.of());

        assertThat(rule.resolve(List.of(critical), context)).isEmpty();
    }

    @Test
    @DisplayName("the department condition is evaluated against the request's service department id")
    void departmentConditionIsEvaluated() {
        CandidateTransition forDept7 = new CandidateTransition(1L, 10L, null, 7L, null, null);
        ResolutionContext matching = new ResolutionContext(null, 7L, Map.of());
        ResolutionContext mismatching = new ResolutionContext(null, 8L, Map.of());

        assertThat(rule.resolve(List.of(forDept7), matching)).contains(forDept7);
        assertThat(rule.resolve(List.of(forDept7), mismatching)).isEmpty();
    }

    @Test
    @DisplayName("the field condition is evaluated against the request's submitted form values, absent counts as a mismatch")
    void fieldConditionIsEvaluated() {
        CandidateTransition forUrgentField = new CandidateTransition(1L, 10L, null, null, "urgency", "Urgent");
        ResolutionContext matching = new ResolutionContext(null, null, Map.of("urgency", "Urgent"));
        ResolutionContext mismatching = new ResolutionContext(null, null, Map.of("urgency", "Normal"));
        ResolutionContext absent = new ResolutionContext(null, null, Map.of());

        assertThat(rule.resolve(List.of(forUrgentField), matching)).contains(forUrgentField);
        assertThat(rule.resolve(List.of(forUrgentField), mismatching)).isEmpty();
        assertThat(rule.resolve(List.of(forUrgentField), absent)).isEmpty();
    }

    @Test
    @DisplayName("a resolved candidate's null toStepId (CLOSE, terminal) is preserved, not treated as no match")
    void nullTargetStepIsPreservedForTerminalActions() {
        CandidateTransition terminal = unconditioned(1L, null);

        assertThat(rule.resolve(List.of(terminal), NO_CONTEXT)).hasValueSatisfying(
                candidate -> assertThat(candidate.toStepId()).isNull());
    }

    @Test
    @DisplayName("among several matching conditioned candidates, the lowest transitionId wins deterministically")
    void tiesAmongMatchingConditionedCandidatesBreakByLowestId() {
        CandidateTransition first = withPriority(5L, 50L, Priority.HIGH);
        CandidateTransition second = withPriority(3L, 30L, Priority.HIGH);
        ResolutionContext context = new ResolutionContext(Priority.HIGH, null, Map.of());

        Optional<CandidateTransition> resolved = rule.resolve(List.of(first, second), context);

        assertThat(resolved).contains(second);
    }

    private static CandidateTransition unconditioned(long transitionId, Long toStepId) {
        return new CandidateTransition(transitionId, toStepId, null, null, null, null);
    }

    private static CandidateTransition withPriority(long transitionId, long toStepId, Priority priority) {
        return new CandidateTransition(transitionId, toStepId, priority, null, null, null);
    }
}
