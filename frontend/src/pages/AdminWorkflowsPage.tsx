import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import * as adminApi from '../api/admin';
import * as catalogApi from '../api/catalog';
import type {
  RequestTypeResponse,
  ServiceCatalogResponse,
  StepAdminResponse,
  TeamResponse,
  UserRole,
  WorkflowAction,
  WorkflowDefinitionAdminResponse,
} from '../api/types';
import { ErrorBanner } from '../components/ErrorBanner';
import { actionLabel, formatDate } from '../lib/format';

const ROLES: UserRole[] = ['REQUESTER', 'MANAGER', 'AGENT', 'SERVICE_MANAGER', 'FUNCTIONAL_ADMIN', 'TECHNICAL_ADMIN', 'AUDITOR'];
const ACTIONS: WorkflowAction[] = ['VALIDATE', 'REJECT', 'RETURN', 'ASSIGN', 'REQUEST_INFO', 'CLOSE'];

/** §6.5/§10.1/§6.10 - administration versionnée des workflows (ADR-17, docs/DECISIONS.md ;
 * RG-03). Un DRAFT à la fois par type de demande ; publier archive l'ancien PUBLISHED
 * (RG-12) sans jamais toucher une demande déjà en cours sur lui (RG-03). Éditeur
 * structuré (tableaux d'étapes/transitions), jamais un canevas graphique (§4.2). */
export function AdminWorkflowsPage() {
  const [services, setServices] = useState<ServiceCatalogResponse[]>([]);
  const [serviceId, setServiceId] = useState<number | null>(null);
  const [requestTypes, setRequestTypes] = useState<RequestTypeResponse[]>([]);
  const [requestTypeId, setRequestTypeId] = useState<number | null>(null);
  const [teams, setTeams] = useState<TeamResponse[]>([]);
  const [versions, setVersions] = useState<WorkflowDefinitionAdminResponse[]>([]);
  const [selectedVersionId, setSelectedVersionId] = useState<number | null>(null);
  const [detail, setDetail] = useState<WorkflowDefinitionAdminResponse | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const [serviceList, teamList] = await Promise.all([catalogApi.searchServices(), adminApi.listTeams()]);
        if (!cancelled) {
          setServices(serviceList);
          setTeams(teamList);
          if (serviceList.length > 0) {
            setServiceId(serviceList[0].id);
          } else {
            setLoading(false);
          }
        }
      } catch (loadError) {
        if (!cancelled) {
          setError(loadError);
          setLoading(false);
        }
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (serviceId === null) {
      return;
    }
    let cancelled = false;
    async function load() {
      try {
        const result = await catalogApi.listRequestTypes(serviceId as number);
        if (!cancelled) {
          setRequestTypes(result);
          setRequestTypeId(result.length > 0 ? result[0].id : null);
          if (result.length === 0) {
            setLoading(false);
          }
        }
      } catch (loadError) {
        if (!cancelled) {
          setError(loadError);
          setLoading(false);
        }
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, [serviceId]);

  async function loadVersions(typeId: number) {
    setLoading(true);
    try {
      const result = await adminApi.listWorkflowVersions(typeId);
      setVersions(result);
      setError(null);
      setSelectedVersionId(null);
      setDetail(null);
    } catch (loadError) {
      setError(loadError);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (requestTypeId === null) {
      return;
    }
    let cancelled = false;
    async function load() {
      setLoading(true);
      try {
        const result = await adminApi.listWorkflowVersions(requestTypeId as number);
        if (!cancelled) {
          setVersions(result);
          setError(null);
          setSelectedVersionId(null);
          setDetail(null);
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
  }, [requestTypeId]);

  async function selectVersion(id: number) {
    setSelectedVersionId(id);
    setError(null);
    try {
      setDetail(await adminApi.getWorkflowVersion(id));
    } catch (loadError) {
      setError(loadError);
    }
  }

  async function handleCreateDraft() {
    if (requestTypeId === null) {
      return;
    }
    setError(null);
    try {
      const draft = await adminApi.createWorkflowDraft(requestTypeId);
      await loadVersions(requestTypeId);
      await selectVersion(draft.id);
    } catch (createError) {
      setError(createError);
    }
  }

  async function handlePublish(id: number) {
    setError(null);
    try {
      await adminApi.publishWorkflowVersion(id);
      if (requestTypeId !== null) {
        await loadVersions(requestTypeId);
      }
    } catch (publishError) {
      setError(publishError);
    }
  }

  async function handleDeleteDraft(id: number) {
    setError(null);
    try {
      await adminApi.deleteWorkflowDraft(id);
      if (requestTypeId !== null) {
        await loadVersions(requestTypeId);
      }
    } catch (deleteError) {
      setError(deleteError);
    }
  }

  return (
    <section>
      <h1>Workflows</h1>

      <div className="task-filters">
        <select value={serviceId ?? ''} onChange={(event) => setServiceId(event.target.value ? Number(event.target.value) : null)}>
          {services.map((service) => (
            <option key={service.id} value={service.id}>
              {service.name}
            </option>
          ))}
        </select>
        <select
          value={requestTypeId ?? ''}
          onChange={(event) => setRequestTypeId(event.target.value ? Number(event.target.value) : null)}
        >
          {requestTypes.map((requestType) => (
            <option key={requestType.id} value={requestType.id}>
              {requestType.name}
            </option>
          ))}
        </select>
      </div>

      <ErrorBanner error={error} />
      {loading && <p className="page-loading">Chargement…</p>}

      {!loading && !error && requestTypeId !== null && (
        <>
          <table className="task-table">
            <thead>
              <tr>
                <th>Version</th>
                <th>Statut</th>
                <th>Publiée le</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {versions.map((version) => (
                <tr key={version.id}>
                  <td>
                    <button type="button" className="link-button" onClick={() => void selectVersion(version.id)}>
                      v{version.version}
                    </button>
                  </td>
                  <td>{version.status}</td>
                  <td>{formatDate(version.publishedAt)}</td>
                  <td>
                    {version.status === 'DRAFT' && (
                      <>
                        <button type="button" onClick={() => void handlePublish(version.id)}>
                          Publier
                        </button>{' '}
                        <button type="button" onClick={() => void handleDeleteDraft(version.id)}>
                          Supprimer
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
              {versions.length === 0 && (
                <tr>
                  <td colSpan={4}>Aucune version pour ce type de demande.</td>
                </tr>
              )}
            </tbody>
          </table>

          {!versions.some((version) => version.status === 'DRAFT') && (
            <button type="button" onClick={() => void handleCreateDraft()}>
              Créer un brouillon
            </button>
          )}

          {detail && selectedVersionId === detail.id && (
            <WorkflowEditor workflow={detail} teams={teams} onChanged={setDetail} />
          )}
        </>
      )}
    </section>
  );
}

function WorkflowEditor({
  workflow,
  teams,
  onChanged,
}: {
  workflow: WorkflowDefinitionAdminResponse;
  teams: TeamResponse[];
  onChanged: (updated: WorkflowDefinitionAdminResponse) => void;
}) {
  const editable = workflow.status === 'DRAFT';
  const [error, setError] = useState<unknown>(null);

  async function refresh() {
    onChanged(await adminApi.getWorkflowVersion(workflow.id));
  }

  async function handleDeleteStep(stepId: number) {
    setError(null);
    try {
      await adminApi.deleteStepAdmin(stepId);
      await refresh();
    } catch (deleteError) {
      setError(deleteError);
    }
  }

  async function handleDeleteTransition(transitionId: number) {
    setError(null);
    try {
      await adminApi.deleteTransitionAdmin(transitionId);
      await refresh();
    } catch (deleteError) {
      setError(deleteError);
    }
  }

  const steps = workflow.steps ?? [];
  const transitions = workflow.transitions ?? [];

  return (
    <div className="parameter-item">
      <h2>
        Étapes (v{workflow.version} - {workflow.status})
      </h2>
      <ErrorBanner error={error} />

      <table className="task-table">
        <thead>
          <tr>
            <th>Code</th>
            <th>Nom</th>
            <th>Ordre</th>
            <th>Rôle responsable</th>
            <th>Équipe</th>
            <th>Suspend SLA</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {steps.map((step) => (
            <tr key={step.id}>
              <td>{step.code}</td>
              <td>{step.name}</td>
              <td>{step.displayOrder}</td>
              <td>{step.responsibleRole ?? '—'}</td>
              <td>{step.responsibleTeamName ?? '—'}</td>
              <td>{step.suspendSla ? 'Oui' : 'Non'}</td>
              <td>
                {editable && (
                  <button type="button" onClick={() => void handleDeleteStep(step.id)}>
                    Supprimer
                  </button>
                )}
              </td>
            </tr>
          ))}
          {steps.length === 0 && (
            <tr>
              <td colSpan={7}>Aucune étape pour l'instant.</td>
            </tr>
          )}
        </tbody>
      </table>
      {editable && <AddStepForm workflowDefinitionId={workflow.id} teams={teams} nextOrder={steps.length + 1} onAdded={refresh} />}

      <h2>Transitions</h2>
      <table className="task-table">
        <thead>
          <tr>
            <th>Depuis</th>
            <th>Action</th>
            <th>Vers</th>
            <th>Condition priorité</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {transitions.map((transition) => (
            <tr key={transition.id}>
              <td>{steps.find((step) => step.id === transition.fromStepId)?.name ?? transition.fromStepId}</td>
              <td>{actionLabel(transition.action)}</td>
              <td>{steps.find((step) => step.id === transition.toStepId)?.name ?? '— (terminale)'}</td>
              <td>{transition.conditionPriority ?? '—'}</td>
              <td>
                {editable && (
                  <button type="button" onClick={() => void handleDeleteTransition(transition.id)}>
                    Supprimer
                  </button>
                )}
              </td>
            </tr>
          ))}
          {transitions.length === 0 && (
            <tr>
              <td colSpan={5}>Aucune transition pour l'instant.</td>
            </tr>
          )}
        </tbody>
      </table>
      {editable && <AddTransitionForm steps={steps} onAdded={refresh} />}
    </div>
  );
}

function AddStepForm({
  workflowDefinitionId,
  teams,
  nextOrder,
  onAdded,
}: {
  workflowDefinitionId: number;
  teams: TeamResponse[];
  nextOrder: number;
  onAdded: () => void;
}) {
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [responsibleRole, setResponsibleRole] = useState('');
  const [responsibleTeamId, setResponsibleTeamId] = useState('');
  const [suspendSla, setSuspendSla] = useState(false);
  const [error, setError] = useState<unknown>(null);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    try {
      await adminApi.addStepAdmin(workflowDefinitionId, {
        code,
        name,
        displayOrder: nextOrder,
        responsibleRole: (responsibleRole || null) as UserRole | null,
        responsibleTeamId: responsibleTeamId ? Number(responsibleTeamId) : null,
        suspendSla,
      });
      setCode('');
      setName('');
      onAdded();
    } catch (submitError) {
      setError(submitError);
    }
  }

  return (
    <form className="parameter-form" onSubmit={handleSubmit}>
      <input placeholder="Code" value={code} onChange={(event) => setCode(event.target.value)} />
      <input placeholder="Nom" value={name} onChange={(event) => setName(event.target.value)} />
      <select value={responsibleRole} onChange={(event) => setResponsibleRole(event.target.value)}>
        <option value="">— Rôle responsable —</option>
        {ROLES.map((role) => (
          <option key={role} value={role}>
            {role}
          </option>
        ))}
      </select>
      <select value={responsibleTeamId} onChange={(event) => setResponsibleTeamId(event.target.value)}>
        <option value="">— Équipe —</option>
        {teams.map((team) => (
          <option key={team.id} value={team.id}>
            {team.name}
          </option>
        ))}
      </select>
      <label>
        <input type="checkbox" checked={suspendSla} onChange={(event) => setSuspendSla(event.target.checked)} />
        Suspend le SLA (RG-07)
      </label>
      <button type="submit" disabled={!code.trim() || !name.trim()}>
        Ajouter une étape
      </button>
      <ErrorBanner error={error} />
    </form>
  );
}

function AddTransitionForm({ steps, onAdded }: { steps: StepAdminResponse[]; onAdded: () => void }) {
  const [fromStepId, setFromStepId] = useState('');
  const [action, setAction] = useState<WorkflowAction>('ASSIGN');
  const [toStepId, setToStepId] = useState('');
  const [error, setError] = useState<unknown>(null);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!fromStepId) {
      return;
    }
    setError(null);
    try {
      await adminApi.addTransitionAdmin(Number(fromStepId), {
        action,
        toStepId: action === 'CLOSE' ? null : toStepId ? Number(toStepId) : null,
      });
      onAdded();
    } catch (submitError) {
      setError(submitError);
    }
  }

  return (
    <form className="parameter-form" onSubmit={handleSubmit}>
      <select value={fromStepId} onChange={(event) => setFromStepId(event.target.value)}>
        <option value="">— Depuis —</option>
        {steps.map((step) => (
          <option key={step.id} value={step.id}>
            {step.name}
          </option>
        ))}
      </select>
      <select value={action} onChange={(event) => setAction(event.target.value as WorkflowAction)}>
        {ACTIONS.map((option) => (
          <option key={option} value={option}>
            {actionLabel(option)}
          </option>
        ))}
      </select>
      {action !== 'CLOSE' && (
        <select value={toStepId} onChange={(event) => setToStepId(event.target.value)}>
          <option value="">— Vers —</option>
          {steps.map((step) => (
            <option key={step.id} value={step.id}>
              {step.name}
            </option>
          ))}
        </select>
      )}
      <button type="submit" disabled={!fromStepId}>
        Ajouter une transition
      </button>
      <ErrorBanner error={error} />
    </form>
  );
}
