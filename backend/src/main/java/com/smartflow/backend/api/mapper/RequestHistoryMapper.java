package com.smartflow.backend.api.mapper;

import com.smartflow.backend.api.dto.response.RequestHistoryResponse;
import com.smartflow.backend.domain.entity.RequestHistory;
import com.smartflow.backend.domain.entity.Step;
import com.smartflow.backend.domain.entity.User;

public final class RequestHistoryMapper {

    private RequestHistoryMapper() {
    }

    public static RequestHistoryResponse toResponse(RequestHistory history) {
        Step fromStep = history.getFromStep();
        Step toStep = history.getToStep();
        User actor = history.getActor();
        return new RequestHistoryResponse(history.getId(), history.getAction().name(),
                fromStep != null ? fromStep.getName() : null, toStep != null ? toStep.getName() : null,
                actor.getId(), actor.getFirstName() + " " + actor.getLastName(), history.getComment(),
                history.getOccurredAt());
    }
}
