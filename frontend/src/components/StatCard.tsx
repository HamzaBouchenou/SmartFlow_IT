import type { ReactNode } from 'react';

/** Carte d'indicateur des maquettes 02 et 07 : libellé, valeur, légende, et un liseré
 * gauche dont la couleur porte le sens (neutre, alerte, urgence, résolu). */
export type StatTone = 'primary' | 'danger' | 'warning' | 'success';

export function StatCard({
  label,
  value,
  caption,
  tone = 'primary',
  empty = false,
}: {
  label: string;
  value: ReactNode;
  caption?: string;
  tone?: StatTone;
  /** Aucune donnée sur la période (§6.9 - un indicateur `null` n'est jamais lu comme un 0).
   * La valeur reste le tiret standard de `lib/format`, mais en gris et en petit : affiché
   * en 30 px dans la couleur d'accent, il se lit comme une barre pleine. */
  empty?: boolean;
}) {
  return (
    <div className={`kpi-card kpi-${tone}`}>
      <div className="kpi-label">{label}</div>
      <div className={empty ? 'kpi-value kpi-value-empty' : 'kpi-value'}>{value}</div>
      {caption && <div className="kpi-caption">{caption}</div>}
    </div>
  );
}
