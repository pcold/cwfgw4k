import { describe, expect, it } from 'vitest';
import type { Rankings } from '@/shared/api/types';
import { nth } from '@/shared/test/nth';
import { buildChartLayout, DEFAULT_DIMENSIONS, teamColor, CHART_COLORS } from './rankingsChartModel';
import type { ChartDimensions, ChartLayout } from './rankingsChartModel';

function buildRankings(overrides: Partial<Rankings> = {}): Rankings {
  return {
    teams: [
      {
        teamId: 't-1',
        teamName: 'Aces',
        subtotal: 0,
        sideBets: 0,
        totalCash: 150,
        series: [50, 100, 150],
      },
      {
        teamId: 't-2',
        teamName: 'Birdies',
        subtotal: 0,
        sideBets: 0,
        totalCash: -35,
        series: [0, -20, -35],
      },
    ],
    weeks: ['9', '10', '11'],
    tournamentNames: ['Sample Open', 'Major', 'Invitational'],
    live: false,
    liveEligible: false,
    cashTotals: { won: 150, lost: -35, net: 115 },
    standings: [],
    ...overrides,
  };
}

function chartLayout(rankings: Rankings, dimensions?: ChartDimensions): ChartLayout {
  const layout = buildChartLayout(rankings, dimensions);
  if (!layout) throw new Error('buildChartLayout returned null for rankings that have weeks and teams');
  return layout;
}

describe('teamColor', () => {
  it('cycles through the palette', () => {
    expect(teamColor(0)).toBe(CHART_COLORS[0]);
    expect(teamColor(CHART_COLORS.length)).toBe(CHART_COLORS[0]);
    expect(teamColor(CHART_COLORS.length + 2)).toBe(CHART_COLORS[2]);
  });

  it('falls back to the first colour for an index the modulo cannot place in the palette', () => {
    expect(teamColor(-1)).toBe(CHART_COLORS[0]);
    expect(teamColor(0.5)).toBe(CHART_COLORS[0]);
  });
});

describe('buildChartLayout', () => {
  it('returns null when there are no weeks', () => {
    expect(buildChartLayout(buildRankings({ weeks: [] }))).toBeNull();
  });

  it('returns null when there are no teams', () => {
    expect(buildChartLayout(buildRankings({ teams: [] }))).toBeNull();
  });

  it('builds one line per team with a point per series value', () => {
    const layout = chartLayout(buildRankings());
    expect(layout.lines).toHaveLength(2);
    expect(nth(layout.lines, 0).teamName).toBe('Aces');
    expect(nth(layout.lines, 0).points).toHaveLength(3);
  });

  it('spaces x positions evenly across the plot width', () => {
    const layout = chartLayout(buildRankings());
    const { points } = nth(layout.lines, 0);
    const p0 = nth(points, 0);
    const p1 = nth(points, 1);
    const p2 = nth(points, 2);
    const step1 = p1.x - p0.x;
    const step2 = p2.x - p1.x;
    expect(step1).toBeCloseTo(step2, 5);
    expect(p0.x).toBe(layout.plot.left);
    expect(p2.x).toBe(layout.plot.right);
  });

  it('places the highest series value near the top of the plot', () => {
    const layout = chartLayout(buildRankings());
    const highest = nth(nth(layout.lines, 0).points, 2); // 150 -- max value
    const lowest = nth(nth(layout.lines, 1).points, 2); // -35 -- min value
    expect(highest.y).toBeLessThan(lowest.y);
    expect(highest.y).toBeGreaterThanOrEqual(layout.plot.top);
    expect(lowest.y).toBeLessThanOrEqual(layout.plot.bottom);
  });

  it('attaches week labels and tournament names to each point', () => {
    const layout = chartLayout(buildRankings());
    const { points } = nth(layout.lines, 0);
    expect(nth(points, 0).weekLabel).toBe('9');
    expect(nth(points, 0).tournamentName).toBe('Sample Open');
    expect(nth(points, 1).tournamentName).toBe('Major');
    expect(nth(points, 2).tournamentName).toBe('Invitational');
  });

  it('derives x-axis ticks with "Wk N" labels', () => {
    const layout = chartLayout(buildRankings());
    expect(layout.xTicks.map((t) => t.label)).toEqual(['Wk 9', 'Wk 10', 'Wk 11']);
  });

  it('generates at least one y-axis tick', () => {
    const layout = chartLayout(buildRankings());
    expect(layout.yTicks.length).toBeGreaterThan(0);
  });

  it('assigns distinct colors to consecutive teams', () => {
    const layout = chartLayout(buildRankings());
    expect(nth(layout.lines, 0).color).not.toBe(nth(layout.lines, 1).color);
  });

  it('respects custom dimensions', () => {
    const layout = chartLayout(buildRankings(), {
      ...DEFAULT_DIMENSIONS,
      width: 200,
      height: 100,
    });
    expect(layout.width).toBe(200);
    expect(layout.height).toBe(100);
  });
});
