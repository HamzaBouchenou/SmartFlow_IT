import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import * as authApi from '../api/auth';
import { onSessionExpired } from '../api/client';
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
  /** §6.1/ADR-22 - vrai quand la session précédente a expiré par inactivité, plutôt que
   * d'avoir été fermée volontairement ou de n'avoir jamais existé. Seul l'écran de connexion
   * s'en sert, pour dire pourquoi l'utilisateur s'y retrouve. */
  sessionExpired: boolean;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<LoginResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [sessionExpired, setSessionExpired] = useState(false);

  /**
   * §6.1 "Expiration de session"/ADR-22 - le serveur répond SESSION_EXPIRED au premier appel
   * qui suit le délai d'inactivité, quel que soit l'écran affiché à ce moment-là. Vider
   * l'utilisateur suffit à ce que ProtectedRoute ramène vers /login : c'est déjà le chemin
   * qu'emprunte toute perte de session, il n'y en a pas un second à écrire ici.
   *
   * L'abonnement vit dans le fournisseur, jamais dans un écran : sans cela, une session
   * expirée découverte par un écran quelconque n'y produirait qu'un ErrorBanner de plus,
   * alors que c'est l'application entière qui vient de perdre sa session.
   */
  useEffect(() => onSessionExpired(() => {
    setUser(null);
    setSessionExpired(true);
  }), []);

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
    setSessionExpired(false);
    setUser(logged);
  }, []);

  const refresh = useCallback(async () => {
    setUser(await authApi.me());
  }, []);

  const logout = useCallback(async () => {
    await authApi.logout();
    // Une déconnexion volontaire n'est pas une expiration : l'écran de connexion ne doit pas
    // annoncer un délai dépassé à qui vient de cliquer sur "Se déconnecter".
    setSessionExpired(false);
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({ user, loading, login, logout, refresh, sessionExpired }),
    [user, loading, login, logout, refresh, sessionExpired],
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
