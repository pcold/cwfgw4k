---
version: alpha
name: CWFGW4K
description: "A light, table-first league tracker in the Tour Broadcast palette. A navy bar frames the page top and bottom, a sky canvas holds white cards and table chrome, with sky again for anything you can type into or that is selected, and navy carries the text. One red carries the brand: the brand red fills primary buttons and the slanted CASTLEWOOD FG block and the LIVE chip, and the brand navy fills every secondary button; page titles are navy, italic and condensed. A surface's bottom-line money reads by sign — green won, red lost, muted even — and the two spreadsheet-style grids (weekly report, rosters) head their team columns in a navy band. Saira throughout, condensed to 75% for display type; small sizes, uppercase wide-tracked labels on table heads and card headings."

colors:
  canvas: "#e8eef7"
  surface-card: "#ffffff"
  surface-raised: "#e8eef7"
  surface-raised-hover: "#d5deeb"
  overlay: "#000000"
  chrome: "#0a1d38"
  on-chrome: "#ffffff"
  on-chrome-muted: "#cad5e2"
  on-chrome-tab-idle: "#8ea2c2"
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
  gold: "#ffd23f"
  on-gold: "#0a1d38"
  late-row-leader: "#fef9c2"
  my-team: "#fef9c2"
  pulse-up: "#7bf1a8"
  pulse-down: "#ffa2a2"
  pulse-new: "#ffd23f"
  score-eagle: "#ffd23f"
  score-birdie: "#0f8a4f"
  score-bogey: "#d7263d"
  score-double-bogey: "#8ea2c2"

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
  report-team-head:
    backgroundColor: "{colors.header-band}"
    textColor: "{colors.on-fill}"
    typography: "{typography.column-header}"
  report-cell:
    backgroundColor: "{colors.surface-card}"
    textColor: "{colors.ink}"
  report-cell-late-row-leader:
    backgroundColor: "{colors.late-row-leader}"
    borderColor: "{colors.gold}"
    textColor: "{colors.ink}"
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
  list-card:
    backgroundColor: "{colors.surface-card}"
    textColor: "{colors.ink}"
    typography: "{typography.card-row-name}"
    rounded: "{rounded.sm}"
    height: 54px
  list-card-my-team:
    backgroundColor: "{colors.my-team}"
    borderColor: "{colors.gold}"
    textColor: "{colors.ink}"
    typography: "{typography.card-row-name}"
    rounded: "{rounded.sm}"
    height: 54px
  you-tag:
    backgroundColor: "{colors.chrome}"
    textColor: "{colors.on-chrome}"
    rounded: "{rounded.sm}"
    padding: 2px 6px
  header-band:
    backgroundColor: "{colors.header-band}"
    textColor: "{colors.on-fill}"
    typography: "{typography.card-heading}"
    padding: 10px 12px
---

## Overview

Castlewood Fantasy Golf is read almost entirely as tables: the week's scoreboard, the weekly report grid, team standings, player rankings, rosters. The design exists to make those tables legible and to make money read at a glance.

The palette is **Tour Broadcast**, and the app is **light only**. The page is `{colors.canvas}` (the brand sky), between a top nav and, from `lg`, a footer on `{colors.chrome}` (the brand navy), with a tab bar at the foot below `lg`; cards are `{colors.surface-card}` (white); form controls are `{colors.surface-raised}`, the canvas's sky again. No table head has a fill but the report grids' team heads, on the navy `{colors.header-band}`. Text is the brand navy (`{colors.ink}`), with its muted slate blue (`{colors.ink-muted}`) for labels and captions.

The one brand colour is red: `{colors.primary}` (the brand red) fills primary buttons, the block behind the CASTLEWOOD FG mark and the LIVE chip. The brand red is 4.25:1 on the canvas, so it is a fill; type takes the darker step, `{colors.primary-ink}` (red-700). Page titles are navy, so a title never shares a hex with a loss.

Only the bottom line reads by sign: Total Cash on the standings and the weekly report, Weekly +/- on the scoreboard, the late row bets total, a golfer's season earnings, and the Won, Lost and Net chips. `{colors.semantic-success}` (green-800) is a gain, `{colors.semantic-danger}` (red-700) a loss, and `{colors.ink-soft}` an amount that prints as `$0`. The standings' and the weekly report's Total Cash, the scoreboard's Weekly +/-, and on a phone Late Row Bets' leader payouts and team totals are the exception in kind: a filled box (`MoneyCell`), `{colors.money-up-fill}` (the brand green), `{colors.money-down-fill}` (red-700) or `{colors.money-even-fill}` (the brand muted blue) under a white figure. Every other signed amount keeps its table's ink, so the colour points at the number that settles the row. `moneyTone` reads the sign from the formatted text, so a cents column and a whole-dollar column each tone at the precision they show. Among amounts, green means a gain and nothing else; the greens that are not money are a known gap (see Known gaps on the light canvas).

From `sm` the weekly report and the rosters page are laid out like the league's spreadsheet, one column per team and one row per draft round, as separate white cells on the canvas; below it they show one team at a time, and Late Row Bets one round at a time. Gold marks one thing there: a late-row round's leader. Gold is a fill, never text: the brand gold is 1.44:1 on white.

**Key characteristics:**
- Light canvas, no dark mode. `index.css` sets `{colors.canvas}` and `{colors.ink}` on `body`.
- White on sky for depth: cards are white on the sky canvas, and raised controls are sky again inside them. Only the modal and the error screen cast a shadow.
- Small type: `text-sm` (14px) body, `text-xs` (12px) for helper text, table heads and the report grids. Every select and text input is the exception at `text-base` (16px), because iOS zooms the page into a smaller control. The roster file input stays `text-sm`: iOS opens a picker for it rather than zooming.
- Uppercase `tracking-wider` labels mark structure: nav links and tabs, table heads, card headings, the league/season picker's labels.
- Tabular numbers (`tabular-nums` or `font-mono`) on every money and count column, and on the Rules page's payout tiles.
- Corners are `rounded` (4px) on controls and chips, `rounded-lg` (8px) on cards and the modal. Nothing is pill-shaped.

## Colors

Every colour below is a `--color-<name>` token in the `@theme` block of `ui/src/index.css`, and every token there has an entry here; `ui/src/index.css.test.ts` fails when the two disagree, by name or by value. Each value is an alias: to a Tailwind ramp step, named after it, or to one of the brand hexes, which `index.css` declares as `--brand-*` in `:root` so Tailwind makes no utility of them. Components use the token (`bg-surface-card`), never the step (`bg-gray-800`) or the palette variable (`var(--brand-navy)`); the lint rejects both.

The contrast table in `ui/src/index.css.test.ts` pins every text token at WCAG AA (4.5:1) on each backdrop it sits on, with one exception: white on `{colors.money-up-fill}` is 4.40:1, so it is held to the large-text floor (3:1) instead, and the same file fails if `MONEY_FIGURE_CLASS` drops below bold 18.66px. The AA floor is why four inks share the brand muted blue (slate-500, the step that would set `{colors.ink-faint}` apart, is 4.08:1 on the canvas), and why status text takes a darker step than the brand red and green: each ink step holds a role, not a brightness rank. The same file pins `{colors.control-border}` at WCAG 1.4.11's 3:1 against a field's fill and each surface a field sits on.

### Surface
- **Canvas** (`{colors.canvas}`, the brand sky #e8eef7): the page background.
- **Surface Card** (`{colors.surface-card}`, white): every card, the money summary chips, the modal panel and the More sheet, the phone picker's summary button, the active nav link, the cells between the boxes of the standings' and scoreboard's card rows but my team's, every phone list card but a late-row leader's or my team's, the report's phone Team totals card, Late Row Bets' phone chasing list and Totals cards, a chip strip's idle chips, the scoreboard's leaderboard card, every cell of the report and roster grids, the team picker's panel. At `/60` it is the row hover on the player-rankings table.
- **Surface Raised** (`{colors.surface-raised}`, the brand sky): form fields, the neutral tag chips, the current page's row in the More sheet and a row hovered, the Rules page's payout tiles but first place's, and its formula block. It is the canvas's colour, so it reads as raised only inside a white card.
- **Surface Raised Hover** (`{colors.surface-raised-hover}`, the brand line #d5deeb): the roster file input's button, hovered.
- **Overlay** (`{colors.overlay}`, black): the modal scrim, the More sheet's, the picker sheet's and the team picker's included, at `/60`.

### Chrome
- **Chrome** (`{colors.chrome}`, the brand navy): the bar behind the top nav, the tab bar and the footer, the FINAL chip, a chip strip's selected chip, the team picker's selected team, the `YOU` tag, the leaderboard chip of the team this device chose, and the your-team card on the standings.
- **On Chrome** (`{colors.on-chrome}`, white): the nav's week button, a nav or footer link hovered, the current tab's label, the FINAL chip's label, a chip strip's selected chip's label, the team picker's selected team's name, the `YOU` tag's label, the chosen team's leaderboard chip's label, the your-team card's name and stat figures.
- **On Chrome Muted** (`{colors.on-chrome-muted}`, slate-300): an idle nav or footer link, the your-team card's labels.
- **On Chrome Tab Idle** (`{colors.on-chrome-tab-idle}`, the brand steel #8ea2c2): an idle tab's label, 6.50:1 on the bar. Not `{colors.on-chrome-muted}`: dimmer, so the current tab's white stands out from the other four.

These three inks are light, so they are for a `{colors.chrome}` fill alone; on the canvas or a card they fail the contrast floor.

### Lines
- **Hairline Soft** (`{colors.hairline-soft}`, the brand line): row dividers (`divide-y`) inside the scoreboard's leaderboard and the player-rankings table, the rules inside the report's phone Team totals card, and the dividers of Late Row Bets' phone team list.
- **Hairline** (`{colors.hairline}`, the brand line): the modal header rule, the rule above Admin in the More sheet, the rankings chart's gridlines (`stroke-hairline`).
- **Hairline Strong** (`{colors.hairline-strong}`, slate-400): borders on the modal panel, the top edge of the More sheet, the picker sheet and the team picker below `sm`, and the error card, and on the outlined chip, the outlined late-row tag and a chip strip's idle chips.
- **Control Border** (`{colors.control-border}`, slate-500): the border on every input and select and on the phone picker's summary button, 4.76:1 on white and 4.08:1 on the canvas. Not the brand line the hairlines keep, which is 1.36:1 on white: a field on the canvas (the league/season picker) has no fill contrast, so its border is its only edge. The brand muted blue also clears 3:1 but draws a field as a heavy box.

### Text
- **Ink Strong** (`{colors.ink-strong}`, the brand navy #0a1d38): every page title, the UPCOMING chip's text and outline, the active nav link on its white block, the current page's row in the More sheet, the phone picker's summary button, the close button and a text button hovered, a competitor's name in the player-links panel, the counts on the roster upload. On a light canvas the darkest ink is the strongest, so it shares navy with `{colors.ink}`.
- **Ink** (`{colors.ink}`, the brand navy): the default text colour, set on `body` and the app root; team names in the scoreboard and standings tables, a player's name on the leaderboard, the standings' Subtotal and Late Row Bets, and in the report grid the round numbers, the bold summary rows' labels, every summary figure but PREVIOUS and the top-ten line under SUBTOTAL, and the name of a pick that earned; on a phone, an idle strip chip's label, the Team totals figures, a pick card's payout, and on Late Row Bets a leader card's `TEAM · Golfer` line, a team name in the list or a Totals card, and a listed team's season earnings; on the Rules page, the payout tiles' amounts but first place's, and the formula block.
- **Ink Body** (`{colors.ink-body}`, the brand navy): golfer names inside the roster grid cells, and the neutral tag chip's text.
- **Ink Secondary** (`{colors.ink-secondary}`, the brand secondary navy #13305a): running text on the Rules page, golfer names and the Top 10 Earnings column on the scoreboard, on `{colors.my-team}` too (12.23:1), the control named in the team picker's footer.
- **Ink Label** (`{colors.ink-label}`, the brand muted blue #4a5f80): uppercase card headings, the Rules payout tiles' ordinals, every table head but the report grids' team heads, a column-filter select on hover or focus, the Team totals card's labels, the rosters' team heading and Late Row Bets' `Leading round N`, `Chasing` and `Round N` labels on a phone.
- **Ink Muted** (`{colors.ink-muted}`, the brand muted blue #4a5f80): helper and caption text — field labels, the subtitle under a page title, loading messages, chart axis labels (`fill-ink-muted`), the golfer line under a scoreboard team, a phone list card's second line, the leaderboard's positions and round scores, the outlined chip's label, and in the report grids the Late row tag and the report's stake under it, a pick's payout lines, the name of a pick that earned nothing, the PREVIOUS and side-bet rows' labels, the PREVIOUS figures and the top-ten line under SUBTOTAL; on a phone the outlined late-row tag, a report pick card's season line and its `Playing` mark, the Team totals card's top-ten line, and on Late Row Bets a leader card's season-earnings line, a listed team's golfer and the no-leader line — and every text button but a Remove link, which is `{colors.semantic-danger}`.
- **Ink Soft** (`{colors.ink-soft}`, the brand muted blue #4a5f80): rank numbers in player rankings, the labels on the money summary chips, empty-state lines, the picker's labels, a bottom-line amount that prints as `$0`, and the ownership share under a roster-grid pick.
- **Ink Faint** (`{colors.ink-faint}`, the brand muted blue #4a5f80): the em dash in an empty cell or pick card, the `/` between a shared player's owners on the rosters page.

### Brand
- **Primary** (`{colors.primary}`, the brand red #d7263d): the fill on every primary button — form submit, Create, Save, Finalize, Reload — the block behind the CASTLEWOOD FG mark, the LIVE chip, and the bar over the current tab.
- **Primary Hover** (`{colors.primary-hover}`, red-700): that fill, hovered.
- **On Primary** (`{colors.on-primary}`, white): text on a primary fill, the CASTLEWOOD FG mark and the LIVE chip's label among it.
- **Primary Ink** (`{colors.primary-ink}`, red-700): a player name hovered in player rankings.
- **Primary Control** (`{colors.primary-control}`, the brand red): the checked colour of a native checkbox (`accent-primary-control`), today only the live-overlay toggle.

### Neutral fills
- **On Fill** (`{colors.on-fill}`, white): text on the secondary, danger, position, header-band and money fills.
- **Secondary** (`{colors.secondary}`, the brand navy): every secondary button — Download PDF, Log out, Manage player links, Save link, the roster upload's Match Players with ESPN and Back buttons, and Cancel in a form modal. There is one secondary fill: a muted one beside it would leave no rule for choosing between them.
- **Secondary Hover** (`{colors.secondary-hover}`, the brand secondary navy): that fill, hovered.
- **Disabled** (`{colors.disabled}`, slate-400): every filled button while disabled (`disabled:bg-disabled`). Its label is exempt from the contrast floor.
- **Position Fill** (`{colors.position-fill}`, the brand navy): the position box of every standings and scoreboard row but the first, and every round box of a phone report or rosters card, round 1 included. Not `{colors.secondary}`, a button's fill, nor `{colors.chrome}`, the bar's; 16.86:1 under white.
- **Header Band** (`{colors.header-band}`, the brand navy): the band that opens a band card — the scoreboard's leaderboard and every Rules card — each team head of the report grids, and the team picker's welcome band. Not `{colors.chrome}`, the bar's, nor `{colors.secondary}`, a button's; 16.86:1 under white.

### Semantic

Each family takes only the rungs it uses. `-tint` is a wash, always at an opacity (`/10` to `/40`); `-soft` is a banner border or a chip fill; the bare name is status text; `-ink` is text on that family's own wash; `-fill` is a solid button. A fill is chosen by its white label and the text by the canvas, so the two are separate tokens even where, as for danger, they land on one step. The brand green and red are fills: as text on the canvas they fall below AA, so a gain and a loss take green-800 and red-700.

- **Success** — `{colors.semantic-success-tint}` (green-500) washes the tournament-complete banner (`/30`) and nothing else; `{colors.semantic-success-soft}` (the brand green #0f8a4f) borders that banner; `{colors.semantic-success}` (green-800) is a bottom-line gain, the tie floor and side-bet amount in the Rules page's text and success messages; `{colors.semantic-success-ink}` (green-900) is the complete banner's text.
- **Warning** — `{colors.semantic-warning-tint}` (yellow-400) washes the in-progress banner (`/40`) and the skipped-rows and player-links notices (`/30`); `{colors.semantic-warning-soft}` (yellow-500) borders them; `{colors.semantic-warning}` (yellow-800) marks an ambiguous or skipped count and the multipliers on the Rules page; `{colors.semantic-warning-ink}` (yellow-900) is the text on those washes; `{colors.semantic-warning-border}` (yellow-600) borders the select for an ambiguous roster match.
- **Danger** — `{colors.semantic-danger}` (red-700) is a bottom-line loss, field errors and failed loads; `{colors.semantic-danger-hover}` (red-800) is a Remove link hovered; `{colors.semantic-danger-strong}` (red-800) is an action error beside the scoreboard's buttons and in the player-links panel; `{colors.semantic-danger-fill}` (red-700) and `{colors.semantic-danger-fill-hover}` (red-800) are the destructive admin buttons — Reset, Clean, Delete; `{colors.semantic-danger-border}` (red-500) borders an invalid payout-multiplier input. Danger is a darker red than `{colors.primary}`, which is the brand red and too light as text on the canvas.
- **Money** — the standings' and the weekly report's Total Cash box and the scoreboard's Weekly +/- box, one fill per `moneyTone`: `{colors.money-up-fill}` (the brand green, 4.40:1 under white, so large text only), `{colors.money-down-fill}` (red-700, 6.42:1) and `{colors.money-even-fill}` (the brand muted blue, 6.48:1). Not `{colors.semantic-danger-fill}` on the same step: that is the destructive button's fill, and `FILLED_BUTTON_RESTRICTIONS` keeps it in `Button.tsx`.

### Report grids
- **Gold** (`{colors.gold}`, the brand gold #ffd23f): the left edge of a late-row leader's cell, phone pick card or Late Row Bets leader card, the `Leads late row` tag, the position box of the standings' and scoreboard's first row, the Rules page's first-place payout tile, the border of the team picker's selected team, and the ring around the visitor's own team's standings or scoreboard card or row.
- **On Gold** (`{colors.on-gold}`, the brand navy): the leader's position number, the `Leads late row` label and both lines of the first-place payout tile on `{colors.gold}`.
- **Late Row Leader** (`{colors.late-row-leader}`, yellow-100): the weekly-report cell, the phone pick card and the Late Row Bets phone leader card of every pick that leads its late-row round, ties included. Its text keeps the cell's inks: `{colors.ink}` 15.69:1, `{colors.ink-muted}` 6.03:1. At 1.07:1 on white it is emphasis, not the only cue; Late Row Bets prints the payouts.

### My team
- **My Team** (`{colors.my-team}`, yellow-100): the standings' and scoreboard's card or row of the team this device chose, ringed in `{colors.gold}`. Not `{colors.late-row-leader}` on the same step, which marks a lead. Its text keeps the row's inks: `{colors.ink}` 15.69:1, `{colors.ink-secondary}` 12.23:1, `{colors.ink-muted}` 6.03:1. The fill is 1.07:1 on white, the card's ring 1.24:1 on the canvas and the row's 1.34:1 on its fill, so the `YOU` tag is the cue and they are emphasis.

### Pulse
- **Pulse Up / Down / New** (`{colors.pulse-up}` green-300, `{colors.pulse-down}` red-300, `{colors.pulse-new}` the brand gold): the flash behind a figure a live refresh changed, under `{colors.ink}` (12.03:1, 8.78:1, 11.67:1) whatever the figure's resting ink, fading in 1.2s to the cell's own fill and ink. Up and down are better and worse for that figure — more money or top 10s, a lower finish or score to par — and new is a change without a direction. Not the money fills, under which white fails small text and an up flash on an up box would not show.

### Scorecard
- **Score Eagle / Birdie / Bogey / Double Bogey** (`{colors.score-eagle}` the brand gold, `{colors.score-birdie}` the brand green, `{colors.score-bogey}` the brand red, `{colors.score-double-bogey}` the brand steel): the mark around a hole's strokes on a leaderboard scorecard, whose figure stays `{colors.ink}`. One off par is a `border-2` outline on the white card (birdie 4.40:1, bogey 4.96:1); two or more is a fill under `{colors.ink}` (eagle 11.67:1, double bogey 6.5:1), since gold and steel are under 3:1 on white and can't outline. Eagle covers any score two or more under par, double bogey any two or more over; a par is unmarked. Under par is round and over par square, so the shape carries the call where green and red don't.

### Known gaps on the light canvas
Tokens only re-colour what the dark theme drew; the component treatment has restyled the app chrome (CWF-231) and begun on the tables (CWF-232) with the standings' and scoreboard's card rows and the report grids, and these are the gaps it has to close.
- **Red is both brand and loss.** Primary buttons and the LIVE chip are the brand red; destructive buttons and losses are red-700, and so are the standings' and scoreboard's loss boxes, never the brand red. Beside a loss, a primary button can still read as a warning.
- **No gold text role.** The team picker's eyebrow is drawn gold on the navy band in the design, but no text role pairs `{colors.gold}` with `{colors.header-band}`, so it renders `{colors.on-fill}` until one is added, with its backdrop row in `ui/src/index.css.test.ts`. The your-team card's `Your team` label is gold in the design too, and waits on a gold role for `{colors.chrome}`, drawn `{colors.on-chrome-muted}` until then.
- **Green outside money.** `{colors.semantic-success}` still colours success messages, exact roster matches, the amounts in the Rules page's text (its payout tiles take ink) and the complete banner; the report grid's payouts no longer take it. Green is meant for gains alone.

### Outside the token set
- **Rankings chart lines** — `CHART_COLORS` in `ui/src/features/rankings/rankingsChartModel.ts` is a 13-hex series palette, one per team. It identifies a series rather than holding a role, so it carries a lint waiver instead of tokens. It was picked against the old dark canvas and has not been re-checked on white.
- **PDF export** — `ui/src/features/weeklyReport/weeklyReportPdf.ts` prints the report grid's roles on white paper. jsPDF takes RGB, so `PDF_COLOURS` holds print copies keyed by role token, and `weeklyReportPdf.test.ts` fails when one drifts from `index.css`. Helvetica stands in for Saira.

## Typography

### Font family
- **Saira** — the brand face, self-hosted from `@fontsource-variable/saira` (width and weight axes, upright and italic) through the imports in `ui/src/main.tsx`. `--font-sans` in `ui/src/index.css` leads with it, so Tailwind's preflight sets it on `html` and every text style inherits it at 100% width. Behind it come `Saira Fallback`, Arial scaled to Saira's metrics so text holds its place while the webfont swaps in, then the system stack.
- **Display** — the `font-display` utility in `ui/src/index.css` sets `font-stretch: 75%`, the condensed width for the nav, titles, team names and numbers. Today the brand mark, the nav links and week button, the tab labels and More sheet rows, the footer link, page titles, the phone picker's summary, the status chip, table heads, the standings' and scoreboard's team names and position and money boxes, a phone list card's name and total, the report grids' round numbers and Late row tag, a chip strip's chips, the late-row tags, the Team totals figures, Late Row Bets' phone labels, card names and listed season earnings, the Rules cards' bands and payout tiles, the team picker's band, team buttons and actions, and the your-team card's labels, name and stats use it. A fallback face has no width axis and sets it at its own width.
- **System Mono** — Tailwind's `font-mono`. The weekly report grid's money figures, late row bets' table golfer names and amounts (from `sm`), the golfer-history modal's place and money columns, and the ESPN week and event names on the schedule import.

### Hierarchy

| Token | Size | Weight | Tracking | Classes | Where |
|---|---|---|---|---|---|
| `{typography.display}` | — | — | — | `font-display` | The brand mark, nav links and week button, tab labels and More sheet rows, the footer link, page titles, the phone picker's summary, the status chip, table heads, the standings' and scoreboard's team names and position and money boxes, a phone list card's name and total, the report grids' round numbers and Late row tag, a chip strip's chips, the late-row tags, the Team totals figures, Late Row Bets' phone labels, card names and listed season earnings, the Rules cards' bands and payout tiles, the team picker's band, team buttons and actions, the your-team card's labels, name and stats |
| `{typography.brand-mark}` | 20px | 800 | 0.025em, italic, uppercase | `font-display italic font-extrabold text-xl uppercase tracking-wide` | The CASTLEWOOD FG mark in the nav |
| `{typography.page-title}` | 24px, 30px from `sm:` | 800 | 0, italic, uppercase | `font-display italic font-extrabold uppercase text-2xl sm:text-3xl leading-tight text-ink-strong` | The `h2` of every page, in `PageTitle` |
| `{typography.modal-title}` | 18px | 700 | 0 | `text-lg font-bold` | Modal header |
| `{typography.card-heading}` | 14px | 600 | 0.05em, uppercase | `text-sm font-semibold uppercase tracking-wider text-ink-label` | Heading inside a card |
| `{typography.column-header}` | 12px | 700, a `th`'s default | 0.05em, uppercase | `font-display text-xs uppercase tracking-wider text-ink-label` | `COLUMN_HEADER_CLASS` in `columnHeader.ts`, on the `<thead>` of every table: the scoreboard and its leaderboard, standings, players, late row bets, the report and roster grids, the schedule import, golfer history |
| `{typography.body}` | 14px | 400 | 0 | `text-sm` | Tables, running text |
| `{typography.control}` | 16px | 400 | 0 | `text-base` | Form fields, toolbar selects, the league/season selects, the column-header filter |
| `{typography.caption}` | 12px | 400 | 0 | `text-xs` | Field labels, helper text, field errors |
| `{typography.button}` | 14px | 500 | 0 | `text-sm font-medium` | `Button`'s `regular` size |
| `{typography.button-compact}` | 12px | 500 | 0 | `text-xs font-medium` | `Button`'s `compact` size: the scoreboard's tournament header, a panel row, a table row |
| `{typography.report-figure}` | 12px | 400 | 0 | `font-mono` in a `text-xs` table | Money in the weekly report grid but TOTAL CASH, a `MoneyCell` |
| `{typography.card-row-name}` | 18px | 700 | 0, uppercase | `font-display font-bold text-lg uppercase text-ink` | A team name in a standings or scoreboard card row, and a phone list card's or Late Row Bets card's name; a golfer's name there is `normal-case` |
| `{typography.position-figure}` | 18px | 700 | 0 | `font-display font-bold text-lg tabular-nums` | `PositionCell` |
| `{typography.money-figure}` | 20px | 700 | 0 | `MONEY_FIGURE_CLASS`: `font-display font-bold text-xl tabular-nums` | `MoneyCell`; bold and at least 18.66px, or white on the up fill fails contrast |

### Principles
- **Weights in use are 400, 500, 600, 700 and 800.** `font-semibold` is the emphasis weight in tables (money chips, the leaderboard's Score and Tot) and the nav links' weight; `font-bold` marks modal titles, the status chip, the report grid's total rows and the name of a pick that earned, the standings' and scoreboard's team names and boxes, and table heads take it as a `th`'s default; `font-extrabold` is the brand mark's and the page titles'.
- **Uppercase is structure, not emphasis.** Nav links, page titles, table heads, card headings and team names in tables are uppercase; body text never is.
- **Numbers line up.** A column of money uses `tabular-nums` (list tables) or `font-mono` (the weekly report grid, late row bets), right-aligned in list tables and the grid's summary rows.

## Layout

### Spacing
- Tailwind's default 4px scale; there are no spacing tokens.
- **Page**: `<main>` is `max-w-screen-2xl mx-auto px-safe-3 sm:px-safe-4 pt-4 sm:pt-6`, and its foot is `pb-above-tab-bar-4 sm:pb-above-tab-bar-6 lg:pb-6`, so below `lg` the last row clears the fixed tab bar and the home indicator. The nav and the footer centre their contents in the same `max-w-screen-2xl px-safe-3 sm:px-safe-4`, so every band's edges line up with the page's.
- **Safe area**: the viewport is `viewport-fit=cover`, so a gutter that meets a screen edge takes the larger of its spacing and the inset: `px-safe-*` and `pb-above-tab-bar-*` are utilities in `ui/src/index.css`. A surface that is itself edge to edge (the tab bar, the More sheet, the picker sheet, the team picker below `sm`) pads by the inset alone, `pb-[env(safe-area-inset-bottom)]` and its left and right pair.
- **Page body**: a stack spaced `space-y-6` or `space-y-4`, opening with `PageTitle`.
- **Cards**: `p-6` for form cards and a Rules card's body under its band, `p-4` for the rankings chart and late-row-bet tables.
- **List tables**: cells `px-3 py-2` (scoreboard teams, standings, player rankings) or `px-2 py-2` with `px-3` at the outer edges (the leaderboard, whose nine columns fit half the page from `xl`); card rows are `h-11` and `border-spacing-y-1` apart. Below `sm` the standings, scoreboard teams and players are cards instead, each at least 54px (`min-h-13.5`) and `gap-1` apart, and the leaderboard is divided rows, `px-3 py-2`.
- **Report grids**: cells `px-2 py-1.5`, `border-separate border-spacing-1` (`REPORT_GRID_TABLE_CLASS`), no cell bordered: the canvas between white cells separates them. They are the densest surface in the app on purpose: one column per team must fit.
- **Forms**: fields in a `flex flex-wrap items-end gap-4` row inside a card; a form modal stacks them with `space-y-3`.

### Containers
- Report grids and list tables sit in `overflow-x-auto -mx-3 sm:mx-0` but show only from `sm` (`hidden sm:block`), where `sm:mx-0` cancels the bleed, so a grid scrolls inside the page margin and the scoreboard teams' floor applies from `sm` only. Below `sm` each is cards, which never scroll sideways: a list one card per row, the report and rosters one team at a time (see Report grids), Late Row Bets one round at a time (see Late row bets). A table card's table scrolls inside the card, `overflow-x-auto` alone, since a bleed would cut the card's rounded edge; the leaderboard's shows only from `sm` too, and below it the card holds rows (see List tables), so no scoreboard table scrolls on a phone. The scoreboard's two tables also set a floor (`min-w-[500px]` teams, `min-w-[480px]` leaderboard), each applying from `sm`, so their columns don't crush first; at a 1280px viewport each column is wider than its floor, so neither scrolls.
- From `xl` the scoreboard sets its teams and the leaderboard side by side, `grid-cols-[minmax(0,5fr)_minmax(0,4fr)]` at `gap-6`, the money summary chips under the teams; the split gives a two-golfer line room in its team cell. Below `xl` the two stack in a `grid-cols-1`, since an implicit track grows to a table's floor and scrolls the page on a phone.
- The report grids pin the first column (`sticky left-0`) so the round label stays visible while the teams scroll.
- A leaderboard scorecard sets its nines side by side once its own box is 36rem wide (`@container`, `@min-[36rem]:flex-row`), the two at 576px, and stacks the back nine under the front below that, so a phone (a 327px row against a 298px back nine) never scrolls it. It is a container query rather than `sm:` because the card is half the page from `xl`; `overflow-x-auto` is the fallback for a narrower phone.

## Elevation & Depth

| Level | Treatment | Use |
|---|---|---|
| 0 | `{colors.canvas}` | The page |
| 1 | `{colors.surface-card}`, no border, no shadow | Cards, summary chips, the standings' and scoreboard's card rows, phone list cards, the report grids' cells |
| 2 | `{colors.surface-raised}` | Fields, neutral chips, the Rules payout tiles but first place's, and the formula block |
| 3 | `{colors.surface-card}` + `{colors.hairline-strong}` border + `{elevation.modal}` (`shadow-xl`) over `{colors.overlay}`/60 | The modal panel, and the error screen's card |

White cards on the sky canvas carry depth without shadows, and a raised control is sky again inside its white card. The `{colors.chrome}` nav, tab bar and footer frame the levels rather than sit on one. `shadow-xl` appears only on the two floating panels; the More sheet and the picker sheet rise from the screen's foot over the scrim and cast none, since nothing lies beside them.

## Shapes

| Token | Value | Class | Use |
|---|---|---|---|
| `{rounded.sm}` | 4px | `rounded` | Buttons, fields, chips, banners, nav items |
| `{rounded.lg}` | 8px | `rounded-lg` | Cards, the modal panel, the error card |

Table cells are square, but for the outer corners of a card row, which its position and money boxes round (`rounded-l`, `rounded-r`); the report's TOTAL CASH boxes, not ending a card row, stay square. A phone list card is `rounded`, and its boxes round the same outer corners; the report's phone Team totals card is `rounded` too, matching the pick cards under it. No component is pill-shaped (`rounded-full`), and no corner is larger than 8px.

## Components

### Top nav — `ui/src/App.tsx`
A `{colors.chrome}` bar, `min-h-14`, with no border, its `<nav>` labelled `Main` beside the tab bar's `Tabs`. On the left, the brand mark: an `h1` reading CASTLEWOOD FG in `{typography.brand-mark}` and `{colors.on-primary}`, on a `{colors.primary}` block slanted with `-skew-x-12` the bar's full height; the text inside is skewed back (`skew-x-12`), since the italic face already slants it. At `lg:` and up, a row of links on the right, ending in a **My team** button.
- Every nav label is `font-display font-semibold uppercase tracking-wider`, idle `text-on-chrome-muted hover:text-on-chrome`; the current page's link is a `bg-surface-card text-ink-strong` block and carries `aria-current="page"`.
- Desktop link: `inline-flex items-center` plus `TAP_TARGET_CLASS`, `px-3 rounded text-sm`.
- My team is a button styled as an idle link, shown wherever the More sheet's My team row would be (see Team picker), and a tap runs the same Change. It reads `MY TEAM` whatever is saved, since a long team name would overflow the row at 1024px; its accessible name carries the state, `My team: WOMBLE, change` or `My team, choose`.
- Below `lg:` the bar holds the brand mark, and below `sm:` the phone picker's week button at its right (see Phone picker); the tab bar navigates.

### Footer — `ui/src/App.tsx`
A `{colors.chrome}` bar matching the nav, `lg` and up only (`hidden lg:block`): below it the More sheet reaches Admin. It is `py-1.5`, holding the Admin link alone at the right: the nav's label and idle ink, `inline-flex items-center` plus `TAP_TARGET_CLASS`, `px-3 rounded text-sm`.

### Tab bar — `ui/src/TabBar.tsx`, `ui/src/MoreSheet.tsx`
Below `lg` only (`lg:hidden`): a `{colors.chrome}` `<nav aria-label="Tabs">` fixed to the viewport's foot, `z-40` over the report grids' sticky `z-20` and under the modal scrim's `z-50`. Five equal tabs in a `grid-cols-5 h-14` row, the bar padded below by the home indicator's inset: Scores, Standings, Report, Players, More.
- A tab is a label alone, `font-display text-xs uppercase tracking-wider` with `TAP_TARGET_CLASS`: idle `font-medium {colors.on-chrome-tab-idle}`; the current one `font-bold {colors.on-chrome}` under a `w-8 h-1 {colors.primary}` bar at the tab's top edge, and carries `aria-current="page"`. More is current on Rosters, Bets, Rules and Admin; `/` marks no tab.
- More is a button (`aria-expanded`, `aria-controls`) opening the More sheet: `ModalShell` with `placement="sheet"`, holding Rosters, Bets and Rules, then a **My team** row, a `{colors.hairline}` rule, then Admin. Each row is `flex items-center` with `TAP_TARGET_CLASS`, `px-3 rounded text-base font-display font-semibold uppercase tracking-wider`, `{colors.ink}` and the current page's row `bg-surface-raised text-ink-strong`. Any navigation closes it, a tap on the current page's row included.
- The My team row is a button styled as an idle row. It reads `MY TEAM: <TEAM> · CHANGE` when a team is saved and `MY TEAM · CHOOSE` otherwise; a tap clears the saved choice, closes the sheet and opens the team picker. It is absent wherever the picker can't open: on Admin, and until the newest season has teams.

### Team picker — `ui/src/features/myTeam/MyTeamPicker.tsx`, `MyTeamContext.tsx`
A first visit asks "Which team is yours?" in a `ModalShell` with `placement="responsive"`, dismissed by a tap on the scrim: a sheet below `sm`, a centred `detail`-width panel from it. It opens on every route but Admin, once the newest season's rosters have loaded and are not empty, and never blocks the page: Close, Escape or the scrim hide it for the visit and record nothing.
- **Band.** Under the shell's title row, a `{colors.header-band}` band runs edge to edge, holding the `WELCOME TO CASTLEWOOD` eyebrow (`text-xs font-bold uppercase tracking-widest`) and a line of copy, both `{colors.on-fill}` and `font-display`.
- **Team grid.** The newest season's teams, uppercased and alphabetical, in `grid-cols-2` at every width. Each is an `aria-pressed` button, `border-2 rounded font-display font-bold uppercase tracking-wider` with `TAP_TARGET_CLASS`, a long name wrapping inside it: idle `{colors.surface-raised}` on `{colors.hairline}` with `{colors.ink}` text, selected `{colors.chrome}` with a `{colors.gold}` border and `{colors.on-chrome}` text.
- **Actions.** A team button only selects. The primary `Button` reads `SET <TEAM> AS MY TEAM`, is disabled until a team is selected, and alone saves; an underlined `{colors.ink-muted}` "Just browsing" text button records that the visitor is browsing. Below `sm` they stack, the primary full width; from `sm` the primary sits right.
- **Footer.** A `{colors.ink-muted}` line points at Change: "Change anytime from **More → My team**" below `lg` (`lg:hidden`), "Change anytime from **My team** in the header" from it (`hidden lg:inline`).
- **Storage.** Per device, under `localStorage['cwfg.myTeam']`: `{"kind":"team","teamName":"WOMBLE"}` (uppercased), `{"kind":"browsing"}`, or no entry while unanswered. The kind is spelled out, so a team named NONE never reads as browsing; unreadable storage counts as unanswered.
- **Validation.** A saved name is checked against the newest season's teams, never the season on screen. While they load, or when there are none, the saved choice stands; a name that has left the newest season counts as unanswered, and the picker asks again.
- **Personal links.** `?team=<name>`, in any case, saves the team and skips the picker. Matched or not, the param is then removed by a replace navigation that keeps the path and the other params; an unmatched name is ignored.
- **Nothing unlocks.** The choice is a preference: it reads no auth state and writes no key but its own.

### Page title — `ui/src/shared/components/PageTitle.tsx`
Every page opens with one: a `<header>` holding the page's only `h2` in `{typography.page-title}` (the `h1` is the brand mark), an optional `text-sm text-ink-muted` subtitle under it, and on the right an `actions` slot. It is `flex flex-wrap items-center justify-between`; below `sm:` the actions take a line of their own under the title. A Page renders it outside its data's loading gates, so the title holds still while the data loads or the season changes; Admin, which has no title until the session check confirms a login, is the exception. The title is static text; what varies — a tournament's name, its week, a count — goes in the subtitle.

The actions slot holds the status chip on a page whose response carries one (Scoreboard, Weekly Report, Standings, Players, Late Row Bets), then the league/season picker, and below `sm:` the phone picker's summary button in its place; Admin puts its Log out button there instead. No rule sits under the header.

### League / season picker — `ui/src/features/leagues/LeagueSeasonPicker.tsx`
Lives in the page title's actions, not in a bar of its own: a page that reads the selected season passes `<LeagueSeasonPicker />` in a `DESKTOP_PICKER_CLASS` wrapper, and a page that doesn't (Admin) shows none. Below `sm:` the wrapper hides it and the phone picker's sheet holds it instead. Two labelled selects in a `flex flex-wrap` row; labels are `text-xs text-ink-soft font-medium uppercase tracking-wider`, selects `bg-surface-raised border border-control-border rounded text-base` with `TAP_TARGET_CLASS`.

### Phone picker — `ui/src/features/leagues/PhonePicker.tsx`, `ui/src/shared/components/HeaderSlot.tsx`
Below `sm:` only, on every page that has a league/season picker, and like that picker only once there is a league to pick. Two buttons open one `ModalShell` sheet (`placement="sheet"`, dismissed by a tap on the scrim), which holds the league/season picker and then the page's tournament or Through field with its trailing controls, the live-overlay toggle and the report's Download PDF; Rosters and Rules add none. A PDF failure is an alert on the page, outside both copies of the field, and a second alert in the sheet while its panel covers the page. A change leaves the sheet open; the close button, Escape or the scrim close it.
- The summary button sits in the title's actions after the status chip, `w-full` on `bg-surface-card` with `CONTROL_CLASS` and `TAP_TARGET_CLASS`, left-aligned in `font-display font-medium {colors.ink-strong}`. It reads `Wk 11 · Butterfield Bermuda ▾` (`tournamentShortLabel`), the season's label without a tournament, `Season` while the seasons load, and `Choose season` once they have loaded without one selected.
- The week button sits at the nav's right, `font-display font-semibold uppercase tracking-wider text-sm {colors.on-chrome}` with `TAP_TARGET_CLASS` and `px-3`, reading `WK 11 ▾` or the same fallbacks.
- A page portals the week button into the nav's slot (`useHeaderSlot`), so its state stays in the page; a page without a picker leaves the slot empty. The ▾ is `aria-hidden`; both buttons carry `aria-haspopup="dialog"`, `aria-expanded` and `aria-controls`, and the week button's name adds what it changes.
- The page's desktop field stays mounted under `DESKTOP_PICKER_CLASS` (`hidden sm:block`), so the sheet's copy takes ids suffixed `-sheet`.

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

A band card — the scoreboard's leaderboard, every Rules card — has no padding of its own and opens instead with a `{colors.header-band}` band, `px-3 py-2.5 rounded-t-lg`, under `{colors.on-fill}`: the `h3` on the left in `{typography.card-heading}`'s type, and on the right a caption saying what the card is of (the tournament name, the paid places, the side-bet rounds), truncating. The leaderboard's table, and below `sm` its rows, run to the card's edges, so their dividers do too; a Rules card pads its body `p-6`. The Rules bands set both lines in `font-display`, since at body width a title and caption crowd each other below 375px; the leaderboard's band is still at body width.

The Rules page's payouts are a tile grid, not a table: an `ol` of one `li` per paid place, `grid grid-cols-5 gap-2 max-w-md`: five to a row whatever the count, so ten make two rows, and a desktop grid stays tile-sized. Each tile is `rounded py-2 text-center` on `{colors.surface-raised}`: the place's ordinal in `font-display font-bold text-xs uppercase tracking-wider {colors.ink-label}`, over the amount in cents in `font-display font-bold text-sm sm:text-lg tabular-nums {colors.ink}`. First place is `{colors.gold}` with `{colors.on-gold}` on both lines. The zero-sum formula sits in a `bg-surface-raised rounded px-3 py-2` block, `text-sm font-semibold {colors.ink}`.

### Buttons — `ui/src/shared/components/Button.tsx`
Every filled button is a `Button`, picked by `tone` and `size`. It takes no `className`, only a `layout` from a closed list, so a call site cannot add a second fill or padding; `FILLED_BUTTON_RESTRICTIONS` in `ui/eslint.config.js` fails every hover fill and the secondary and danger fills outside `Button.tsx` and tests; bare `bg-primary` stays open for the brand mark and the LIVE chip. A text button stays a plain `<button>`.

| Variant | Classes | Example |
|---|---|---|
| `tone="primary"` | `bg-primary hover:bg-primary-hover text-on-primary` | Form submit in `FormModal.tsx`, Create League, Confirm Import, Finalize Results, Reload in `ErrorBoundary.tsx` |
| `tone="secondary"` | `bg-secondary hover:bg-secondary-hover text-on-fill` | Cancel in `FormModal.tsx`, Download PDF, Log out, Manage player links, Save link, Match Players with ESPN |
| `tone="danger"` | `bg-semantic-danger-fill hover:bg-semantic-danger-fill-hover text-on-fill` | Reset Results, Clean Results, Delete Season |
| Text | `text-ink-muted hover:text-ink-strong text-xs` | `CreateSeasonSection.tsx`, `PlayerLinksPanel.tsx` |

Every tone adds `rounded font-medium disabled:bg-disabled` and its size: `size="regular"` is `px-4 py-2 text-sm`, and `size="compact"` is `px-3 py-1 text-xs`, for a button in the scoreboard's tournament header, a panel row or a table row (Finalize Results and Manage player links, Save link and Create & link in the player-links panel, the schedule import's Save). A disabled filled button switches to `{colors.disabled}`; a disabled text button takes `disabled:opacity-50`.

Every button, every tone and size included, carries `TAP_TARGET_CLASS` (`min-h-11`, 44px) from `formControls.ts`; `Button` adds it itself. The compact padding narrows a button; it never makes it shorter than a thumb.

### Form fields — `ui/src/shared/components/formControls.ts`, `FieldShell.tsx`, `TextField.tsx`, `DateField.tsx`, `SelectField.tsx`
`FIELD_CLASS` is `bg-surface-raised border border-control-border rounded px-3 py-2 text-base min-h-11 w-full`, built on `CONTROL_CLASS` and `TAP_TARGET_CLASS` and shared by `TextField`, `DateField` and `SelectField`. `FieldShell` puts a `block text-xs text-ink-muted mb-1` label above the control and a `mt-1 text-semantic-danger text-xs` error, with `role="alert"`, below it. Size a field with the `width` prop, not by appending a width class.

A select in a page's toolbar rather than a form — the scoreboard's tournament, player rankings' Through — is `PickerRow.tsx`: an uppercase label, then the select on `bg-surface-card` rather than a field's raised fill, over the same `CONTROL_CLASS` and `TAP_TARGET_CLASS`, then any trailing controls. A page's toolbar row hides below `sm:` (`DESKTOP_PICKER_CLASS`), and the phone picker's sheet holds a second copy. The row is `flex-wrap`; below `sm:`, in the sheet, the select is `w-full min-w-0 truncate`, so a long tournament name truncates rather than scroll the page sideways. The admin payout input is `CONTROL_CLASS` at `w-16`. The schedule-multiplier input and the roster-match select copy its sizing by hand: each has a border colour of its own, and two border colours would tie on stylesheet order.

A table head that filters its column uses `ColumnHeaderFilter.tsx`: a transparent, borderless select that inherits the head's family, weight, tracking and colour and turns `{colors.ink-label}` on hover or focus. It restates `uppercase`, because a browser resets a select's text-transform. It is `text-base` with `TAP_TARGET_CLASS` inside the `text-xs` head, so its column is as wide as its longest option at 16px.

### Modal — `ui/src/shared/components/ModalShell.tsx`, `FormModal.tsx`
A fixed scrim `bg-overlay/60`, centring a panel `bg-surface-card rounded-lg shadow-xl border border-hairline-strong`, capped at `max-h-[90dvh]` and scrolling inside. Header `px-5 py-4 border-b border-hairline` with a `{typography.modal-title}` and a 44px-tall `×` close button; body `px-5 py-4`. Widths are `form` (22rem) and `detail` (28rem), each capped at 92vw, and `wide` (`w-full max-w-3xl`). `FormModal` adds a primary submit stretched with `flex-1` and a secondary Cancel. Focus is trapped while open, Escape closes it, and focus returns to the control that opened it. Modals stack: one opened over another, as the team picker can open over a sheet, takes the keyboard and Escape until it closes, and the page stays scroll-locked until the last one closes. `placement="sheet"` sets the panel instead full width at the viewport's foot, `rounded-t-lg border-t`, with no shadow and no width, padded by the safe-area insets, sliding up on open (`starting:translate-y-full`, none under reduced motion). `placement="responsive"` is that sheet below `sm`; from it, `sm:` classes centre the panel at the `detail` width with the centred panel's radius, border and shadow, drop the safe-area padding, and open it without the slide. No JavaScript media query is involved.

### List tables — `ScoreboardView.tsx`, `RankingsView.tsx`, `PlayerRankingsView.tsx`, `PhoneFilter.tsx`
`text-sm`, full width. Head: `{typography.column-header}`, with no fill. PlayerRankings and the scoreboard's leaderboard: body `divide-y divide-hairline-soft`; PlayerRankings' rows also take `hover:bg-surface-card/60`. A row holding a golfer-name button is at least 44px: the button's `TAP_TARGET_CLASS`, plus the cell's own padding. A leaderboard row with a scorecard is the tap target itself, `h-11`, since a table row ignores `min-height`. A row with nothing to tap keeps the dense padding and has no minimum.

Rankings (the standings) sets each row as a card instead: the table is `border-separate border-spacing-y-1`, each row `h-11`, every cell between the boxes `bg-surface-card`, a `PositionCell` first and a `MoneyCell` last. No divider and no hover: the canvas between cards separates them, and a `/60` tint does not read on a white card. The scoreboard's team table is set the same way, its name cell holding the team's paid golfers on a second line: `text-sm {colors.ink-muted}`, joined by ` · `, each golfer's name, finish (`T` when tied), payout and a share under 100% kept to one line, so a wrap falls only between golfers. Each name is a button with `TAP_TARGET_CLASS`, so a row with golfers is 88px (the name, then a 44px line) and 132px where the line wraps; `h-11` is the floor a team with no golfer keeps. Before tee-off the scoreboard sets the team table with `#` and Team alone — a `PositionCell` and no `MoneyCell` — in the same wrapper, `min-w-[500px]` floor and `h-11` rows: the name cell's second line is `N playing`, then the team's golfers in the field in draft-round order, joined by ` · `, each name a `TAP_TARGET_CLASS` button kept to one line, with no finish or payout. A team with nobody in the field reads `0 playing` and nothing else. PlayerRankings stays on divided rows.

The team this device chose is marked in every one of these tables and in their phone cards; browsing, or never having been asked, marks nothing. Its standings or scoreboard row takes `CARD_ROW_CLASS`'s `my-team` look: the `tr` a rounded `outline-2 {colors.gold}` ring, inset by `-outline-offset-2` because the table's `overflow-x-auto` wrapper clips an outline's sides outside the row, and each cell but the boxes `{colors.my-team}` in place of white; its phone card is `ListCard`'s `fill="my-team"`, `{colors.my-team}` inside a `ring-2 {colors.gold}`. Either way a `YouTag` follows the team's name. On PlayerRankings, whose rows are golfers, the tag alone follows the team name of each golfer that team drafted, in the table and on the card's second line. On the scoreboard's leaderboard, a chip naming that team takes `Chip`'s `my-team` variant and no tag, since the team list above it already carries one.

The standings open on that team's card, `YourTeamCard.tsx`, when it is in the season on screen: a `rounded-lg {colors.chrome}` section holding a `Your team` label over the name in italic uppercase `font-display text-3xl`, the Total Cash `MoneyCell` `card-end` box, and a `<dl>` of stats from the response's `standings`, never from the row order — `Pos` (`T8th / 13` when tied), then a chaser's `Behind 7th` and `Behind leader` or a leader's `Ahead of 2nd`, left out when nothing is below it. Gaps are whole dollars in the total box's formatter. Below `sm` the stats wrap to a full-width strip of equal columns under the name and box; from `sm` they sit between the two on one row, the box last.

Below `sm` each of the three renders as a list of cards instead, an `<ol>` of `ListCard.tsx` (`sm:hidden`), and its table only from `sm` (`hidden sm:block`). A card is a white `rounded` flex row, at least 54px (`min-h-13.5`), the cards `gap-1` apart, with no bleed: a position box, then the name over a second line, then the total. The name is `{typography.card-row-name}` and `truncate`s, its column `min-w-0`, so a long name never pushes the total off the screen; the second line is `text-sm {colors.ink-muted}`. Standings: `Sub … · Bets …` in cents, the box Total Cash in whole dollars. Scoreboard: the team's golfer line, or "No golfers in the top 10", the box the week's +/- in cents; before tee-off, the `N playing` line and no box. PlayerRankings: the golfer's name in `normal-case`, still a `TAP_TARGET_CLASS` button, over `TEAM · Rd N · K top 10s` (Undrafted for no team, the round left out when there is none, `1 top 10` singular); its total takes no box, a plain `{typography.money-figure}` in `moneyToneClass`. Its team and round filters move above the cards as two `FIELD_CLASS` selects, `grid-cols-2 gap-2`, each first offering all. Their labels are screen-reader only, so a round option reads `Rd N`; one filter state drives them and the header's filters both. Each is a `PhoneFilter.tsx`, a `FieldShell` with a hidden label rather than a `SelectField`, whose placeholder can't be picked back.

The scoreboard's leaderboard keeps its band card below `sm`, and under the band, in place of the table, a Team `PhoneFilter` padded `p-3` (All teams, each team, and Undrafted while an undrafted golfer is on the board), then an `<ol>` of divided rows running to the card's edges (`sm:hidden`), not `ListCard`s: the navy and gold position boxes and the money boxes are the league's own standings, and a golfer field is not that. A row is `px-3 py-2`, `items-baseline`: the position in a `w-8` column, `{colors.ink-muted}` and `tabular-nums`, "—" for a competitor yet to tee off; the player name in `{colors.ink}`, `truncate`s in a `min-w-0 flex-1` column, over a wrapping line of the row's chips and its round line, `R1 68 · R2 70 · Tot 138` in `text-xs {colors.ink-muted} tabular-nums`, the rounds played so far and the total once there is one, left out before tee-off; then the score to par, `font-semibold tabular-nums`. A row with a scorecard is tappable along its whole width and at least 44px (`TAP_TARGET_CLASS`), and opens the scorecard under it (see Leaderboard scorecard); a row without one keeps the dense padding. The one filter state drives the select and the header's filter both.

When a live refresh reorders one of these lists, or Late Row Bets', each row and card glides to its new place (`glideAt`, `glide.ts`), moving only, never resizing. A new selection, filter or round redraws the list in place; content changing above a list shifts it without a glide; under reduced motion rows move without gliding. On the scoreboard, standings, player rankings and weekly report, a figure a refresh changes in place — money, a top-10 count, a golfer's finish, a score to par — pulses in `{colors.pulse-up}`, `{colors.pulse-down}` or `{colors.pulse-new}` (`Pulse.tsx`). A position box never does, since the glide already shows the row's new rank; nor do Late Row Bets, the won/lost/net cards, the undrafted top tens or the your-team card's gaps. A colour fade, not motion, so it plays under reduced motion too.

### Leaderboard scorecard — `ui/src/features/scoreboard/LeaderboardScorecard.tsx`
A leaderboard row with a scorecard opens it under the row, one row at a time, in the phone list and the table alike: the whole row toggles it, `cursor-pointer hover:bg-surface-raised/50`, and the name is a plain button holding `aria-expanded`, then a `{colors.ink-muted}` ▸ or ▾. The row stays open through a live refresh and closes for a new selection. The card is `px-3 pb-3 space-y-2`: a `Round` `ChipStrip` (`R1`, `R2` …) when there is more than one round, opening on the latest; the two nines; then a legend of the four marks in `font-display text-xs uppercase tracking-wider {colors.ink-muted}`. Each nine is a `text-xs tabular-nums` table with a screen-reader caption (`Round 2, front nine`): a `{typography.column-header}` head of Hole, the hole numbers and the sums (Out; In and Tot), then Par in `{colors.ink-muted}` and Score. A hole is `w-5.5 h-7`, a sum `w-7 font-semibold`, the label column `w-11`. A marked hole's strokes sit in a `size-5` mark (see Scorecard), its call screen-reader only after the figure (`5, Bogey`). An unplayed hole, and a nine not yet begun, is blank; so are the par row's sums, which the API doesn't send.

### Position and money boxes — `ui/src/shared/components/PositionCell.tsx`, `MoneyCell.tsx`
The first and last `<td>` of a card row; the box is the cell's fill, edge to edge, and each rounds its outer corners. `MoneyCell`'s `shape="standalone"` is the square box of a grid cell, the report's TOTAL CASH. Like `Button`, neither takes a `className`. `PositionCell` is `{typography.position-figure}` on `{colors.position-fill}` under `{colors.on-fill}`, and `{colors.gold}` under `{colors.on-gold}` for the row numbered 1. `kind="round"` numbers a draft round instead, and stays navy at 1: gold marks first place. `MoneyCell` takes the formatted amount and fills by its `moneyTone` — up `{colors.money-up-fill}`, down `{colors.money-down-fill}`, even `{colors.money-even-fill}` — under a white `{typography.money-figure}`.

A phone list card takes the same boxes as `div`s: `PositionCell`'s `shape="card-start"` is `w-9.5` (38px) and `MoneyCell`'s `shape="card-end"` is at least `min-w-24` (96px), its figure centred, so the boxes line up down the list and a wider figure still fits; each is the card's full height. One shape prop picks the element and the corners, so a box's fills and type have one definition.

### Report grids — `WeeklyReportView.tsx`, `RostersView.tsx`
One column per team, one row per draft round. Both grids are built from the same pieces in `ui/src/shared/components/`: `REPORT_GRID_TABLE_CLASS` (`text-xs border-separate border-spacing-1`), `ReportGridHead`, `ReportGridRoundLabel` and `ReportGridCell`. Rosters takes those and nothing the report adds.
- Head: `{typography.column-header}` on the `<thead>`; each team head adds `bg-header-band text-on-fill`, a `font-normal` number over the team name in `text-sm font-bold`. The sticky corner sits on the canvas.
- Round label: sticky, on the canvas, the number in `font-display font-bold text-xl text-ink`. A late-row round adds a `Late row` tag in `{colors.ink-muted}`, from the season's side-bet rounds; a season with none tags nothing. The report puts the round's stake under the tag.
- Cells: `bg-surface-card`, no border, no row hover. A golfer name in a cell is a button carrying `TAP_TARGET_CLASS`, so a round row is 44px plus the cell's padding and its other lines; the summary rows, with nothing to tap, stay dense.
- Weekly-report cell: a pick that earned has no fill and a `font-bold {colors.ink}` name, one that didn't a `{colors.ink-muted}` name; the payout lines are `{colors.ink-muted}`. A pick the live report marks in the field but has not paid prints `Playing` in the payout's place; one out of the field prints `-`. A late-row round's leader is `ReportGridCell`'s `late-row-leader` fill, `{colors.late-row-leader}` with a `border-l-4 {colors.gold}` edge.
- Summary rows: labels on the canvas, right-aligned and sticky; cells white, figures right-aligned. PREVIOUS is italic and muted. One transparent `h-2` spacer row sets the summary rows apart from the rounds.
- TOTAL CASH: a bold label on the canvas, then a square `MoneyCell` per team (`shape="standalone"`), in whole dollars — the only summary row toned by sign.

Below `sm` neither grid shows: the page shows one team at a time, chosen from a `TeamStrip` above its cards, the team this device chose until a chip is tapped and whenever the chosen id leaves the data, or the first team when that team is not in this season or none was chosen. Each round is a `ListCard` whose box is `PositionCell`'s `kind="round"`; an empty round's name is a `{colors.ink-faint}` em dash. What prints once under the grid — Shared Players, the undrafted top tens, the Won/Lost/Net chips — prints once, under either layout.
- Rosters: an `h3`, `TEAM · N picks` in `font-display font-bold text-sm uppercase tracking-wider text-ink-label`, over the cards: the golfer's name, a share under 100% on the second line, and an outlined `LateRowTag` in the total's place on a late-row round.
- Report Team totals: a `rounded bg-surface-card` group. `Total cash` over the team name, then a `card-end` `MoneyCell` in whole dollars; a `grid-cols-3 divide-x divide-hairline-soft` row of Previous, Week (Season in season mode) and Late row in signed cents; a `{colors.ink-muted}` line `N top 10s this season · +$M`. Total cash is those three figures' sum, so the card prints no Subtotal.
- Report pick card: the name, a share under 100% in brackets, `{colors.ink-muted}` when the pick earned nothing; a late-row round's `LateRowTag` on the second line, so the right column keeps the money: the payout (`T3 - +$18.00`, `Playing` in `{colors.ink-muted}` at body weight for an unpaid pick in the field, or `-`) over `+$season · N top 10s` outside season mode. A round the team leads takes `fill="late-row-leader"`, the grid cell's fill and gold edge, and the `late-row-leader` tag.

### Late row bets — `LateRowBetsView.tsx`
From `sm` a table card per side-bet round, then the all-rounds total table (`hidden sm:block`). Below `sm` (`sm:hidden`) one round at a time, chosen from a `ChipStrip` labelled `Late row round`: `Rd N` per round, then `Totals`. The first round shows until a chip is tapped and whenever the chosen round leaves the report. The rules sentence and the Won/Lost/Net chips print once, under either layout. Who leads and what moves are the server's `outcome`, `payout` and `amountPerTeam`, never worked out from rank or team count.
- Leaders: every `won` entry, ties included, is its own `rounded border-l-4 {colors.gold}` card on `{colors.late-row-leader}`, at least 54px: `Leading round N` over `TEAM · Golfer` in `{typography.card-row-name}`, truncating so the box stays on screen, over `+$season season earnings` in `text-sm {colors.ink-muted}`; then a `card-end` `MoneyCell` of the payout, in cents.
- Chasing: under `Chasing · each pays $X` (no amount when the report sends no rules), one white `rounded` list, rows `divide-hairline-soft`: the team in `{typography.card-row-name}` over the golfer, the season earnings at the right in `{typography.money-figure}` and `{colors.ink}`. A round every team ties for has no chasing list.
- No leader yet: `No team has earned in round N yet.` in `{colors.ink-muted}`, then every team in that list under `Round N`.
- Totals: a white card per team, the name, then a `card-end` `MoneyCell` of its all-rounds total.

### Money summary chips — `ui/src/shared/components/WonLostNetCards.tsx`, `StatCard.tsx`
A `flex flex-wrap gap-3 text-sm` row of `bg-surface-card rounded px-3 py-2` chips: a `{colors.ink-soft}` label (Won, Lost, Net), then a `font-semibold tabular-nums` amount coloured by the sign it prints, so a `$0` Won reads `{colors.ink-soft}`. The scoreboard, standings, late row bets and weekly report each pass their own formatted amounts: whole dollars on the standings and weekly report, cents on the other two.

### Banners — `ScoreboardView.tsx`, `UploadScheduleSection.tsx` (`SKIPPED_BANNER_CLASS`), `PlayerLinksPanel.tsx`
`px-3 py-2` (scoreboard `px-4 py-2`) `border rounded`, in one of two pairs: complete is `bg-semantic-success-tint/30 border-semantic-success-soft text-semantic-success-ink`; in progress or needing attention is `bg-semantic-warning-tint/30` (`/40` on the scoreboard) `border-semantic-warning-soft text-semantic-warning-ink`.

### Chips — `ui/src/shared/components/Chip.tsx`
`Chip` is `inline-flex rounded border px-1.5 py-0.5 text-xs uppercase tracking-wider whitespace-nowrap`, in one of three variants, none a status tone. `neutral` is `bg-surface-raised text-ink-body` with a transparent border: the player-links panel's partner and manual-override chips, and a team on the leaderboard. `outlined` has no fill, a `{colors.hairline-strong}` border and `{colors.ink-muted}` text, for something absent: the leaderboard's UNDRAFTED. `my-team` is the `YOU` tag's `bg-chrome text-on-chrome` with a transparent border: the leaderboard chip of the team this device chose, which the team list above already tags. A leaderboard row has a chip per team each player is on, in player order, so a split golfer shows every team and a pair one chip per partner; a rostered row takes no tone of its own. Like `Button`, it takes no `className`. A page's status is the status chip above, not one of these.

### Chip strip — `ui/src/shared/components/ChipStrip.tsx`, `TeamStrip.tsx`
A `role="group"` with a caller's label, one chip per `{id, label}`, scrolling sideways (`overflow-x-auto`) rather than wrapping, so the cards keep the top of the screen. Each chip is a button with `aria-pressed` and `TAP_TARGET_CLASS`, `rounded border px-3 font-display font-bold text-sm uppercase tracking-wider`: the selected one `{colors.chrome}` under `{colors.on-chrome}` (16.86:1), the rest `{colors.surface-card}` with a `{colors.hairline-strong}` border under `{colors.ink}`. Not `{colors.position-fill}`, the box's. The end fades (`mask-r-from-[calc(100%-2rem)]`) to show there is more, and pads `pr-8`, the fade's width, so the last chip scrolls clear of it. The chips are `aria-pressed` buttons, not a `tablist`, which promises arrow-key focus the strip does not move. `TeamStrip` is the strip labelled `Team` over a report's teams; Late Row Bets labels its rounds `Late row round`.

### You tag — `ui/src/shared/components/YouTag.tsx`
The team this device chose, after its name: `ml-2 inline-flex shrink-0 items-center align-middle whitespace-nowrap rounded px-1.5 py-0.5 font-display font-bold text-xs uppercase tracking-wider`, `{colors.chrome}` under `{colors.on-chrome}`. It reads `YOU` but a screen reader hears `your team`: the visible word is `aria-hidden` and an `sr-only` span carries the name. White, not gold: no text role pairs `{colors.gold}` with a navy fill. Like `LateRowTag`, it takes no `className`.

### Late-row tags — `ui/src/shared/components/LateRowTag.tsx`
A phone pick card's late-row round: `inline-flex items-center whitespace-nowrap rounded border px-1.5 py-0.5 font-display font-bold text-xs uppercase tracking-wider`. `late-row` reads `Late row`, outlined in `{colors.hairline-strong}` with `{colors.ink-muted}` text; `late-row-leader` reads `Leads late row`, `{colors.gold}` under `{colors.on-gold}`, so the lead never rests on the fill alone. Not `Chip`, which is body type and has no gold; like it, the tag takes no `className`.

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
- Open every page with `PageTitle`, its actions holding the status chip where the response has one, then the league/season picker on every page but Admin, hidden below `sm:` beside a `PhonePicker`; never a hand-rolled `h2`.
- Use `FIELD_CLASS` (through `TextField`, `DateField`, `SelectField`, or `FieldShell` for anything else) for every form input and select, and `PickerRow` for a page toolbar's select; both build on `CONTROL_CLASS`.
- Put a wide table in `overflow-x-auto -mx-3 sm:mx-0` with a `min-w-*`, so a phone scrolls the table rather than the page; a list or a report grid instead sets cards below `sm`, or inside a band card divided rows (see List tables, Report grids).
- Give every button, link, select, input and click handler `TAP_TARGET_CLASS` (`Button` carries its own). A link also needs `flex items-center` (or `inline-flex`), or its text sits at the top of the 44px; a checkbox gives it to its label.

### Don't
- Don't use a Tailwind shade, a hex, a ramp variable or a `--brand-*` palette variable in a component (`text-gray-400`, `#fff`, `var(--color-gray-400)`, `var(--brand-red)`). `PALETTE_RESTRICTIONS` in `ui/eslint.config.js` fails the lint on each.
- Don't merge two tokens because they share a step today (`ink` and `ink-strong`, `ink-soft` and `ink-faint`, `surface-raised` and `canvas`). They hold different roles and move separately.
- Don't use gold as text: the brand gold is 1.44:1 on white. It marks a leader: the standings' and scoreboard's first position box, a late-row round's leader in the report grid, on its phone pick card and on Late Row Bets' phone leader card, and first place in the Rules payout grid. It also rings the visitor's own team's row.
- Don't add shadows to cards or borders to them, but for a gold mark of state: a late-row leader's edge, my team's ring. White on sky carries depth; the modal, centred or a sheet, is the only floating surface.
- Don't add a dark theme piecemeal: the ink steps are chosen for the light canvas and the contrast table only measures that.

## Responsive Behavior

| Breakpoint | Behavior |
|---|---|
| Below `sm:` (<640px) | Page padding `px-safe-3 pt-4`, the foot clearing the tab bar. The standings, scoreboard teams and players are cards and the leaderboard divided rows (see List tables), the report and rosters show one team at a time, chosen from the team strip (see Report grids), and Late Row Bets one round at a time (see Late row bets); other wide tables scroll edge to edge. The title's actions wrap onto a line under the title, where the phone picker's summary button stands in for the league/season picker and the page's tournament field, and the nav shows its week button. Rankings chart 220px tall. Centred modal panels take 92vw; the More sheet, the picker sheet and the team picker span the width. |
| `sm:` to `lg:` (640–1024px) | Padding `px-safe-4 pt-6`, the foot clearing the tab bar; tables scroll inside the page margin. The actions sit at the title's right while they fit. Chart 320px. The tab bar still navigates. The team picker centres at the `detail` width. |
| `lg:` and up (≥1024px) | The nav shows its links inline, then My team, each 44px tall; the tab bar hides and the footer shows. Chart 400px. |

- Below `lg` the tab bar is fixed at the viewport's foot, each tab and each More sheet row 44px or taller; `<main>`'s foot clears it.
- A gutter or a bar at a screen edge clears the device's safe-area inset (see Spacing), since the viewport is `viewport-fit=cover`.
- Report grids keep their round column sticky wherever they show, from `sm`; only the team columns scroll.
- Modal height caps at `90dvh`, not `vh`: mobile `vh` ignores the on-screen keyboard and pushes the footer off-screen.

## Agent Prompt Guide

Use these when building UI here:

- **A card:** `bg-surface-card rounded-lg p-6`, opening with `<h3 className="text-sm font-semibold text-ink-label uppercase tracking-wider mb-4">`.
- **A filled button:** `<Button tone="primary" size="regular">` from `@/shared/components/Button`, with `tone="secondary"` or `tone="danger"` for the other fills and `size="compact"` in a header, panel or table row; never a hand-rolled fill.
- **A field:** `TextField` / `SelectField` / `DateField`, each from its own file in `@/shared/components/`; never a hand-rolled input class.
- **A tap target:** append `TAP_TARGET_CLASS` from `@/shared/components/formControls` to any text button, link, select or input (`Button` carries its own); a link adds `inline-flex items-center`.
- **A toolbar select:** `PickerRow` from `@/shared/components/PickerRow`, with the row's other controls as its children.
- **A page title:** `<PageTitle title="…" subtitle="…" actions={<><StatusChip variant={statusChipVariant(query.data)} /><div className={DESKTOP_PICKER_CLASS}><LeagueSeasonPicker /></div><PhonePicker tournament={…} renderField={…} /></>} />`, outside the page's loading gates.
- **A table head:** `<thead className={COLUMN_HEADER_CLASS}>` from `@/shared/components/columnHeader`, with no type on its rows or cells but an ink its fill needs, as a report team head's `text-on-fill`; body `divide-y divide-hairline-soft`, or for card rows a `border-separate border-spacing-y-1` table (see List tables).
- **A money value:** `tabular-nums`, or `font-mono` in the grids; a bottom-line amount adds `moneyToneClass` of the text it prints and keeps its table's weight, or in a card row is a `<MoneyCell display={…} />` (`shape="card-end"` on a phone list card).
- **A modal:** `FormModal` for a form, `ModalShell` when the body waits on a query, `ModalShell placement="sheet"` for a phone sheet (More, the phone picker), and `ModalShell placement="responsive"` for a sheet that centres from `sm` (the team picker).

Quick reference: nav, tab bar and footer on a navy bar, the current tab white over a red bar, canvas the brand sky, cards white, controls sky; text the brand navy, down to its muted blue (`ink-label`, `ink-muted`, `ink-soft`, `ink-faint`); brand red as a fill, red-700 as type; page titles navy, italic, condensed and uppercase; money green-800 / red-700, or on the standings, scoreboard, report and phone late row bets a filled box; report team heads the navy band, a late-row leader pale gold, your own team pale gold in a gold ring with a navy YOU tag; Saira, condensed to 75% with `font-display`.
