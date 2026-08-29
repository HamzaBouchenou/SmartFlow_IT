import { useState } from 'react';
import { actionLabel } from '../lib/format';

// RG-08/ADR-14 (docs/DECISIONS.md) - REOPEN n'est jamais résolue par ActionBar (elle ne
// fait jamais partie de l'ExecuteTransitionRequest posté sur /transitions) : ce composant
// n'apparaît que lorsque le parent a vu 'REOPEN' dans availableActions[], jamais depuis un
// statut ou un rôle deviné côté client.

interface ReopenButtonProps {
  onReopen: () => Promise<void>;
}

export function ReopenButton({ onReopen }: ReopenButtonProps) {
  const [submitting, setSubmitting] = useState(false);

  async function handleClick() {
    setSubmitting(true);
    try {
      await onReopen();
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <button type="button" className="action-button action-reopen" disabled={submitting} onClick={() => void handleClick()}>
      {actionLabel('REOPEN')}
    </button>
  );
}
