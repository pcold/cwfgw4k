import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import type { api } from '@/shared/api/client';
import type { DataHealthGolfer, DataHealthReport } from '@/shared/api/types';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import DataHealthSection from './DataHealthSection';

const dataHealthMock = vi.fn<typeof api.dataHealth>();

vi.mock('@/shared/api/client', () => ({
  api: {
    dataHealth: () => dataHealthMock(),
  },
  ApiError: class ApiError extends Error {},
}));

const ROSTERED_KIM: DataHealthGolfer = {
  id: 'g-1',
  firstName: 'Si Woo',
  lastName: 'Kim',
  pgaPlayerId: null,
};

const ESPN_KIM: DataHealthGolfer = { ...ROSTERED_KIM, id: 'g-2', pgaPlayerId: '7081' };

const BLANK_GRIFFIN: DataHealthGolfer = {
  id: 'g-3',
  firstName: '',
  lastName: 'Griffin',
  pgaPlayerId: null,
};

const NOTHING_FOUND: DataHealthReport = {
  rosteredTwins: { count: 0, rows: [] },
  rosteredWithoutEspnId: { count: 0, rows: [] },
  blankFirstNames: { count: 0, rows: [] },
};

describe('DataHealthSection', () => {
  beforeEach(() => {
    dataHealthMock.mockReset();
  });

  it('renders each check with its count and the rows behind it', async () => {
    dataHealthMock.mockResolvedValue({
      rosteredTwins: {
        count: 1,
        rows: [
          {
            normalizedName: 'si woo kim',
            rosteredWithoutEspnId: [ROSTERED_KIM],
            holdingEspnId: [ESPN_KIM],
          },
        ],
      },
      rosteredWithoutEspnId: { count: 2, rows: [BLANK_GRIFFIN, ROSTERED_KIM] },
      blankFirstNames: { count: 1, rows: [BLANK_GRIFFIN] },
    });

    renderWithProviders(<DataHealthSection />, { withLeagueSeasonProvider: false });

    const twins = await screen.findByRole('region', { name: 'Rostered twins' });
    expect(within(twins).getByRole('heading')).toHaveTextContent('Rostered twins (1)');
    expect(within(twins).getByText('si woo kim')).toBeInTheDocument();
    expect(within(twins).getByText(/Rostered: Si Woo Kim/)).toHaveTextContent('g-1');
    expect(within(twins).getByText(/Takes results: Si Woo Kim/)).toHaveTextContent('ESPN 7081');

    const withoutId = screen.getByRole('region', { name: 'Rostered without an ESPN id' });
    expect(within(withoutId).getByRole('heading')).toHaveTextContent('(2)');
    expect(
      within(withoutId)
        .getAllByRole('listitem')
        .map((row) => row.textContent),
    ).toEqual(['(blank) Griffin g-3', 'Si Woo Kim g-1']);

    const blank = screen.getByRole('region', { name: 'Blank first names' });
    expect(within(blank).getByRole('heading')).toHaveTextContent('(1)');
    expect(within(blank).getByRole('listitem')).toHaveTextContent('(blank) Griffin');
  });

  it('shows every check clear when the server finds nothing', async () => {
    dataHealthMock.mockResolvedValue(NOTHING_FOUND);

    renderWithProviders(<DataHealthSection />, { withLeagueSeasonProvider: false });

    const checks = await screen.findAllByRole('region');
    expect(checks.map((check) => within(check).getByRole('heading').textContent)).toEqual([
      'Rostered twins (0)',
      'Rostered without an ESPN id (0)',
      'Blank first names (0)',
    ]);
    checks.forEach((check) => {
      expect(within(check).getByText('None found.')).toBeInTheDocument();
      expect(within(check).queryByRole('listitem')).not.toBeInTheDocument();
    });
  });

  it('reports a failed read instead of a clear bill of health', async () => {
    dataHealthMock.mockRejectedValue(new Error('403 forbidden'));

    renderWithProviders(<DataHealthSection />, { withLeagueSeasonProvider: false });

    expect(await screen.findByText(/Failed to load golfer data health: Error: 403 forbidden/)).toBeInTheDocument();
    expect(screen.queryByText('None found.')).not.toBeInTheDocument();
  });
});
