package com.smartflow.backend.api.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * §6.1 - "Consultation ... des informations de profil autorisées", par l'utilisateur
 * lui-même. Distinct d'`UserResponse` (§6.10, ce qu'un FUNCTIONAL_ADMIN voit d'un tiers) :
 * ce que le titulaire d'un compte a le droit de lire de son propre rattachement n'est pas
 * ce qu'un administrateur a le droit de lire de celui d'un autre - deux DTO plutôt qu'un
 * seul réutilisé, pour que l'un puisse évoluer sans élargir l'autre par accident.
 *
 * Aucun champ n'est modifiable depuis cet écran : `UpdateProfileRequest` reste borné au
 * prénom et au nom (§6.1's own ligne suivante réserve rôles, service de rattachement et
 * responsable hiérarchique à l'administration). Le reste est en lecture, pas en saisie.
 *
 * `sessionExpiresAt` matérialise "Expiration de session" du même §6.1 : l'instant auquel la
 * session courante expirera si aucune requête ne l'atteint d'ici là (posé par
 * SessionTimeoutListener, donc bien la durée administrable du §6.10 et non une constante).
 * Nul si la session ne connaît pas de délai d'inactivité.
 */
public record MyProfileResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        boolean active,
        Long departmentId,
        String departmentName,
        String directionName,
        String managerName,
        List<RoleAssignmentResponse> roles,
        Instant sessionExpiresAt) {
}
