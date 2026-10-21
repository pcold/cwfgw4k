import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import js from '@eslint/js';
import tseslint from 'typescript-eslint';
import react from 'eslint-plugin-react';
import reactHooks from 'eslint-plugin-react-hooks';
import jsxA11y from 'eslint-plugin-jsx-a11y';
import importX from 'eslint-plugin-import-x';
import vitest from '@vitest/eslint-plugin';
import testingLibrary from 'eslint-plugin-testing-library';
import globals from 'globals';

/**
 * Mechanizes MUST rules from ui/CLAUDE.md, as ktlint and detekt do for the backend. Wired into
 * `./gradlew check` through the `uiLint` Gradle task.
 */

// ui/CLAUDE.md → What We Do Not Use: banning the import turns "discuss before adding" into a red build.
const BANNED_IMPORT_PATTERNS = [
  'lodash',
  'lodash/*',
  'lodash-es',
  'lodash-es/*',
  'underscore',
  'ramda',
  'moment',
  'redux',
  'redux/*',
  '@reduxjs/toolkit',
  'react-redux',
  'zustand',
  'jotai',
  'recoil',
  'mobx',
  'mobx-react',
  'mobx-react-lite',
  'formik',
  'react-hook-form',
  'styled-components',
  '@emotion/*',
  '*.module.css',
];

// `satisfies` is a different node, so it never matches.
const AS_CAST_SELECTOR = 'TSAsExpression:not([typeAnnotation.typeName.name="const"])';

const AS_CAST_MESSAGE =
  'Avoid `as` casts (ui/CLAUDE.md). Use a type guard or restructure; `as const` and `satisfies` are allowed.';

// The one `as` ui/CLAUDE.md grants test files. A partial fixture or an `as never` mock return is
// still a cast, so the test block subtracts these from the ban rather than dropping it.
const DOM_NARROWING_TYPES = 'HTML[A-Za-z]*Element|SVG[A-Za-z]*Element|Element|Node|DocumentFragment|Document|Window';

const TEST_AS_CAST_MESSAGE =
  'Avoid `as` casts in tests too (ui/CLAUDE.md); DOM narrowing such as `as HTMLInputElement` is the ' +
  'only exception. Build a real fixture, or narrow the type the code under test asks for.';

const IMPORT_RESTRICTIONS = {
  paths: [
    {
      name: '@testing-library/react',
      importNames: ['fireEvent'],
      message: 'Use userEvent over fireEvent (ui/CLAUDE.md → Testing).',
    },
  ],
  patterns: [
    {
      group: BANNED_IMPORT_PATTERNS,
      message:
        'Excluded by ui/CLAUDE.md → What We Do Not Use. Discuss before adding; ' +
        'prefer Context + TanStack Query, Tailwind, Intl, or a small inline helper.',
    },
  ],
};

const TESTID_MESSAGE =
  'Query by accessible role/label/text, not data-testid (ui/CLAUDE.md → Testing). ' +
  'If there is genuinely no accessible selector, add an eslint-disable with a one-line reason.';

// The lookbehind guards the prefix position: it is what stops the `text-gray-500` inside `subtext-gray-500`.
const COLOR_UTILITY_PREFIXES =
  'bg|text|decoration|border(?:-[xytrbsel])?|divide|outline|ring(?:-offset)?|inset-ring|' +
  'shadow|inset-shadow|drop-shadow|text-shadow|fill|stroke|accent|caret|placeholder|from|via|to';

// Read from the theme that ships, so a ramp a Tailwind upgrade adds is banned with no edit here.
const tailwindTheme = readFileSync(createRequire(import.meta.url).resolve('tailwindcss/theme.css'), 'utf8');
const TAILWIND_COLOR_RAMPS = [...new Set([...tailwindTheme.matchAll(/--color-([a-z]+)-950:/g)].map(([, ramp]) => ramp))]
  .join('|');

const COLOR_SHADE = `(?:(?:${TAILWIND_COLOR_RAMPS})-(?:50|950|[1-9]00)|white|black)`;

const RAW_COLOR_PATTERN = `(?<![\\w-])(?:${COLOR_UTILITY_PREFIXES})-${COLOR_SHADE}(?![\\w-])`;

const COLOR_FUNCTION = '(?:rgba?|hsla?|hwb|oklch|oklab|lab|lch|color-mix|color)\\(';

// An arbitrary-value utility (`bg-[#1f2937]`, `text-(--color-gray-400)`), or a ramp or brand-palette variable
// anywhere: index.css aliases the role tokens to `--brand-*`, which is a palette, not a role.
const RAW_COLOR_VALUE_PATTERN =
  `(?:(?<![\\w-])(?:${COLOR_UTILITY_PREFIXES})-[\\[(](?:#|${COLOR_FUNCTION}|color:|var\\(--color-|--color-)` +
  `|--color-${COLOR_SHADE}(?![\\w-])|--brand-[a-z])`;

// Anchored to the whole string, so `'#root'` and prose that mentions `rgb(` stay out.
const COLOR_LITERAL_PATTERN = `^\\s*(?:#[0-9a-fA-F]{3,8}|${COLOR_FUNCTION}.*)\\s*$`;

const RAW_COLOR_MESSAGE =
  "Name the colour with a token from src/index.css's `@theme` block (ui/CLAUDE.md → Styling): " +
  '`text-ink-muted`, not `text-gray-400`. A shade no token names gets a token there first.';

const RAW_COLOR_VALUE_MESSAGE =
  "Name the colour with a token from src/index.css's `@theme` block (ui/CLAUDE.md → Styling): a utility " +
  'such as `fill-ink-muted`, or `var(--color-ink-muted)` where only a value fits, never a hex, a ramp variable ' +
  'or a `--brand-*` palette variable.';

// Every Literal and TemplateElement, not only `className`: hoisted class constants, the model helpers
// that return a class name, and SVG paint attributes hold colours too. The anchoring keeps prose out.
const PALETTE_RESTRICTIONS = [
  { selector: `Literal[value=/${RAW_COLOR_PATTERN}/]`, message: RAW_COLOR_MESSAGE },
  { selector: `TemplateElement[value.raw=/${RAW_COLOR_PATTERN}/]`, message: RAW_COLOR_MESSAGE },
  { selector: `Literal[value=/${RAW_COLOR_VALUE_PATTERN}/]`, message: RAW_COLOR_VALUE_MESSAGE },
  { selector: `TemplateElement[value.raw=/${RAW_COLOR_VALUE_PATTERN}/]`, message: RAW_COLOR_VALUE_MESSAGE },
  { selector: `Literal[value=/${COLOR_LITERAL_PATTERN}/]`, message: RAW_COLOR_VALUE_MESSAGE },
];

// A filled button's fill comes from `Button` alone, so a tone's fill or hover fill spelled elsewhere fails. Bare
// `bg-primary` stays open: the brand mark and the LIVE chip fill with the brand red without being buttons.
const FILLED_BUTTON_FILL_PATTERN =
  '(?<![\\w-])bg-(?:primary-hover|secondary(?:-hover)?|semantic-danger-fill(?:-hover)?)(?![\\w-])';

const FILLED_BUTTON_FILL_MESSAGE =
  'Render a filled button with `Button` from src/shared/components/Button.tsx, which owns every tone and size ' +
  '(DESIGN.md → Buttons), rather than spelling its fill.';

const FILLED_BUTTON_RESTRICTIONS = [
  { selector: `Literal[value=/${FILLED_BUTTON_FILL_PATTERN}/]`, message: FILLED_BUTTON_FILL_MESSAGE },
  { selector: `TemplateElement[value.raw=/${FILLED_BUTTON_FILL_PATTERN}/]`, message: FILLED_BUTTON_FILL_MESSAGE },
];

export default tseslint.config(
  { ignores: ['dist', 'coverage', 'test-report', 'node_modules'] },
  js.configs.recommended,
  {
    // Strings stay in scope: a long Tailwind class string is the line this catches, and it splits
    // as a hoisted const joined with `+`. URLs and regex literals have no split point.
    rules: {
      'max-len': ['error', { code: 140, ignoreUrls: true, ignoreRegExpLiterals: true }],
    },
  },
  {
    files: ['**/*.{ts,tsx}'],
    extends: [...tseslint.configs.recommendedTypeChecked],
    languageOptions: {
      ecmaVersion: 2023,
      sourceType: 'module',
      globals: { ...globals.browser },
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    plugins: {
      react,
      'react-hooks': reactHooks,
      'jsx-a11y': jsxA11y,
      'import-x': importX,
    },
    settings: { react: { version: 'detect' } },
    rules: {
      ...jsxA11y.flatConfigs.recommended.rules,
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'error',
      'react/no-array-index-key': 'error',
      'react/jsx-key': ['error', { checkFragmentShorthand: true }],
      '@typescript-eslint/explicit-module-boundary-types': 'error',
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/no-non-null-assertion': 'error',
      // The `as` ban below matches `as` only; this turns an angle-bracket assertion into one it sees.
      '@typescript-eslint/consistent-type-assertions': ['error', { assertionStyle: 'as' }],
      '@typescript-eslint/no-unused-vars': ['error', { ignoreRestSiblings: true }],
      '@typescript-eslint/consistent-type-imports': 'error',
      '@typescript-eslint/no-unnecessary-condition': 'error',
      eqeqeq: ['error', 'always'],
      'import-x/order': [
        'error',
        {
          groups: ['builtin', 'external', 'internal', ['parent', 'sibling', 'index']],
          pathGroups: [{ pattern: '@/**', group: 'internal', position: 'before' }],
          // Emptied so an `@/` import the resolver cannot classify still sorts as internal rather
          // than falling into the external bucket.
          pathGroupsExcludedImportTypes: [],
          'newlines-between': 'ignore',
        },
      ],
      'import-x/no-duplicates': 'error',
      'no-restricted-imports': ['error', IMPORT_RESTRICTIONS],
      'no-restricted-syntax': [
        'error',
        { selector: AS_CAST_SELECTOR, message: AS_CAST_MESSAGE },
        ...PALETTE_RESTRICTIONS,
        ...FILLED_BUTTON_RESTRICTIONS,
      ],
    },
  },
  {
    // The one file that spells the filled fills. The `as` ban and the palette are restated, as for tests.
    files: ['src/shared/components/Button.tsx'],
    rules: {
      'no-restricted-syntax': ['error', { selector: AS_CAST_SELECTOR, message: AS_CAST_MESSAGE }, ...PALETTE_RESTRICTIONS],
    },
  },
  {
    // `no-restricted-syntax` replaces rather than merges across blocks, so the `as` ban and the
    // palette are restated here or every test file loses them. `no-restricted-imports` is never
    // restated, so it carries over. The filled-button ban is left out: a test pins the fill it expects.
    files: ['**/*.test.{ts,tsx}'],
    plugins: { vitest, 'testing-library': testingLibrary },
    rules: {
      'vitest/no-focused-tests': 'error',
      // `--max-warnings 0` fails the gate on a warning anyway.
      'vitest/no-disabled-tests': 'error',
      'testing-library/prefer-screen-queries': 'error',
      'testing-library/no-debugging-utils': 'error',
      'no-restricted-syntax': [
        'error',
        {
          selector: `${AS_CAST_SELECTOR}:not([typeAnnotation.typeName.name=/^(?:${DOM_NARROWING_TYPES})$/])`,
          message: TEST_AS_CAST_MESSAGE,
        },
        { selector: 'MemberExpression[property.name=/ByTestId$/]', message: TESTID_MESSAGE },
        { selector: 'CallExpression[callee.name=/ByTestId$/]', message: TESTID_MESSAGE },
        ...PALETTE_RESTRICTIONS,
      ],
    },
  },
  {
    // Pinned rather than switched off for the file, which would also pass any later cast in the
    // module every endpoint call goes through.
    files: ['src/shared/api/client/core.ts'],
    rules: {
      'no-restricted-syntax': [
        'error',
        {
          selector:
            `${AS_CAST_SELECTOR}:not(FunctionDeclaration[id.name="decodeJson"] ` +
            'TSAsExpression[expression.name="parsed"][typeAnnotation.typeName.name="T"])',
          message:
            "The only `as` this file may use is decodeJson's `parsed as T` JSON boundary " +
            '(ui/CLAUDE.md). Any other cast belongs behind a type guard.',
        },
        ...PALETTE_RESTRICTIONS,
        ...FILLED_BUTTON_RESTRICTIONS,
      ],
    },
  },
);
