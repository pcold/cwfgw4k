import { useEffect } from 'react';

export function useDefaultSelectedId<T extends { id: string }>(
  items: T[] | undefined,
  currentId: string,
  setId: (id: string) => void,
): void {
  useEffect(() => {
    if (!currentId && items && items.length > 0) {
      setId(items[0].id);
    }
  }, [items, currentId, setId]);
}
