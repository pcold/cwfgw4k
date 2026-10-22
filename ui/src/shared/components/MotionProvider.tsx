import type { ReactNode } from 'react';
import { LazyMotion, MotionConfig, type FeatureBundle } from 'motion/react';

/**
 * Its own chunk: `domMax` is about 30 kB gzipped, and a glide only plays on a refresh, long after the page loads.
 * Until it arrives an `m.*` element renders plain.
 */
function loadFeatures(): Promise<FeatureBundle> {
  return import('./motionFeatures').then((module) => module.default);
}

/**
 * `strict` makes a `motion.*` component throw: use `m.*`, which carries only the features loaded here. Layout
 * animation needs `domMax`. `reducedMotion="user"` drops movement for a visitor who asks the OS for less.
 */
export default function MotionProvider({ children }: { children: ReactNode }): ReactNode {
  return (
    <LazyMotion features={loadFeatures} strict>
      <MotionConfig reducedMotion="user">{children}</MotionConfig>
    </LazyMotion>
  );
}
