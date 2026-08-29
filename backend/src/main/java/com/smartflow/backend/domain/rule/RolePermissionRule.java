package com.smartflow.backend.domain.rule;

import com.smartflow.backend.domain.enums.Role;
import com.smartflow.backend.domain.enums.WorkflowAction;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The "roleAccordePermission" clause of canAct (CLAUDE.md, application/security): which
 * WorkflowAction a Role may ever perform, independent of perimeter and of the current
 * step. No Spring, no database access - a fixed matrix read directly off §5's acteurs
 * table, unit-testable in isolation (see RolePermissionRuleTest).
 *
 * This is authorization policy, not workflow configuration: it does not vary per
 * RequestType and must not be duplicated as scattered @PreAuthorize annotations (CLAUDE.md
 * - "Ne disperse jamais ces règles"). A role absent from the map, or an action absent from
 * its set, grants nothing - the default is deny, not allow.
 */
public class RolePermissionRule {

    private static final Map<Role, Set<WorkflowAction>> GRANTS = buildGrants();

    private static Map<Role, Set<WorkflowAction>> buildGrants() {
        Map<Role, Set<WorkflowAction>> grants = new EnumMap<>(Role.class);
        // §5 - "Manager / valideur : accepter, rejeter ou retourner une demande".
        grants.put(Role.MANAGER, EnumSet.of(WorkflowAction.VALIDATE, WorkflowAction.REJECT, WorkflowAction.RETURN));
        // §5 - "Agent de traitement : qualifier, prendre en charge, commenter et résoudre".
        // Prendre en charge -> ASSIGN, résoudre -> CLOSE ; qualifier/commenter ne sont pas
        // des WorkflowAction (ce ne sont pas des transitions de workflow). REOPEN (ADR-14) :
        // symétrique de CLOSE - un rôle qui peut clôturer peut rouvrir ce qu'il a clôturé.
        grants.put(Role.AGENT, EnumSet.of(WorkflowAction.ASSIGN, WorkflowAction.REQUEST_INFO,
                WorkflowAction.CLOSE, WorkflowAction.REOPEN));
        // §5 - "Responsable de service : piloter la charge, les délais et les règles de son
        // service" sur "toutes les demandes ... de son service" -> autorité de recours sur
        // l'ensemble des actions de workflow, bornée par le périmètre DEPARTMENT de
        // perimetreCouvre, pas par ce rôle.
        grants.put(Role.SERVICE_MANAGER, EnumSet.allOf(WorkflowAction.class));
        // REQUESTER, FUNCTIONAL_ADMIN, TECHNICAL_ADMIN, AUDITOR : aucune cellule du §5 ne
        // leur attribue d'action de workflow sur une demande - absents de la map, donc
        // aucune permission (le défaut est un refus, pas un accord).
        return Map.copyOf(grants);
    }

    public boolean grants(Role role, WorkflowAction action) {
        return GRANTS.getOrDefault(role, Set.of()).contains(action);
    }
}
