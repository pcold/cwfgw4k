import type { Rankings, TeamRanking } from '@/api/types';

export const CHART_COLORS = [
  '#ef4444',
  '#f97316',
  '#eab308',
  '#22c55e',
  '#14b8a6',
  '#3b82f6',
  '#6366f1',
  '#a855f7',
  '#ec4899',
  '#f43f5e',
  '#84cc16',
  '#06b6d4',
  '#8b5cf6',
] as const;

export interface ChartDimensions {
  width: number;
  height: number;
  padTop: number;
  padRight: number;
  padBottom: number;
  padLeft: number;
}

export const DEFAULT_DIMENSIONS: ChartDimensions = {
  width: 800,
  height: 400,
  padTop: 16,
  padRight: 16,
  padBottom: 56,
  padLeft: 56,
};

export interface ChartPoint {
  x: number;
  y: number;
  value: number;
  weekLabel: string;
  tournamentName: string | null;
}

export interface ChartLine {
  teamId: string;
  teamName: string;
  color: string;
  points: ChartPoint[];
}

export interface AxisTick {
  position: number;
  label: string;
}

export interface ChartLayout {
  width: number;
  height: number;
  plot: { left: number; top: number; right: number; bottom: number };
  lines: ChartLine[];
  xTicks: AxisTick[];
  yTicks: AxisTick[];
  minValue: number;
  maxValue: number;
}

export function teamColor(index: number): string {
  return CHART_COLORS[index % CHART_COLORS.length];
}

function seriesValues(teams: TeamRanking[]): number[] {
  const values: number[] = [];
  for (const team of teams) {
    for (const v of team.series) values.push(v);
  }
  return values;
}

function niceRound(value: number): number {
  if (value === 0) return 0;
  const magnitude = Math.pow(10, Math.floor(Math.log10(Math.abs(value))));
  const normalized = value / magnitude;
  const rounded =
    normalized >= 5 ? 5 : normalized >= 2 ? 2 : normalized >= 1 ? 1 : 0.5;
  return rounded * magnitude * Math.sign(value);
}

function niceRangeStep(min: number, max: number, targetTicks: number): number {
  const rough = (max - min) / Math.max(1, targetTicks);
  return Math.max(1, niceRound(rough));
}

function buildYTicks(
  minValue: number,
  maxValue: number,
  plotTop: number,
  plotBottom: number,
): AxisTick[] {
  if (minValue === maxValue) {
    const y = (plotTop + plotBottom) / 2;
    return [{ position: y, label: formatAxisMoney(minValue) }];
  }
  const step = niceRangeStep(minValue, maxValue, 5);
  const start = Math.ceil(minValue / step) * step;
  const ticks: AxisTick[] = [];
  for (let v = start; v <= maxValue; v += step) {
    const ratio = (v - minValue) / (maxValue - minValue);
    const y = plotBottom - ratio * (plotBottom - plotTop);
    ticks.push({ position: y, label: formatAxisMoney(v) });
  }
  return ticks;
}

function formatAxisMoney(value: number): string {
  const abs = Math.abs(value);
  const sign = value < 0 ? '-' : '';
  if (abs >= 1000) return `${sign}$${Math.round(abs / 1000)}k`;
  return `${sign}$${Math.round(abs)}`;
}

export function buildChartLayout(
  rankings: Rankings,
  dimensions: ChartDimensions = DEFAULT_DIMENSIONS,
): ChartLayout | null {
  const weeks = rankings.weeks;
  if (weeks.length === 0 || rankings.teams.length === 0) return null;

  const plotLeft = dimensions.padLeft;
  const plotRight = dimensions.width - dimensions.padRight;
  const plotTop = dimensions.padTop;
  const plotBottom = dimensions.height - dimensions.padBottom;
  const plotWidth = plotRight - plotLeft;
  const plotHeight = plotBottom - plotTop;

  const allValues = seriesValues(rankings.teams);
  const rawMin = Math.min(0, ...allValues);
  const rawMax = Math.max(0, ...allValues);
  const pad = (rawMax - rawMin) * 0.05 || 1;
  const minValue = rawMin - pad;
  const maxValue = rawMax + pad;
  const span = maxValue - minValue || 1;

  const xStep = weeks.length > 1 ? plotWidth / (weeks.length - 1) : 0;

  const lines: ChartLine[] = rankings.teams.map((team, teamIndex) => {
    const points: ChartPoint[] = team.series.map((value, i) => {
      const x = weeks.length > 1 ? plotLeft + i * xStep : plotLeft + plotWidth / 2;
      const y = plotBottom - ((value - minValue) / span) * plotHeight;
      return {
        x,
        y,
        value,
        weekLabel: weeks[i] ?? '',
        tournamentName: rankings.tournamentNames[i] ?? null,
      };
    });
    return {
      teamId: team.teamId,
      teamName: team.teamName,
      color: teamColor(teamIndex),
      points,
    };
  });

  const xTicks: AxisTick[] = weeks.map((week, i) => ({
    position: weeks.length > 1 ? plotLeft + i * xStep : plotLeft + plotWidth / 2,
    label: `Wk ${week}`,
  }));

  const yTicks = buildYTicks(minValue, maxValue, plotTop, plotBottom);

  return {
    width: dimensions.width,
    height: dimensions.height,
    plot: { left: plotLeft, top: plotTop, right: plotRight, bottom: plotBottom },
    lines,
    xTicks,
    yTicks,
    minValue,
    maxValue,
  };
}
