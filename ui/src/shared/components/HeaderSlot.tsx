import { createContext, useContext, type ReactNode } from 'react';

// Undefined is the missing provider; null is a provider whose slot hasn't mounted, or a layout without one.
const HeaderSlotContext = createContext<HTMLElement | null | undefined>(undefined);

interface HeaderSlotProviderProps {
  readonly slot: HTMLElement | null;
  readonly children: ReactNode;
}

/**
 * The top nav's element a page portals a control into, so the page keeps the control's state rather than
 * pushing a node up into the app's.
 */
export function HeaderSlotProvider({ slot, children }: HeaderSlotProviderProps): ReactNode {
  return <HeaderSlotContext.Provider value={slot}>{children}</HeaderSlotContext.Provider>;
}

/** Null until the nav has mounted its slot; a portal into it then renders nothing. */
export function useHeaderSlot(): HTMLElement | null {
  const slot = useContext(HeaderSlotContext);
  if (slot === undefined) throw new Error('useHeaderSlot must be used inside a HeaderSlotProvider');
  return slot;
}
