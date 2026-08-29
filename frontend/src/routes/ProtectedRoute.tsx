import { Navigate, useLocation } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from '../auth/AuthContext';

/** §6.1 - redirige vers la connexion tant que GET /auth/me n'a pas confirmé une session
 * active. `loading` évite un flash de redirection au premier rendu, avant que la réponse
 * de /auth/me ne soit connue. */
export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return <p className="page-loading">Chargement…</p>;
  }
  if (!user) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  return <>{children}</>;
}
