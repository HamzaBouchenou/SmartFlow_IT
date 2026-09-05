package com.smartflow.backend.application.service;

import com.smartflow.backend.application.security.AuthorizationService;
import com.smartflow.backend.crosscutting.audit.AuditService;
import com.smartflow.backend.domain.entity.Department;
import com.smartflow.backend.domain.entity.Team;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.exception.EntityNotFoundException;
import com.smartflow.backend.infrastructure.repository.DepartmentRepository;
import com.smartflow.backend.infrastructure.repository.TeamRepository;
import com.smartflow.backend.infrastructure.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * §4.1/§6.10 - "Organisation : directions, services, équipes et responsables" ;
 * "Gestion... des équipes." Department and Team share this one service (both
 * organisational, Team nested under Department, exactly as CatalogService already pairs
 * ServiceCatalog with RequestType for the same reason). RG-02/RG-12 - never a physical
 * delete: both referentials are only ever created, updated, or logically
 * (de)activated - activate/deactivate are their own methods, deliberately excluded from
 * update, mirroring RequestController separating submit/cancel/reopen from a plain update.
 * Every write is RG-11 configuration and journalized.
 */
@Service
public class OrganizationAdminService {

    private final DepartmentRepository departmentRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public OrganizationAdminService(DepartmentRepository departmentRepository, TeamRepository teamRepository,
                                     UserRepository userRepository, AuthorizationService authorizationService,
                                     AuditService auditService) {
        this.departmentRepository = departmentRepository;
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    // --- Department ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Department> listDepartments(User actingUser) {
        requireFunctionalAdmin(actingUser);
        return departmentRepository.findAll();
    }

    @Transactional
    public Department createDepartment(User actingUser, String name, Long parentId, Long leadId) {
        requireFunctionalAdmin(actingUser);
        Department department = new Department(name, resolveParent(parentId));
        department.setLead(resolveUser(leadId));
        department = departmentRepository.save(department);
        auditService.record(actingUser, "CREATE", "Department", department.getId().toString(), "name=" + name);
        return department;
    }

    @Transactional
    public Department updateDepartment(User actingUser, Long departmentId, String name, Long parentId, Long leadId) {
        requireFunctionalAdmin(actingUser);
        Department department = getDepartment(departmentId);
        department.setName(name);
        department.setParent(resolveParent(parentId));
        department.setLead(resolveUser(leadId));
        department = departmentRepository.save(department);
        auditService.record(actingUser, "UPDATE", "Department", department.getId().toString(), "name=" + name);
        return department;
    }

    @Transactional
    public Department setDepartmentActive(User actingUser, Long departmentId, boolean active) {
        requireFunctionalAdmin(actingUser);
        Department department = getDepartment(departmentId);
        department.setActive(active);
        department = departmentRepository.save(department);
        auditService.record(actingUser, active ? "ACTIVATE" : "DEACTIVATE", "Department", department.getId().toString(), null);
        return department;
    }

    // --- Team --------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Team> listTeams(User actingUser, Long departmentId) {
        requireFunctionalAdmin(actingUser);
        return departmentId != null ? teamRepository.findByDepartmentId(departmentId) : teamRepository.findAll();
    }

    @Transactional
    public Team createTeam(User actingUser, String name, Long departmentId, Long leadId) {
        requireFunctionalAdmin(actingUser);
        Team team = new Team(name, getDepartment(departmentId));
        team.setLead(resolveUser(leadId));
        team = teamRepository.save(team);
        auditService.record(actingUser, "CREATE", "Team", team.getId().toString(), "name=" + name);
        return team;
    }

    @Transactional
    public Team updateTeam(User actingUser, Long teamId, String name, Long departmentId, Long leadId) {
        requireFunctionalAdmin(actingUser);
        Team team = getTeam(teamId);
        team.setName(name);
        team.setDepartment(getDepartment(departmentId));
        team.setLead(resolveUser(leadId));
        team = teamRepository.save(team);
        auditService.record(actingUser, "UPDATE", "Team", team.getId().toString(), "name=" + name);
        return team;
    }

    @Transactional
    public Team setTeamActive(User actingUser, Long teamId, boolean active) {
        requireFunctionalAdmin(actingUser);
        Team team = getTeam(teamId);
        team.setActive(active);
        team = teamRepository.save(team);
        auditService.record(actingUser, active ? "ACTIVATE" : "DEACTIVATE", "Team", team.getId().toString(), null);
        return team;
    }

    // --- shared --------------------------------------------------------------------------

    private void requireFunctionalAdmin(User actingUser) {
        if (!authorizationService.isFunctionalAdmin(actingUser)) {
            throw new EntityNotFoundException("Ressource introuvable.");
        }
    }

    private Department getDepartment(Long departmentId) {
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new EntityNotFoundException("Direction/service introuvable."));
    }

    private Department resolveParent(Long parentId) {
        return parentId != null ? getDepartment(parentId) : null;
    }

    private Team getTeam(Long teamId) {
        return teamRepository.findById(teamId).orElseThrow(() -> new EntityNotFoundException("Équipe introuvable."));
    }

    private User resolveUser(Long userId) {
        return userId != null
                ? userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("Utilisateur introuvable."))
                : null;
    }
}
