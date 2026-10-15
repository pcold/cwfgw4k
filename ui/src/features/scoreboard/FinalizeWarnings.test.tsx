import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import { makeTournament } from '@/shared/test/fixtures';
import type { EspnImportWarnings, FinalizedTournament, Golfer } from '@/shared/api/types';
import FinalizeWarnings from './FinalizeWarnings';

function golfer(id: string, firstName: string, lastName: string): Golfer {
  return {
    id,
    pgaPlayerId: null,
    firstName,
    lastName,
    country: null,
    worldRanking: null,
    updatedAt: '2026-01-01T00:00:00Z',
  };
}

function warningsFor(tournamentName: string, kinds: Partial<EspnImportWarnings>): EspnImportWarnings {
  return {
    tournamentId: `tn-${tournamentName}`,
    tournamentName,
    rosteredSurnameTwins: [],
    createdGolfers: [],
    unmatchedCompetitors: [],
    contestedCompetitors: [],
    ...kinds,
  };
}

function finalizedWith(importWarnings: EspnImportWarnings[]): FinalizedTournament {
  return { tournament: makeTournament({ name: 'Zurich Classic' }), importWarnings };
}

const twin = {
  created: golfer('g-rasmus', 'Rasmus', 'Hojgaard'),
  rostered: [golfer('g-nicolai', 'Nicolai', 'Højgaard')],
};

describe('FinalizeWarnings', () => {
  it('raises a created twin of a rostered surname as an alert naming both golfers', () => {
    renderWithProviders(
      <FinalizeWarnings finalized={finalizedWith([warningsFor('Zurich Classic', { rosteredSurnameTwins: [twin] })])} />,
    );

    const alert = screen.getByRole('alert');
    expect(within(alert).getByText('Possible duplicate of a rostered golfer')).toBeInTheDocument();
    expect(alert).toHaveTextContent(/Rasmus Hojgaard was created as a new golfer.*rostered Nicolai Højgaard/);
  });

  it('lists a contested competitor with every golfer its name could be, outside the alert', () => {
    const contested = {
      espnCompetitorId: '5501',
      name: 'Tom Kim',
      candidates: [golfer('g-kim-1', 'Tom', 'Kim'), golfer('g-kim-2', 'Tom', 'Kim')],
    };
    renderWithProviders(
      <FinalizeWarnings finalized={finalizedWith([warningsFor('Zurich Classic', { contestedCompetitors: [contested] })])} />,
    );

    expect(screen.getByText(/the name fits more than one golfer/)).toBeInTheDocument();
    expect(screen.getByText('Tom Kim (ESPN 5501) could be Tom Kim or Tom Kim.')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it.each([
    ['no name', ''],
    ['a blank name', '  '],
  ])('lists an unmatched competitor by its ESPN id, saying so when ESPN sent %s', (_sent, name) => {
    const unmatched = { espnCompetitorId: '5502', name };
    renderWithProviders(
      <FinalizeWarnings finalized={finalizedWith([warningsFor('Zurich Classic', { unmatchedCompetitors: [unmatched] })])} />,
    );

    expect(screen.getByText(/matched to no golfer/)).toBeInTheDocument();
    expect(screen.getByText('an unnamed competitor (ESPN 5502)')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('notes the golfers the import created, outside the alert', () => {
    const created = [golfer('g-burns', 'Sam', 'Burns'), golfer('g-straka', 'Sepp', 'Straka')];
    renderWithProviders(
      <FinalizeWarnings finalized={finalizedWith([warningsFor('Zurich Classic', { createdGolfers: created })])} />,
    );

    expect(screen.getByText('New golfers created: Sam Burns, Sepp Straka')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('heads each tournament an unlinked finalize imported into with its own name', () => {
    renderWithProviders(
      <FinalizeWarnings
        finalized={finalizedWith([
          warningsFor('Zurich Classic', { rosteredSurnameTwins: [twin] }),
          warningsFor('Corales Puntacana', { createdGolfers: [golfer('g-burns', 'Sam', 'Burns')] }),
        ])}
      />,
    );

    expect(screen.getAllByRole('heading').map((heading) => heading.textContent)).toEqual([
      'ESPN import warnings: Zurich Classic',
      'ESPN import warnings: Corales Puntacana',
    ]);
  });

  it('names the finalized tournament when every import it ran was clean', () => {
    renderWithProviders(<FinalizeWarnings finalized={finalizedWith([warningsFor('Zurich Classic', {})])} />);

    expect(screen.getByRole('status')).toHaveTextContent('No ESPN import warnings from finalizing Zurich Classic.');
    expect(screen.queryByRole('region', { name: 'ESPN import warnings' })).not.toBeInTheDocument();
  });

  it('heads only the tournaments that raised warnings', () => {
    renderWithProviders(
      <FinalizeWarnings
        finalized={finalizedWith([
          warningsFor('Zurich Classic', { rosteredSurnameTwins: [twin] }),
          warningsFor('Valspar Championship', {}),
        ])}
      />,
    );

    expect(screen.getAllByRole('heading').map((heading) => heading.textContent)).toEqual([
      'ESPN import warnings: Zurich Classic',
    ]);
  });

  it('says no ESPN event was imported rather than that an import was clean', () => {
    renderWithProviders(<FinalizeWarnings finalized={finalizedWith([])} />);

    expect(screen.getByRole('status')).toHaveTextContent('Finalizing Zurich Classic imported no ESPN event.');
    expect(screen.queryByText(/No ESPN import warnings/)).not.toBeInTheDocument();
  });
});
