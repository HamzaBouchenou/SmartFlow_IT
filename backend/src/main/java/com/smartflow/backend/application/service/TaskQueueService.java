package com.smartflow.backend.application.service;

import com.smartflow.backend.domain.entity.Request;
import com.smartflow.backend.domain.entity.TaskAssignment;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.enums.ScopeType;
import com.smartflow.backend.domain.enums.SlaStatus;
import com.smartflow.backend.infrastructure.repository.RequestRepository;
import com.smartflow.backend.infrastructure.repository.UserRoleAssignmentRepository;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * §6.6 - "File personnelle « Mes tâches » et file d'équipe", avec les filtres qu'elle
 * décrit. RequestRepository extends JpaSpecificationExecutor précisément pour ce cas
 * d'usage (voir son propre commentaire) : chaque filtre optionnel est une Specification
 * composée avec Specification.allOf, jamais une méthode dérivée par combinaison de filtres
 * (qui exploserait combinatoirement).
 *
 * Ni myTasks ni teamTasks n'appellent AuthorizationService.canAct : la file personnelle
 * est intrinsèquement bornée à actingUser lui-même, et la file d'équipe aux seules équipes
 * où actingUser tient une affectation de rôle scope=TEAM - aucune des deux ne peut exposer
 * les tâches d'un tiers.
 */
@Service
public class TaskQueueService {

    private final RequestRepository requestRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;

    public TaskQueueService(RequestRepository requestRepository, UserRoleAssignmentRepository userRoleAssignmentRepository) {
        this.requestRepository = requestRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
    }

    /** §6.6 - "File personnelle « Mes tâches »" : demandes activement affectées à actingUser en personne. */
    @Transactional(readOnly = true)
    public Page<Request> myTasks(User actingUser, TaskQueueFilter filter, Pageable pageable) {
        Specification<Request> spec = compose(assignedToUser(actingUser.getId()), filter);
        return requestRepository.findAll(spec, pageable);
    }

    /**
     * §6.6 - "file d'équipe" : demandes déposées sur une équipe où actingUser tient une
     * affectation de rôle scope=TEAM (§5.1), pas encore reprises par une personne précise.
     * Sans équipe, la file est vide - ce n'est pas une erreur, un demandeur n'en a
     * simplement aucune.
     */
    @Transactional(readOnly = true)
    public Page<Request> teamTasks(User actingUser, TaskQueueFilter filter, Pageable pageable) {
        List<Long> teamIds = userRoleAssignmentRepository.findByUserId(actingUser.getId()).stream()
                .filter(assignment -> assignment.getScopeType() == ScopeType.TEAM)
                .map(assignment -> assignment.getScopeId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (teamIds.isEmpty()) {
            return Page.empty(pageable);
        }
        Specification<Request> spec = compose(assignedToAnyTeam(teamIds), filter);
        return requestRepository.findAll(spec, pageable);
    }

    private Specification<Request> compose(Specification<Request> queueScope, TaskQueueFilter filter) {
        List<Specification<Request>> specs = new ArrayList<>();
        specs.add(queueScope);
        if (filter.status() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("status"), filter.status()));
        }
        if (filter.priority() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("priority"), filter.priority()));
        }
        if (filter.requesterId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("requester").get("id"), filter.requesterId()));
        }
        if (filter.category() != null && !filter.category().isBlank()) {
            specs.add((root, query, cb) -> cb.equal(
                    root.get("requestType").get("serviceCatalog").get("category"), filter.category()));
        }
        if (Boolean.TRUE.equals(filter.overdue())) {
            specs.add((root, query, cb) -> cb.equal(root.get("slaStatus"), SlaStatus.OVERDUE));
        }
        if (filter.submittedFrom() != null) {
            specs.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("submittedAt"), filter.submittedFrom()));
        }
        if (filter.submittedTo() != null) {
            specs.add((root, query, cb) -> cb.lessThanOrEqualTo(root.get("submittedAt"), filter.submittedTo()));
        }
        return Specification.allOf(specs);
    }

    private Specification<Request> assignedToUser(Long userId) {
        return (root, query, cb) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            var taskAssignment = subquery.from(TaskAssignment.class);
            subquery.select(taskAssignment.get("id"))
                    .where(cb.equal(taskAssignment.get("request"), root),
                            cb.equal(taskAssignment.get("assignedUser").get("id"), userId),
                            cb.isTrue(taskAssignment.get("active")));
            return cb.exists(subquery);
        };
    }

    private Specification<Request> assignedToAnyTeam(List<Long> teamIds) {
        return (root, query, cb) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            var taskAssignment = subquery.from(TaskAssignment.class);
            subquery.select(taskAssignment.get("id"))
                    .where(cb.equal(taskAssignment.get("request"), root),
                            taskAssignment.get("assignedTeam").get("id").in(teamIds),
                            cb.isTrue(taskAssignment.get("active")));
            return cb.exists(subquery);
        };
    }
}
