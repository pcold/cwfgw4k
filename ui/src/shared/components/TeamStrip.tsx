import type { ReactElement } from 'react';
import ChipStrip from './ChipStrip';

/** @see TeamStrip */
export interface TeamStripTeam {
  readonly teamId: string;
  readonly teamName: string;
}

interface TeamStripProps {
  readonly teams: readonly TeamStripTeam[];
  readonly selectedTeamId: string;
  readonly onSelect: (teamId: string) => void;
}

/** @see ChipStrip */
export default function TeamStrip({ teams, selectedTeamId, onSelect }: TeamStripProps): ReactElement {
  const chips = teams.map((team) => ({ id: team.teamId, label: team.teamName }));
  return <ChipStrip groupLabel="Team" chips={chips} selectedId={selectedTeamId} onSelect={onSelect} />;
}
