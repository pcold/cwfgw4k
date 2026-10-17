import { useState, type ReactNode } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import type { League } from '@/shared/api/types';
import { TAP_TARGET_CLASS, TextField } from '@/shared/components/formFields';
import { mutationError } from '@/shared/util/mutationError';

const CREATE_BUTTON_CLASS =
  'bg-primary hover:bg-primary-hover disabled:bg-disabled text-on-primary px-4 py-2 rounded text-sm ' +
  `font-medium ${TAP_TARGET_CLASS}`;

function CreateLeagueSection(): ReactNode {
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [success, setSuccess] = useState<string | null>(null);

  const createMutation = useMutation({
    mutationFn: (leagueName: string) => api.createLeague(leagueName),
    onSuccess: (league: League) => {
      setSuccess(`Created "${league.name}"`);
      setName('');
      void queryClient.invalidateQueries({ queryKey: queryKeys.leagues.root });
    },
  });

  const disabled = !name.trim() || createMutation.isPending;
  const errorMessage = mutationError(createMutation.error);

  return (
    <div className="bg-surface-card rounded-lg p-6">
      <h3 className="text-sm font-semibold text-ink-label uppercase tracking-wider mb-4">
        Create League
      </h3>
      <div className="flex flex-wrap items-end gap-4">
        <TextField
          id="new-league-name"
          label="New league name"
          value={name}
          error={undefined}
          placeholder="e.g. Castlewood Fantasy Golf"
          width="w-full sm:w-72"
          onChange={(value) => {
            setName(value);
            setSuccess(null);
          }}
        />
        <button
          type="button"
          onClick={() => createMutation.mutate(name.trim())}
          disabled={disabled}
          className={CREATE_BUTTON_CLASS}
        >
          {createMutation.isPending ? 'Creating...' : 'Create League'}
        </button>
        {errorMessage ? (
          <span role="alert" className="text-semantic-danger text-sm">
            {errorMessage}
          </span>
        ) : null}
        {success ? <span className="text-semantic-success text-sm">{success}</span> : null}
      </div>
    </div>
  );
}

export default CreateLeagueSection;
