import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import * as authApi from '../api/auth';
import type { LoginResponse } from '../api/types';

// §6.1/§11.2 - état de session côté SPA. ADR-01 : le cookie de session ne dit rien en
// JavaScript, donc "qui est connecté" ne peut venir que d'un appel serveur (GET /auth/me),
// jamais d'une donnée décodée côté client comme le serait un JWT.

interface AuthContextValue {
  user: LoginResponse | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  /** Relit GET /auth/me : appelé après une mise à jour de profil (§6.1), pour que l'identité
   * affichée dans l'ossature suive le changement sans recharger la page. */
  refresh: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<LoginResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    authApi.me().then((current) => {
      if (!cancelled) {
        setUser(current);
        setLoading(false);
      }
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const logged = await authApi.login({ email, password });
    setUser(logged);
  }, []);

  const refresh = useCallback(async () => {
    setUser(await authApi.me());
  }, []);

  const logout = useCallback(async () => {
    await authApi.logout();
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({ user, loading, login, logout, refresh }),
    [user, loading, login, logout, refresh],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// Le hook d'accès au contexte vit délibérément à côté de son Provider (paire Context/hook
// usuelle en React) ; le séparer dans un fichier dédié n'apporterait rien ici, seulement
// une indirection.
// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth doit être utilisé sous AuthProvider.');
  }
  return context;
}
