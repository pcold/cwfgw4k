import type { ReactElement, ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, type RenderResult } from '@testing-library/react';
import { LeagueSeasonProvider } from '@/context/LeagueSeasonContext';
import { AuthProvider } from '@/context/AuthContext';

interface Options {
  initialPath?: string;
  withLeagueSeasonProvider?: boolean;
  withAuthProvider?: boolean;
}

export function renderWithProviders(ui: ReactElement, options: Options = {}): RenderResult {
  const {
    initialPath = '/',
    withLeagueSeasonProvider = true,
    withAuthProvider = false,
  } = options;

  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, staleTime: Infinity },
    },
  });

  let tree: ReactNode = ui;
  if (withLeagueSeasonProvider) tree = <LeagueSeasonProvider>{tree}</LeagueSeasonProvider>;
  if (withAuthProvider) tree = <AuthProvider>{tree}</AuthProvider>;

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialPath]}>{tree}</MemoryRouter>
    </QueryClientProvider>,
  );
}
