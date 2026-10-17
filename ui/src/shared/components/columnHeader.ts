/**
 * Goes on the `<thead>`, and every cell inherits it: a head `<tr>` or `<th>` adds alignment, padding and fill,
 * and ink only to sit on that fill, as the gold grids' `text-on-gold` does. No weight: a `<th>` is bold by the
 * browser's default, which a class on the `<thead>` cannot override. `{typography.column-header}` in DESIGN.md.
 */
export const COLUMN_HEADER_CLASS = 'font-display text-xs uppercase tracking-wider text-ink-label';
