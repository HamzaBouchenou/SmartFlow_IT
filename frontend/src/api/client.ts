import type { ErrorResponse } from './types';

// ADR-01 (docs/DECISIONS.md) - authentification par session portée par un cookie
// HttpOnly : ce client n'a donc jamais de jeton à transporter lui-même, seulement le
// cookie de session (envoyé automatiquement par le navigateur via `credentials: 'include'`)
// et le jeton CSRF, lui volontairement lisible en JavaScript
// (`CookieCsrfTokenRepository.withHttpOnlyFalse()` côté back-end) pour être recopié dans
// l'en-tête X-XSRF-TOKEN de chaque requête qui modifie un état.

const API_BASE = '/api/v1';
const CSRF_COOKIE_NAME = 'XSRF-TOKEN';
const CSRF_HEADER_NAME = 'X-XSRF-TOKEN';
const BACKGROUND_HEADER_NAME = 'X-SmartFlow-Background';
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

/** Erreur levée pour toute réponse HTTP non 2xx, portant le format d'erreur commun
 * (CLAUDE.md - `{ code, message, traceId, fieldErrors[] }`) tel que reçu du serveur. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly traceId: string | undefined;
  readonly fieldErrors: ErrorResponse['fieldErrors'];

  constructor(status: number, body: Partial<ErrorResponse> | undefined) {
    super(body?.message ?? `Erreur HTTP ${status}`);
    this.status = status;
    this.code = body?.code ?? 'UNKNOWN_ERROR';
    this.traceId = body?.traceId;
    this.fieldErrors = body?.fieldErrors ?? [];
  }
}

/** ADR-22 - le code que le serveur renvoie quand SessionActivityFilter vient d'invalider la
 * session pour inactivité, à distinguer d'`UNAUTHENTICATED` ("vous n'avez jamais été
 * connecté"). */
const SESSION_EXPIRED_CODE = 'SESSION_EXPIRED';

type SessionExpiredListener = () => void;
const sessionExpiredListeners = new Set<SessionExpiredListener>();

/**
 * ADR-22 - s'abonne à l'expiration de session, quelle que soit la requête qui l'a
 * découverte.
 *
 * L'expiration n'appartient à aucun écran : elle se manifeste sur le premier appel qui suit
 * le délai, et cet appel-là peut être n'importe lequel. Chaque écran ne peut donc pas la
 * traiter pour son compte - il rendrait un ErrorBanner de plus alors que l'application
 * entière vient de perdre sa session. Ce client, qui voit passer tous les appels, la signale
 * une fois ; AuthProvider s'y abonne et vide l'état d'authentification, ce qui suffit à
 * ramener ProtectedRoute vers l'écran de connexion.
 *
 * Le module d'API ne connaît volontairement ni React ni le routeur : il émet un événement,
 * il ne navigue pas.
 */
export function onSessionExpired(listener: SessionExpiredListener): () => void {
  sessionExpiredListeners.add(listener);
  return () => sessionExpiredListeners.delete(listener);
}

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : null;
}

/** Amorce le cookie CSRF si absent (AuthController.csrf) - nécessaire avant toute
 * première requête mutante (POST/PUT/DELETE) de la session du navigateur. */
async function ensureCsrfToken(): Promise<string | null> {
  let token = readCookie(CSRF_COOKIE_NAME);
  if (token) {
    return token;
  }
  await fetch(`${API_BASE}/auth/csrf`, { credentials: 'include' });
  token = readCookie(CSRF_COOKIE_NAME);
  return token;
}

export interface ApiFetchOptions {
  method?: string;
  body?: unknown;
  /** ADR-22 - requête périodique déclenchée par un minuteur, pas par l'utilisateur : elle
   * porte alors l'en-tête que SessionActivityFilter lit pour ne PAS repousser l'expiration
   * de session. Sans cela, un onglet simplement laissé ouvert (le compteur de notifications
   * s'interroge toutes les 30 s depuis l'ossature) maintiendrait la session indéfiniment
   * vivante et la durée administrable du §6.10 ne serait jamais atteinte. */
  background?: boolean;
  // `object` plutôt qu'un Record<string, ...> : les appelants passent souvent une
  // interface nommée (TaskQueueFilterParams, par exemple) qui ne déclare pas de signature
  // d'index - un Record l'exigerait et rejetterait l'appel malgré des valeurs par ailleurs
  // toutes compatibles avec des paramètres de requête.
  searchParams?: object;
}

function buildUrl(path: string, searchParams?: ApiFetchOptions['searchParams']): string {
  const url = new URL(`${API_BASE}${path}`, window.location.origin);
  if (searchParams) {
    for (const [key, value] of Object.entries(searchParams)) {
      if (value !== undefined && value !== null && value !== '') {
        url.searchParams.set(key, String(value));
      }
    }
  }
  return url.pathname + url.search;
}

/** Appelle l'API back-end sous /api/v1 et renvoie le corps JSON typé, ou lève ApiError.
 * Gère seule la mécanique transverse (CSRF, JSON, 204) : chaque module api/*.ts n'a plus
 * qu'à décrire la ressource qu'il appelle. */
export async function apiFetch<T>(path: string, options: ApiFetchOptions = {}): Promise<T> {
  const method = options.method ?? 'GET';
  const headers: Record<string, string> = {};
  let body: string | undefined;

  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(options.body);
  }
  if (options.background) {
    headers[BACKGROUND_HEADER_NAME] = 'true';
  }
  if (!SAFE_METHODS.has(method)) {
    const token = await ensureCsrfToken();
    if (token) {
      headers[CSRF_HEADER_NAME] = token;
    }
  }

  const response = await fetch(buildUrl(path, options.searchParams), {
    method,
    headers,
    body,
    credentials: 'include',
  });

  return handleResponse<T>(response);
}

/** RG-09 - dépose une pièce jointe (multipart/form-data) : apiFetch ne convient pas, son
 * body est toujours du JSON. Le jeton CSRF reste requis (POST, méthode non "safe"). */
export async function apiUpload<T>(path: string, formData: FormData): Promise<T> {
  const headers: Record<string, string> = {};
  const token = await ensureCsrfToken();
  if (token) {
    headers[CSRF_HEADER_NAME] = token;
  }
  // Pas de Content-Type ici : le navigateur doit poser lui-même le boundary multipart,
  // un en-tête forcé à la main le casserait.
  const response = await fetch(buildUrl(path), {
    method: 'POST',
    headers,
    body: formData,
    credentials: 'include',
  });
  return handleResponse<T>(response);
}

async function handleResponse<T>(response: Response): Promise<T> {
  if (response.status === 204) {
    return undefined as T;
  }

  const isJson = response.headers.get('content-type')?.includes('application/json');
  const payload = isJson ? await response.json() : undefined;

  if (!response.ok) {
    const error = new ApiError(response.status, payload as Partial<ErrorResponse> | undefined);
    if (error.status === 401 && error.code === SESSION_EXPIRED_CODE) {
      sessionExpiredListeners.forEach((listener) => listener());
    }
    // L'erreur est levée dans tous les cas : l'appelant doit toujours voir son appel échouer,
    // l'abonnement ci-dessus ne fait que prévenir l'application en plus de lui.
    throw error;
  }
  return payload as T;
}
