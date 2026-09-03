/** État vide d'une liste (README des maquettes - "toute liste vide"). Une liste vide est
 * un résultat, pas une erreur : elle ne passe donc jamais par `ErrorBanner`. */
export function EmptyState({ message }: { message: string }) {
  return <p className="empty-state">{message}</p>;
}
