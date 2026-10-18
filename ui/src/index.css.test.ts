import { describe, expect, it } from 'vitest';
import sairaWidthFaces from '@fontsource-variable/saira/wdth.css?raw';
import tailwindTheme from 'tailwindcss/theme.css?raw';
import { MONEY_FIGURE_CLASS } from '@/shared/components/MoneyCell';
import designMd from '../../DESIGN.md?raw';
import indexCss from './index.css?raw';

/**
 * Nothing else reads a colour token's value, so a shade that fails WCAG contrast passes typecheck, lint
 * and every component test. This resolves the chain the browser does: `index.css` aliases each token to
 * a ramp step, whose OKLCH value `tailwindcss/theme.css` holds, or to a `--brand-*` hex it declares itself.
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

// WCAG 2.2 §1.4.3. Its large-text floor is taken once, in LARGE_TEXT_PAIRS: the up money box keeps the brand green,
// 4.40:1 under white, so its figure must be bold and at least 14pt (18.66px). Every other pair clears 4.5.
const AA_NORMAL_TEXT = 4.5;
const AA_LARGE_TEXT = 3;
const LARGE_BOLD_TEXT_MIN_PX = (14 * 96) / 72;
const BOLD_WEIGHT = 700;
// WCAG 2.2 §1.4.11, for the line that bounds a control rather than a glyph.
const NON_TEXT_CONTRAST = 3;

// The weekly report's cash-total row.
const CASH_TOTAL_ROW: Wash = { token: 'gold-soft', percent: 30, over: 'canvas' };
// The rosters page's side-bet round rows, and a weekly-report round row hovered.
const SIDE_BET_ROUND_ROW: Wash = { token: 'surface-card', percent: 70, over: 'canvas' };
const REPORT_ROW_HOVERED: Wash = { token: 'surface-card', percent: 80, over: 'canvas' };

// Where each text token renders, from the call sites, inherited colour included. Hover fills are listed
// because the label stays put; disabled controls are not, since §1.4.3 exempts an inactive component.
// A wash between two backdrops an ink is listed on needs no row: on these near-neutral surfaces the
// blend's luminance lies between theirs, so it contrasts no less than the weaker of the two.
const TEXT_BACKDROPS: Readonly<Record<string, readonly Backdrop[]>> = {
  'ink-strong': ['canvas', 'surface-card'],
  ink: ['canvas', 'surface-card', 'surface-raised', 'surface-raised-hover', 'gold-tint', 'late-row-leader'],
  'ink-body': ['surface-card', 'surface-raised', 'side-bet-winner', SIDE_BET_ROUND_ROW, REPORT_ROW_HOVERED],
  'ink-secondary': ['canvas', 'surface-card', 'surface-raised'],
  'ink-label': ['canvas', 'surface-card', 'surface-raised'],
  'ink-muted': [
    'canvas',
    'surface-card',
    'late-row-leader',
    { token: 'surface-raised', percent: 50, over: 'surface-card' },
  ],
  'ink-soft': ['canvas', 'surface-card', 'surface-raised', 'side-bet-winner', CASH_TOTAL_ROW],
  'ink-faint': ['canvas', 'surface-card'],
  'on-chrome': ['chrome'],
  'on-chrome-muted': ['chrome'],
  'on-primary': ['primary', 'primary-hover'],
  'on-fill': [
    'semantic-danger-fill',
    'semantic-danger-fill-hover',
    'secondary',
    'secondary-hover',
    'position-fill',
    'header-band',
    'money-down-fill',
    'money-even-fill',
  ],
  'primary-ink': ['canvas', 'surface-card'],
  'semantic-success': [
    'canvas',
    'surface-card',
    'surface-raised',
    'side-bet-winner',
    CASH_TOTAL_ROW,
  ],
  'semantic-success-ink': [{ token: 'semantic-success-tint', percent: 30, over: 'canvas' }],
  'semantic-warning': ['canvas', 'surface-card'],
  'semantic-warning-ink': [
    { token: 'semantic-warning-tint', percent: 40, over: 'canvas' },
    { token: 'semantic-warning-tint', percent: 30, over: 'surface-card' },
    { token: 'semantic-warning-soft', percent: 50, over: 'canvas' },
  ],
  'semantic-danger': ['canvas', 'surface-card', 'surface-raised', CASH_TOTAL_ROW],
  'semantic-danger-hover': ['surface-card'],
  'semantic-danger-strong': ['surface-card'],
  'on-gold': ['gold', 'gold-bright'],
  'on-gold-earnings': ['gold-bright'],
};

// A field's border against its own fill and against each surface a field sits on: a white card, and the canvas
// under the league/season picker, where the fill matches the page and the border is the only boundary.
const BOUNDARY_BACKDROPS: Readonly<Record<string, readonly Backdrop[]>> = {
  'control-border': ['canvas', 'surface-card', 'surface-raised'],
};

// Names alone cannot say which tokens are text: `semantic-danger` is, `semantic-danger-fill` is not.
const productionSources = import.meta.glob<string>(['./**/*.{ts,tsx}', '!./**/*.test.{ts,tsx}'], {
  query: '?raw',
  import: 'default',
  eager: true,
});

const TOKEN_ALIAS_PATTERN = /--color-([\w-]+):\s*var\(--((?:color|brand)-[\w-]+)\)/g;
const RAMP_OKLCH_PATTERN = /--(color-[\w-]+):\s*oklch\(([\d.]+)%\s+([\d.]+)\s+([\d.]+)\)/g;
const RAMP_HEX_PATTERN = /--(color-[\w-]+):\s*#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})\b/g;
const BRAND_HEX_PATTERN = /--(brand-[\w-]+):\s*#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})\b/g;
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

// Token to the variable it aliases, prefix kept: `color-gray-900` or `brand-navy`.
const aliasOf: ReadonlyMap<string, string> = new Map(
  [...indexCss.matchAll(TOKEN_ALIAS_PATTERN)].map((match): [string, string] => [captured(match, 1), captured(match, 2)]),
);

const paletteColours = ((): ReadonlyMap<string, Srgb> => {
  const colours = new Map<string, Srgb>();
  for (const match of tailwindTheme.matchAll(RAMP_OKLCH_PATTERN)) {
    const lightness = Number(captured(match, 2)) / 100;
    colours.set(captured(match, 1), oklchToSrgb(lightness, Number(captured(match, 3)), Number(captured(match, 4))));
  }
  for (const match of [...tailwindTheme.matchAll(RAMP_HEX_PATTERN), ...indexCss.matchAll(BRAND_HEX_PATTERN)]) {
    colours.set(captured(match, 1), hexToSrgb(captured(match, 2)));
  }
  return colours;
})();

const resolveToken = (token: string): Srgb => {
  const variable = aliasOf.get(token);
  if (variable === undefined) {
    throw new Error(`index.css declares no --color-${token}, or aliases it to neither a ramp step nor a brand colour`);
  }
  const colour = paletteColours.get(variable);
  if (colour === undefined) {
    throw new Error(`Neither tailwindcss/theme.css nor index.css declares --${variable}, which --color-${token} aliases`);
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
      .filter((token) => aliasOf.has(token)),
  ),
);

interface ContrastPair {
  readonly foreground: string;
  readonly backdrop: Backdrop;
  readonly label: string;
}

const pairsOf = (table: Readonly<Record<string, readonly Backdrop[]>>): readonly ContrastPair[] =>
  Object.entries(table).flatMap(([foreground, backdrops]) =>
    backdrops.map((backdrop) => ({ foreground, backdrop, label: `${foreground} on ${describeBackdrop(backdrop)}` })),
  );

const measure = (pair: ContrastPair): number =>
  contrastRatio(resolveToken(pair.foreground), resolveBackdrop(pair.backdrop));

/** The class that sets the pair's type rides along, since only that type earns the lower floor. */
interface LargeTextPair extends ContrastPair {
  readonly typeClass: string;
}

const LARGE_TEXT_PAIRS: readonly LargeTextPair[] = [
  { foreground: 'on-fill', backdrop: 'money-up-fill', label: 'on-fill on money-up-fill', typeClass: MONEY_FIGURE_CLASS },
];

const themeScale = (pattern: RegExp, toValue: (raw: string) => number): ReadonlyMap<string, number> =>
  new Map(
    [...tailwindTheme.matchAll(pattern)].map((match): [string, number] => [captured(match, 1), toValue(captured(match, 2))]),
  );

const themeTextSizesPx = themeScale(/--text-([\w-]+):\s*([\d.]+)rem;/g, (rem) => Number(rem) * 16);
const themeFontWeights = themeScale(/--font-weight-([\w-]+):\s*(\d+);/g, Number);

// The largest value a utility in the class string sets on the theme's scale; 0 when none does.
const scaleValueIn = (typeClass: string, prefix: 'text-' | 'font-', scale: ReadonlyMap<string, number>): number =>
  Math.max(
    0,
    ...typeClass
      .split(/\s+/)
      .filter((utility) => utility.startsWith(prefix))
      .map((utility) => scale.get(utility.slice(prefix.length)) ?? 0),
  );

describe('the text tokens declared in index.css', () => {
  // Without this the table goes quiet rather than red: a new text colour would simply have no row.
  it('name the backdrops of every text colour a component sets', () => {
    expect([...textTokensInUse].sort()).toEqual(Object.keys(TEXT_BACKDROPS).sort());
  });

  it.each(pairsOf(TEXT_BACKDROPS))('clear AA for normal text where $label', (pair) => {
    expect(measure(pair)).toBeGreaterThanOrEqual(AA_NORMAL_TEXT);
  });
});

describe('the large-text carve-out', () => {
  it.each(LARGE_TEXT_PAIRS)('clears AA for large text where $label', (pair) => {
    expect(measure(pair)).toBeGreaterThanOrEqual(AA_LARGE_TEXT);
  });

  it.each(LARGE_TEXT_PAIRS)('sets bold type of at least 18.66px where $label', (pair) => {
    expect(scaleValueIn(pair.typeClass, 'font-', themeFontWeights)).toBeGreaterThanOrEqual(BOLD_WEIGHT);
    expect(scaleValueIn(pair.typeClass, 'text-', themeTextSizesPx)).toBeGreaterThanOrEqual(LARGE_BOLD_TEXT_MIN_PX);
  });

  // The size check reads unprefixed utilities only, so a `max-sm:text-sm` would shrink the figure past it.
  it.each(LARGE_TEXT_PAIRS)('sets one type at every breakpoint and state where $label', (pair) => {
    expect(pair.typeClass.split(/\s+/).filter((utility) => utility.includes(':'))).toEqual([]);
  });
});

describe('the control boundaries declared in index.css', () => {
  it.each(pairsOf(BOUNDARY_BACKDROPS))('clear 3:1 for a non-text boundary where $label', (pair) => {
    expect(measure(pair)).toBeGreaterThanOrEqual(NON_TEXT_CONTRAST);
  });
});

// A narrow read of the front matter's `colors:` block rather than a YAML parser, which would be a dependency.
const designColorEntries = ((): readonly { readonly token: string; readonly value: string }[] => {
  const block = designMd.match(/^colors:\n((?: {2}.*\n)+)/m);
  if (block === null) {
    throw new Error('DESIGN.md has no `colors:` block of two-space-indented entries');
  }
  return [...captured(block, 1).matchAll(/^ {2}([\w-]+):\s*(.*)$/gm)].map((match) => ({
    token: captured(match, 1),
    value: captured(match, 2),
  }));
})();

// Every declaration, not only ramp aliases, so a literal colour can't hide from the name check.
const declaredTokens: readonly string[] = [...indexCss.matchAll(/--color-([\w-]+):/g)].map((match) =>
  captured(match, 1),
);

const toHex = (colour: Srgb): string =>
  `#${colour.map((channel) => channel.toString(16).padStart(2, '0')).join('')}`;

describe("DESIGN.md's colors", () => {
  it('name exactly the tokens index.css declares', () => {
    expect(designColorEntries.map((entry) => entry.token).sort()).toEqual([...declaredTokens].sort());
  });

  it.each(designColorEntries)('give $token the hex its alias resolves to', (entry) => {
    expect(entry.value).toBe(`"${toHex(resolveToken(entry.token))}"`);
  });

  it('are the only colours its prose references', () => {
    const referenced = [...designMd.matchAll(/\{colors\.([\w-]+)\}/g)].map((match) => captured(match, 1));
    expect(referenced.filter((token) => !declaredTokens.includes(token))).toEqual([]);
  });
});

const fontStretchOf = (source: string, block: RegExp, where: string): string => {
  const match = source.match(block);
  if (match === null) {
    throw new Error(`${where} sets no font-stretch`);
  }
  return captured(match, 1);
};

describe("DESIGN.md's typography", () => {
  it('gives display the width the font-display utility sets', () => {
    expect(fontStretchOf(designMd, /^ {2}display:\n(?: {4}.*\n)*? {4}fontStretch:\s*(\S+)$/m, 'DESIGN.md display')).toBe(
      fontStretchOf(indexCss, /@utility font-display\s*\{[^}]*font-stretch:\s*([^;]+);/, 'index.css font-display'),
    );
  });
});

describe("index.css's sans font", () => {
  it('leads with the family the Saira faces main.tsx imports declare', () => {
    const declared = sairaWidthFaces.match(/font-family:\s*'([^']+)'/);
    const sans = indexCss.match(/--font-sans:\s*"([^"]+)"/);
    if (declared === null || sans === null) {
      throw new Error('Either the Saira faces or --font-sans names no family');
    }
    expect(captured(sans, 1)).toBe(captured(declared, 1));
  });
});
