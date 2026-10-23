/** @type {import('prettier').Config} */
export default {
  // Explicit, because Prettier reads the root .editorconfig and its indent_size = 4 would apply here.
  tabWidth: 2,
  useTabs: false,
  printWidth: 120,
  singleQuote: true,
  semi: true,
  trailingComma: 'all',
};
