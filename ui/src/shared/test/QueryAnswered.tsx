import type { ReactNode } from 'react';
import { useQuery, type QueryKey } from '@tanstack/react-query';

interface QueryAnsweredProps {
  queryKey: QueryKey;
  fetcher: () => Promise<unknown>;
}

/**
 * Prints "answered" once `queryKey` holds data, for a page whose correct response to that answer is
 * to show nothing new: it gives the test a point to wait on before asserting what did not happen.
 */
function QueryAnswered({ queryKey, fetcher }: QueryAnsweredProps): ReactNode {
  const { data } = useQuery({ queryKey, queryFn: fetcher });
  return data === undefined ? null : <p>answered</p>;
}

export default QueryAnswered;
