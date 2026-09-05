package com.smartflow.backend.domain.rule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests AutoAssignmentRule.pickLeastLoaded - §6.6 "affectation automatique... une règle de
 * répartition simple" : le candidat qui porte le moins de demandes actives l'emporte, un id
 * plus petit départageant une égalité (déterministe, jamais un ordre de retour arbitraire).
 */
class AutoAssignmentRuleTest {

    private final AutoAssignmentRule rule = new AutoAssignmentRule();

    @Test
    @DisplayName("picks the candidate with the smallest current load")
    void picksLeastLoadedCandidate() {
        List<AutoAssignmentRule.Candidate> candidates = List.of(
                new AutoAssignmentRule.Candidate(1L, 3),
                new AutoAssignmentRule.Candidate(2L, 1),
                new AutoAssignmentRule.Candidate(3L, 5));

        assertThat(rule.pickLeastLoaded(candidates)).isEqualTo(2L);
    }

    @Test
    @DisplayName("a tie on load is broken by the smallest user id, deterministically")
    void tieBrokenByUserId() {
        List<AutoAssignmentRule.Candidate> candidates = List.of(
                new AutoAssignmentRule.Candidate(7L, 2),
                new AutoAssignmentRule.Candidate(4L, 2));

        assertThat(rule.pickLeastLoaded(candidates)).isEqualTo(4L);
    }

    @Test
    @DisplayName("no candidates means no one to assign to")
    void emptyCandidateListThrows() {
        assertThatThrownBy(() -> rule.pickLeastLoaded(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
