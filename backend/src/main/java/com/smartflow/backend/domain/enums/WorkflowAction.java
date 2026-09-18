package com.smartflow.backend.domain.enums;

/**
 * Actions a Step can allow (§6.5 - Workflow et validations) :
 * "valider, rejeter, retourner, affecter, demander un complément ou clôturer".
 *
 * Deux valeurs font exception et forment une famille à part : elles écrivent une ligne
 * RequestHistory sans jamais être un arc du graphe de workflow, donc sans jamais être
 * résolues par une Transition (WorkflowActionAvailabilityRule) ni évaluées par canAct.
 * <ul>
 *   <li>REOPEN (RG-08, ADR-14) : une demande CLOSED n'a plus de currentStep (ADR-03) pour
 *       porter une Transition sortante. AuthorizationService.canReopen décide,
 *       RequestService.reopen exécute.</li>
 *   <li>SUBMIT (§3.4, ADR-23) : un brouillon n'est encore sur aucune étape, et son workflow
 *       n'est gelé (RG-03) que par ce geste même. La propriété du brouillon décide
 *       (RequestService.getOwnedDraft, RG-06), RequestService.submit exécute.</li>
 * </ul>
 * {@link #isConfigurable()} est ce qui empêche un administrateur de câbler l'une d'elles
 * comme transition depuis AdminWorkflowsPage : elle serait enregistrée, proposée dans
 * availableActions[], puis jamais exécutable.
 */
public enum WorkflowAction {
    SUBMIT,
    VALIDATE,
    REJECT,
    RETURN,
    ASSIGN,
    REQUEST_INFO,
    CLOSE,
    REOPEN;

    /**
     * Une action qu'un WorkflowDefinition peut porter comme Transition (§6.5/§6.10). Toute
     * valeur ajoutée ici devra se positionner explicitement : le défaut est "configurable",
     * et seules les deux exceptions documentées ci-dessus ne le sont pas.
     */
    public boolean isConfigurable() {
        return this != SUBMIT && this != REOPEN;
    }
}
