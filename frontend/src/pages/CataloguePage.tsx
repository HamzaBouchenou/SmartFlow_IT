import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as catalogApi from '../api/catalog';
import type { ServiceCatalogResponse } from '../api/types';
import { CategoryHeader } from '../components/CategoryHeader';
import { EmptyState } from '../components/EmptyState';
import { ErrorBanner } from '../components/ErrorBanner';

/** §6.2 - "Présentation des services sous forme de catégories et de fiches
 * compréhensibles" + "Recherche par mot-clé, catégorie" (maquette 03).
 *
 * Le catalogue reste à deux niveaux, comme §6.2 le décrit : un service ici, puis ses types
 * de demande sur `/catalogue/:serviceId`. La maquette montre des fiches portant déjà le
 * délai cible et les pièces attendues - ces informations appartiennent au *type de demande*
 * (`RequestTypeResponse`), pas au service, et les afficher ici aurait demandé un appel par
 * service. L'anatomie de fiche de la maquette est donc appliquée aux deux niveaux, avec les
 * données que chacun porte réellement. */
export function CataloguePage() {
  const [services, setServices] = useState<ServiceCatalogResponse[]>([]);
  const [allCategories, setAllCategories] = useState<string[]>([]);
  const [q, setQ] = useState('');
  const [category, setCategory] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      try {
        const result = await catalogApi.searchServices({ q: q || undefined, category: category || undefined });
        if (!cancelled) {
          setServices(result);
          setError(null);
          // Les puces de catégorie sont construites depuis la liste complète, pas depuis le
          // résultat filtré : sinon filtrer sur "Informatique" ferait disparaître toutes les
          // autres puces et on ne pourrait plus changer de catégorie sans revenir à "Tous".
          if (!q && !category) {
            setAllCategories([...new Set(result.map((service) => service.category).filter(Boolean))].sort());
          }
        }
      } catch (searchError) {
        if (!cancelled) {
          setError(searchError);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, [q, category]);

  return (
    <section>
      <div className="catalogue-filters">
        <input
          type="search"
          className="filter-search"
          placeholder="Rechercher un service…"
          aria-label="Rechercher un service"
          value={q}
          onChange={(event) => setQ(event.target.value)}
        />
      </div>

      {allCategories.length > 0 && (
        <div className="chip-row" role="group" aria-label="Filtrer par catégorie">
          <button
            type="button"
            className={`chip${category === '' ? ' active' : ''}`}
            aria-pressed={category === ''}
            onClick={() => setCategory('')}
          >
            Tous
          </button>
          {allCategories.map((name) => (
            <button
              key={name}
              type="button"
              className={`chip${category === name ? ' active' : ''}`}
              aria-pressed={category === name}
              onClick={() => setCategory(name)}
            >
              {name}
            </button>
          ))}
        </div>
      )}

      <ErrorBanner error={error} />
      {loading && <p className="page-loading">Chargement…</p>}

      {!loading && services.length === 0 ? (
        <EmptyState message="Aucun service ne correspond à cette recherche." />
      ) : (
        <div className="card-grid">
          {services.map((service) => (
            <Link key={service.id} to={`/catalogue/${service.id}`} className="card service-card">
              <CategoryHeader category={service.category} />
              <h2>{service.name}</h2>
              <p className="card-description">{service.description}</p>
              <span className="card-cta">Voir les demandes disponibles →</span>
            </Link>
          ))}
        </div>
      )}
    </section>
  );
}
