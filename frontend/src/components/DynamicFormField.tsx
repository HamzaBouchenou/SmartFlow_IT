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
}

export function DynamicFormField({ field, value, onChange, error }: DynamicFormFieldProps) {
  const inputId = `field-${field.code}`;

  return (
    <div className="form-field">
      <label htmlFor={inputId}>
        {field.label}
        {field.required && <span className="required-mark"> *</span>}
      </label>
      {renderInput(field, inputId, value, onChange)}
      {field.helpText && <p className="field-help">{field.helpText}</p>}
      {error && <p className="field-error">{error}</p>}
    </div>
  );
}

function renderInput(
  field: FormFieldResponse,
  inputId: string,
  value: string,
  onChange: (code: string, value: string) => void,
) {
  switch (field.fieldType) {
    case 'TEXT':
      return (
        <input
          id={inputId}
          type="text"
          value={value}
          required={field.required}
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
          onChange={(event) => onChange(field.code, event.target.value)}
        />
      );
    case 'LIST':
      return (
        <select
          id={inputId}
          value={value}
          required={field.required}
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
          onChange={(event) => onChange(field.code, event.target.value)}
        />
      );
    case 'FILE':
      return (
        <>
          <input id={inputId} type="file" disabled title="Les pièces jointes ne sont pas encore disponibles." />
          <p className="field-help">Les pièces jointes ne sont pas encore disponibles dans cette version.</p>
        </>
      );
  }
}
