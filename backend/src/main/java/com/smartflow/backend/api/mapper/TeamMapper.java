package com.smartflow.backend.api.mapper;

import com.smartflow.backend.api.dto.response.TeamResponse;
import com.smartflow.backend.domain.entity.Team;
import com.smartflow.backend.domain.entity.User;

public final class TeamMapper {

    private TeamMapper() {
    }

    public static TeamResponse toResponse(Team team) {
        User lead = team.getLead();
        return new TeamResponse(team.getId(), team.getName(), team.getDepartment().getId(),
                team.getDepartment().getName(), lead != null ? lead.getId() : null,
                lead != null ? lead.getFirstName() + " " + lead.getLastName() : null, team.isActive());
    }
}
