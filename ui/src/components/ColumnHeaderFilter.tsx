export interface ColumnHeaderFilterOption {
  value: string;
  label: string;
}

interface Props {
  ariaLabel: string;
  value: string;
  onChange: (value: string) => void;
  options: readonly ColumnHeaderFilterOption[];
  className?: string;
}

// Styled <select> that lives inside a table header cell and narrows the rows below it.
// The first option in `options` is treated as the "show all" entry (its value — typically
// "" — is the default filter state). Callers own the filter state and predicate; this
// component just renders the control consistently across tables.
function ColumnHeaderFilter({ ariaLabel, value, onChange, options, className }: Props) {
  const base =
    'bg-transparent text-xs uppercase tracking-wider text-inherit hover:text-gray-300 ' +
    'focus:text-gray-300 focus:outline-none cursor-pointer -ml-1 pr-1';
  return (
    <select
      aria-label={ariaLabel}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className={className ? `${base} ${className}` : base}
    >
      {options.map((opt) => (
        <option key={opt.value} value={opt.value}>
          {opt.label}
        </option>
      ))}
    </select>
  );
}

export default ColumnHeaderFilter;
