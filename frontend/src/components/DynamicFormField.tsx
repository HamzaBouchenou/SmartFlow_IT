import type { FormFieldResponse } from '../api/types';

// §6.3 - un champ configurable rendu selon son FieldType. Le formulaire n'a besoin de
// connaître que ce que le back-end lui a envoyé (FormDefinitionResponse) : aucun type de
// champ n'est codé en dur ailleurs que dans ce switch, qui n'invente aucune règle de
// validation (l'obligation est vérifiée par domain/rule/FormValidationRule côté serveur ;
// ce composant se contente d'un attribut HTML `required` pour l'ergonomie).

interface DynamicFormFieldProps {
  field: FormFieldResponse;
  value: string;
  onChange: (code: string, value: string) => void;
  error?: string;
  /** §6.3 - champ affiché conditionnellement ("afficher X si Y vaut Z"). Purement visuel :
   * la décision d'afficher ou non le champ reste à l'appelant, qui seul connaît les valeurs
   * des autres champs du formulaire. */
  conditional?: boolean;
}

export function DynamicFormField({ field, value, onChange, error, conditional }: DynamicFormFieldProps) {
  const inputId = `field-${field.code}`;
  const helpId = field.helpText ? `${inputId}-help` : undefined;
  const errorId = error ? `${inputId}-error` : undefined;
  // §8 (Accessibilité) - "messages associés aux champs en erreur" : aria-describedby relie
  // le champ à son aide/erreur pour un lecteur d'écran, pas seulement pour l'œil (htmlFor
  // relie déjà le libellé). aria-invalid signale l'état d'erreur au-delà de la seule
  // couleur du texte.
  const describedBy = [helpId, errorId].filter(Boolean).join(' ') || undefined;

  return (
    <div className={conditional ? 'form-field form-field-conditional' : 'form-field'}>
      <label htmlFor={inputId}>
        {field.label}
        {field.required && <span className="required-mark"> *</span>}
      </label>
      {renderInput(field, inputId, value, onChange, describedBy, Boolean(error))}
      {field.helpText && (
        <p id={helpId} className="field-help">
          {field.helpText}
        </p>
      )}
      {error && (
        <p id={errorId} className="field-error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}

function renderInput(
  field: FormFieldResponse,
  inputId: string,
  value: string,
  onChange: (code: string, value: string) => void,
  describedBy: string | undefined,
  invalid: boolean,
) {
  switch (field.fieldType) {
    case 'TEXT':
      return (
        <input
          id={inputId}
          type="text"
          value={value}
          required={field.required}
          aria-describedby={describedBy}
          aria-invalid={invalid || undefined}
          onChange={(event) => onChange(field.code, event.target.value)}
        />
      );
    case 'NUMBER':
      return (
        <input
          id={inputId}
          type="number"
          value={value}
          required={field.required}
          aria-describedby={describedBy}
          aria-invalid={invalid || undefined}
          onChange={(event) => onChange(field.code, event.target.value)}
        />
      );
    case 'DATE':
      return (
        <input
          id={inputId}
          type="date"
          value={value}
          required={field.required}
          aria-describedby={describedBy}
          aria-invalid={invalid || undefined}
          onChange={(event) => onChange(field.code, event.target.value)}
        />
      );
    case 'LIST':
      return (
        <select
          id={inputId}
          value={value}
          required={field.required}
          aria-describedby={describedBy}
          aria-invalid={invalid || undefined}
          onChange={(event) => onChange(field.code, event.target.value)}
        >
          <option value="">— choisir —</option>
          {field.options
            .slice()
            .sort((a, b) => a.displayOrder - b.displayOrder)
            .map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
        </select>
      );
    case 'CHECKBOX':
      return (
        <input
          id={inputId}
          type="checkbox"
          checked={value === 'true'}
          aria-describedby={describedBy}
          aria-invalid={invalid || undefined}
          onChange={(event) => onChange(field.code, event.target.checked ? 'true' : 'false')}
        />
      );
    case 'USER':
    case 'DEPARTMENT':
      return (
        <input
          id={inputId}
          type="number"
          value={value}
          required={field.required}
          placeholder="Identifiant"
          aria-describedby={describedBy}
          aria-invalid={invalid || undefined}
          onChange={(event) => onChange(field.code, event.target.value)}
        />
      );
    case 'FILE': {
      const fileHelpId = `${inputId}-file-help`;
      return (
        <>
          <input
            id={inputId}
            type="file"
            disabled
            aria-describedby={[describedBy, fileHelpId].filter(Boolean).join(' ')}
            title="Les pièces jointes ne sont pas encore disponibles."
          />
          <p id={fileHelpId} className="field-help">
            Les pièces jointes ne sont pas encore disponibles dans cette version.
          </p>
        </>
      );
    }
  }
}
