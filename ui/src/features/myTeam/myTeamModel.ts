/**
 * Never asked is its own variant, not a missing team: "Just browsing" is an answer, and only `unasked` opens the
 * picker. A preference only: nothing may unlock on it, since anyone can pick any team.
 */
export type MyTeamChoice =
  | { readonly kind: 'team'; readonly teamName: string }
  | { readonly kind: 'browsing' }
  | { readonly kind: 'unasked' };

export const UNASKED: MyTeamChoice = { kind: 'unasked' };

/** Names are matched and saved in this form, so a link typed in lower case finds the team. */
export function normaliseTeamName(name: string): string {
  return name.trim().toUpperCase();
}

/** Case-insensitive; returns the normalised name, the form a choice is saved in. */
export function matchTeamName(name: string, teamNames: readonly string[]): string | null {
  const wanted = normaliseTeamName(name);
  return teamNames.some((teamName) => normaliseTeamName(teamName) === wanted) ? wanted : null;
}

function isRecord(value: unknown): value is Readonly<Record<string, unknown>> {
  return typeof value === 'object' && value !== null;
}

/** Anything unreadable is `unasked`: asking again is the only recovery a visitor has. */
export function parseMyTeam(stored: string | null): MyTeamChoice {
  if (stored === null) return UNASKED;
  let parsed: unknown;
  try {
    parsed = JSON.parse(stored);
  } catch {
    return UNASKED;
  }
  if (!isRecord(parsed)) return UNASKED;
  if (parsed.kind === 'browsing') return { kind: 'browsing' };
  if (parsed.kind === 'team' && typeof parsed.teamName === 'string' && parsed.teamName.trim() !== '') {
    return { kind: 'team', teamName: normaliseTeamName(parsed.teamName) };
  }
  return UNASKED;
}

/**
 * JSON with the kind spelled out, so a team named NONE never reads as browsing. Null removes the entry: `unasked`
 * is the absence of an answer.
 */
export function serialiseMyTeam(choice: MyTeamChoice): string | null {
  switch (choice.kind) {
    case 'team':
      return JSON.stringify({ kind: 'team', teamName: choice.teamName });
    case 'browsing':
      return JSON.stringify({ kind: 'browsing' });
    case 'unasked':
      return null;
    default:
      return choice satisfies never;
  }
}

/**
 * Checked against the newest season's teams, never the season being viewed, so browsing an old season keeps the
 * choice. Undefined names (still loading) and an empty list (before the draft) keep it too; a name missing from a
 * real list was renamed or left, and is asked again.
 */
export function validateMyTeam(choice: MyTeamChoice, newestSeasonTeamNames: readonly string[] | undefined): MyTeamChoice {
  if (choice.kind !== 'team' || newestSeasonTeamNames === undefined || newestSeasonTeamNames.length === 0) {
    return choice;
  }
  return matchTeamName(choice.teamName, newestSeasonTeamNames) === null ? UNASKED : choice;
}

export function sortTeamNames(teamNames: readonly string[]): string[] {
  return teamNames.map(normaliseTeamName).sort((left, right) => left.localeCompare(right));
}
