export function mutationError(err: unknown): string | null {
  if (err instanceof Error) return err.message;
  return null;
}
