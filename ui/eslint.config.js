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
 * Mechanizes the statically-checkable MUST rules of ui/CLAUDE.md, as ktlint and detekt do for the
 * backend. Wired into `./gradlew check` through the `uiLint` Gradle task.
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
  '@emotion/react',
  '@emotion/styled',
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
      'no-restricted-syntax': ['error', { selector: AS_CAST_SELECTOR, message: AS_CAST_MESSAGE }],
    },
  },
  {
    // `no-restricted-syntax` replaces rather than merges across blocks, so the `as` ban is restated
    // here or every test file loses it. `no-restricted-imports` is never restated, so it carries over.
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
      ],
    },
  },
  {
    // Pinned rather than switched off for the file, which would also pass any later cast in the
    // module every endpoint call goes through.
    files: ['src/shared/api/client.ts'],
    rules: {
      'no-restricted-syntax': [
        'error',
        {
          selector: `${AS_CAST_SELECTOR}:not([expression.callee.name="camelizeKeys"][typeAnnotation.typeName.name="T"])`,
          message:
            'The only `as` this file may use is the `camelizeKeys(parsed) as T` JSON boundary ' +
            '(ui/CLAUDE.md). Any other cast belongs behind a type guard.',
        },
      ],
    },
  },
);
