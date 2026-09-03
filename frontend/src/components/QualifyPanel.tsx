import { useState } from 'react';
import type { Priority } from '../api/types';
import { priorityLabel } from '../lib/format';

// §5/RG-07 - "qualifier" n'est pas un WorkflowAction (jamais posté sur /transitions, voir
// RequestService.qualify's own javadoc côté back-end) : ce composant n'apparaît que
// lorsque le parent a vu request.canQualify=true, jamais depuis un rôle ou un statut
// deviné côté client (CLAUDE.md - même principe que ReopenButton pour REOPEN).

const PRIORITIES: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

interface QualifyPanelProps {
  currentPriority: Priority | null;
  onQualify: (priority: Priority) => Promise<void>;
}

export function QualifyPanel({ currentPriority, onQualify }: QualifyPanelProps) {
  const [priority, setPriority] = useState<Priority>(currentPriority ?? 'MEDIUM');
  const [submitting, setSubmitting] = useState(false);

  async function handleClick() {
    setSubmitting(true);
    try {
      await onQualify(priority);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="qualify-panel">
      <label htmlFor="qualify-priority">Priorité</label>
      <select
        id="qualify-priority"
        value={priority}
        disabled={submitting}
        onChange={(event) => setPriority(event.target.value as Priority)}
      >
        {PRIORITIES.map((value) => (
          <option key={value} value={value}>
            {priorityLabel(value)}
          </option>
        ))}
      </select>
      <button type="button" disabled={submitting} onClick={() => void handleClick()}>
        {currentPriority ? 'Requalifier' : 'Qualifier'}
      </button>
    </div>
  );
}
