---
version: alpha
name: CWFGW4K
description: "A light, table-first league tracker in the Tour Broadcast palette. A navy bar frames the page top and bottom, a sky canvas holds white cards and table chrome, with sky again for anything you can type into or that is selected, and navy carries the text. One red carries the brand: the brand red fills primary buttons and the slanted CASTLEWOOD FG block and the LIVE chip, and the brand navy fills every secondary button; page titles are navy, italic and condensed. A surface's bottom-line money reads by sign — green won, red lost, muted even — and the two spreadsheet-style grids (weekly report, rosters) head their columns in gold. Saira throughout, condensed to 75% for display type; small sizes, uppercase wide-tracked labels on table heads and card headings."

colors:
  canvas: "#e8eef7"
  surface-card: "#ffffff"
  surface-raised: "#e8eef7"
  surface-raised-hover: "#d5deeb"
  overlay: "#000000"
  chrome: "#0a1d38"
  on-chrome: "#ffffff"
  on-chrome-muted: "#cad5e2"
  hairline-soft: "#d5deeb"
  hairline: "#d5deeb"
  hairline-strong: "#90a1b9"
  control-border: "#62748e"
  ink-strong: "#0a1d38"
  ink: "#0a1d38"
  ink-body: "#0a1d38"
  ink-secondary: "#13305a"
  ink-label: "#4a5f80"
  ink-muted: "#4a5f80"
  ink-soft: "#4a5f80"
  ink-faint: "#4a5f80"
  primary: "#d7263d"
  primary-hover: "#c10007"
  on-primary: "#ffffff"
  primary-ink: "#c10007"
  primary-control: "#d7263d"
  on-fill: "#ffffff"
  secondary: "#0a1d38"
  secondary-hover: "#13305a"
  disabled: "#90a1b9"
  position-fill: "#0a1d38"
  header-band: "#0a1d38"
  semantic-success-tint: "#00c950"
  semantic-success-soft: "#0f8a4f"
  semantic-success: "#016630"
  semantic-success-ink: "#0d542b"
  semantic-warning-tint: "#fdc700"
  semantic-warning-soft: "#f0b100"
  semantic-warning: "#894b00"
  semantic-warning-ink: "#733e0a"
  semantic-warning-border: "#d08700"
  semantic-danger: "#c10007"
  semantic-danger-hover: "#9f0712"
  semantic-danger-strong: "#9f0712"
  semantic-danger-fill: "#c10007"
  semantic-danger-fill-hover: "#9f0712"
  semantic-danger-border: "#fb2c36"
  money-up-fill: "#0f8a4f"
  money-down-fill: "#c10007"
  money-even-fill: "#4a5f80"
  gold-tint: "#fff085"
  gold-soft: "#ffd23f"
  gold: "#ffd23f"
  gold-bright: "#fef9c2"
  on-gold: "#0a1d38"
  on-gold-earnings: "#016630"
  side-bet-winner: "#ffe2e2"
  late-row-leader: "#fef9c2"

typography:
  display:
    fontFamily: Saira
    fontStretch: 75%
  brand-mark:
    fontFamily: Saira
    fontStretch: 75%
    fontStyle: italic
    fontSize: 20px
    fontWeight: 800
    lineHeight: 1.40
    letterSpacing: 0.025em
    textTransform: uppercase
  page-title:
    fontFamily: Saira
    fontStretch: 75%
    fontStyle: italic
    fontSize: 24px
    fontWeight: 800
    lineHeight: 1.25
    letterSpacing: 0
    textTransform: uppercase
  modal-title:
    fontFamily: Saira
    fontStretch: 100%
    fontSize: 18px
    fontWeight: 700
    lineHeight: 1.56
    letterSpacing: 0
  card-heading:
    fontFamily: Saira
    fontStretch: 100%
    fontSize: 14px
    fontWeight: 600
    lineHeight: 1.43
    letterSpacing: 0.05em
    textTransform: uppercase
  column-header:
    fontFamily: Saira
    fontStretch: 75%
    fontSize: 12px
    fontWeight: 700
    lineHeight: 1.33
    letterSpacing: 0.05em
    textTransform: uppercase
  body:
    fontFamily: Saira
    fontStretch: 100%
    fontSize: 14px
    fontWeight: 400
    lineHeight: 1.43
    letterSpacing: 0
  control:
    fontFamily: Saira
    fontStretch: 100%
    fontSize: 16px
    fontWeight: 400
    lineHeight: 1.50
    letterSpacing: 0
  caption:
    fontFamily: Saira
    fontStretch: 100%
    fontSize: 12px
    fontWeight: 400
    lineHeight: 1.33
    letterSpacing: 0
  button:
    fontFamily: Saira
    fontStretch: 100%
    fontSize: 14px
    fontWeight: 500
    lineHeight: 1.43
    letterSpacing: 0
  button-compact:
    fontFamily: Saira
    fontStretch: 100%
    fontSize: 12px
    fontWeight: 500
    lineHeight: 1.33
    letterSpacing: 0
  report-figure:
    fontFamily: System Mono
    fontSize: 12px
    fontWeight: 400
    lineHeight: 1.33
    letterSpacing: 0
  card-row-name:
    fontFamily: Saira
    fontStretch: 75%
    fontSize: 18px
    fontWeight: 700
    lineHeight: 1.56
    letterSpacing: 0
    textTransform: uppercase
  position-figure:
    fontFamily: Saira
    fontStretch: 75%
    fontSize: 18px
    fontWeight: 700
    lineHeight: 1.56
    letterSpacing: 0
  money-figure:
    fontFamily: Saira
    fontStretch: 75%
    fontSize: 20px
    fontWeight: 700
    lineHeight: 1.40
    letterSpacing: 0

rounded:
  sm: 4px
  lg: 8px

elevation:
  flat: none
  modal: "0 20px 25px -5px rgb(0 0 0 / 0.1), 0 8px 10px -6px rgb(0 0 0 / 0.1)"

components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.button}"
    rounded: "{rounded.sm}"
    padding: 8px 16px
  button-secondary:
    backgroundColor: "{colors.secondary}"
    textColor: "{colors.on-fill}"
    typography: "{typography.button}"
    rounded: "{rounded.sm}"
    padding: 8px 16px
  button-danger:
    backgroundColor: "{colors.semantic-danger-fill}"
    textColor: "{colors.on-fill}"
    typography: "{typography.button}"
    rounded: "{rounded.sm}"
    padding: 8px 16px
  field:
    backgroundColor: "{colors.surface-raised}"
    borderColor: "{colors.control-border}"
    typography: "{typography.control}"
    rounded: "{rounded.sm}"
    padding: 8px 12px
  card:
    backgroundColor: "{colors.surface-card}"
    rounded: "{rounded.lg}"
    padding: 24px
  modal-panel:
    backgroundColor: "{colors.surface-card}"
    borderColor: "{colors.hairline-strong}"
    rounded: "{rounded.lg}"
    elevation: "{elevation.modal}"
  report-header-row:
    backgroundColor: "{colors.gold}"
    textColor: "{colors.on-gold}"
    borderColor: "{colors.hairline-strong}"
  report-cell-earned:
    backgroundColor: "{colors.gold-bright}"
    textColor: "{colors.on-gold}"
  report-cell-side-bet-winner:
    backgroundColor: "{colors.side-bet-winner}"
    textColor: "{colors.ink-body}"
  position-box:
    backgroundColor: "{colors.position-fill}"
    textColor: "{colors.on-fill}"
    typography: "{typography.position-figure}"
    rounded: "{rounded.sm}"
    padding: 8px 12px
  position-box-leader:
    backgroundColor: "{colors.gold}"
    textColor: "{colors.on-gold}"
    typography: "{typography.position-figure}"
    rounded: "{rounded.sm}"
    padding: 8px 12px
  money-box-up:
    backgroundColor: "{colors.money-up-fill}"
    textColor: "{colors.on-fill}"
    typography: "{typography.money-figure}"
    rounded: "{rounded.sm}"
    padding: 8px 12px
  money-box-down:
    backgroundColor: "{colors.money-down-fill}"
    textColor: "{colors.on-fill}"
    typography: "{typography.money-figure}"
    rounded: "{rounded.sm}"
    padding: 8px 12px
  money-box-even:
    backgroundColor: "{colors.money-even-fill}"
    textColor: "{colors.on-fill}"
    typography: "{typography.money-figure}"
    rounded: "{rounded.sm}"
    padding: 8px 12px
  header-band:
    backgroundColor: "{colors.header-band}"
    textColor: "{colors.on-fill}"
    typography: "{typography.card-heading}"
    padding: 10px 12px
---

## Overview

Castlewood Fantasy Golf is read almost entirely as tables: the week's scoreboard, the weekly report grid, team standings, player rankings, rosters. The design exists to make those tables legible and to make money read at a glance.

The palette is **Tour Broadcast**, and the app is **light only**. The page is `{colors.canvas}` (the brand sky), between a top nav and a footer on `{colors.chrome}` (the brand navy); cards are `{colors.surface-card}` (white); form controls and highlighted rows are `{colors.surface-raised}`, the canvas's sky again. No table head has a fill but the report grids' gold row. Text is the brand navy (`{colors.ink}`), with its muted slate blue (`{colors.ink-muted}`) for labels and captions.

The one brand colour is red: `{colors.primary}` (the brand red) fills primary buttons, the block behind the CASTLEWOOD FG mark and the LIVE chip. The brand red is 4.25:1 on the canvas, so it is a fill; type takes the darker step, `{colors.primary-ink}` (red-700). Page titles are navy, so a title never shares a hex with a loss.

Only the bottom line reads by sign: Total Cash on the standings and the weekly report, Weekly +/- on the scoreboard, the late row bets total, a golfer's season earnings, and the Won, Lost and Net chips. `{colors.semantic-success}` (green-800) is a gain, `{colors.semantic-danger}` (red-700) a loss, and `{colors.ink-soft}` an amount that prints as `$0`. The standings' Total Cash and the scoreboard's Weekly +/- are the exception in kind: a filled box (`MoneyCell`), `{colors.money-up-fill}` (the brand green), `{colors.money-down-fill}` (red-700) or `{colors.money-even-fill}` (the brand muted blue) under a white figure. Every other signed amount keeps its table's ink, so the colour points at the number that settles the row; the one exception is a payout in the report grid's pick cells, which takes the cell's state colour (see On Gold Earnings). `moneyTone` reads the sign from the formatted text, so a cents column and a whole-dollar column each tone at the precision they show. Among amounts, green means a gain and nothing else; the greens that are not money are a known gap (see Known gaps on the light canvas).

The weekly report and the rosters page are laid out like the league's spreadsheet, and they keep its gold: a `{colors.gold}` (the brand gold) header row, `{colors.gold-bright}` (yellow-100) cells for a pick that earned a payout, and a gold-washed TOTAL CASH row. Gold is a fill, never text: the brand gold is 1.44:1 on white.

**Key characteristics:**
- Light canvas, no dark mode. `index.css` sets `{colors.canvas}` and `{colors.ink}` on `body`.
- White on sky for depth: cards are white on the sky canvas, and raised controls are sky again inside them. Only the modal and the error screen cast a shadow.
- Small type: `text-sm` (14px) body, `text-xs` (12px) for helper text, table heads and the report grids. Every select and text input is the exception at `text-base` (16px), because iOS zooms the page into a smaller control. The roster file input stays `text-sm`: iOS opens a picker for it rather than zooming.
- Uppercase `tracking-wider` labels mark structure: nav links, table heads, card headings, the league/season picker's labels.
- Tabular numbers (`tabular-nums` or `font-mono`) on every money and count column. The Rules page's payout schedule does not have them yet.
- Corners are `rounded` (4px) on controls and chips, `rounded-lg` (8px) on cards and the modal. Nothing is pill-shaped.

## Colors

Every colour below is a `--color-<name>` token in the `@theme` block of `ui/src/index.css`, and every token there has an entry here; `ui/src/index.css.test.ts` fails when the two disagree, by name or by value. Each value is an alias: to a Tailwind ramp step, named after it, or to one of the brand hexes, which `index.css` declares as `--brand-*` in `:root` so Tailwind makes no utility of them. Components use the token (`bg-surface-card`), never the step (`bg-gray-800`) or the palette variable (`var(--brand-navy)`); the lint rejects both.

The contrast table in `ui/src/index.css.test.ts` pins every text token at WCAG AA (4.5:1) on each backdrop it sits on, with one exception: white on `{colors.money-up-fill}` is 4.40:1, so it is held to the large-text floor (3:1) instead, and the same file fails if `MONEY_FIGURE_CLASS` drops below bold 18.66px. The AA floor is why four inks share the brand muted blue (slate-500, the step that would set `{colors.ink-faint}` apart, is 4.08:1 on the canvas), and why status text takes a darker step than the brand red and green: each ink step holds a role, not a brightness rank. The same file pins `{colors.control-border}` at WCAG 1.4.11's 3:1 against a field's fill and each surface a field sits on.

### Surface
- **Canvas** (`{colors.canvas}`, the brand sky #e8eef7): the page background.
- **Surface Card** (`{colors.surface-card}`, white): every card, the money summary chips, the modal panel, the active nav link, the cells between the boxes of the standings' and scoreboard's card rows, the scoreboard's leaderboard card, the thin spacer rows between blocks of the weekly report grid. At `/60` it is the row hover on the player-rankings table.
- **Surface Raised** (`{colors.surface-raised}`, the brand sky): form fields, the neutral tag chips, the report grid's highlighted row. It is the canvas's colour, so it reads as raised only inside a white card.
- **Surface Raised Hover** (`{colors.surface-raised-hover}`, the brand line #d5deeb): the roster file input's button, hovered.
- **Overlay** (`{colors.overlay}`, black): the modal scrim, at `/60`.

### Chrome
- **Chrome** (`{colors.chrome}`, the brand navy): the bar behind the top nav and the footer, and the FINAL chip.
- **On Chrome** (`{colors.on-chrome}`, white): a nav or footer link, or the menu button, hovered; the FINAL chip's label.
- **On Chrome Muted** (`{colors.on-chrome-muted}`, slate-300): an idle nav or footer link, and the menu button.

These two inks are light, so they are for a `{colors.chrome}` fill alone; on the canvas or a card they fail the contrast floor.

### Lines
- **Hairline Soft** (`{colors.hairline-soft}`, the brand line): row dividers (`divide-y`) inside the scoreboard's leaderboard and the player-rankings table.
- **Hairline** (`{colors.hairline}`, the brand line): report-grid cell borders, the modal header rule, the rankings chart's gridlines (`stroke-hairline`).
- **Hairline Strong** (`{colors.hairline-strong}`, slate-400): borders on the modal panel and error card, on the report grids' header and highlighted rows, and on the outlined chip.
- **Control Border** (`{colors.control-border}`, slate-500): the border on every input and select, 4.76:1 on white and 4.08:1 on the canvas. Not the brand line the hairlines keep, which is 1.36:1 on white: a field on the canvas (the league/season picker) has no fill contrast, so its border is its only edge. The brand muted blue also clears 3:1 but draws a field as a heavy box.

### Text
- **Ink Strong** (`{colors.ink-strong}`, the brand navy #0a1d38): every page title, the UPCOMING chip's text and outline, the active nav link on its white block, the close button and a text button hovered, a competitor's name in the player-links panel, the counts on the roster upload. On a light canvas the darkest ink is the strongest, so it shares navy with `{colors.ink}`.
- **Ink** (`{colors.ink}`, the brand navy): the default text colour, set on `body` and the app root; team names in the scoreboard and standings tables, a player's name on the leaderboard, and the standings' Subtotal and Late Row Bets.
- **Ink Body** (`{colors.ink-body}`, the brand navy): golfer names inside the report and roster grid cells, and the neutral tag chip's text.
- **Ink Secondary** (`{colors.ink-secondary}`, the brand secondary navy #13305a): running text on the Rules page, golfer names and the Top 10 Earnings column on the scoreboard.
- **Ink Label** (`{colors.ink-label}`, the brand muted blue #4a5f80): uppercase card headings, every table head but the report grids' gold row, a column-filter select on hover or focus.
- **Ink Muted** (`{colors.ink-muted}`, the brand muted blue #4a5f80): helper and caption text — field labels, the subtitle under a page title, loading messages, chart axis labels (`fill-ink-muted`), the golfer line under a scoreboard team, the leaderboard's positions and round scores, the outlined chip's label — and every text button but a Remove link, which is `{colors.semantic-danger}`.
- **Ink Soft** (`{colors.ink-soft}`, the brand muted blue #4a5f80): rank numbers in player rankings, the labels on the money summary chips, empty-state lines, the picker's labels, a bottom-line amount that prints as `$0`, and in the report grid the dash for a pick that earned nothing and the top-ten line under SUBTOTAL.
- **Ink Faint** (`{colors.ink-faint}`, the brand muted blue #4a5f80): the em dash in an empty cell, the `/` between a shared player's owners on the rosters page.

### Brand
- **Primary** (`{colors.primary}`, the brand red #d7263d): the fill on every primary button — form submit, Create, Save, Finalize, Reload — the block behind the CASTLEWOOD FG mark, and the LIVE chip.
- **Primary Hover** (`{colors.primary-hover}`, red-700): that fill, hovered.
- **On Primary** (`{colors.on-primary}`, white): text on a primary fill, the CASTLEWOOD FG mark and the LIVE chip's label among it.
- **Primary Ink** (`{colors.primary-ink}`, red-700): a player name hovered in player rankings.
- **Primary Control** (`{colors.primary-control}`, the brand red): the checked colour of a native checkbox (`accent-primary-control`), today only the live-overlay toggle.

### Neutral fills
- **On Fill** (`{colors.on-fill}`, white): text on the secondary, danger, position, header-band and money fills.
- **Secondary** (`{colors.secondary}`, the brand navy): every secondary button — Download PDF, Log out, Manage player links, Save link, the roster upload's Match Players with ESPN and Back buttons, and Cancel in a form modal. There is one secondary fill: a muted one beside it would leave no rule for choosing between them.
- **Secondary Hover** (`{colors.secondary-hover}`, the brand secondary navy): that fill, hovered.
- **Disabled** (`{colors.disabled}`, slate-400): every filled button while disabled (`disabled:bg-disabled`). Its label is exempt from the contrast floor.
- **Position Fill** (`{colors.position-fill}`, the brand navy): the position box of every standings and scoreboard row but the first. Not `{colors.secondary}`, a button's fill, nor `{colors.chrome}`, the bar's; 16.86:1 under white.
- **Header Band** (`{colors.header-band}`, the brand navy): the band that opens a table card, today the scoreboard's leaderboard. Not `{colors.chrome}`, the bar's, nor `{colors.secondary}`, a button's; 16.86:1 under white.

### Semantic

Each family takes only the rungs it uses. `-tint` is a wash, always at an opacity (`/10` to `/40`); `-soft` is a banner border or a chip fill; the bare name is status text; `-ink` is text on that family's own wash; `-fill` is a solid button. A fill is chosen by its white label and the text by the canvas, so the two are separate tokens even where, as for danger, they land on one step. The brand green and red are fills: as text on the canvas they fall below AA, so a gain and a loss take green-800 and red-700.

- **Success** — `{colors.semantic-success-tint}` (green-500) washes the tournament-complete banner (`/30`) and nothing else; `{colors.semantic-success-soft}` (the brand green #0f8a4f) borders that banner; `{colors.semantic-success}` (green-800) is a bottom-line gain, a side-bet winner's payout in the report grid, the rule amounts on the Rules page and success messages; `{colors.semantic-success-ink}` (green-900) is the complete banner's text.
- **Warning** — `{colors.semantic-warning-tint}` (yellow-400) washes the in-progress banner (`/40`) and the skipped-rows and player-links notices (`/30`); `{colors.semantic-warning-soft}` (yellow-500) borders them; `{colors.semantic-warning}` (yellow-800) marks an ambiguous or skipped count and the multipliers on the Rules page; `{colors.semantic-warning-ink}` (yellow-900) is the text on those washes; `{colors.semantic-warning-border}` (yellow-600) borders the select for an ambiguous roster match.
- **Danger** — `{colors.semantic-danger}` (red-700) is a bottom-line loss, field errors and failed loads; `{colors.semantic-danger-hover}` (red-800) is a Remove link hovered; `{colors.semantic-danger-strong}` (red-800) is an action error beside the scoreboard's buttons and in the player-links panel; `{colors.semantic-danger-fill}` (red-700) and `{colors.semantic-danger-fill-hover}` (red-800) are the destructive admin buttons — Reset, Clean, Delete; `{colors.semantic-danger-border}` (red-500) borders an invalid payout-multiplier input. Danger is a darker red than `{colors.primary}`, which is the brand red and too light as text on the canvas.
- **Money** — the standings' Total Cash box and the scoreboard's Weekly +/- box, one fill per `moneyTone`: `{colors.money-up-fill}` (the brand green, 4.40:1 under white, so large text only), `{colors.money-down-fill}` (red-700, 6.42:1) and `{colors.money-even-fill}` (the brand muted blue, 6.48:1). Not `{colors.semantic-danger-fill}` on the same step: that is the destructive button's fill, and `FILLED_BUTTON_RESTRICTIONS` keeps it in `Button.tsx`.

### Report grids
- **Gold** (`{colors.gold}`, the brand gold #ffd23f): the header row of the weekly report and roster grids, including its sticky first cell; at `/50` the cell borders of the TOTAL CASH row; the position box of the standings' and scoreboard's first row.
- **Gold Bright** (`{colors.gold-bright}`, yellow-100): a weekly-report cell whose pick earned a payout.
- **Gold Soft** (`{colors.gold-soft}`, the brand gold): the TOTAL CASH row's wash, at `/30` over the canvas.
- **Gold Tint** (`{colors.gold-tint}`, yellow-200): the TOTAL CASH row's label cell.
- **On Gold** (`{colors.on-gold}`, the brand navy): header text on `{colors.gold}`, the leader's position number, and golfer names on `{colors.gold-bright}`.
- **On Gold Earnings** (`{colors.on-gold-earnings}`, green-800): the payout amount inside a `{colors.gold-bright}` cell.
- **Side Bet Winner** (`{colors.side-bet-winner}`, red-100): the weekly-report cell of the pick that won that round's side bet. It overrides gold-bright; its text drops back to `{colors.ink-body}`.

### Known gaps on the light canvas
Tokens only re-colour what the dark theme drew; the component treatment has restyled the app chrome (CWF-231) and begun on the tables (CWF-232) with the standings' and scoreboard's card rows, and these are the gaps it has to close.
- **Red is both brand and loss.** Primary buttons and the LIVE chip are the brand red; destructive buttons and losses are red-700, and so are the standings' and scoreboard's loss boxes, never the brand red. Beside a loss, a primary button can still read as a warning.
- **Green outside money.** `{colors.semantic-success}` still colours success messages, exact roster matches, the Rules page's amounts and the complete banner. Green is meant for gains alone.

### Outside the token set
- **Rankings chart lines** — `CHART_COLORS` in `ui/src/features/rankings/rankingsChartModel.ts` is a 13-hex series palette, one per team. It identifies a series rather than holding a role, so it carries a lint waiver instead of tokens. It was picked against the old dark canvas and has not been re-checked on white.
- **PDF export** — `ui/src/features/weeklyReport/weeklyReportPdf.ts` prints a light-background report with its own RGB constants, not yet re-checked against the Tour Broadcast palette. Paper is not the screen; nothing here applies to it.

## Typography

### Font family
- **Saira** — the brand face, self-hosted from `@fontsource-variable/saira` (width and weight axes, upright and italic) through the imports in `ui/src/main.tsx`. `--font-sans` in `ui/src/index.css` leads with it, so Tailwind's preflight sets it on `html` and every text style inherits it at 100% width. Behind it come `Saira Fallback`, Arial scaled to Saira's metrics so text holds its place while the webfont swaps in, then the system stack.
- **Display** — the `font-display` utility in `ui/src/index.css` sets `font-stretch: 75%`, the condensed width for the nav, titles, team names and numbers. Today the brand mark, the nav links, the footer link, page titles, the status chip, table heads, and the standings' and scoreboard's team names and position and money boxes use it. A fallback face has no width axis and sets it at its own width.
- **System Mono** — Tailwind's `font-mono`. The weekly report grid's money figures, late row bets' golfer names and amounts, the golfer-history modal's place and money columns, and the ESPN week and event names on the schedule import.

### Hierarchy

| Token | Size | Weight | Tracking | Classes | Where |
|---|---|---|---|---|---|
| `{typography.display}` | — | — | — | `font-display` | The brand mark, nav links, the footer link, page titles, the status chip, table heads, the standings' and scoreboard's team names and position and money boxes |
| `{typography.brand-mark}` | 20px | 800 | 0.025em, italic, uppercase | `font-display italic font-extrabold text-xl uppercase tracking-wide` | The CASTLEWOOD FG mark in the nav |
| `{typography.page-title}` | 24px, 30px from `sm:` | 800 | 0, italic, uppercase | `font-display italic font-extrabold uppercase text-2xl sm:text-3xl leading-tight text-ink-strong` | The `h2` of every page, in `PageTitle` |
| `{typography.modal-title}` | 18px | 700 | 0 | `text-lg font-bold` | Modal header |
| `{typography.card-heading}` | 14px | 600 | 0.05em, uppercase | `text-sm font-semibold uppercase tracking-wider text-ink-label` | Heading inside a card |
| `{typography.column-header}` | 12px | 700, a `th`'s default | 0.05em, uppercase | `font-display text-xs uppercase tracking-wider text-ink-label` | `COLUMN_HEADER_CLASS` in `columnHeader.ts`, on the `<thead>` of every table: the scoreboard and its leaderboard, standings, players, late row bets, the Rules payouts, the report and roster grids, the schedule import, golfer history |
| `{typography.body}` | 14px | 400 | 0 | `text-sm` | Tables, running text |
| `{typography.control}` | 16px | 400 | 0 | `text-base` | Form fields, toolbar selects, the league/season selects, the column-header filter |
| `{typography.caption}` | 12px | 400 | 0 | `text-xs` | Field labels, helper text, field errors |
| `{typography.button}` | 14px | 500 | 0 | `text-sm font-medium` | `Button`'s `regular` size |
| `{typography.button-compact}` | 12px | 500 | 0 | `text-xs font-medium` | `Button`'s `compact` size: the scoreboard's tournament header, a panel row, a table row |
| `{typography.report-figure}` | 12px | 400 | 0 | `font-mono` in a `text-xs` table | Money in the weekly report grid |
| `{typography.card-row-name}` | 18px | 700 | 0, uppercase | `font-display font-bold text-lg uppercase text-ink` | A team name in a standings or scoreboard card row |
| `{typography.position-figure}` | 18px | 700 | 0 | `font-display font-bold text-lg tabular-nums` | `PositionCell` |
| `{typography.money-figure}` | 20px | 700 | 0 | `MONEY_FIGURE_CLASS`: `font-display font-bold text-xl tabular-nums` | `MoneyCell`; bold and at least 18.66px, or white on the up fill fails contrast |

### Principles
- **Weights in use are 400, 500, 600, 700 and 800.** `font-semibold` is the emphasis weight in tables (money chips, the leaderboard's Score and Tot) and the nav links' weight; `font-bold` marks modal titles, the status chip, the report grids' total rows, the standings' and scoreboard's team names and boxes, and table heads take it as a `th`'s default; `font-extrabold` is the brand mark's and the page titles'.
- **Uppercase is structure, not emphasis.** Nav links, page titles, table heads, card headings and team names in tables are uppercase; body text never is.
- **Numbers line up.** A column of money uses `tabular-nums` (list tables) or `font-mono` (the weekly report grid, late row bets), right-aligned in list tables and centred in the grid.

## Layout

### Spacing
- Tailwind's default 4px scale; there are no spacing tokens.
- **Page**: `<main>` is `max-w-screen-2xl mx-auto px-3 sm:px-4 py-4 sm:py-6`. The nav and the footer centre their contents in the same `max-w-screen-2xl px-3 sm:px-4`, so every band's edges line up with the page's.
- **Page body**: a stack spaced `space-y-6` or `space-y-4`, opening with `PageTitle`.
- **Cards**: `p-6` for form and rules cards, `p-4` for the rankings chart and late-row-bet tables.
- **List tables**: cells `px-3 py-2` (scoreboard teams, standings, player rankings) or `px-2 py-2` with `px-3` at the outer edges (the leaderboard, whose nine columns fit half the page from `xl`); card rows are `h-11` and `border-spacing-y-1` apart.
- **Report grids**: cells `px-1 py-1` to `px-2 py-1.5`, `border-collapse`, every cell bordered. They are the densest surface in the app on purpose: one column per team must fit.
- **Forms**: fields in a `flex flex-wrap items-end gap-4` row inside a card; a form modal stacks them with `space-y-3`.

### Containers
- List tables and report grids sit in `overflow-x-auto -mx-3 sm:mx-0`, so on a phone they scroll sideways edge to edge. A table card's table scrolls inside the card, `overflow-x-auto` alone, since a bleed would cut the card's rounded edge. The scoreboard's two tables also set a floor (`min-w-[500px]` teams, `min-w-[480px]` leaderboard) so their columns don't crush first; at a 1280px viewport each column is wider than its floor, so neither scrolls.
- From `xl` the scoreboard sets its teams and the leaderboard side by side, `grid-cols-[minmax(0,5fr)_minmax(0,4fr)]` at `gap-6`, the money summary chips under the teams; the split gives a two-golfer line room in its team cell. Below `xl` the two stack in a `grid-cols-1`, since an implicit track grows to a table's floor and scrolls the page on a phone.
- The report grids pin the first column (`sticky left-0`) so the round label stays visible while the teams scroll.

## Elevation & Depth

| Level | Treatment | Use |
|---|---|---|
| 0 | `{colors.canvas}` | The page |
| 1 | `{colors.surface-card}`, no border, no shadow | Cards, summary chips, the standings' and scoreboard's card rows, the report grid's spacer rows |
| 2 | `{colors.surface-raised}` | Fields, neutral chips, the report's highlighted row |
| 3 | `{colors.surface-card}` + `{colors.hairline-strong}` border + `{elevation.modal}` (`shadow-xl`) over `{colors.overlay}`/60 | The modal panel, and the error screen's card |

White cards on the sky canvas carry depth without shadows, and a raised control is sky again inside its white card. The `{colors.chrome}` nav and footer frame the levels rather than sit on one. `shadow-xl` appears only on the two floating panels.

## Shapes

| Token | Value | Class | Use |
|---|---|---|---|
| `{rounded.sm}` | 4px | `rounded` | Buttons, fields, chips, banners, nav items |
| `{rounded.lg}` | 8px | `rounded-lg` | Cards, the modal panel, the error card |

Table cells are square, but for the outer corners of a card row, which its position and money boxes round (`rounded-l`, `rounded-r`). No component is pill-shaped (`rounded-full`), and no corner is larger than 8px.

## Components

### Top nav — `ui/src/App.tsx`
A `{colors.chrome}` bar, `min-h-14`, with no border. On the left, the brand mark: an `h1` reading CASTLEWOOD FG in `{typography.brand-mark}` and `{colors.on-primary}`, on a `{colors.primary}` block slanted with `-skew-x-12` the bar's full height; the text inside is skewed back (`skew-x-12`), since the italic face already slants it. At `lg:` and up, a row of links on the right.
- Every nav label is `font-display font-semibold uppercase tracking-wider`, idle `text-on-chrome-muted hover:text-on-chrome`; the current page's link is a `bg-surface-card text-ink-strong` block and carries `aria-current="page"`.
- Desktop link: `inline-flex items-center` plus `TAP_TARGET_CLASS`, `px-3 rounded text-sm`.
- Below `lg:`, a 44px (`w-11 h-11`) menu button in the idle ink opens a stacked list inside the same bar; its links are `px-4 py-3 rounded text-base`, and clear 44px on that padding (48px) without `TAP_TARGET_CLASS`.

### Footer — `ui/src/App.tsx`
A `{colors.chrome}` bar matching the nav, `py-1.5`, holding the Admin link alone at the right: the nav's label and idle ink, `inline-flex items-center` plus `TAP_TARGET_CLASS`, `px-3 rounded text-sm`.

### Page title — `ui/src/shared/components/PageTitle.tsx`
Every page opens with one: a `<header>` holding the page's only `h2` in `{typography.page-title}` (the `h1` is the brand mark), an optional `text-sm text-ink-muted` subtitle under it, and on the right an `actions` slot. It is `flex flex-wrap items-center justify-between`; below `sm:` the actions take a line of their own under the title. A Page renders it outside its data's loading gates, so the title holds still while the data loads or the season changes; Admin, which has no title until the session check confirms a login, is the exception. The title is static text; what varies — a tournament's name, its week, a count — goes in the subtitle.

The actions slot holds the status chip on a page whose response carries one (Scoreboard, Weekly Report, Standings, Players, Late Row Bets), then the league/season picker; Admin puts its Log out button there instead. No rule sits under the header.

### League / season picker — `ui/src/features/leagues/LeagueSeasonPicker.tsx`
Lives in the page title's actions, not in a bar of its own: a page that reads the selected season passes `<LeagueSeasonPicker />`, and a page that doesn't (Admin) shows none. Two labelled selects in a `flex flex-wrap` row; labels are `text-xs text-ink-soft font-medium uppercase tracking-wider`, selects `bg-surface-raised border border-control-border rounded text-base` with `TAP_TARGET_CLASS`.

### Status chip — `ui/src/shared/components/StatusChip.tsx`
`statusChipVariant` picks the chip from a response's `live` flag and its tournament's `status`, in this order:

| Response | Chip | Classes |
|---|---|---|
| `live` is true | LIVE | `bg-primary text-on-primary` |
| tournament status `completed` | FINAL | `bg-chrome text-on-chrome` |
| tournament status `upcoming` | UPCOMING | `border-ink-strong text-ink-strong`, no fill |
| anything else, or not loaded | none | — |

`live` is true only when ESPN answered the overlay, so an in-progress tournament read from stored scores shows no chip, and LIVE wins over the stored status. Every chip is `rounded border px-2.5 py-1 font-display font-bold text-xs uppercase tracking-wider`.

### Card — admin sections, `RulesPage.tsx`, `RankingsChart.tsx`, `LateRowBetsView.tsx`
`bg-surface-card rounded-lg p-6` (`p-4` for the chart and late-row-bet tables), no border, no shadow. A card opens with a `{typography.card-heading}` (`mb-4`, or `mb-3` over a table or chart).

A table card — today the scoreboard's leaderboard — has no padding of its own and opens instead with a `{colors.header-band}` band, `px-3 py-2.5 rounded-t-lg`, under `{colors.on-fill}`: the `h3` on the left in `{typography.card-heading}`'s type, and on the right what the table is of (the tournament name, truncating). The table runs to the card's edges, so its rows' dividers do too.

### Buttons — `ui/src/shared/components/Button.tsx`
Every filled button is a `Button`, picked by `tone` and `size`. It takes no `className`, only a `layout` from a closed list, so a call site cannot add a second fill or padding; `FILLED_BUTTON_RESTRICTIONS` in `ui/eslint.config.js` fails every hover fill and the secondary and danger fills outside `Button.tsx` and tests; bare `bg-primary` stays open for the brand mark and the LIVE chip. A text button stays a plain `<button>`.

| Variant | Classes | Example |
|---|---|---|
| `tone="primary"` | `bg-primary hover:bg-primary-hover text-on-primary` | Form submit in `FormModal.tsx`, Create League, Confirm Import, Finalize Results, Reload in `ErrorBoundary.tsx` |
| `tone="secondary"` | `bg-secondary hover:bg-secondary-hover text-on-fill` | Cancel in `FormModal.tsx`, Download PDF, Log out, Manage player links, Save link, Match Players with ESPN |
| `tone="danger"` | `bg-semantic-danger-fill hover:bg-semantic-danger-fill-hover text-on-fill` | Reset Results, Clean Results, Delete Season |
| Text | `text-ink-muted hover:text-ink-strong text-xs` | `CreateSeasonSection.tsx`, `PlayerLinksPanel.tsx` |

Every tone adds `rounded font-medium disabled:bg-disabled` and its size: `size="regular"` is `px-4 py-2 text-sm`, and `size="compact"` is `px-3 py-1 text-xs`, for a button in the scoreboard's tournament header, a panel row or a table row (Finalize Results and Manage player links, Save link and Create & link in the player-links panel, the schedule import's Save). A disabled filled button switches to `{colors.disabled}`; a disabled text button takes `disabled:opacity-50`.

Every button, every tone and size included, carries `TAP_TARGET_CLASS` (`min-h-11`, 44px) from `formFields.tsx`; `Button` adds it itself. The compact padding narrows a button; it never makes it shorter than a thumb.

### Form fields — `ui/src/shared/components/formFields.tsx`
`FIELD_CLASS` is `bg-surface-raised border border-control-border rounded px-3 py-2 text-base min-h-11 w-full`, built on `CONTROL_CLASS` and `TAP_TARGET_CLASS` and shared by `TextField`, `DateField` and `SelectField`. `FieldShell` puts a `block text-xs text-ink-muted mb-1` label above the control and a `mt-1 text-semantic-danger text-xs` error, with `role="alert"`, below it. Size a field with the `width` prop, not by appending a width class.

A select in a page's toolbar rather than a form — the scoreboard's tournament, player rankings' Through — is `PickerRow.tsx`: an uppercase label, then the select on `bg-surface-card` rather than a field's raised fill, over the same `CONTROL_CLASS` and `TAP_TARGET_CLASS`, then any trailing controls. The row is `flex-wrap`; below `sm:` the select is `w-full min-w-0 truncate`, so a long tournament name truncates rather than scroll the page sideways. The admin payout input is `CONTROL_CLASS` at `w-16`. The schedule-multiplier input and the roster-match select copy its sizing by hand: each has a border colour of its own, and two border colours would tie on stylesheet order.

A table head that filters its column uses `ColumnHeaderFilter.tsx`: a transparent, borderless select that inherits the head's family, weight, tracking and colour and turns `{colors.ink-label}` on hover or focus. It restates `uppercase`, because a browser resets a select's text-transform. It is `text-base` with `TAP_TARGET_CLASS` inside the `text-xs` head, so its column is as wide as its longest option at 16px.

### Modal — `ui/src/shared/components/ModalShell.tsx`, `FormModal.tsx`
A fixed scrim `bg-overlay/60`, centring a panel `bg-surface-card rounded-lg shadow-xl border border-hairline-strong`, capped at `max-h-[90dvh]` and scrolling inside. Header `px-5 py-4 border-b border-hairline` with a `{typography.modal-title}` and a 44px-tall `×` close button; body `px-5 py-4`. Widths are `form` (22rem) and `detail` (28rem), each capped at 92vw, and `wide` (`w-full max-w-3xl`). `FormModal` adds a primary submit stretched with `flex-1` and a secondary Cancel. Focus is trapped while open, and Escape closes it.

### List tables — `ScoreboardView.tsx`, `RankingsView.tsx`, `PlayerRankingsView.tsx`
`text-sm`, full width. Head: `{typography.column-header}`, with no fill. PlayerRankings and the scoreboard's leaderboard: body `divide-y divide-hairline-soft`; PlayerRankings' rows also take `hover:bg-surface-card/60`. A row holding a golfer-name button is at least 44px: the button's `TAP_TARGET_CLASS`, plus the cell's own padding. A row with nothing to tap keeps the dense padding and has no minimum.

Rankings (the standings) sets each row as a card instead: the table is `border-separate border-spacing-y-1`, each row `h-11`, every cell between the boxes `bg-surface-card`, a `PositionCell` first and a `MoneyCell` last. No divider and no hover: the canvas between cards separates them, and a `/60` tint does not read on a white card. The scoreboard's team table is set the same way, its name cell holding the team's paid golfers on a second line: `text-sm {colors.ink-muted}`, joined by ` · `, each golfer's name, finish (`T` when tied), payout and a share under 100% kept to one line, so a wrap falls only between golfers. Each name is a button with `TAP_TARGET_CLASS`, so a row with golfers is 88px (the name, then a 44px line) and 132px where the line wraps; `h-11` is the floor a team with no golfer keeps. PlayerRankings stays on divided rows.

### Position and money boxes — `ui/src/shared/components/PositionCell.tsx`, `MoneyCell.tsx`
The first and last `<td>` of a card row; the box is the cell's fill, edge to edge, and each rounds its outer corners. Like `Button`, neither takes a `className`. `PositionCell` is `{typography.position-figure}` on `{colors.position-fill}` under `{colors.on-fill}`, and `{colors.gold}` under `{colors.on-gold}` for the row numbered 1. `MoneyCell` takes the formatted amount and fills by its `moneyTone` — up `{colors.money-up-fill}`, down `{colors.money-down-fill}`, even `{colors.money-even-fill}` — under a white `{typography.money-figure}`.

### Report grids — `ui/src/features/weeklyReport/WeeklyReportView.tsx`, `ui/src/features/rosters/RostersView.tsx`
One column per team, one row per draft round, `text-xs border-collapse`, every cell bordered — in `{colors.hairline}` unless a row below says otherwise.
- Header row: `{typography.column-header}` on the `<thead>`, and `bg-gold text-on-gold` with `border-hairline-strong` on its row, whose `text-on-gold` holds over the head's ink.
- Round rows: `bg-surface-card`; the round label is sticky, `font-bold text-ink-muted`. On the rosters page a side-bet round dims to `bg-surface-card/70`.
- A golfer name in a round cell is a button carrying `TAP_TARGET_CLASS`, so a round row is 44px plus the cell's padding and its other lines; the summary rows, with nothing to tap, stay dense.
- Weekly-report cell: `{colors.gold-bright}` with `{colors.on-gold}` text when the pick earned, `{colors.side-bet-winner}` when it won the round's side bet, otherwise no fill. `roundCellBg` and `roundTextColor` in `weeklyReportModel.ts` decide which.
- Summary rows: `bg-surface-raised font-bold` with `border-hairline-strong` for highlighted totals, `bg-surface-card` for sub-rows, separated by `h-1 bg-surface-card` spacer rows.
- TOTAL CASH row: `bg-gold-soft/30 font-bold text-sm`, borders `border-gold/50`, label cell `bg-gold-tint`, amounts coloured by sign — the only summary row coloured by sign.

### Money summary chips — `ScoreboardView.tsx`, `RankingsView.tsx`, `LateRowBetsView.tsx`, `WeeklyReportView.tsx` (`StatCard`)
A `flex flex-wrap gap-3 text-sm` row of `bg-surface-card rounded px-3 py-2` chips: a `{colors.ink-soft}` label (Won, Lost, Net), then a `font-semibold tabular-nums` amount coloured by the sign it prints, so a `$0` Won reads `{colors.ink-soft}`.

### Banners — `ScoreboardView.tsx`, `UploadScheduleSection.tsx` (`SKIPPED_BANNER_CLASS`), `PlayerLinksPanel.tsx`
`px-3 py-2` (scoreboard `px-4 py-2`) `border rounded`, in one of two pairs: complete is `bg-semantic-success-tint/30 border-semantic-success-soft text-semantic-success-ink`; in progress or needing attention is `bg-semantic-warning-tint/30` (`/40` on the scoreboard) `border-semantic-warning-soft text-semantic-warning-ink`.

### Chips — `ui/src/shared/components/Chip.tsx`
`Chip` is `inline-flex rounded border px-1.5 py-0.5 text-xs uppercase tracking-wider whitespace-nowrap`, in one of two variants told apart by shape and label, never by tone. `neutral` is `bg-surface-raised text-ink-body` with a transparent border: the player-links panel's partner and manual-override chips, and a team on the leaderboard. `outlined` has no fill, a `{colors.hairline-strong}` border and `{colors.ink-muted}` text, for something absent: the leaderboard's UNDRAFTED. A leaderboard row has a chip per team each player is on, in player order, so a split golfer shows every team and a pair one chip per partner; a rostered row takes no tone of its own. Like `Button`, it takes no `className`. A page's status is the status chip above, not one of these.

### Loading and errors — `ui/src/shared/components/QueryState.tsx`, `ErrorBoundary.tsx`
`QueryState` renders `Loading {label}…` in `{colors.ink-muted}` and `Failed to load {label}: …` in `{colors.semantic-danger}`, as plain paragraphs. `ErrorBoundary` replaces the app with a centred `max-w-md` card at level 3 (see Elevation), a `text-2xl font-bold` heading and a primary Reload button.

### Live overlay toggle — `ui/src/shared/components/LiveOverlayToggle.tsx`
A native checkbox, `accent-primary-control w-4 h-4`, inside a `text-xs text-ink-muted` label carrying `TAP_TARGET_CLASS`: the box is too small to hit, so the label is the target. It renders only while the selected tournament is in the season's current week.

### Rankings chart — `ui/src/features/rankings/RankingsChart.tsx`
An SVG line chart in a `p-4` card. Gridlines `stroke-hairline`, axis labels `fill-ink-muted`, one line per team in `CHART_COLORS` (see Outside the token set), and a `text-xs text-ink-muted` legend. Height steps `h-[220px] sm:h-[320px] lg:h-[400px]`.

## Patterns & Do's / Don'ts

### Do
- Set condensed display type with `font-display`, never a raw `font-stretch`.
- Name a colour by its token: `text-ink-muted`, `bg-surface-card`. A new role gets a token in `ui/src/index.css` and an entry in this file's `colors:` in the same change, plus a row in the contrast table when it colours text.
- Keep green for a gain. Colour only a surface's bottom-line money, through `moneyToneClass` on the text it prints, or `MoneyCell` in a card row; leave every other amount in the table's ink. Set all money in `tabular-nums` or `font-mono`.
- Open every page with `PageTitle`, its actions holding the status chip where the response has one, then the league/season picker on every page but Admin; never a hand-rolled `h2`.
- Use `FIELD_CLASS` (through `TextField`, `DateField`, `SelectField`, or `FieldShell` for anything else) for every form input and select, and `PickerRow` for a page toolbar's select; both build on `CONTROL_CLASS`.
- Put a wide table in `overflow-x-auto -mx-3 sm:mx-0` with a `min-w-*`, so a phone scrolls the table rather than the page.
- Give every button, link, select, input and click handler `TAP_TARGET_CLASS` (`Button` carries its own). A link also needs `flex items-center` (or `inline-flex`), or its text sits at the top of the 44px; a checkbox gives it to its label.

### Don't
- Don't use a Tailwind shade, a hex, a ramp variable or a `--brand-*` palette variable in a component (`text-gray-400`, `#fff`, `var(--color-gray-400)`, `var(--brand-red)`). `PALETTE_RESTRICTIONS` in `ui/eslint.config.js` fails the lint on each.
- Don't merge two tokens because they share a step today (`ink` and `ink-strong`, `ink-soft` and `ink-faint`, `surface-raised` and `canvas`). They hold different roles and move separately.
- Don't use gold as text: the brand gold is 1.44:1 on white. Outside the report grids it waits on the leader and "you" highlights.
- Don't add shadows to cards or borders to them. White on sky carries depth; the modal is the only floating surface.
- Don't add a dark theme piecemeal: the ink steps are chosen for the light canvas and the contrast table only measures that.

## Responsive Behavior

| Breakpoint | Behavior |
|---|---|
| Below `sm:` (<640px) | Page padding `px-3 py-4`. Wide tables scroll edge to edge. The title's actions wrap onto a line under the title, and the picker's selects take a row each. Rankings chart 220px tall. Modal panels take 92vw. |
| `sm:` to `lg:` (640–1024px) | Padding `px-4 py-6`; tables scroll inside the page margin. The actions sit at the title's right while they fit. Chart 320px. The nav is still the menu button. |
| `lg:` and up (≥1024px) | The nav shows its links inline, each 44px tall, and hides the menu button. Chart 400px. |

- The menu button is 44px square (`w-11 h-11`), and the menu's links clear 44px on their own padding.
- Report grids keep their round column sticky at every width; only the team columns scroll.
- Modal height caps at `90dvh`, not `vh`: mobile `vh` ignores the on-screen keyboard and pushes the footer off-screen.

## Agent Prompt Guide

Use these when building UI here:

- **A card:** `bg-surface-card rounded-lg p-6`, opening with `<h3 className="text-sm font-semibold text-ink-label uppercase tracking-wider mb-4">`.
- **A filled button:** `<Button tone="primary" size="regular">` from `@/shared/components/Button`, with `tone="secondary"` or `tone="danger"` for the other fills and `size="compact"` in a header, panel or table row; never a hand-rolled fill.
- **A field:** `TextField` / `SelectField` / `DateField` from `@/shared/components/formFields`; never a hand-rolled input class.
- **A tap target:** append `TAP_TARGET_CLASS` from `@/shared/components/formFields` to any text button, link, select or input (`Button` carries its own); a link adds `inline-flex items-center`.
- **A toolbar select:** `PickerRow` from `@/shared/components/PickerRow`, with the row's other controls as its children.
- **A page title:** `<PageTitle title="…" subtitle="…" actions={<><StatusChip variant={statusChipVariant(query.data)} /><LeagueSeasonPicker /></>} />`, outside the page's loading gates.
- **A table head:** `<thead className={COLUMN_HEADER_CLASS}>` from `@/shared/components/columnHeader`, with no type on its rows or cells but an ink its fill needs, as the gold row's `text-on-gold`; body `divide-y divide-hairline-soft`, or for card rows a `border-separate border-spacing-y-1` table (see List tables).
- **A money value:** `tabular-nums`, or `font-mono` in the grids; a bottom-line amount adds `moneyToneClass` of the text it prints and keeps its table's weight, or in a card row is a `<MoneyCell display={…} />`.
- **A modal:** `FormModal` for a form, `ModalShell` when the body waits on a query.

Quick reference: nav and footer on a navy bar, canvas the brand sky, cards white, controls sky; text the brand navy, down to its muted blue (`ink-label`, `ink-muted`, `ink-soft`, `ink-faint`); brand red as a fill, red-700 as type; page titles navy, italic, condensed and uppercase; money green-800 / red-700, or on the standings a filled box; report headers the brand gold; Saira, condensed to 75% with `font-display`.
