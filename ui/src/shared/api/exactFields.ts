/**
 * [K] is a Kotlin wire type's JSON keys, compared as `camelizeKeys` hands them to the UI. A verdict, not a
 * failure: the generated file's index signature of `true` turns any other verdict into a `tsc` error.
 * Key names only: `x?: string` and `x: string | null` are the same key to it.
 */
export type ExactFields<T, K extends string> = [T] extends [never]
  ? ['no such variant in types.ts']
  : [Exclude<WireKeys<T>, CamelizedKey<K>> | Exclude<CamelizedKey<K>, WireKeys<T>>] extends [never]
    ? true
    : [
        'not on the wire',
        Exclude<WireKeys<T>, CamelizedKey<K>>,
        'absent from types.ts',
        Exclude<CamelizedKey<K>, WireKeys<T>>,
      ];

/** [V] is a Kotlin enum's serial names, which reach the UI untouched: `camelizeKeys` renames keys only. */
export type ExactValues<T extends string, V extends string> = [Exclude<T, V> | Exclude<V, T>] extends [never]
  ? true
  : ['not on the wire', Exclude<T, V>, 'absent from types.ts', Exclude<V, T>];

/** `camelizeKeys`'s rename at the type level; `exactFields.test.ts` holds the two to one answer. */
export type CamelizedKey<S extends string> = S extends `${infer Head}_${infer Next}${infer Rest}`
  ? Next extends LowercaseOrDigit
    ? `${Head}${Uppercase<Next>}${CamelizedKey<Rest>}`
    : `${Head}_${CamelizedKey<`${Next}${Rest}`>}`
  : S;

type LowercaseOrDigit =
  | 'a' | 'b' | 'c' | 'd' | 'e' | 'f' | 'g' | 'h' | 'i' | 'j' | 'k' | 'l' | 'm'
  | 'n' | 'o' | 'p' | 'q' | 'r' | 's' | 't' | 'u' | 'v' | 'w' | 'x' | 'y' | 'z'
  | '0' | '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9';

// Distributed so a union yields every arm's keys.
type WireKeys<T> = T extends unknown ? Extract<keyof T, string> : never;
