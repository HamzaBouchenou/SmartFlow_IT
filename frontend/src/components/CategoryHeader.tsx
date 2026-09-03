// §6.2 - "présentation des services sous forme de catégories". La catégorie est une chaîne
// libre côté serveur (`ServiceCatalog.category`) : sa couleur est donc dérivée de façon
// déterministe de son nom, jamais d'une table de correspondance qu'il faudrait tenir à jour
// à chaque catégorie créée depuis l'écran d'administration.
const TONES = ['primary', 'violet', 'success', 'warning', 'danger'] as const;

function toneFor(value: string): string {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) {
    hash = (hash * 31 + value.charCodeAt(index)) % 100_000;
  }
  return TONES[hash % TONES.length];
}

export function CategoryHeader({ category }: { category: string | null | undefined }) {
  const tone = toneFor(category ?? '');
  return (
    <div className="category-header">
      <span className={`category-mark tone-${tone}`} aria-hidden="true" />
      {category && <span className={`category-badge tone-${tone}`}>{category}</span>}
    </div>
  );
}
