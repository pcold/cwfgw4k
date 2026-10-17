/** Scopes a query to the `<thead>` a column header sits in, which carries the head's type for every cell. */
export function columnHead(inside: HTMLElement): HTMLElement {
  const head = inside.closest('thead');
  if (!head) throw new Error('Expected the element inside a table head, but no thead encloses it.');
  return head;
}
