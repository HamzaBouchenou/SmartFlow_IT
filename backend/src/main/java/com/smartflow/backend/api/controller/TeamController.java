package com.smartflow.backend.api.controller;

import com.smartflow.backend.api.dto.request.CreateTeamRequest;
import com.smartflow.backend.api.dto.request.UpdateTeamRequest;
import com.smartflow.backend.api.dto.response.TeamResponse;
import com.smartflow.backend.api.mapper.TeamMapper;
import com.smartflow.backend.application.service.OrganizationAdminService;
import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** §4.1/§6.10 - équipes. RG-02/RG-12 - jamais de suppression physique, seulement
 * activate/deactivate. */
@RestController
@RequestMapping("/api/v1/admin/teams")
public class TeamController {

    private final OrganizationAdminService organizationAdminService;

    public TeamController(OrganizationAdminService organizationAdminService) {
        this.organizationAdminService = organizationAdminService;
    }

    @GetMapping
    public List<TeamResponse> list(@AuthenticationPrincipal SmartFlowUserDetails principal,
                                    @RequestParam(required = false) Long departmentId) {
        return organizationAdminService.listTeams(principal.getUser(), departmentId).stream()
                .map(TeamMapper::toResponse)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeamResponse create(@AuthenticationPrincipal SmartFlowUserDetails principal, @Valid @RequestBody CreateTeamRequest body) {
        return TeamMapper.toResponse(
                organizationAdminService.createTeam(principal.getUser(), body.name(), body.departmentId(), body.leadId()));
    }

    @PutMapping("/{id}")
    public TeamResponse update(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id,
                                @Valid @RequestBody UpdateTeamRequest body) {
        return TeamMapper.toResponse(
                organizationAdminService.updateTeam(principal.getUser(), id, body.name(), body.departmentId(), body.leadId()));
    }

    @PostMapping("/{id}/activate")
    public TeamResponse activate(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id) {
        return TeamMapper.toResponse(organizationAdminService.setTeamActive(principal.getUser(), id, true));
    }

    @PostMapping("/{id}/deactivate")
    public TeamResponse deactivate(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id) {
        return TeamMapper.toResponse(organizationAdminService.setTeamActive(principal.getUser(), id, false));
    }
}
