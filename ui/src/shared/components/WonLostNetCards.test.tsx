import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import WonLostNetCards from './WonLostNetCards';

const NO_PROVIDERS = { withLeagueSeasonProvider: false };

describe('WonLostNetCards', () => {
  it('sets Won, Lost and Net in that order, each card with its own amount', () => {
    renderWithProviders(<WonLostNetCards won="+$9" lost={'−$2'} net="+$7" />, NO_PROVIDERS);

    const strip = screen.getByText('Won').parentElement?.parentElement;
    expect(Array.from(strip?.children ?? []).map((card) => card.textContent)).toEqual([
      'Won+$9',
      'Lost−$2',
      'Net+$7',
    ]);
  });
});
