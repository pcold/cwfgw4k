import { describe, it, expect, beforeAll } from 'vitest';
import { ESLint, type Linter } from 'eslint';

/**
 * Runs the real `eslint.config.js` through ESLint's Node API: `no-restricted-syntax` replaces rather
 * than merges across flat-config blocks, so a block that drops a selector goes quiet with
 * `npm run lint` still green, and only a fixture notices.
 */

// Vite rewrites a literal `new URL(<path>, import.meta.url)` into an asset lookup that resolves to
// `undefined`; reading it into a binding first keeps it a plain URL. `node:url` is untyped here.
const SPEC_URL = import.meta.url;
const uiPath = (relativePath: string): string => decodeURIComponent(new URL(`../${relativePath}`, SPEC_URL).pathname);

// `projectService: true` loads the TypeScript program on the first lint; one instance pays it once.
const eslint = new ESLint({ cwd: uiPath('.') });

// The cold load runs to minutes on a CI runner shared with the backend suite.
const COLD_START_TIMEOUT_MS = 120_000;

const PRODUCTION_FILE = 'src/main.tsx';
const TEST_FILE = 'src/App.test.tsx';
const CLIENT_FILE = 'src/shared/api/client/core.ts';
const GOVERNED_FILES: readonly string[] = [PRODUCTION_FILE, TEST_FILE, CLIENT_FILE];

const BANNED_IMPORT_LINES: readonly string[] = [
  "import { debounce } from 'lodash';",
  "import { fireEvent } from '@testing-library/react';",
  "import { css } from '@emotion/css';",
  "import styles from './App.module.css';",
];

const ANGLE_BRACKET_CAST_LINE = "export const count = <number>JSON.parse('1');";

const PLAIN_CAST_LINE = "export const count = JSON.parse('1') as number;";
const DOM_NARROWING_LINE = "export const found = document.querySelector('input') as HTMLInputElement;";
const CAST_LINES: readonly string[] = [PLAIN_CAST_LINE, DOM_NARROWING_LINE];

const BOUNDARY_CAST_LINE = 'export function read<T>(parsed: unknown): T { return camelizeKeys(parsed) as T; }';
const CLIENT_BOUNDARY_LINES: readonly string[] = [
  'declare function camelizeKeys(value: unknown): unknown;',
  BOUNDARY_CAST_LINE,
];

const CONST_ASSERTION_LINE = "export const modes = ['live', 'final'] as const;";

const reportedLines = (
  messages: readonly Linter.LintMessage[],
  ruleId: string,
  lines: readonly string[],
): readonly (string | undefined)[] =>
  messages.filter((message) => message.ruleId === ruleId).map((message) => lines[message.line - 1]);

const lintFixture = async (lines: readonly string[], hostFile: string): Promise<readonly Linter.LintMessage[]> => {
  const [result, ...extra] = await eslint.lintText(lines.join('\n'), { filePath: uiPath(hostFile) });
  expect(extra).toEqual([]);
  if (result === undefined) {
    throw new Error(`ESLint returned no result for ${hostFile}`);
  }
  // A parse failure or a host file no block matches arrives with no `ruleId` and hides every real result.
  const beforeAnyRule = result.messages.filter((message) => message.ruleId === null);
  expect(beforeAnyRule.map((message) => message.message)).toEqual([]);
  return result.messages;
};

// A fixture stands in for a file that really exists: the project service cannot resolve a synthetic path.
beforeAll(async () => {
  for (const hostFile of GOVERNED_FILES) {
    await eslint.lintText('export {};', { filePath: uiPath(hostFile) });
  }
}, COLD_START_TIMEOUT_MS);

describe.each(GOVERNED_FILES)("eslint.config.js over '%s'", (hostFile) => {
  it('reports every banned import', async () => {
    const messages = await lintFixture(BANNED_IMPORT_LINES, hostFile);

    expect(reportedLines(messages, 'no-restricted-imports', BANNED_IMPORT_LINES)).toEqual(BANNED_IMPORT_LINES);
  });

  it('leaves `as const` alone', async () => {
    const messages = await lintFixture([CONST_ASSERTION_LINE], hostFile);

    expect(reportedLines(messages, 'no-restricted-syntax', [CONST_ASSERTION_LINE])).toEqual([]);
  });
});

describe('the `as` ban in eslint.config.js', () => {
  it.each([PRODUCTION_FILE, CLIENT_FILE])('reports every cast in %s', async (hostFile) => {
    const messages = await lintFixture(CAST_LINES, hostFile);

    expect(reportedLines(messages, 'no-restricted-syntax', CAST_LINES)).toEqual(CAST_LINES);
  });

  // A `.tsx` file cannot parse the angle-bracket form, so only the client hosts it.
  it('turns an angle-bracket assertion into the `as` the ban sees', async () => {
    const messages = await lintFixture([ANGLE_BRACKET_CAST_LINE], CLIENT_FILE);

    expect(reportedLines(messages, '@typescript-eslint/consistent-type-assertions', [ANGLE_BRACKET_CAST_LINE]))
      .toEqual([ANGLE_BRACKET_CAST_LINE]);
  });

  it('grants a test file DOM narrowing and nothing else', async () => {
    const messages = await lintFixture(CAST_LINES, TEST_FILE);

    expect(reportedLines(messages, 'no-restricted-syntax', CAST_LINES)).toEqual([PLAIN_CAST_LINE]);
  });

  it('grants the api client its `camelizeKeys(...) as T` boundary', async () => {
    const messages = await lintFixture(CLIENT_BOUNDARY_LINES, CLIENT_FILE);

    expect(reportedLines(messages, 'no-restricted-syntax', CLIENT_BOUNDARY_LINES)).toEqual([]);
  });

  it('keeps that boundary to the api client', async () => {
    const messages = await lintFixture(CLIENT_BOUNDARY_LINES, PRODUCTION_FILE);

    expect(reportedLines(messages, 'no-restricted-syntax', CLIENT_BOUNDARY_LINES)).toEqual([BOUNDARY_CAST_LINE]);
  });
});
