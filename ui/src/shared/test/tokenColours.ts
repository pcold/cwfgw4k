import tailwindTheme from 'tailwindcss/theme.css?raw';
import indexCss from '@/index.css?raw';

/** 8-bit sRGB, the space WCAG's relative luminance is defined over. */
export type Srgb = readonly [number, number, number];

const TOKEN_ALIAS_PATTERN = /--color-([\w-]+):\s*var\(--((?:color|brand)-[\w-]+)\)/g;
const RAMP_OKLCH_PATTERN = /--(color-[\w-]+):\s*oklch\(([\d.]+)%\s+([\d.]+)\s+([\d.]+)\)/g;
const RAMP_HEX_PATTERN = /--(color-[\w-]+):\s*#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})\b/g;
const BRAND_HEX_PATTERN = /--(brand-[\w-]+):\s*#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})\b/g;

// `noUncheckedIndexedAccess` types each group as possibly missing; `!` is banned and a `?? ''` would
// file a key nobody wrote, which resurfaces as `resolveToken` blaming the wrong token.
export const captured = (match: RegExpMatchArray, group: number): string => {
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

/** Token to the variable it aliases, prefix kept: `color-gray-900` or `brand-navy`. */
export const aliasOf: ReadonlyMap<string, string> = new Map(
  [...indexCss.matchAll(TOKEN_ALIAS_PATTERN)].map((match): [string, string] => [
    captured(match, 1),
    captured(match, 2),
  ]),
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

/**
 * Resolves the chain the browser does: `index.css` aliases each token to a ramp step, whose OKLCH value
 * `tailwindcss/theme.css` holds, or to a `--brand-*` hex it declares itself.
 */
export const resolveToken = (token: string): Srgb => {
  const variable = aliasOf.get(token);
  if (variable === undefined) {
    throw new Error(`index.css declares no --color-${token}, or aliases it to neither a ramp step nor a brand colour`);
  }
  const colour = paletteColours.get(variable);
  if (colour === undefined) {
    throw new Error(
      `Neither tailwindcss/theme.css nor index.css declares --${variable}, which --color-${token} aliases`,
    );
  }
  return colour;
};
