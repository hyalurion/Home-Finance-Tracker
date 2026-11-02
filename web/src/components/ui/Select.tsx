'use client';

interface Option {
  label: string;
  value: string;
}

interface SelectProps {
  value: string;
  onChange: (value: string) => void;
  options: Option[];
  emptyOptionLabel?: string;
  className?: string;
}

/** Styled native <select> — accessible and zero-dependency. */
export function Select({ value, onChange, options, emptyOptionLabel, className }: SelectProps) {
  return (
    <select
      className={`select ${className || ''}`}
      value={value}
      onChange={(e) => onChange(e.target.value)}
    >
      {emptyOptionLabel !== undefined ? (
        <option value="">{emptyOptionLabel}</option>
      ) : null}
      {options.map((opt) => (
        <option key={opt.value} value={opt.value}>
          {opt.label}
        </option>
      ))}
    </select>
  );
}
