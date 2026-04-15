import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '@/api/client';
import type { League } from '@/api/types';

function CreateLeagueSection() {
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [success, setSuccess] = useState<string | null>(null);

  const createMutation = useMutation({
    mutationFn: (leagueName: string) => api.createLeague(leagueName),
    onSuccess: (league: League) => {
      setSuccess(`Created "${league.name}"`);
      setName('');
      void queryClient.invalidateQueries({ queryKey: ['leagues'] });
    },
  });

  const disabled = !name.trim() || createMutation.isPending;
  const errorMessage =
    createMutation.error instanceof ApiError
      ? createMutation.error.message
      : createMutation.error instanceof Error
        ? createMutation.error.message
        : null;

  return (
    <div className="bg-gray-800 rounded-lg p-6">
      <h3 className="text-sm font-semibold text-gray-300 uppercase tracking-wider mb-4">
        Create League
      </h3>
      <div className="flex flex-wrap items-center gap-4">
        <input
          type="text"
          aria-label="New league name"
          value={name}
          onChange={(e) => {
            setName(e.target.value);
            setSuccess(null);
          }}
          placeholder="e.g. Castlewood Fantasy Golf"
          className="bg-gray-700 border border-gray-600 rounded px-3 py-2 text-sm w-full sm:w-72"
        />
        <button
          type="button"
          onClick={() => createMutation.mutate(name.trim())}
          disabled={disabled}
          className="bg-green-600 hover:bg-green-700 disabled:bg-gray-600 text-white px-4 py-2 rounded text-sm font-medium"
        >
          {createMutation.isPending ? 'Creating...' : 'Create League'}
        </button>
        {errorMessage ? (
          <span role="alert" className="text-red-400 text-sm">
            {errorMessage}
          </span>
        ) : null}
        {success ? <span className="text-green-400 text-sm">{success}</span> : null}
      </div>
    </div>
  );
}

export default CreateLeagueSection;
