import type { FormEvent, ReactElement, ReactNode } from 'react';
import ModalShell from './ModalShell';

interface FormModalProps {
  readonly title: string;
  /** Includes its pending form, such as `Logging in…`; the modal derives nothing from [isPending]. */
  readonly submitLabel: string;
  readonly isPending: boolean;
  readonly serverError: string | undefined;
  readonly onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  readonly onClose: () => void;
  readonly children: ReactNode;
}

/** Consumers own their fields, validation and mutations. */
export default function FormModal({
  title,
  submitLabel,
  isPending,
  serverError,
  onSubmit,
  onClose,
  children,
}: FormModalProps): ReactElement {
  return (
    <ModalShell title={title} onClose={onClose}>
      <form className="space-y-3" onSubmit={onSubmit} noValidate>
        {children}

        {serverError !== undefined && (
          <p role="alert" className="text-semantic-danger text-xs">
            {serverError}
          </p>
        )}

        <div className="flex gap-3">
          <button
            type="submit"
            disabled={isPending}
            className="bg-primary hover:bg-primary-hover disabled:bg-disabled text-on-primary px-4 py-2 rounded text-sm font-medium flex-1"
          >
            {submitLabel}
          </button>
          <button
            type="button"
            onClick={onClose}
            className="bg-secondary hover:bg-secondary-hover text-on-fill px-4 py-2 rounded text-sm font-medium"
          >
            Cancel
          </button>
        </div>
      </form>
    </ModalShell>
  );
}
