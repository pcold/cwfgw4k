import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { api } from '@/shared/api/client';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import { expectSharedColumnHeads } from '@/shared/test/columnHeads';
import type { GolferHistory } from '@/shared/api/types';
import GolferHistoryModal from './GolferHistoryModal';

const golferHistoryMock = vi.fn<typeof api.golferHistory>();

vi.mock('@/shared/api/client', () => ({
  api: {
    golferHistory: (seasonId: string, golferId: string) => golferHistoryMock(seasonId, golferId),
  },
  ApiError: class ApiError extends Error {},
}));

const sampleHistory: GolferHistory = {
  golferName: 'Scottie Scheffler',
  totalEarnings: 240,
  topTens: 3,
  results: [
    { tournament: 'Sample Open', position: 1, earnings: 120 },
    { tournament: 'Other Open', position: 5, earnings: 80 },
    { tournament: 'Third Open', position: 9, earnings: 40 },
  ],
};

describe('GolferHistoryModal', () => {
  beforeEach(() => {
    golferHistoryMock.mockReset();
  });

  it('renders nothing when golferId is null', () => {
    renderWithProviders(
      <GolferHistoryModal seasonId="s-1" golferId={null} onClose={() => {}} />,
      { withLeagueSeasonProvider: false },
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(golferHistoryMock).not.toHaveBeenCalled();
  });

  it('fetches and displays a golfer history when opened', async () => {
    golferHistoryMock.mockResolvedValue(sampleHistory);
    renderWithProviders(
      <GolferHistoryModal seasonId="s-1" golferId="g-1" onClose={() => {}} />,
      { withLeagueSeasonProvider: false },
    );

    expect(await screen.findByText('Scottie Scheffler')).toBeInTheDocument();
    expect(screen.getByText(/3 top 10s/)).toBeInTheDocument();
    expect(screen.getByText('Sample Open')).toBeInTheDocument();
    expect(screen.getByText('Other Open')).toBeInTheDocument();
    expect(screen.getByText('Third Open')).toBeInTheDocument();
    expect(golferHistoryMock).toHaveBeenCalledWith('s-1', 'g-1');
  });

  it('shows the empty-state message when there are no top 10 finishes', async () => {
    golferHistoryMock.mockResolvedValue({
      ...sampleHistory,
      topTens: 0,
      totalEarnings: 0,
      results: [],
    });
    renderWithProviders(
      <GolferHistoryModal seasonId="s-1" golferId="g-1" onClose={() => {}} />,
      { withLeagueSeasonProvider: false },
    );

    expect(await screen.findByText(/No top 10 finishes this season/i)).toBeInTheDocument();
  });

  it('singular "top 10" label when topTens === 1', async () => {
    golferHistoryMock.mockResolvedValue({ ...sampleHistory, topTens: 1, results: sampleHistory.results.slice(0, 1) });
    renderWithProviders(
      <GolferHistoryModal seasonId="s-1" golferId="g-1" onClose={() => {}} />,
      { withLeagueSeasonProvider: false },
    );

    expect(await screen.findByText('1 top 10')).toBeInTheDocument();
  });

  it('calls onClose when the close button is clicked', async () => {
    golferHistoryMock.mockResolvedValue(sampleHistory);
    const onClose = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(
      <GolferHistoryModal seasonId="s-1" golferId="g-1" onClose={onClose} />,
      { withLeagueSeasonProvider: false },
    );

    await screen.findByText('Scottie Scheffler');
    await user.click(screen.getByRole('button', { name: /close/i }));
    expect(onClose).toHaveBeenCalled();
  });

  it('calls onClose on a click outside the panel, but not on one inside it', async () => {
    golferHistoryMock.mockResolvedValue(sampleHistory);
    const onClose = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(
      <GolferHistoryModal seasonId="s-1" golferId="g-1" onClose={onClose} />,
      { withLeagueSeasonProvider: false },
    );

    await user.click(await screen.findByText('Sample Open'));
    expect(onClose).not.toHaveBeenCalled();

    await user.click(screen.getByRole('dialog').parentElement as HTMLElement);
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('shows an error state when the fetch fails', async () => {
    golferHistoryMock.mockRejectedValue(new Error('boom'));
    renderWithProviders(
      <GolferHistoryModal seasonId="s-1" golferId="g-1" onClose={() => {}} />,
      { withLeagueSeasonProvider: false },
    );

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/Failed to load golfer history/i);
    });
  });

  it('tones the season total in the header and leaves each tournament payout in the table ink', async () => {
    golferHistoryMock.mockResolvedValue(sampleHistory);
    renderWithProviders(
      <GolferHistoryModal seasonId="s-1" golferId="g-1" onClose={() => {}} />,
      { withLeagueSeasonProvider: false },
    );

    expect(await screen.findByText('+$240.00')).toHaveClass('text-semantic-success');
    expect(screen.getByText('+$120.00').className).not.toMatch(/text-semantic-|text-ink-soft/);
  });

  it('sets every column head in the shared column-header type', async () => {
    golferHistoryMock.mockResolvedValue(sampleHistory);
    renderWithProviders(
      <GolferHistoryModal seasonId="s-1" golferId="g-1" onClose={() => {}} />,
      { withLeagueSeasonProvider: false },
    );

    await screen.findByText('Sample Open');
    expectSharedColumnHeads(1);
  });
});
