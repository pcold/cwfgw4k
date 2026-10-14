import type { ReactNode } from 'react';
import type { Rankings } from '@/shared/api/types';
import { formatMoney } from '@/shared/util/money';
import {
  buildChartLayout,
  DEFAULT_DIMENSIONS,
  type ChartLine,
  type ChartPoint,
} from './rankingsChartModel';

function polylinePath(points: ChartPoint[]): string {
  return points.map((p) => `${p.x},${p.y}`).join(' ');
}

interface LineProps {
  line: ChartLine;
}

function TeamLine({ line }: LineProps) {
  if (line.points.length === 0) return null;
  return (
    <g>
      <polyline
        fill="none"
        stroke={line.color}
        strokeWidth={2}
        points={polylinePath(line.points)}
      />
      {line.points.map((p) => (
        <circle
          key={`${line.teamId}-${p.x}`}
          cx={p.x}
          cy={p.y}
          r={3}
          fill={line.color}
        >
          <title>
            {`${line.teamName.toUpperCase()} · ${p.tournamentName ?? `Wk ${p.weekLabel}`}: ${formatMoney(p.value, 0)}`}
          </title>
        </circle>
      ))}
    </g>
  );
}

interface Props {
  rankings: Rankings;
}

function RankingsChart({ rankings }: Props): ReactNode {
  const layout = buildChartLayout(rankings, DEFAULT_DIMENSIONS);

  return (
    <div
      className="bg-surface-card rounded-lg p-4"
      aria-label="Cumulative Totals by Week"
      role="figure"
    >
      <h3 className="text-sm font-semibold text-ink-label uppercase tracking-wider mb-3">
        Cumulative Totals by Week
      </h3>

      {!layout ? (
        <p className="text-ink-soft text-sm">No ranking history yet.</p>
      ) : (
        <>
          <div className="w-full">
            <svg
              viewBox={`0 0 ${layout.width} ${layout.height}`}
              preserveAspectRatio="xMidYMid meet"
              className="w-full h-[220px] sm:h-[320px] lg:h-[400px]"
              role="img"
              aria-label="Team cumulative totals over time"
            >
              <line
                x1={layout.plot.left}
                x2={layout.plot.right}
                y1={layout.plot.bottom}
                y2={layout.plot.bottom}
                stroke="#374151"
              />
              <line
                x1={layout.plot.left}
                x2={layout.plot.left}
                y1={layout.plot.top}
                y2={layout.plot.bottom}
                stroke="#374151"
              />

              {layout.yTicks.map((tick) => (
                <g key={tick.position}>
                  <line
                    x1={layout.plot.left}
                    x2={layout.plot.right}
                    y1={tick.position}
                    y2={tick.position}
                    stroke="#374151"
                    strokeDasharray="2 4"
                  />
                  <text
                    x={layout.plot.left - 8}
                    y={tick.position + 4}
                    textAnchor="end"
                    fontSize={11}
                    fill="#9ca3af"
                  >
                    {tick.label}
                  </text>
                </g>
              ))}

              {layout.xTicks.map((tick) => (
                <text
                  key={tick.position}
                  x={tick.position}
                  y={layout.plot.bottom + 18}
                  textAnchor="middle"
                  fontSize={11}
                  fill="#9ca3af"
                >
                  {tick.label}
                </text>
              ))}

              {layout.lines.map((line) => (
                <TeamLine key={line.teamId} line={line} />
              ))}
            </svg>
          </div>

          <ul className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-ink-muted">
            {layout.lines.map((line) => (
              <li key={`legend-${line.teamId}`} className="flex items-center gap-1.5 uppercase">
                <svg width={12} height={2} aria-hidden="true">
                  <rect width={12} height={2} fill={line.color} />
                </svg>
                {line.teamName}
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}

export default RankingsChart;
