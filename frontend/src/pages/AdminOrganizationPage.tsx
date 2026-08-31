import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import * as adminApi from '../api/admin';
import type { DepartmentResponse, TeamResponse } from '../api/types';
import { ErrorBanner } from '../components/ErrorBanner';

/** §4.1/§6.10 - "Organisation : directions, services, équipes et responsables", avec
 * activation/désactivation logique (RG-02/RG-12 - jamais de suppression physique).
 * L'affectation d'un responsable (leadId) n'a pas encore de sélecteur ici : elle suppose un
 * écran d'administration des utilisateurs qui n'existe pas encore (voir CLAUDE.md, "pas
 * encore fait") - un id de responsable resterait un champ numérique brut, une régression
 * d'ergonomie plutôt qu'un vrai raccourci, donc volontairement omis pour l'instant. */
export function AdminOrganizationPage() {
  const [departments, setDepartments] = useState<DepartmentResponse[]>([]);
  const [teams, setTeams] = useState<TeamResponse[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      try {
        const [departmentList, teamList] = await Promise.all([adminApi.listDepartments(), adminApi.listTeams()]);
        if (!cancelled) {
          setDepartments(departmentList);
          setTeams(teamList);
          setError(null);
        }
      } catch (loadError) {
        if (!cancelled) {
          setError(loadError);
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
  }, []);

  function upsertDepartment(updated: DepartmentResponse) {
    setDepartments((current) => {
      const exists = current.some((department) => department.id === updated.id);
      return exists ? current.map((department) => (department.id === updated.id ? updated : department)) : [...current, updated];
    });
  }

  function upsertTeam(updated: TeamResponse) {
    setTeams((current) => {
      const exists = current.some((team) => team.id === updated.id);
      return exists ? current.map((team) => (team.id === updated.id ? updated : team)) : [...current, updated];
    });
  }

  return (
    <section>
      <h1>Organisation</h1>
      <ErrorBanner error={error} />
      {loading && <p className="page-loading">Chargement…</p>}

      {!loading && !error && (
        <>
          <h2>Directions et services</h2>
          <DepartmentTable departments={departments} onChanged={upsertDepartment} />
          <CreateDepartmentForm departments={departments} onCreated={upsertDepartment} />

          <h2>Équipes</h2>
          <TeamTable teams={teams} departments={departments} onChanged={upsertTeam} />
          <CreateTeamForm departments={departments} onCreated={upsertTeam} />
        </>
      )}
    </section>
  );
}

function DepartmentTable({
  departments,
  onChanged,
}: {
  departments: DepartmentResponse[];
  onChanged: (updated: DepartmentResponse) => void;
}) {
  const [error, setError] = useState<unknown>(null);

  async function toggle(department: DepartmentResponse) {
    setError(null);
    try {
      const updated = department.active
        ? await adminApi.deactivateDepartment(department.id)
        : await adminApi.activateDepartment(department.id);
      onChanged(updated);
    } catch (toggleError) {
      setError(toggleError);
    }
  }

  return (
    <>
      <ErrorBanner error={error} />
      <table className="task-table">
        <thead>
          <tr>
            <th>Nom</th>
            <th>Rattachée à</th>
            <th>Statut</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {departments.map((department) => (
            <tr key={department.id}>
              <td>{department.name}</td>
              <td>{department.parentName ?? '— (direction)'}</td>
              <td>{department.active ? 'Active' : 'Désactivée'}</td>
              <td>
                <button type="button" onClick={() => void toggle(department)}>
                  {department.active ? 'Désactiver' : 'Activer'}
                </button>
              </td>
            </tr>
          ))}
          {departments.length === 0 && (
            <tr>
              <td colSpan={4}>Aucune direction/service pour l'instant.</td>
            </tr>
          )}
        </tbody>
      </table>
    </>
  );
}

function CreateDepartmentForm({
  departments,
  onCreated,
}: {
  departments: DepartmentResponse[];
  onCreated: (created: DepartmentResponse) => void;
}) {
  const [name, setName] = useState('');
  const [parentId, setParentId] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const created = await adminApi.createDepartment({ name, parentId: parentId ? Number(parentId) : null });
      onCreated(created);
      setName('');
      setParentId('');
    } catch (submitError) {
      setError(submitError);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="parameter-form" onSubmit={handleSubmit}>
      <input placeholder="Nom de la direction/service" value={name} onChange={(event) => setName(event.target.value)} />
      <select value={parentId} onChange={(event) => setParentId(event.target.value)}>
        <option value="">— Direction (aucun rattachement) —</option>
        {departments.map((department) => (
          <option key={department.id} value={department.id}>
            {department.name}
          </option>
        ))}
      </select>
      <button type="submit" disabled={submitting || !name.trim()}>
        Ajouter
      </button>
      <ErrorBanner error={error} />
    </form>
  );
}

function TeamTable({
  teams,
  departments,
  onChanged,
}: {
  teams: TeamResponse[];
  departments: DepartmentResponse[];
  onChanged: (updated: TeamResponse) => void;
}) {
  const [error, setError] = useState<unknown>(null);

  async function toggle(team: TeamResponse) {
    setError(null);
    try {
      const updated = team.active ? await adminApi.deactivateTeam(team.id) : await adminApi.activateTeam(team.id);
      onChanged(updated);
    } catch (toggleError) {
      setError(toggleError);
    }
  }

  return (
    <>
      <ErrorBanner error={error} />
      <table className="task-table">
        <thead>
          <tr>
            <th>Nom</th>
            <th>Département</th>
            <th>Statut</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {teams.map((team) => (
            <tr key={team.id}>
              <td>{team.name}</td>
              <td>{team.departmentName}</td>
              <td>{team.active ? 'Active' : 'Désactivée'}</td>
              <td>
                <button type="button" onClick={() => void toggle(team)}>
                  {team.active ? 'Désactiver' : 'Activer'}
                </button>
              </td>
            </tr>
          ))}
          {teams.length === 0 && (
            <tr>
              <td colSpan={4}>Aucune équipe pour l'instant.</td>
            </tr>
          )}
        </tbody>
      </table>
      {departments.length === 0 && <p className="page-loading">Créez d'abord une direction/service ci-dessus.</p>}
    </>
  );
}

function CreateTeamForm({
  departments,
  onCreated,
}: {
  departments: DepartmentResponse[];
  onCreated: (created: TeamResponse) => void;
}) {
  const [name, setName] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!departmentId) {
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const created = await adminApi.createTeam({ name, departmentId: Number(departmentId) });
      onCreated(created);
      setName('');
    } catch (submitError) {
      setError(submitError);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="parameter-form" onSubmit={handleSubmit}>
      <input placeholder="Nom de l'équipe" value={name} onChange={(event) => setName(event.target.value)} />
      <select value={departmentId} onChange={(event) => setDepartmentId(event.target.value)}>
        <option value="">— Département —</option>
        {departments.map((department) => (
          <option key={department.id} value={department.id}>
            {department.name}
          </option>
        ))}
      </select>
      <button type="submit" disabled={submitting || !name.trim() || !departmentId}>
        Ajouter
      </button>
      <ErrorBanner error={error} />
    </form>
  );
}
