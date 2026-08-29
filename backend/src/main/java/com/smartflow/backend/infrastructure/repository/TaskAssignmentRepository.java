package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.TaskAssignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskAssignmentRepository extends JpaRepository<TaskAssignment, Long> {

    // Un seul active=true par demande (contrainte V2 : uq_task_assignments_active_per_request).
    Optional<TaskAssignment> findByRequestIdAndActiveTrue(Long requestId);

    // §6.6 - file personnelle "Mes tâches" et file d'équipe.
    Page<TaskAssignment> findByAssignedUserIdAndActiveTrue(Long assignedUserId, Pageable pageable);

    Page<TaskAssignment> findByAssignedTeamIdAndActiveTrue(Long assignedTeamId, Pageable pageable);

    // §6.9 - "délai moyen de prise en charge" (DashboardService) : toutes les affectations
    // (pas seulement l'active) d'un lot de demandes, pour retenir la première par demande.
    List<TaskAssignment> findByRequestIdIn(List<Long> requestIds);
}
