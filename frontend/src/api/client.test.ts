import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, apiFetch, onSessionExpired } from './client';

/**
 * ADR-22 - l'expiration de session doit se voir de l'application entière, pas du seul écran
 * qui a eu la malchance d'appeler le serveur au mauvais moment.
 *
 * Ces tests portent sur le point de bascule : c'est ce client, qui voit passer tous les
 * appels, qui distingue « votre session a expiré » de « vous n'êtes pas connecté » et le
 * signale une fois. Sans cette distinction, le code SESSION_EXPIRED que le serveur produit
 * ne servait à rien - il était renvoyé et jamais lu.
 */
function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

describe('apiFetch - expiration de session (ADR-22)', () => {
  // Les abonnements vivent au niveau du module : sans ce nettoyage, un abonné d'un test
  // continuerait d'être appelé par les suivants.
  const unsubscribes: (() => void)[] = [];

  function subscribe() {
    const listener = vi.fn();
    unsubscribes.push(onSessionExpired(listener));
    return listener;
  }

  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    unsubscribes.splice(0).forEach((unsubscribe) => unsubscribe());
    vi.unstubAllGlobals();
  });

  it('prévient les abonnés quand le serveur répond 401 SESSION_EXPIRED', async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(401, { code: 'SESSION_EXPIRED', message: 'Votre session a expiré.' }),
    );
    const listener = subscribe();

    // L'appel échoue tout de même : l'abonnement prévient l'application en plus de
    // l'appelant, il ne le remplace pas.
    await expect(apiFetch('/requests')).rejects.toBeInstanceOf(ApiError);
    expect(listener).toHaveBeenCalledTimes(1);
  });

  it("ne prévient personne pour un 401 ordinaire : n'avoir jamais été connecté n'est pas une expiration", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(401, { code: 'UNAUTHENTICATED', message: 'Authentification requise.' }),
    );
    const listener = subscribe();

    await expect(apiFetch('/requests')).rejects.toBeInstanceOf(ApiError);
    expect(listener).not.toHaveBeenCalled();
  });

  it('ne prévient plus après désabonnement', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(401, { code: 'SESSION_EXPIRED', message: 'expirée' }));
    const listener = vi.fn();
    onSessionExpired(listener)();

    await expect(apiFetch('/requests')).rejects.toBeInstanceOf(ApiError);
    expect(listener).not.toHaveBeenCalled();
  });
});
