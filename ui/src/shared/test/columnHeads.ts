import { expect } from 'vitest';
import { screen } from '@testing-library/react';
import { COLUMN_HEADER_CLASS } from '@/shared/components/columnHeader';

/**
 * Pins the `<thead>` above every column header on screen to the shared type. It counts the heads, so a
 * table that never rendered fails here rather than passing by absence.
 */
export function expectSharedColumnHeads(headCount: number): void {
  const heads = new Set(screen.getAllByRole('columnheader').map((header) => header.closest('thead')));
  expect(heads.size).toBe(headCount);
  for (const head of heads) {
    expect(head).toHaveClass(COLUMN_HEADER_CLASS);
  }
}
