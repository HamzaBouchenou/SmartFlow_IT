package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.Request;
import com.smartflow.backend.domain.enums.RequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RequestRepository extends JpaRepository<Request, Long>, JpaSpecificationExecutor<Request> {

    // RG-01 - accès par la référence unique et non réutilisable (ex. DEM-2026-000123).
    Optional<Request> findByReference(String reference);

    // RG-01 - "Séquence PostgreSQL + contrainte UNIQUE. Jamais count(*) + 1" ; ADR-02
    // (docs/DECISIONS.md) - la partie numérique de la référence vient de cette séquence
    // (V2 : request_reference_seq), jamais réinitialisée par année.
    @Query(value = "select nextval('request_reference_seq')", nativeQuery = true)
    long nextReferenceSequenceValue();

    // RG-06 - "Un demandeur ne voit que ses dossiers", filtré côté serveur. §9.4/§6.9 -
    // "vue demandeur" (RequestService.listMine) : la seule liste dont l'accès est borné par
    // la propriété du dossier plutôt que par canView/canAct, donc jamais de brouillon d'un
    // tiers ici, y compris pour un rôle complémentaire (ADR-10 ne s'applique pas).
    Page<Request> findByRequesterId(Long requesterId, Pageable pageable);

    // Même liste que ci-dessus, restreinte à un statut (§11.1 - "filtres normalisés").
    Page<Request> findByRequesterIdAndStatus(Long requesterId, RequestStatus status, Pageable pageable);

    // RG-07 - le balayage SLA planifié (infrastructure/scheduler) ne recalcule que les
    // demandes en cours ; un brouillon n'a pas encore de compteur démarré et une demande
    // close/annulée/archivée n'en a plus besoin. Sans pagination : volumétrie PFA seulement,
    // à revoir avant un usage à plus grande échelle.
    List<Request> findByStatus(RequestStatus status);

    // RG-12/ADR-15 - le balayage d'archivage (infrastructure/scheduler) ne reconsidère que
    // les demandes déjà closes ou annulées, jamais un brouillon ni une demande en cours.
    List<Request> findByStatusIn(List<RequestStatus> statuses);

    // JpaSpecificationExecutor porte les filtres dynamiques normalisés des listes (§11.1)
    // et les filtres de "Mes tâches" / tableaux de bord (§6.6, §6.9) : statut, priorité,
    // demandeur, catégorie, équipe, dates, retard — composés en application/service plutôt
    // que comme autant de méthodes dérivées ici.
}
