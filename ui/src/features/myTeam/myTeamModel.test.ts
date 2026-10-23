import { describe, expect, it } from 'vitest';
import {
  chosenTeamName,
  isMyTeam,
  matchTeamName,
  normaliseTeamName,
  parseMyTeam,
  serialiseMyTeam,
  sortTeamNames,
  validateMyTeam,
  type MyTeamChoice,
} from './myTeamModel';

const TEAMS = ['Womble', 'BLAU', 'Rose H2O'];

describe('parseMyTeam and serialiseMyTeam', () => {
  it.each<MyTeamChoice>([{ kind: 'team', teamName: 'WOMBLE' }, { kind: 'browsing' }, { kind: 'unasked' }])(
    'round-trips %o',
    (choice) => {
      expect(parseMyTeam(serialiseMyTeam(choice))).toEqual(choice);
    },
  );

  it('stores unasked as no entry at all', () => {
    expect(serialiseMyTeam({ kind: 'unasked' })).toBeNull();
  });

  it('keeps a team named NONE apart from browsing', () => {
    const none: MyTeamChoice = { kind: 'team', teamName: 'NONE' };

    expect(parseMyTeam(serialiseMyTeam(none))).toEqual(none);
    expect(serialiseMyTeam(none)).not.toEqual(serialiseMyTeam({ kind: 'browsing' }));
  });

  it.each([
    ['missing', null],
    ['not JSON', 'WOMBLE'],
    ['a bare sentinel', 'none'],
    ['JSON null', 'null'],
    ['a number', '42'],
    ['an unknown kind', '{"kind":"admin"}'],
    ['a team without a name', '{"kind":"team"}'],
    ['a team with a blank name', '{"kind":"team","teamName":"  "}'],
    ['a team whose name is not text', '{"kind":"team","teamName":7}'],
  ])('reads %s as unasked', (_case, stored) => {
    expect(parseMyTeam(stored)).toEqual({ kind: 'unasked' });
  });

  it('normalises a stored name on the way in', () => {
    expect(parseMyTeam('{"kind":"team","teamName":" womble "}')).toEqual({ kind: 'team', teamName: 'WOMBLE' });
  });
});

describe('normaliseTeamName', () => {
  it('trims and uppercases', () => {
    expect(normaliseTeamName('  Rose h2o ')).toBe('ROSE H2O');
  });
});

describe('matchTeamName', () => {
  it('matches whatever the case and surrounding space, answering in the saved form', () => {
    expect(matchTeamName(' womble', TEAMS)).toBe('WOMBLE');
    expect(matchTeamName('rose h2o', TEAMS)).toBe('ROSE H2O');
  });

  it('answers null for a name no team carries', () => {
    expect(matchTeamName('PAYRAY', TEAMS)).toBeNull();
  });
});

describe('validateMyTeam', () => {
  const womble: MyTeamChoice = { kind: 'team', teamName: 'WOMBLE' };

  it('keeps a team the newest season still has', () => {
    expect(validateMyTeam(womble, TEAMS)).toEqual(womble);
  });

  it('asks again for a team the newest season no longer has', () => {
    expect(validateMyTeam({ kind: 'team', teamName: 'PAYRAY' }, TEAMS)).toEqual({ kind: 'unasked' });
  });

  it('keeps the team while the names load', () => {
    expect(validateMyTeam(womble, undefined)).toEqual(womble);
  });

  it('keeps the team in a season with no teams yet', () => {
    expect(validateMyTeam(womble, [])).toEqual(womble);
  });

  it.each<MyTeamChoice>([{ kind: 'browsing' }, { kind: 'unasked' }])('leaves %o as it is', (choice) => {
    expect(validateMyTeam(choice, TEAMS)).toEqual(choice);
  });
});

describe('sortTeamNames', () => {
  it('sorts the normalised names alphabetically', () => {
    expect(sortTeamNames(TEAMS)).toEqual(['BLAU', 'ROSE H2O', 'WOMBLE']);
  });
});

describe('chosenTeamName', () => {
  it('names the team a team choice holds', () => {
    expect(chosenTeamName({ kind: 'team', teamName: 'WOMBLE' })).toBe('WOMBLE');
  });

  it.each<MyTeamChoice>([{ kind: 'browsing' }, { kind: 'unasked' }])('names no team for %o', (choice) => {
    expect(chosenTeamName(choice)).toBeNull();
  });
});

describe('isMyTeam', () => {
  it.each(['WOMBLE', 'Womble', ' womble '])('matches %j to WOMBLE, ignoring case and outer spaces', (teamName) => {
    expect(isMyTeam(teamName, 'WOMBLE')).toBe(true);
  });

  it('matches no other team', () => {
    expect(isMyTeam('BLAU', 'WOMBLE')).toBe(false);
  });

  it('matches nothing when no team is chosen', () => {
    expect(isMyTeam('WOMBLE', null)).toBe(false);
  });
});
