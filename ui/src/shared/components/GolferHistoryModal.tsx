import type { ReactNode } from 'react';
import { skipToken, useQuery } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import { formatMoney } from '@/shared/util/money';
import ModalShell from './ModalShell';

interface Props {
  seasonId: string | null;
  golferId: string | null;
  onClose: () => void;
}

function GolferHistoryModal({ seasonId, golferId, onClose }: Props): ReactNode {
  const open = !!golferId;

  const historyQuery = useQuery({
    queryKey: queryKeys.golferHistory.detail(seasonId, golferId),
    queryFn:
      seasonId === null || golferId === null
        ? skipToken
        : () => api.golferHistory(seasonId, golferId),
  });

  if (!open) return null;

  const history = historyQuery.data;
  const loading = historyQuery.isLoading;
  const error = historyQuery.error;

  return (
    <ModalShell
      title={loading ? 'Loading…' : (history?.golferName ?? 'Golfer')}
      onClose={onClose}
      width="detail"
      dismissOnBackdrop
    >
      {loading ? (
        <div className="text-center text-gray-500 py-4">Loading…</div>
      ) : error ? (
        <div role="alert" className="text-center text-red-400 py-4">
          Failed to load golfer history
        </div>
      ) : history ? (
        <div>
          <div className="flex justify-between text-sm text-gray-400 mb-3">
            <span>
              {history.topTens} top 10{history.topTens !== 1 ? 's' : ''}
            </span>
            <span className="text-green-400 font-mono font-bold">
              {formatMoney(history.totalEarnings)}
            </span>
          </div>
          {history.results.length > 0 ? (
            <table className="w-full text-sm">
              <thead className="text-gray-400 text-xs uppercase">
                <tr>
                  <th className="text-left px-2 py-1">Tournament</th>
                  <th className="text-right px-2 py-1">Place</th>
                  <th className="text-right px-2 py-1">Money</th>
                </tr>
              </thead>
              <tbody>
                {history.results.map((r) => (
                  <tr key={r.tournament} className="border-t border-gray-700">
                    <td className="px-2 py-1.5">{r.tournament}</td>
                    <td className="px-2 py-1.5 text-right font-mono">{r.position}</td>
                    <td className="px-2 py-1.5 text-right font-mono text-green-400">
                      {formatMoney(r.earnings)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <p className="text-gray-500 text-center py-4">No top 10 finishes this season</p>
          )}
          <p className="text-gray-500 text-xs italic mt-3">
            Finalized results only. Does not include live projections or shared ownership splits.
          </p>
        </div>
      ) : null}
    </ModalShell>
  );
}

export default GolferHistoryModal;
