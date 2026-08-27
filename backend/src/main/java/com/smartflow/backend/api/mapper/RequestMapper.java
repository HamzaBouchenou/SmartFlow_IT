package com.smartflow.backend.api.mapper;

import com.smartflow.backend.api.dto.response.RequestDetailResponse;
import com.smartflow.backend.api.dto.response.RequestSummaryResponse;
import com.smartflow.backend.application.service.RequestService.RequestDetailView;
import com.smartflow.backend.domain.entity.Request;
import com.smartflow.backend.domain.entity.Step;
import com.smartflow.backend.domain.entity.TaskAssignment;
import com.smartflow.backend.domain.entity.Team;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.enums.WorkflowAction;

public final class RequestMapper {

    private RequestMapper() {
    }

    public static RequestDetailResponse toResponse(RequestDetailView view) {
        Request request = view.request();
        Step currentStep = request.getCurrentStep();
        TaskAssignment assignment = view.activeAssignment();
        User assignedUser = assignment != null ? assignment.getAssignedUser() : null;
        Team assignedTeam = assignment != null ? assignment.getAssignedTeam() : null;
        return new RequestDetailResponse(request.getId(), request.getReference(), request.getRequestType().getId(),
                request.getStatus().name(), request.getTitle(), request.getDescription(),
                currentStep != null ? currentStep.getId() : null, request.getSubmittedAt(), view.fieldValues(),
                view.availableActions().stream().map(WorkflowAction::name).toList(),
                assignedUser != null ? assignedUser.getId() : null, fullName(assignedUser),
                assignedTeam != null ? assignedTeam.getId() : null, assignedTeam != null ? assignedTeam.getName() : null);
    }

    /** §6.6 - une ligne de file de travail : voir RequestSummaryResponse pour ce qu'une liste n'a pas besoin de porter. */
    public static RequestSummaryResponse toSummary(Request request) {
        Step currentStep = request.getCurrentStep();
        return new RequestSummaryResponse(request.getId(), request.getReference(), request.getTitle(),
                request.getStatus().name(), request.getPriority() != null ? request.getPriority().name() : null,
                request.getRequestType().getServiceCatalog().getCategory(), fullName(request.getRequester()),
                currentStep != null ? currentStep.getName() : null, request.getSubmittedAt(),
                request.getSlaStatus() != null ? request.getSlaStatus().name() : null);
    }

    private static String fullName(User user) {
        return user != null ? user.getFirstName() + " " + user.getLastName() : null;
    }
}
