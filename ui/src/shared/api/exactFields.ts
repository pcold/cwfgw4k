/**
 * [K] is a Kotlin wire type's JSON keys. A verdict, not a failure: the generated file's index signature of
 * `true` turns any other verdict into a `tsc` error.
 * Key names only: `x?: string` and `x: string | null` are the same key to it.
 */
export type ExactFields<T, K extends string> = [T] extends [never]
  ? ['no such variant in types.ts']
  : [Exclude<WireKeys<T>, K> | Exclude<K, WireKeys<T>>] extends [never]
    ? true
    : [
        'not on the wire',
        Exclude<WireKeys<T>, K>,
        'absent from types.ts',
        Exclude<K, WireKeys<T>>,
      ];

/** [V] is a Kotlin enum's serial names. */
export type ExactValues<T extends string, V extends string> = [Exclude<T, V> | Exclude<V, T>] extends [never]
  ? true
  : ['not on the wire', Exclude<T, V>, 'absent from types.ts', Exclude<V, T>];

// Distributed so a union yields every arm's keys.
type WireKeys<T> = T extends unknown ? Extract<keyof T, string> : never;
