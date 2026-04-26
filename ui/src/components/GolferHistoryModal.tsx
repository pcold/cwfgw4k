import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';
import { formatMoney } from '@/util/money';

interface Props {
  seasonId: string | null;
  golferId: string | null;
  onClose: () => void;
}

function GolferHistoryModal({ seasonId, golferId, onClose }: Props) {
  const open = !!golferId;

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  const historyQuery = useQuery({
    queryKey: ['golferHistory', seasonId, golferId],
    queryFn: () => api.golferHistory(seasonId!, golferId!),
    enabled: !!seasonId && !!golferId,
  });

  if (!open) return null;

  const history = historyQuery.data;
  const loading = historyQuery.isLoading;
  const error = historyQuery.error;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="golfer-history-title"
      className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-3 sm:p-4"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="bg-gray-800 rounded-lg shadow-xl w-[min(92vw,28rem)] max-h-[90vh] overflow-y-auto border border-gray-600">
        <div className="px-5 py-4 border-b border-gray-700 flex items-center justify-between">
          <h3 id="golfer-history-title" className="text-lg font-bold">
            {loading ? 'Loading…' : (history?.golferName ?? 'Golfer')}
          </h3>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="text-gray-400 hover:text-white text-xl leading-none px-2"
          >
            ×
          </button>
        </div>
        <div className="px-5 py-4">
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
        </div>
      </div>
    </div>
  );
}

export default GolferHistoryModal;
