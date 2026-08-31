package com.smartflow.backend.api.mapper;

import com.smartflow.backend.api.dto.response.StepAdminResponse;
import com.smartflow.backend.api.dto.response.TransitionAdminResponse;
import com.smartflow.backend.api.dto.response.WorkflowDefinitionAdminResponse;
import com.smartflow.backend.application.service.WorkflowAdminService.WorkflowDefinitionView;
import com.smartflow.backend.domain.entity.Department;
import com.smartflow.backend.domain.entity.Step;
import com.smartflow.backend.domain.entity.Team;
import com.smartflow.backend.domain.entity.Transition;
import com.smartflow.backend.domain.entity.WorkflowDefinition;

public final class WorkflowDefinitionAdminMapper {

    private WorkflowDefinitionAdminMapper() {
    }

    public static WorkflowDefinitionAdminResponse toResponse(WorkflowDefinitionView view) {
        WorkflowDefinition definition = view.definition();
        return new WorkflowDefinitionAdminResponse(definition.getId(), definition.getRequestType().getId(),
                definition.getVersion(), definition.getStatus().name(), definition.getPublishedAt(),
                definition.getCreatedAt(), view.steps().stream().map(WorkflowDefinitionAdminMapper::toStepResponse).toList(),
                view.transitions().stream().map(WorkflowDefinitionAdminMapper::toTransitionResponse).toList());
    }

    /** Sans étapes/transitions (`null`) - pour une ligne d'une liste de versions. */
    public static WorkflowDefinitionAdminResponse toSummary(WorkflowDefinition definition) {
        return new WorkflowDefinitionAdminResponse(definition.getId(), definition.getRequestType().getId(),
                definition.getVersion(), definition.getStatus().name(), definition.getPublishedAt(),
                definition.getCreatedAt(), null, null);
    }

    public static StepAdminResponse toStepResponse(Step step) {
        Team team = step.getResponsibleTeam();
        return new StepAdminResponse(step.getId(), step.getCode(), step.getName(), step.getDisplayOrder(),
                step.getResponsibleRole() != null ? step.getResponsibleRole().name() : null,
                team != null ? team.getId() : null, team != null ? team.getName() : null, step.isSuspendSla());
    }

    public static TransitionAdminResponse toTransitionResponse(Transition transition) {
        Department department = transition.getConditionDepartment();
        return new TransitionAdminResponse(transition.getId(), transition.getFromStep().getId(),
                transition.getAction().name(), transition.getToStep() != null ? transition.getToStep().getId() : null,
                transition.getConditionPriority() != null ? transition.getConditionPriority().name() : null,
                department != null ? department.getId() : null, department != null ? department.getName() : null,
                transition.getConditionFieldCode(), transition.getConditionFieldValue());
    }
}
