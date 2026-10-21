/** @see SelectField */
export interface SelectOption {
  readonly value: string;
  readonly label: string;
}

/**
 * `text-base`, not the body's `text-sm`: iOS zooms the page into any control under 16px. The surface and
 * width are left to the caller, since a form field and a toolbar select differ in both.
 */
export const CONTROL_CLASS = 'border border-control-border rounded px-3 py-2 text-base';

/**
 * 44px, the smallest target a thumb reliably hits. A button or form control centres its content in the
 * height; a link needs `flex items-center` beside it, or its text sits at the top.
 */
export const TAP_TARGET_CLASS = 'min-h-11';

/**
 * Its `w-full` fills the field's box, so size a field through `width` rather than appending a width
 * here: two unprefixed widths on one control resolve by stylesheet order, not by class order.
 */
export const FIELD_CLASS = `bg-surface-raised ${CONTROL_CLASS} ${TAP_TARGET_CLASS} w-full`;
