import { useState, type ReactNode } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import type { League } from '@/shared/api/types';
import Button from '@/shared/components/Button';
import { TextField } from '@/shared/components/formFields';
import { mutationError } from '@/shared/util/mutationError';
import AdminSection from './AdminSection';

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
    <AdminSection title="Create League">
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
        <Button tone="primary" size="regular" onClick={() => createMutation.mutate(name.trim())} disabled={disabled}>
          {createMutation.isPending ? 'Creating...' : 'Create League'}
        </Button>
        {errorMessage ? (
          <span role="alert" className="text-semantic-danger text-sm">
            {errorMessage}
          </span>
        ) : null}
        {success ? <span className="text-semantic-success text-sm">{success}</span> : null}
      </div>
    </AdminSection>
  );
}

export default CreateLeagueSection;
