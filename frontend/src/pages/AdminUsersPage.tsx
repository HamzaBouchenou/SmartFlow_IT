import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import * as adminApi from '../api/admin';
import type {
  CreateRoleAssignmentRequest,
  DepartmentResponse,
  ScopeType,
  TeamResponse,
  UserResponse,
  UserRole,
  UserRoleAssignmentResponse,
} from '../api/types';
import { ErrorBanner } from '../components/ErrorBanner';

const ROLES: UserRole[] = ['REQUESTER', 'MANAGER', 'AGENT', 'SERVICE_MANAGER', 'FUNCTIONAL_ADMIN', 'TECHNICAL_ADMIN', 'AUDITOR'];
const SCOPE_TYPES: ScopeType[] = ['OWN', 'TEAM', 'DEPARTMENT', 'DIRECTION', 'GLOBAL'];

/** §6.1/§6.10 - "Gestion des utilisateurs" (comptes, RG-02 - jamais de suppression
 * physique) et §5.1 "les droits seront attribués par rôle" (affectations). Un compte est
 * toujours provisionné ici par un administrateur : aucune auto-inscription (voir
 * CLAUDE.md). */
export function AdminUsersPage() {
  const [users, setUsers] = useState<UserResponse[]>([]);
  const [departments, setDepartments] = useState<DepartmentResponse[]>([]);
  const [teams, setTeams] = useState<TeamResponse[]>([]);
  const [selectedUserId, setSelectedUserId] = useState<number | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      try {
        const [userList, departmentList, teamList] = await Promise.all([
          adminApi.listUsers(),
          adminApi.listDepartments(),
          adminApi.listTeams(),
        ]);
        if (!cancelled) {
          setUsers(userList);
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

  function upsertUser(updated: UserResponse) {
    setUsers((current) => {
      const exists = current.some((u) => u.id === updated.id);
      return exists ? current.map((u) => (u.id === updated.id ? updated : u)) : [...current, updated];
    });
  }

  const selectedUser = users.find((u) => u.id === selectedUserId) ?? null;

  return (
    <section>
      <ErrorBanner error={error} />
      {loading && <p className="page-loading">Chargement…</p>}

      {!loading && !error && (
        <>
          <div className="table-scroll">
            <table className="task-table">
              <thead>
                <tr>
                  <th>Nom</th>
                  <th>E-mail</th>
                  <th>Département</th>
                  <th>Statut</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {users.map((u) => (
                  <tr key={u.id}>
                    <td>
                      {u.firstName} {u.lastName}
                    </td>
                    <td>{u.email}</td>
                    <td>{u.departmentName ?? '—'}</td>
                    <td>
                      {u.active ? 'Actif' : 'Désactivé'}
                      {u.locked && ' · Verrouillé'}
                    </td>
                    <td>
                      <button type="button" onClick={() => setSelectedUserId(u.id)}>
                        Gérer
                      </button>
                    </td>
                  </tr>
                ))}
                {users.length === 0 && (
                  <tr>
                    <td colSpan={5}>Aucun utilisateur pour l'instant.</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <h2>Créer un compte</h2>
          <CreateUserForm departments={departments} onCreated={upsertUser} />

          {selectedUser && (
            <>
              <h2>
                Gérer {selectedUser.firstName} {selectedUser.lastName}
              </h2>
              <UserDetailPanel
                user={selectedUser}
                users={users}
                departments={departments}
                teams={teams}
                onUpdated={upsertUser}
                onClose={() => setSelectedUserId(null)}
              />
            </>
          )}
        </>
      )}
    </section>
  );
}

function CreateUserForm({ departments, onCreated }: { departments: DepartmentResponse[]; onCreated: (created: UserResponse) => void }) {
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const created = await adminApi.createUser({
        firstName,
        lastName,
        email,
        password,
        departmentId: departmentId ? Number(departmentId) : null,
      });
      onCreated(created);
      setFirstName('');
      setLastName('');
      setEmail('');
      setPassword('');
      setDepartmentId('');
    } catch (submitError) {
      setError(submitError);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="parameter-form" onSubmit={handleSubmit}>
      <input placeholder="Prénom" value={firstName} onChange={(event) => setFirstName(event.target.value)} />
      <input placeholder="Nom" value={lastName} onChange={(event) => setLastName(event.target.value)} />
      <input placeholder="E-mail" type="email" value={email} onChange={(event) => setEmail(event.target.value)} />
      <input
        placeholder="Mot de passe initial"
        type="password"
        value={password}
        onChange={(event) => setPassword(event.target.value)}
      />
      <select value={departmentId} onChange={(event) => setDepartmentId(event.target.value)}>
        <option value="">— Département —</option>
        {departments.map((department) => (
          <option key={department.id} value={department.id}>
            {department.name}
          </option>
        ))}
      </select>
      <button type="submit" disabled={submitting || !firstName.trim() || !lastName.trim() || !email.trim() || password.length < 8}>
        Créer
      </button>
      <ErrorBanner error={error} />
    </form>
  );
}

function UserDetailPanel({
  user,
  users,
  departments,
  teams,
  onUpdated,
  onClose,
}: {
  user: UserResponse;
  users: UserResponse[];
  departments: DepartmentResponse[];
  teams: TeamResponse[];
  onUpdated: (updated: UserResponse) => void;
  onClose: () => void;
}) {
  const [firstName, setFirstName] = useState(user.firstName);
  const [lastName, setLastName] = useState(user.lastName);
  const [email, setEmail] = useState(user.email);
  const [departmentId, setDepartmentId] = useState(user.departmentId ? String(user.departmentId) : '');
  const [managerId, setManagerId] = useState(user.managerId ? String(user.managerId) : '');
  const [newPassword, setNewPassword] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [saving, setSaving] = useState(false);

  async function handleUpdate(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const updated = await adminApi.updateUser(user.id, {
        firstName,
        lastName,
        email,
        departmentId: departmentId ? Number(departmentId) : null,
        managerId: managerId ? Number(managerId) : null,
      });
      onUpdated(updated);
    } catch (updateError) {
      setError(updateError);
    } finally {
      setSaving(false);
    }
  }

  async function toggleActive() {
    setError(null);
    try {
      const updated = user.active ? await adminApi.deactivateUser(user.id) : await adminApi.activateUser(user.id);
      onUpdated(updated);
    } catch (toggleError) {
      setError(toggleError);
    }
  }

  async function handleResetPassword(event: FormEvent) {
    event.preventDefault();
    setError(null);
    try {
      const updated = await adminApi.resetPassword(user.id, { newPassword });
      onUpdated(updated);
      setNewPassword('');
    } catch (resetError) {
      setError(resetError);
    }
  }

  async function handleUnlock() {
    setError(null);
    try {
      onUpdated(await adminApi.unlockUser(user.id));
    } catch (unlockError) {
      setError(unlockError);
    }
  }

  return (
    <div className="parameter-item">
      <ErrorBanner error={error} />

      <form className="parameter-form" onSubmit={handleUpdate}>
        <input value={firstName} onChange={(event) => setFirstName(event.target.value)} />
        <input value={lastName} onChange={(event) => setLastName(event.target.value)} />
        <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} />
        <select value={departmentId} onChange={(event) => setDepartmentId(event.target.value)}>
          <option value="">— Département —</option>
          {departments.map((department) => (
            <option key={department.id} value={department.id}>
              {department.name}
            </option>
          ))}
        </select>
        <select value={managerId} onChange={(event) => setManagerId(event.target.value)}>
          <option value="">— Responsable hiérarchique —</option>
          {users
            .filter((candidate) => candidate.id !== user.id)
            .map((candidate) => (
              <option key={candidate.id} value={candidate.id}>
                {candidate.firstName} {candidate.lastName}
              </option>
            ))}
        </select>
        <button type="submit" disabled={saving}>
          Enregistrer
        </button>
      </form>

      <div className="parameter-form">
        <button type="button" onClick={() => void toggleActive()}>
          {user.active ? 'Désactiver le compte' : 'Activer le compte'}
        </button>
        {user.locked && (
          <button type="button" onClick={() => void handleUnlock()}>
            Déverrouiller (ADR-13)
          </button>
        )}
        <button type="button" onClick={onClose}>
          Fermer
        </button>
      </div>

      <form className="parameter-form" onSubmit={handleResetPassword}>
        <input
          type="password"
          placeholder="Nouveau mot de passe"
          value={newPassword}
          onChange={(event) => setNewPassword(event.target.value)}
        />
        <button type="submit" disabled={newPassword.length < 8}>
          Réinitialiser le mot de passe
        </button>
      </form>

      <RoleAssignmentPanel userId={user.id} teams={teams} departments={departments} />
    </div>
  );
}

function RoleAssignmentPanel({
  userId,
  teams,
  departments,
}: {
  userId: number;
  teams: TeamResponse[];
  departments: DepartmentResponse[];
}) {
  const [assignments, setAssignments] = useState<UserRoleAssignmentResponse[]>([]);
  const [role, setRole] = useState<UserRole>('AGENT');
  const [scopeType, setScopeType] = useState<ScopeType>('TEAM');
  const [scopeId, setScopeId] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      try {
        const result = await adminApi.listRoleAssignments(userId);
        if (!cancelled) {
          setAssignments(result);
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
  }, [userId]);

  async function handleGrant(event: FormEvent) {
    event.preventDefault();
    setError(null);
    try {
      const request: CreateRoleAssignmentRequest = {
        role,
        scopeType,
        scopeId: scopeType === 'TEAM' || scopeType === 'DEPARTMENT' || scopeType === 'DIRECTION' ? Number(scopeId) : null,
      };
      const created = await adminApi.grantRole(userId, request);
      setAssignments((current) => [...current, created]);
      setScopeId('');
    } catch (grantError) {
      setError(grantError);
    }
  }

  async function handleRevoke(assignmentId: number) {
    setError(null);
    try {
      await adminApi.revokeRole(userId, assignmentId);
      setAssignments((current) => current.filter((assignment) => assignment.id !== assignmentId));
    } catch (revokeError) {
      setError(revokeError);
    }
  }

  const needsScopeId = scopeType === 'TEAM' || scopeType === 'DEPARTMENT' || scopeType === 'DIRECTION';

  return (
    <div>
      <h3>Rôles</h3>
      <ErrorBanner error={error} />
      {loading && <p className="page-loading">Chargement…</p>}

      {!loading && (
        <>
          <ul className="parameter-list">
            {assignments.map((assignment) => (
              <li key={assignment.id} className="parameter-item">
                <span>
                  {assignment.role} · {assignment.scopeType}
                  {assignment.scopeName ? ` (${assignment.scopeName})` : ''}
                </span>{' '}
                <button type="button" onClick={() => void handleRevoke(assignment.id)}>
                  Révoquer
                </button>
              </li>
            ))}
            {assignments.length === 0 && <li className="timeline-empty">Aucun rôle affecté pour l'instant.</li>}
          </ul>

          <form className="parameter-form" onSubmit={handleGrant}>
            <select value={role} onChange={(event) => setRole(event.target.value as UserRole)}>
              {ROLES.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
            <select
              value={scopeType}
              onChange={(event) => {
                setScopeType(event.target.value as ScopeType);
                setScopeId('');
              }}
            >
              {SCOPE_TYPES.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
            {scopeType === 'TEAM' && (
              <select value={scopeId} onChange={(event) => setScopeId(event.target.value)}>
                <option value="">— Équipe —</option>
                {teams.map((team) => (
                  <option key={team.id} value={team.id}>
                    {team.name}
                  </option>
                ))}
              </select>
            )}
            {(scopeType === 'DEPARTMENT' || scopeType === 'DIRECTION') && (
              <select value={scopeId} onChange={(event) => setScopeId(event.target.value)}>
                <option value="">— Direction/service —</option>
                {departments.map((department) => (
                  <option key={department.id} value={department.id}>
                    {department.name}
                  </option>
                ))}
              </select>
            )}
            <button type="submit" disabled={needsScopeId && !scopeId}>
              Accorder
            </button>
          </form>
        </>
      )}
    </div>
  );
}
