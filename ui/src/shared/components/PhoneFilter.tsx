import { useId, type ReactNode } from 'react';
import { FIELD_CLASS, FieldShell, type SelectOption } from './formFields';

interface PhoneFilterProps {
  readonly label: string;
  readonly value: string;
  readonly options: readonly SelectOption[];
  readonly onChange: (value: string) => void;
}

/**
 * Not `SelectField`: its placeholder is disabled, and the first option here is "all", which must stay
 * selectable. The label is screen-reader only, so an option has to name what it picks.
 */
function PhoneFilter({ label, value, options, onChange }: PhoneFilterProps): ReactNode {
  const id = useId();
  return (
    <FieldShell id={id} label={label} error={undefined} hideLabel>
      <select id={id} value={value} onChange={(event) => onChange(event.target.value)} className={FIELD_CLASS}>
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </FieldShell>
  );
}

export default PhoneFilter;
