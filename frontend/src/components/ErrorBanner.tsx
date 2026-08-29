import { ApiError } from '../api/client';

/** Affiche une ApiError de façon uniforme : CLAUDE.md impose un format d'erreur unique
 * côté serveur, ce composant en est le seul point d'affichage côté client pour rester
 * cohérent partout où une action peut échouer. */
export function ErrorBanner({ error }: { error: unknown }) {
  if (!error) {
    return null;
  }
  const message = error instanceof ApiError ? error.message : 'Une erreur inattendue est survenue.';
  const fieldErrors = error instanceof ApiError ? error.fieldErrors : [];

  return (
    <div className="error-banner" role="alert">
      <p>{message}</p>
      {fieldErrors.length > 0 && (
        <ul>
          {fieldErrors.map((fieldError) => (
            <li key={fieldError.field}>
              {fieldError.field} : {fieldError.message}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
