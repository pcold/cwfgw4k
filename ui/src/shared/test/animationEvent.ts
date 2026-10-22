/*
 * jsdom has no AnimationEvent, so React listens for `webkitAnimationEnd` instead of the `animationend` a browser
 * fires. React picks the name when react-dom loads, so this runs before setup.ts imports it.
 */
if (!('AnimationEvent' in window)) {
  class AnimationEvent extends Event {
    readonly animationName: string;
    readonly elapsedTime: number;
    readonly pseudoElement: string;

    constructor(type: string, init: AnimationEventInit = {}) {
      super(type, init);
      this.animationName = init.animationName ?? '';
      this.elapsedTime = init.elapsedTime ?? 0;
      this.pseudoElement = init.pseudoElement ?? '';
    }
  }
  Object.defineProperty(window, 'AnimationEvent', { value: AnimationEvent, writable: true, configurable: true });
}
