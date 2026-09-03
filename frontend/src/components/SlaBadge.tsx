import { slaStatusLabel } from '../lib/format';

/** §6.7 - "indicateur visuel : dans le délai, à risque, en retard".
 *
 * La valeur affichée est celle que le serveur a matérialisée (RG-07, `SlaCalculator` puis
 * `SlaSweepScheduler`) : ce composant la traduit et la colore, il ne la recalcule jamais à
 * partir d'une échéance et de l'heure du navigateur. */
export function SlaBadge({ status }: { status: string | null | undefined }) {
  if (!status) {
    return <span className="sla-badge">—</span>;
  }
  return <span className={`sla-badge sla-${status.toLowerCase()}`}>{slaStatusLabel(status)}</span>;
}
