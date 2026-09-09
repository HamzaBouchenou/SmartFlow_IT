interface ToggleSwitchProps {
  /** Libellé accessible : ce que la bascule commande, jamais « activer »/« désactiver ». */
  label: string;
  checked: boolean;
  disabled?: boolean;
  onChange: (checked: boolean) => void;
}

/**
 * L'interrupteur des maquettes 09 et 10. Une vraie case à cocher native, seulement
 * habillée : elle garde son rôle, son état pour les lecteurs d'écran, son anneau
 * `:focus-visible` et sa navigation au clavier (§8 - accessibilité). Elle est
 * déplacée hors de l'écran plutôt que masquée par `display: none`, qui la retirerait de
 * l'ordre de tabulation.
 *
 * `disabled` n'est jamais décoratif ici : le §6.8 exige que les alertes obligatoires
 * restent *visibles* et non basculables, pas qu'elles disparaissent - et c'est le serveur
 * qui décide lesquelles (MandatoryNotificationRule/ADR-12), jamais cet habillage.
 */
export function ToggleSwitch({ label, checked, disabled = false, onChange }: ToggleSwitchProps) {
  return (
    <span className="toggle">
      <input
        type="checkbox"
        aria-label={label}
        checked={checked}
        disabled={disabled}
        onChange={(event) => onChange(event.target.checked)}
      />
      <span className="toggle-track" aria-hidden="true">
        <span className="toggle-thumb" />
      </span>
    </span>
  );
}
