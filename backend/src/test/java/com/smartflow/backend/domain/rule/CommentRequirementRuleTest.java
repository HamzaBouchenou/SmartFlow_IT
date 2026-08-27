package com.smartflow.backend.domain.rule;

import com.smartflow.backend.domain.enums.WorkflowAction;
import com.smartflow.backend.domain.exception.InvalidRequestStateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RG-05 - "Commentaire de rejet obligatoire... porté par la transition, pas par un if codé en dur". */
class CommentRequirementRuleTest {

    private final CommentRequirementRule rule = new CommentRequirementRule();

    @Test
    @DisplayName("RG-05 - REJECT with a blank comment is refused")
    void rejectWithBlankCommentFails() {
        assertThatThrownBy(() -> rule.validate(WorkflowAction.REJECT, "   "))
                .isInstanceOf(InvalidRequestStateException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(
                        ((InvalidRequestStateException) ex).getCode()).isEqualTo("COMMENT_REQUIRED"));
    }

    @Test
    @DisplayName("RG-05 - REJECT with a null comment is refused")
    void rejectWithNullCommentFails() {
        assertThatThrownBy(() -> rule.validate(WorkflowAction.REJECT, null))
                .isInstanceOf(InvalidRequestStateException.class);
    }

    @Test
    @DisplayName("RG-05 - REJECT with a real comment passes")
    void rejectWithCommentPasses() {
        assertThatCode(() -> rule.validate(WorkflowAction.REJECT, "Budget non disponible")).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = WorkflowAction.class, names = "REJECT", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("no other action requires a comment, even blank")
    void noOtherActionRequiresAComment(WorkflowAction action) {
        assertThatCode(() -> rule.validate(action, null)).doesNotThrowAnyException();
        assertThatCode(() -> rule.validate(action, "")).doesNotThrowAnyException();
    }
}
