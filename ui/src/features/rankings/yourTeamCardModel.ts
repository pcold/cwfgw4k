import type { Rankings, StandingGap, TeamRanking, TeamStanding } from '@/shared/api/types';
import { isMyTeam } from '@/features/myTeam/myTeamModel';
import { formatWholeDollars } from '@/shared/util/money';

export interface YourTeam {
  readonly team: TeamRanking;
  readonly standing: TeamStanding;
}

export interface CardStat {
  readonly label: string;
  readonly value: string;
}

export function findYourTeam(rankings: Rankings, myTeamName: string | null): YourTeam | null {
  const team = rankings.teams.find((ranking) => isMyTeam(ranking.teamName, myTeamName));
  if (!team) return null;
  const standing = rankings.standings.find((candidate) => candidate.teamId === team.teamId);
  return standing ? { team, standing } : null;
}

export function ordinal(position: number): string {
  const lastTwo = position % 100;
  if (lastTwo >= 11 && lastTwo <= 13) return `${position}th`;
  switch (position % 10) {
    case 1:
      return `${position}st`;
    case 2:
      return `${position}nd`;
    case 3:
      return `${position}rd`;
    default:
      return `${position}th`;
  }
}

export function placeLabel(position: number, tied: boolean): string {
  return `${tied ? 'T' : ''}${ordinal(position)}`;
}

export function standingStats(standing: TeamStanding, teamCount: number): CardStat[] {
  switch (standing.type) {
    case 'leader':
      return [
        positionStat(1, standing.tied, teamCount),
        ...(standing.aheadOf ? [gapStat('Ahead of', standing.aheadOf)] : []),
      ];
    case 'chaser':
      return [
        positionStat(standing.position, standing.tied, teamCount),
        gapStat('Behind', standing.behind),
        { label: 'Behind leader', value: formatWholeDollars(standing.behindLeader) },
      ];
    default:
      return standing satisfies never;
  }
}

function positionStat(position: number, tied: boolean, teamCount: number): CardStat {
  return { label: 'Pos', value: `${placeLabel(position, tied)} / ${teamCount}` };
}

function gapStat(relation: string, gap: StandingGap): CardStat {
  return { label: `${relation} ${placeLabel(gap.position, gap.tied)}`, value: formatWholeDollars(gap.amount) };
}
