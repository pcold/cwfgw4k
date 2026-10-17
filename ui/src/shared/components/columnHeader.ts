/**
 * Goes on the `<thead>`, and every cell inherits it: a head `<tr>` or `<th>` carries alignment, padding and
 * fill, never type of its own. No weight: a `<th>` is bold by the browser's default, which a class on the
 * `<thead>` cannot override. `{typography.column-header}` in DESIGN.md.
 */
export const COLUMN_HEADER_CLASS = 'font-display text-xs uppercase tracking-wider text-ink-label';
