/** Scopes a query to the page title's row, which inside `<main>` carries no `banner` role to find it by. */
export function pageTitleRow(inside: HTMLElement): HTMLElement {
  const header = inside.closest('header');
  if (!header) throw new Error('Expected the element inside the page title row, but no header encloses it.');
  return header;
}
