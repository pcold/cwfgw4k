import { describe, expect, it } from 'vitest';
import tailwindTheme from 'tailwindcss/theme.css?raw';
import indexCss from './index.css?raw';

/**
 * Nothing else reads a colour token's value, so a shade that fails WCAG contrast passes typecheck, lint
 * and every component test. This resolves the chain the browser does: `index.css` aliases each token to
 * a ramp step, and `tailwindcss/theme.css` holds the OKLCH value that ships.
 */

/** 8-bit sRGB, the space WCAG's relative luminance is defined over. */
type Srgb = readonly [number, number, number];

/** A token used at a Tailwind opacity modifier (`bg-semantic-warning-tint/30`), composited over the surface below it. */
interface Wash {
  readonly token: string;
  readonly percent: number;
  readonly over: string;
}

type Backdrop = string | Wash;

// WCAG 2.2 §1.4.3. The large-text carve-out (3:1) is not taken: no more than a page heading could claim it.
const AA_NORMAL_TEXT = 4.5;

// The scoreboard's tinted team and leaderboard rows, and the weekly report's cash-total row.
const SUCCESS_ROW: Wash = { token: 'semantic-success-tint', percent: 10, over: 'canvas' };
const DANGER_ROW: Wash = { token: 'semantic-danger-tint', percent: 10, over: 'canvas' };
const CASH_TOTAL_ROW: Wash = { token: 'gold-soft', percent: 30, over: 'canvas' };

// Where each text token renders, from the call sites, inherited colour included. Hover fills are listed
// because the label stays put; disabled controls are not, since §1.4.3 exempts an inactive component.
// A wash of one listed backdrop over a darker one needs no row: light ink contrasts more on the blend.
const TEXT_BACKDROPS: Readonly<Record<string, readonly Backdrop[]>> = {
  'ink-strong': ['surface-card', 'surface-raised'],
  ink: ['canvas', 'surface-card', 'surface-raised', 'gold-tint', SUCCESS_ROW, DANGER_ROW],
  'ink-body': ['surface-card', 'surface-raised', 'side-bet-winner'],
  'ink-secondary': ['canvas', 'surface-card', 'surface-raised', SUCCESS_ROW, DANGER_ROW],
  'ink-label': ['surface-card', 'surface-raised'],
  'ink-muted': [
    'canvas',
    'surface-card',
    { token: 'surface-raised', percent: 50, over: 'surface-card' },
    SUCCESS_ROW,
    DANGER_ROW,
  ],
  'ink-soft': ['canvas', 'surface-card', 'surface-raised', 'side-bet-winner', SUCCESS_ROW, DANGER_ROW, CASH_TOTAL_ROW],
  'ink-faint': ['canvas', 'surface-card', SUCCESS_ROW, DANGER_ROW],
  'on-primary': ['primary', 'primary-hover'],
  'on-fill': [
    'semantic-danger-fill',
    'semantic-danger-fill-hover',
    'semantic-info-fill',
    'semantic-info-fill-hover',
    'secondary',
    'secondary-hover',
  ],
  'primary-ink': ['canvas', 'surface-card'],
  'semantic-success': [
    'canvas',
    'surface-card',
    'surface-raised',
    'side-bet-winner',
    SUCCESS_ROW,
    DANGER_ROW,
    CASH_TOTAL_ROW,
  ],
  'semantic-success-ink': [{ token: 'semantic-success-tint', percent: 30, over: 'canvas' }],
  'semantic-warning': ['canvas', 'surface-card'],
  'semantic-warning-ink': [
    { token: 'semantic-warning-tint', percent: 40, over: 'canvas' },
    { token: 'semantic-warning-tint', percent: 30, over: 'surface-card' },
    { token: 'semantic-warning-soft', percent: 50, over: 'canvas' },
  ],
  'semantic-danger': ['canvas', 'surface-card', 'surface-raised', DANGER_ROW, CASH_TOTAL_ROW],
  'semantic-danger-hover': ['surface-card'],
  'semantic-danger-strong': ['surface-card'],
  'semantic-info': ['surface-card'],
  'semantic-info-hover': ['surface-card'],
  'semantic-info-ink': ['semantic-info-soft'],
  'on-gold': ['gold', 'gold-bright'],
  'on-gold-earnings': ['gold-bright'],
};

// Pairs that measured below AA when the tokens were introduced. Each keeps its colour until a restyle
// decides it; the spec goes red once one clears, so the entry is deleted rather than left stale.
const BELOW_AA_AT_INTRODUCTION: ReadonlySet<string> = new Set([
  'ink-soft on canvas',
  'ink-soft on surface-card',
  'ink-soft on surface-raised',
  'ink-soft on side-bet-winner',
  'ink-soft on semantic-success-tint/10 over canvas',
  'ink-soft on semantic-danger-tint/10 over canvas',
  'ink-soft on gold-soft/30 over canvas',
  'ink-faint on canvas',
  'ink-faint on surface-card',
  'ink-faint on semantic-success-tint/10 over canvas',
  'ink-faint on semantic-danger-tint/10 over canvas',
  'semantic-danger on surface-raised',
  'on-primary on primary',
  'on-gold-earnings on gold-bright',
]);

// Names alone cannot say which tokens are text: `semantic-danger` is, `semantic-danger-fill` is not.
const productionSources = import.meta.glob<string>(['./**/*.{ts,tsx}', '!./**/*.test.{ts,tsx}'], {
  query: '?raw',
  import: 'default',
  eager: true,
});

const TOKEN_ALIAS_PATTERN = /--color-([\w-]+):\s*var\(--color-([\w-]+)\)/g;
const RAMP_OKLCH_PATTERN = /--color-([\w-]+):\s*oklch\(([\d.]+)%\s+([\d.]+)\s+([\d.]+)\)/g;
const RAMP_HEX_PATTERN = /--color-([\w-]+):\s*#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})\b/g;
const TEXT_CLASS_PATTERN = /(?<![\w-])((?:[\w-]+:)*)text-([\w-]+)/g;

// `noUncheckedIndexedAccess` types each group as possibly missing; `!` is banned and a `?? ''` would
// file a key nobody wrote, which resurfaces as `resolveToken` blaming the wrong token.
const captured = (match: RegExpMatchArray, group: number): string => {
  const value = match[group];
  if (value === undefined) {
    throw new Error(`Capture ${group} is missing from the match on: ${match[0]}`);
  }
  return value;
};

const hexToSrgb = (digits: string): Srgb => {
  const expanded = digits.length === 3 ? [...digits].map((digit) => digit + digit).join('') : digits;
  const channel = (offset: number): number => parseInt(expanded.slice(offset, offset + 2), 16);
  return [channel(0), channel(2), channel(4)];
};

const gammaEncode = (linear: number): number => {
  const encoded = linear <= 0.0031308 ? 12.92 * linear : 1.055 * linear ** (1 / 2.4) - 0.055;
  return Math.min(255, Math.max(0, Math.round(encoded * 255)));
};

// OKLab's inverse, then the sRGB transfer function; hand-rolled because the alternative is a colour library.
const oklchToSrgb = (lightness: number, chroma: number, hueDegrees: number): Srgb => {
  const hue = (hueDegrees * Math.PI) / 180;
  const a = chroma * Math.cos(hue);
  const b = chroma * Math.sin(hue);
  const long = (lightness + 0.3963377774 * a + 0.2158037573 * b) ** 3;
  const medium = (lightness - 0.1055613458 * a - 0.0638541728 * b) ** 3;
  const short = (lightness - 0.0894841775 * a - 1.291485548 * b) ** 3;
  return [
    gammaEncode(4.0767416621 * long - 3.3077115913 * medium + 0.2309699292 * short),
    gammaEncode(-1.2684380046 * long + 2.6097574011 * medium - 0.3413193965 * short),
    gammaEncode(-0.0041960863 * long - 0.7034186147 * medium + 1.707614701 * short),
  ];
};

const relativeLuminance = ([red, green, blue]: Srgb): number => {
  const linear = (channel: number): number => {
    const scaled = channel / 255;
    return scaled <= 0.03928 ? scaled / 12.92 : ((scaled + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue);
};

const contrastRatio = (foreground: Srgb, background: Srgb): number => {
  const ink = relativeLuminance(foreground);
  const surface = relativeLuminance(background);
  return (Math.max(ink, surface) + 0.05) / (Math.min(ink, surface) + 0.05);
};

const rampStepOf: ReadonlyMap<string, string> = new Map(
  [...indexCss.matchAll(TOKEN_ALIAS_PATTERN)].map((match): [string, string] => [captured(match, 1), captured(match, 2)]),
);

const rampColours = ((): ReadonlyMap<string, Srgb> => {
  const colours = new Map<string, Srgb>();
  for (const match of tailwindTheme.matchAll(RAMP_OKLCH_PATTERN)) {
    const lightness = Number(captured(match, 2)) / 100;
    colours.set(captured(match, 1), oklchToSrgb(lightness, Number(captured(match, 3)), Number(captured(match, 4))));
  }
  for (const match of tailwindTheme.matchAll(RAMP_HEX_PATTERN)) {
    colours.set(captured(match, 1), hexToSrgb(captured(match, 2)));
  }
  return colours;
})();

const resolveToken = (token: string): Srgb => {
  const rampStep = rampStepOf.get(token);
  if (rampStep === undefined) {
    throw new Error(`index.css declares no --color-${token}, or aliases it to something other than a ramp step`);
  }
  const colour = rampColours.get(rampStep);
  if (colour === undefined) {
    throw new Error(`tailwindcss/theme.css declares no --color-${rampStep}, which --color-${token} aliases`);
  }
  return colour;
};

// Alpha compositing happens on the gamma-encoded channels, which is what a browser paints.
const resolveBackdrop = (backdrop: Backdrop): Srgb => {
  if (typeof backdrop === 'string') {
    return resolveToken(backdrop);
  }
  const wash = resolveToken(backdrop.token);
  const under = resolveToken(backdrop.over);
  const alpha = backdrop.percent / 100;
  const blend = (channel: 0 | 1 | 2): number => Math.round(alpha * wash[channel] + (1 - alpha) * under[channel]);
  return [blend(0), blend(1), blend(2)];
};

const describeBackdrop = (backdrop: Backdrop): string =>
  typeof backdrop === 'string' ? backdrop : `${backdrop.token}/${backdrop.percent} over ${backdrop.over}`;

// A disabled control's label is exempt (§1.4.3), so a token set only there needs no row.
const textTokensInUse: ReadonlySet<string> = new Set(
  Object.values(productionSources).flatMap((source) =>
    [...source.matchAll(TEXT_CLASS_PATTERN)]
      .filter((match) => !captured(match, 0).includes('disabled:'))
      .map((match) => captured(match, 2))
      .filter((token) => rampStepOf.has(token)),
  ),
);

const declaredPairs: readonly { readonly ink: string; readonly backdrop: Backdrop; readonly label: string }[] =
  Object.entries(TEXT_BACKDROPS).flatMap(([ink, backdrops]) =>
    backdrops.map((backdrop) => ({ ink, backdrop, label: `${ink} on ${describeBackdrop(backdrop)}` })),
  );

const measure = (pair: (typeof declaredPairs)[number]): number =>
  contrastRatio(resolveToken(pair.ink), resolveBackdrop(pair.backdrop));

describe('the text tokens declared in index.css', () => {
  // Without this the table goes quiet rather than red: a new text colour would simply have no row.
  it('name the backdrops of every text colour a component sets', () => {
    expect([...textTokensInUse].sort()).toEqual(Object.keys(TEXT_BACKDROPS).sort());
  });

  it('mark only pairs the table declares as below AA', () => {
    const declaredLabels = new Set(declaredPairs.map((pair) => pair.label));
    expect([...BELOW_AA_AT_INTRODUCTION].filter((label) => !declaredLabels.has(label))).toEqual([]);
  });

  it.each(declaredPairs.filter((pair) => !BELOW_AA_AT_INTRODUCTION.has(pair.label)))(
    'clear AA for normal text where $label',
    (pair) => {
      expect(measure(pair)).toBeGreaterThanOrEqual(AA_NORMAL_TEXT);
    },
  );

  it.each(declaredPairs.filter((pair) => BELOW_AA_AT_INTRODUCTION.has(pair.label)))(
    'still fall short of AA where $label, a marked finding',
    (pair) => {
      expect(measure(pair)).toBeLessThan(AA_NORMAL_TEXT);
    },
  );
});
