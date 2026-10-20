import type { ReactElement, ReactNode } from 'react';
import { MemoryRouter } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, type RenderResult } from '@testing-library/react';
import { LeagueSeasonProvider } from '@/features/leagues/LeagueSeasonContext';
import { AuthProvider } from '@/features/auth/AuthContext';
import { HeaderSlotProvider } from '@/shared/components/HeaderSlot';

interface Options {
  readonly initialPath?: string;
  readonly withLeagueSeasonProvider?: boolean;
  readonly withAuthProvider?: boolean;
  readonly withHeaderSlotProvider?: boolean;
  /** The nav's slot a page portals into; null is the app before the nav mounts it. */
  readonly headerSlot?: HTMLElement | null;
}

export function renderWithProviders(ui: ReactElement, options: Options = {}): RenderResult {
  const {
    initialPath = '/',
    withLeagueSeasonProvider = true,
    withAuthProvider = false,
    withHeaderSlotProvider = true,
    headerSlot = null,
  } = options;

  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, staleTime: Infinity },
    },
  });

  let tree: ReactNode = ui;
  if (withHeaderSlotProvider) tree = <HeaderSlotProvider slot={headerSlot}>{tree}</HeaderSlotProvider>;
  if (withLeagueSeasonProvider) tree = <LeagueSeasonProvider>{tree}</LeagueSeasonProvider>;
  if (withAuthProvider) tree = <AuthProvider>{tree}</AuthProvider>;

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialPath]}>{tree}</MemoryRouter>
    </QueryClientProvider>,
  );
}
