import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import * as catalogApi from '../api/catalog';
import type { RequestTypeResponse } from '../api/types';
import { CategoryHeader } from '../components/CategoryHeader';
import { EmptyState } from '../components/EmptyState';
import { ErrorBanner } from '../components/ErrorBanner';

/** §6.2 - "détail d'un service et démarrage d'une demande" : les types de demande actifs
 * d'un service, avec les informations utiles avant la saisie (délai cible, documents,
 * contact) portées par chaque RequestTypeResponse. Anatomie de fiche de la maquette 03.
 *
 * Aucune catégorie n'est affichée ici : elle appartient au service, et `RequestTypeResponse`
 * ne la porte pas - la récupérer aurait demandé un appel supplémentaire pour une information
 * que le fil d'Ariane donne déjà. */
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
      <nav className="breadcrumb">
        <Link to="/catalogue">← Retour au catalogue</Link>
      </nav>

      <ErrorBanner error={error} />
      {loading && <p className="page-loading">Chargement…</p>}

      {!loading && requestTypes.length === 0 ? (
        <EmptyState message="Aucun type de demande actif pour ce service." />
      ) : (
        <div className="card-grid">
          {requestTypes.map((requestType) => (
            <article key={requestType.id} className="card service-card">
              <CategoryHeader category={null} />
              <h2>{requestType.name}</h2>
              <p className="card-description">{requestType.description}</p>

              <dl className="request-type-meta card-meta">
                <dt>Délai cible</dt>
                <dd>{requestType.targetDelayDescription || '—'}</dd>
                <dt>Pièces</dt>
                <dd>{requestType.requiredDocuments || 'Aucune'}</dd>
                <dt>Contact</dt>
                <dd>{requestType.contactInfo || '—'}</dd>
              </dl>

              <Link to={`/demandes/nouvelle/${requestType.id}`} className="button-link">
                Démarrer la demande
              </Link>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}
