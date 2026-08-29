import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as catalogApi from '../api/catalog';
import type { ServiceCatalogResponse } from '../api/types';
import { ErrorBanner } from '../components/ErrorBanner';

/** §6.2 - "Présentation des services sous forme de catégories et de fiches
 * compréhensibles" + "Recherche par mot-clé, catégorie". */
export function CataloguePage() {
  const [services, setServices] = useState<ServiceCatalogResponse[]>([]);
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
      <h1>Catalogue des services</h1>

      <div className="catalogue-filters">
        <input
          type="search"
          placeholder="Rechercher un service…"
          value={q}
          onChange={(event) => setQ(event.target.value)}
        />
        <input
          type="text"
          placeholder="Catégorie"
          value={category}
          onChange={(event) => setCategory(event.target.value)}
        />
      </div>

      <ErrorBanner error={error} />
      {loading && <p className="page-loading">Chargement…</p>}

      <div className="card-grid">
        {services.map((service) => (
          <Link key={service.id} to={`/catalogue/${service.id}`} className="card">
            <h2>{service.name}</h2>
            <p className="card-category">{service.category}</p>
            <p>{service.description}</p>
          </Link>
        ))}
        {!loading && services.length === 0 && <p>Aucun service ne correspond à cette recherche.</p>}
      </div>
    </section>
  );
}
