package com.smartflow.backend.domain.rule;

import com.smartflow.backend.domain.enums.WorkflowAction;
import com.smartflow.backend.domain.exception.InvalidRequestStateException;

import java.util.EnumSet;
import java.util.Set;

/**
 * RG-05 - "Commentaire de rejet obligatoire... porté par la transition, pas par un if codé
 * en dur" : quelles actions exigent un commentaire est cette seule petite table testable,
 * pas un `if (action == REJECT)` dispersé dans l'exécuteur de transition.
 */
public class CommentRequirementRule {

    private static final Set<WorkflowAction> REQUIRES_COMMENT = EnumSet.of(WorkflowAction.REJECT);

    public void validate(WorkflowAction action, String comment) {
        if (REQUIRES_COMMENT.contains(action) && (comment == null || comment.isBlank())) {
            throw new InvalidRequestStateException("COMMENT_REQUIRED",
                    "Un commentaire est obligatoire pour l'action " + action + " (RG-05).");
        }
    }
}
