package com.smartflow.backend.api.dto.response;

/**
 * §9.4 - écran Accueil : "Raccourcis, demandes récentes, tâches à traiter et indicateurs
 * adaptés au rôle." Cette réponse unique porte les indicateurs "adaptés au rôle" que §9.4
 * demande en les faisant coïncider avec les deux vues que §6.9 nomme séparément : requester
 * = "vue demandeur", agent = "vue agent" (la troisième, "vue responsable", reste le
 * tableau de bord de service existant, §11.2 - GET /api/v1/dashboards/service).
 *
 * requester est toujours renseigné : tout utilisateur authentifié est demandeur de ses
 * propres dossiers (§5), qu'il porte par ailleurs un rôle complémentaire ou non. agent est
 * `null` pour un utilisateur qui ne porte aucune UserRoleAssignment - un REQUESTER pur n'a
 * structurellement aucune file de travail à afficher.
 */
public record HomeDashboardResponse(RequesterHomeResponse requester, AgentHomeResponse agent) {
}
