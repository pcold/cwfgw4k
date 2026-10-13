/** Fails naming the match count, rather than "undefined is not an Element" several lines later. */
export function nth<T>(matches: readonly T[], index: number): T {
  const match = matches[index];
  if (match === undefined) {
    throw new Error(`Expected a match at index ${index}, but the query returned only ${matches.length}.`);
  }
  return match;
}
