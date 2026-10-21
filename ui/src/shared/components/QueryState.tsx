import type { ReactNode } from 'react';

/** @see QueryState */
export interface QueryStateLike<T> {
  isLoading: boolean;
  isError: boolean;
  error: unknown;
  data: T | undefined;
}

interface QueryStateProps<T> {
  query: QueryStateLike<T>;
  label: string;
  children: (data: T) => ReactNode;
}

/**
 * A query neither loading nor failed with no data, such as a disabled one, renders nothing rather than a
 * loading line.
 */
export default function QueryState<T>({ query, label, children }: QueryStateProps<T>): ReactNode {
  if (query.isLoading) {
    return <p className="text-ink-muted">Loading {label}…</p>;
  }
  if (query.isError) {
    return (
      <p className="text-semantic-danger">
        Failed to load {label}: {String(query.error)}
      </p>
    );
  }
  if (query.data === undefined) return null;
  return <>{children(query.data)}</>;
}
