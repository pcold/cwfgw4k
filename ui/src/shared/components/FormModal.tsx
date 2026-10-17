import type { FormEvent, ReactElement, ReactNode } from 'react';
import Button from './Button';
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
          <Button tone="primary" size="regular" layout="flex-1" type="submit" disabled={isPending}>
            {submitLabel}
          </Button>
          <Button tone="secondary" size="regular" onClick={onClose}>
            Cancel
          </Button>
        </div>
      </form>
    </ModalShell>
  );
}
