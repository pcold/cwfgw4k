/**
 * Position only: `true` would stretch a row's text through a change of height. Motion measures the row only when
 * `index` changes, so content resizing above a list shifts its rows without a glide.
 */
export function glideAt(index: number): { readonly layout: 'position'; readonly layoutDependency: number } {
  return { layout: 'position', layoutDependency: index };
}
