import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import * as catalogApi from '../api/catalog';
import type { RequestTypeResponse } from '../api/types';
import { ErrorBanner } from '../components/ErrorBanner';

/** §6.2 - "détail d'un service et démarrage d'une demande" : les types de demande actifs
 * d'un service, avec les informations utiles avant la saisie (délai cible, documents,
 * contact) portées par chaque RequestTypeResponse. */
export function ServiceRequestTypesPage() {
  const { serviceId } = useParams<{ serviceId: string }>();
  const [requestTypes, setRequestTypes] = useState<RequestTypeResponse[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!serviceId) {
      return;
    }
    let cancelled = false;
    catalogApi
      .listRequestTypes(Number(serviceId))
      .then((result) => {
        if (!cancelled) {
          setRequestTypes(result);
        }
      })
      .catch((listError) => {
        if (!cancelled) {
          setError(listError);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [serviceId]);

  return (
    <section>
      <p>
        <Link to="/catalogue">← Retour au catalogue</Link>
      </p>
      <h1>Types de demande disponibles</h1>

      <ErrorBanner error={error} />
      {loading && <p className="page-loading">Chargement…</p>}

      <div className="card-grid">
        {requestTypes.map((requestType) => (
          <div key={requestType.id} className="card">
            <h2>{requestType.name}</h2>
            <p>{requestType.description}</p>
            <dl className="request-type-meta">
              <dt>Délai cible</dt>
              <dd>{requestType.targetDelayDescription}</dd>
              <dt>Pièces nécessaires</dt>
              <dd>{requestType.requiredDocuments}</dd>
              <dt>Contact</dt>
              <dd>{requestType.contactInfo}</dd>
            </dl>
            <Link to={`/demandes/nouvelle/${requestType.id}`} className="button-link">
              Démarrer une demande
            </Link>
          </div>
        ))}
        {!loading && requestTypes.length === 0 && <p>Aucun type de demande actif pour ce service.</p>}
      </div>
    </section>
  );
}
