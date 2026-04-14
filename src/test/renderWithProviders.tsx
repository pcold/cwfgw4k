import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, type RenderResult } from '@testing-library/react';
import { LeagueSeasonProvider } from '../context/LeagueSeasonContext';

interface Options {
  initialPath?: string;
  withLeagueSeasonProvider?: boolean;
}

export function renderWithProviders(ui: ReactElement, options: Options = {}): RenderResult {
  const { initialPath = '/', withLeagueSeasonProvider = true } = options;

  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, staleTime: Infinity },
    },
  });

  const inner = withLeagueSeasonProvider ? <LeagueSeasonProvider>{ui}</LeagueSeasonProvider> : ui;

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialPath]}>{inner}</MemoryRouter>
    </QueryClientProvider>,
  );
}
