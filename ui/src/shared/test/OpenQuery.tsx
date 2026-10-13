import { useQuery, type QueryKey } from '@tanstack/react-query';

interface OpenQueryProps {
  queryKey: QueryKey;
  fetcher: () => Promise<unknown>;
}

/**
 * Stands in for a page left open while the handler under test writes. Build `queryKey` from the
 * `queryKeys` entry that page reads: a key no page holds refetches just the same and proves nothing.
 */
function OpenQuery({ queryKey, fetcher }: OpenQueryProps): null {
  useQuery({ queryKey, queryFn: fetcher });
  return null;
}

export default OpenQuery;
