---
version: alpha
name: CWFGW4K
description: "A dark, table-first league tracker. A gray-900 canvas holds gray-800 cards and table chrome, with gray-700 for anything you can type into or that is selected. One green carries the brand: a green-700 fill on primary buttons and green-400 type on the CWFG mark and page titles. A surface's bottom-line money reads by sign — green won, red lost, gray even — and the two spreadsheet-style grids (weekly report, rosters) head their columns in gold. System sans throughout, small sizes, uppercase wide-tracked labels on table heads and card headings."

colors:
  canvas: "#101828"
  surface-card: "#1e2939"
  surface-raised: "#364153"
  surface-raised-hover: "#4a5565"
  overlay: "#000000"
  hairline-soft: "#1e2939"
  hairline: "#364153"
  hairline-strong: "#4a5565"
  control-border: "#4a5565"
  ink-strong: "#ffffff"
  ink: "#f3f4f6"
  ink-body: "#e5e7eb"
  ink-secondary: "#d1d5dc"
  ink-label: "#d1d5dc"
  ink-muted: "#99a1af"
  ink-soft: "#d1d5dc"
  ink-faint: "#99a1af"
  primary: "#008236"
  primary-hover: "#016630"
  on-primary: "#ffffff"
  primary-ink: "#05df72"
  primary-control: "#00c950"
  on-fill: "#ffffff"
  secondary: "#4a5565"
  secondary-hover: "#6a7282"
  disabled: "#4a5565"
  semantic-success-tint: "#0d542b"
  semantic-success-soft: "#008236"
  semantic-success: "#05df72"
  semantic-success-ink: "#7bf1a8"
  semantic-warning-tint: "#733e0a"
  semantic-warning-soft: "#a65f00"
  semantic-warning: "#fdc700"
  semantic-warning-ink: "#ffdf20"
  semantic-warning-border: "#d08700"
  semantic-danger: "#ffa2a2"
  semantic-danger-hover: "#ffc9c9"
  semantic-danger-strong: "#ffa2a2"
  semantic-danger-fill: "#e7000b"
  semantic-danger-fill-hover: "#c10007"
  semantic-danger-border: "#fb2c36"
  semantic-info-soft: "#1447e6"
  semantic-info: "#51a2ff"
  semantic-info-hover: "#8ec5ff"
  semantic-info-ink: "#dbeafe"
  semantic-info-fill: "#155dfc"
  semantic-info-fill-hover: "#1447e6"
  gold-tint: "#733e0a"
  gold-soft: "#a65f00"
  gold: "#d08700"
  gold-bright: "#ffdf20"
  on-gold: "#000000"
  on-gold-earnings: "#016630"
  side-bet-winner: "#9f0712"

typography:
  brand-mark:
    fontFamily: System Sans
    fontSize: 18px
    fontWeight: 700
    lineHeight: 1.56
    letterSpacing: 0.025em
  page-title:
    fontFamily: System Sans
    fontSize: 20px
    fontWeight: 600
    lineHeight: 1.40
    letterSpacing: 0
  modal-title:
    fontFamily: System Sans
    fontSize: 18px
    fontWeight: 700
    lineHeight: 1.56
    letterSpacing: 0
  card-heading:
    fontFamily: System Sans
    fontSize: 14px
    fontWeight: 600
    lineHeight: 1.43
    letterSpacing: 0.05em
    textTransform: uppercase
  column-header:
    fontFamily: System Sans
    fontSize: 12px
    fontWeight: 400
    lineHeight: 1.33
    letterSpacing: 0.05em
    textTransform: uppercase
  body:
    fontFamily: System Sans
    fontSize: 14px
    fontWeight: 400
    lineHeight: 1.43
    letterSpacing: 0
  control:
    fontFamily: System Sans
    fontSize: 16px
    fontWeight: 400
    lineHeight: 1.50
    letterSpacing: 0
  caption:
    fontFamily: System Sans
    fontSize: 12px
    fontWeight: 400
    lineHeight: 1.33
    letterSpacing: 0
  button:
    fontFamily: System Sans
    fontSize: 14px
    fontWeight: 500
    lineHeight: 1.43
    letterSpacing: 0
  button-compact:
    fontFamily: System Sans
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
  button-info:
    backgroundColor: "{colors.semantic-info-fill}"
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
---

## Overview

Castlewood Fantasy Golf is read almost entirely as tables: the week's scoreboard, the weekly report grid, team standings, player rankings, rosters. The design exists to make those tables legible on a dark screen and to make money read at a glance.

The app is **dark only**. The page is `{colors.canvas}` (gray-900); cards, the top nav and table heads sit one step lighter on `{colors.surface-card}` (gray-800); form controls, the active nav item and highlighted rows sit one step lighter again on `{colors.surface-raised}` (gray-700). Depth comes from that three-step gray ladder, not from shadows.

The one brand colour is green: `{colors.primary}` (green-700) fills primary buttons, and `{colors.primary-ink}` (green-400) colours the CWFG mark and the title of each data page.

Only the bottom line reads by sign: Total Cash on the standings and the weekly report, Weekly +/- on the scoreboard, the late row bets total, a golfer's season earnings, and the Won, Lost and Net chips. `{colors.semantic-success}` (green-400) is a gain, `{colors.semantic-danger}` (red-300) a loss, and `{colors.ink-soft}` an amount that prints as `$0`. Every other signed amount keeps its table's ink, so the colour points at the number that settles the row; the one exception is a payout in the report grid's pick cells, which takes the cell's state colour (see On Gold Earnings). `moneyTone` reads the sign from the formatted text, so a cents column and a whole-dollar column each tone at the precision they show.

The weekly report and the rosters page are laid out like the league's spreadsheet, and they keep its gold: a `{colors.gold}` (yellow-600) header row, `{colors.gold-bright}` (yellow-300) cells for a pick that earned a payout, and a gold-washed TOTAL CASH row.

**Key characteristics:**
- Dark canvas, no light mode. `index.css` sets `{colors.canvas}` and `{colors.ink}` on `body`.
- Gray ladder for depth: canvas → card → raised. Only the modal and the error screen cast a shadow.
- Small type: `text-sm` (14px) body, `text-xs` (12px) for helper text, table heads and the report grids. Every select and text input is the exception at `text-base` (16px), because iOS zooms the page into a smaller control. The roster file input stays `text-sm`: iOS opens a picker for it rather than zooming.
- Uppercase `tracking-wider` labels mark structure: table heads, card headings, the league/season picker's labels.
- Tabular numbers (`tabular-nums` or `font-mono`) on every money and count column. The Rules page's payout schedule does not have them yet.
- Corners are `rounded` (4px) on controls and chips, `rounded-lg` (8px) on cards and the modal. Nothing is pill-shaped.

## Colors

Every colour below is a `--color-<name>` token in the `@theme` block of `ui/src/index.css`, and every token there has an entry here; `ui/src/index.css.test.ts` fails when the two disagree, by name or by value. Each value is an alias to a Tailwind ramp step, named after the hex. Components use the token (`bg-surface-card`), never the step (`bg-gray-800`); the lint rejects the latter.

The contrast table in `ui/src/index.css.test.ts` pins every text token at WCAG AA (4.5:1) on each backdrop it sits on. That floor is why some tokens share a step with a neighbour, and why the lighter `{colors.ink-soft}` sits below `{colors.ink-muted}` in this list: each ink step holds a role, not a brightness rank.

### Surface
- **Canvas** (`{colors.canvas}`, gray-900): the page background, and the thin spacer rows between blocks of the weekly report grid.
- **Surface Card** (`{colors.surface-card}`, gray-800): the top nav, every card, table heads, the money summary chips, the modal panel. At `/60` it backs the league/season picker bar and the row hover on list tables.
- **Surface Raised** (`{colors.surface-raised}`, gray-700): form fields, the active nav item, the neutral tag chip, the report grid's highlighted row.
- **Surface Raised Hover** (`{colors.surface-raised-hover}`, gray-600): a raised button hovered — the Admin page's logout button and the roster file input's button.
- **Overlay** (`{colors.overlay}`, black): the modal scrim, at `/60`.

### Lines
- **Hairline Soft** (`{colors.hairline-soft}`, gray-800): row dividers (`divide-y`) inside the scoreboard, standings and player-rankings tables.
- **Hairline** (`{colors.hairline}`, gray-700): the nav's bottom border, the underline under each page header, report-grid cell borders, the modal header rule, the rankings chart's gridlines (`stroke-hairline`).
- **Hairline Strong** (`{colors.hairline-strong}`, gray-600): borders on the modal panel and error card, and on the report grids' header and highlighted rows.
- **Control Border** (`{colors.control-border}`, gray-600): the border on every input and select.

### Text
- **Ink Strong** (`{colors.ink-strong}`, white): the active nav item, nav and close-button hover, the logout button, a competitor's name in the player-links panel.
- **Ink** (`{colors.ink}`, gray-100): the default text colour, set on `body` and the app root; team names in the scoreboard and standings tables.
- **Ink Body** (`{colors.ink-body}`, gray-200): golfer names inside the report and roster grid cells, and the neutral tag chip's text.
- **Ink Secondary** (`{colors.ink-secondary}`, gray-300): running text on the Rules page, the mobile nav's idle links, golfer names and the Top 10 Earnings column on the scoreboard.
- **Ink Label** (`{colors.ink-label}`, gray-300): uppercase card headings, the scoreboard's table heads, a column-filter select on hover or focus.
- **Ink Muted** (`{colors.ink-muted}`, gray-400): helper and caption text — field labels, the line under a page title, loading messages, desktop nav idle links, chart axis labels (`fill-ink-muted`).
- **Ink Soft** (`{colors.ink-soft}`, gray-300): rank numbers, the labels on the money summary chips, empty-state lines, the picker's labels, a bottom-line amount that prints as `$0`, and in the report grid the dash for a pick that earned nothing and the top-ten line under SUBTOTAL. Lighter than `{colors.ink-muted}` because it also sits on `{colors.surface-raised}` and `{colors.side-bet-winner}`.
- **Ink Faint** (`{colors.ink-faint}`, gray-400): the em dash in an empty cell, the `/` between a shared player's owners on the rosters page, the owner column of an unrostered golfer on the scoreboard's leaderboard.

### Brand
- **Primary** (`{colors.primary}`, green-700): the fill on every primary button — form submit, Create, Save, Finalize, Reload.
- **Primary Hover** (`{colors.primary-hover}`, green-800): that fill, hovered.
- **On Primary** (`{colors.on-primary}`, white): text on a primary fill.
- **Primary Ink** (`{colors.primary-ink}`, green-400): the CWFG mark, the title of each data page, a player name hovered in player rankings.
- **Primary Control** (`{colors.primary-control}`, green-500): the checked colour of a native checkbox (`accent-primary-control`), today only the live-overlay toggle.

### Neutral fills
- **On Fill** (`{colors.on-fill}`, white): text on the secondary, danger and info fills.
- **Secondary** (`{colors.secondary}`, gray-600): the Cancel button in a form modal and the roster upload's back-out button.
- **Secondary Hover** (`{colors.secondary-hover}`, gray-500): that fill, hovered.
- **Disabled** (`{colors.disabled}`, gray-600): every filled button while disabled (`disabled:bg-disabled`). Its label is exempt from the contrast floor.

### Semantic

Each family takes only the rungs it uses. `-tint` is a wash, always at an opacity (`/10` to `/40`); `-soft` is a banner border or a chip fill; the bare name is status text; `-ink` is text on that family's own wash; `-fill` is a solid button. Danger and info carry `-fill` because the text step that clears AA on the dark canvas is too light to hold white button text.

- **Success** — `{colors.semantic-success-tint}` (green-900) washes a rostered golfer's leaderboard row (`/10`) and the tournament-complete banner (`/30`); `{colors.semantic-success-soft}` (green-700) borders that banner; `{colors.semantic-success}` (green-400) is a bottom-line gain, a side-bet winner's payout in the report grid, a rostered golfer's team on the leaderboard, the rule amounts on the Rules page and success messages; `{colors.semantic-success-ink}` (green-300) is the complete banner's text.
- **Warning** — `{colors.semantic-warning-tint}` (yellow-900) washes the in-progress banner (`/40`) and the skipped-rows and player-links notices (`/30`); `{colors.semantic-warning-soft}` (yellow-700) borders them, and at `/50` fills the weekly report's LIVE chip; `{colors.semantic-warning}` (yellow-400) marks an ambiguous or skipped count and the multipliers on the Rules page; `{colors.semantic-warning-ink}` (yellow-300) is the text on those washes; `{colors.semantic-warning-border}` (yellow-600) borders the select for an ambiguous roster match.
- **Danger** — `{colors.semantic-danger}` (red-300) is a bottom-line loss, field errors and failed loads; `{colors.semantic-danger-hover}` (red-200) is a Remove link hovered; `{colors.semantic-danger-strong}` (red-300, the same step) is an action error beside the scoreboard's buttons and in the player-links panel; `{colors.semantic-danger-fill}` (red-600) and `{colors.semantic-danger-fill-hover}` (red-700) are the destructive admin buttons — Reset, Clean, Delete; `{colors.semantic-danger-border}` (red-500) borders an invalid payout-multiplier input.
- **Info** — `{colors.semantic-info-fill}` (blue-600) and `{colors.semantic-info-fill-hover}` (blue-700) are secondary actions beside a primary one — Preview on the roster upload, Manage player links on the scoreboard, Save link in the player-links panel; `{colors.semantic-info}` (blue-400) and `{colors.semantic-info-hover}` (blue-300) are text buttons and an informational count; `{colors.semantic-info-soft}` (blue-700) fills the manual-override chip, and `{colors.semantic-info-ink}` (blue-100) is its text.

### Report grids
- **Gold** (`{colors.gold}`, yellow-600): the header row of the weekly report and roster grids, including its sticky first cell; at `/50` the cell borders of the TOTAL CASH row.
- **Gold Bright** (`{colors.gold-bright}`, yellow-300): a weekly-report cell whose pick earned a payout.
- **Gold Soft** (`{colors.gold-soft}`, yellow-700): the TOTAL CASH row's wash, at `/30` over the canvas.
- **Gold Tint** (`{colors.gold-tint}`, yellow-900): the TOTAL CASH row's label cell.
- **On Gold** (`{colors.on-gold}`, black): header text on `{colors.gold}` and golfer names on `{colors.gold-bright}`.
- **On Gold Earnings** (`{colors.on-gold-earnings}`, green-800): the payout amount inside a `{colors.gold-bright}` cell, where green-400 would not read.
- **Side Bet Winner** (`{colors.side-bet-winner}`, red-800): the weekly-report cell of the pick that won that round's side bet. It overrides gold-bright; its text drops back to `{colors.ink-body}`.

### Outside the token set
- **Rankings chart lines** — `CHART_COLORS` in `ui/src/features/rankings/rankingsChartModel.ts` is a 13-hex series palette, one per team. It identifies a series rather than holding a role, so it carries a lint waiver instead of tokens.
- **PDF export** — `ui/src/features/weeklyReport/weeklyReportPdf.ts` prints a light-background report with its own RGB constants. Paper is not the screen; nothing here applies to it.

## Typography

### Font family
- **System Sans** — `ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto, sans-serif`, set on `body` in `ui/src/index.css`. Every text style uses it.
- **System Mono** — Tailwind's `font-mono`. The weekly report grid's money figures, late row bets' golfer names and amounts, the golfer-history modal's place and money columns, and the ESPN week and event names on the schedule import.

### Hierarchy

| Token | Size | Weight | Tracking | Classes | Where |
|---|---|---|---|---|---|
| `{typography.brand-mark}` | 18px | 700 | 0.025em | `text-lg font-bold tracking-wide` | The CWFG mark in the nav |
| `{typography.page-title}` | 20px | 600 | 0 | `text-xl font-semibold text-primary-ink` | Title of each data page's header |
| `{typography.modal-title}` | 18px | 700 | 0 | `text-lg font-bold` | Modal header |
| `{typography.card-heading}` | 14px | 600 | 0.05em, uppercase | `text-sm font-semibold uppercase tracking-wider text-ink-label` | Heading inside a card |
| `{typography.column-header}` | 12px | 400 | 0.05em, uppercase | `text-xs uppercase tracking-wider` | Table heads, the picker's labels |
| `{typography.body}` | 14px | 400 | 0 | `text-sm` | Tables, running text |
| `{typography.control}` | 16px | 400 | 0 | `text-base` | Form fields, toolbar selects, the league/season selects, the column-header filter |
| `{typography.caption}` | 12px | 400 | 0 | `text-xs` | Field labels, helper text, field errors |
| `{typography.button}` | 14px | 500 | 0 | `text-sm font-medium` | Standard buttons |
| `{typography.button-compact}` | 12px | 500 | 0 | `text-xs font-medium` | Buttons in a page's header row or a panel row |
| `{typography.report-figure}` | 12px | 400 | 0 | `font-mono` in a `text-xs` table | Money in the weekly report grid |

### Principles
- **Weights in use are 400, 500, 600 and 700.** `font-semibold` is the emphasis weight in tables (team totals, money chips); `font-bold` marks the nav mark, modal titles, the report grids' header and total rows.
- **Uppercase is structure, not emphasis.** Table heads, card headings and team names in tables are uppercase; body text never is.
- **Numbers line up.** A column of money uses `tabular-nums` (list tables) or `font-mono` (the weekly report grid, late row bets), right-aligned in list tables and centred in the grid.

## Layout

### Spacing
- Tailwind's default 4px scale; there are no spacing tokens.
- **Page**: `<main>` is `max-w-screen-2xl mx-auto px-3 sm:px-4 py-4 sm:py-6`. The nav and the picker bar centre their contents in `max-w-6xl`.
- **Page body**: a stack spaced `space-y-6` or `space-y-4`, the data views opening with a header ruled `border-b border-hairline pb-3`.
- **Cards**: `p-6` for form and rules cards, `p-4` for the rankings chart and late-row-bet tables.
- **List tables**: cells `px-4 py-3` (scoreboard teams) or `px-3 py-2` (leaderboard, standings).
- **Report grids**: cells `px-1 py-1` to `px-2 py-1.5`, `border-collapse`, every cell bordered. They are the densest surface in the app on purpose: one column per team must fit.
- **Forms**: fields in a `flex flex-wrap items-end gap-4` row inside a card; a form modal stacks them with `space-y-3`.

### Containers
- List tables and report grids sit in `overflow-x-auto -mx-3 sm:mx-0`, so on a phone they scroll sideways edge to edge. The scoreboard's two tables also set a floor (`min-w-[500px]`, `min-w-[640px]`) so their columns don't crush first.
- The report grids pin the first column (`sticky left-0`) so the round label stays visible while the teams scroll.

## Elevation & Depth

| Level | Treatment | Use |
|---|---|---|
| 0 | `{colors.canvas}` | The page |
| 1 | `{colors.surface-card}`, no border, no shadow | Cards, nav, table heads, summary chips |
| 2 | `{colors.surface-raised}` | Fields, the active nav item, chips, the report's highlighted row |
| 3 | `{colors.surface-card}` + `{colors.hairline-strong}` border + `{elevation.modal}` (`shadow-xl`) over `{colors.overlay}`/60 | The modal panel, and the error screen's card |

On a dark canvas a drop shadow barely shows, so lightness carries depth: each level up is one gray step lighter. `shadow-xl` appears only on the two floating panels.

## Shapes

| Token | Value | Class | Use |
|---|---|---|---|
| `{rounded.sm}` | 4px | `rounded` | Buttons, fields, chips, banners, nav items |
| `{rounded.lg}` | 8px | `rounded-lg` | Cards, the modal panel, the error card |

Table cells are square. No component is pill-shaped (`rounded-full`), and no corner is larger than 8px.

## Components

### Top nav — `ui/src/App.tsx`
A `{colors.surface-card}` bar with a `{colors.hairline}` bottom border. The CWFG mark (`{typography.brand-mark}`, `{colors.primary-ink}`) on the left; at `lg:` and up, a row of links on the right.
- Desktop link: `px-3 py-2 rounded text-sm font-medium`; idle `text-ink-muted hover:text-ink-strong`, active `bg-surface-raised text-ink-strong`.
- Below `lg:`, a 44px (`w-11 h-11`) menu button opens a stacked list; its links are `px-4 py-3 rounded text-base font-medium`, idle `text-ink-secondary`.

### League / season picker — `ui/src/features/leagues/LeagueSeasonPicker.tsx`
A bar under the nav: `bg-surface-card/60 border-b border-hairline`, two labelled selects. Labels are `text-ink-soft font-medium uppercase tracking-wider`; selects are `bg-surface-raised border border-control-border rounded text-base`. Stacks one per row below `sm:`.

### Page header — `ScoreboardView.tsx`, `WeeklyReportView.tsx`, `RankingsView.tsx`, `PlayerRankingsView.tsx`, `RostersView.tsx`
`<header className="border-b border-hairline pb-3">` holding a `{typography.page-title}` in `{colors.primary-ink}` and a `text-xs text-ink-muted` line beneath it. The Admin, Rules and Late Row Bets pages title themselves `text-xl font-bold` in the inherited `{colors.ink}` instead, and Player Rankings carries such a title above its controls as well as the view's header beneath them.

### Card — admin sections, `RulesPage.tsx`, `RankingsChart.tsx`, `LateRowBetsView.tsx`
`bg-surface-card rounded-lg p-6` (`p-4` for the chart and late-row-bet tables), no border, no shadow. A card opens with a `{typography.card-heading}` (`mb-4`, or `mb-3` over a table or chart).

### Buttons
| Variant | Classes | Example |
|---|---|---|
| Primary | `bg-primary hover:bg-primary-hover disabled:bg-disabled text-on-primary px-4 py-2 rounded text-sm font-medium` | Form submit in `FormModal.tsx`, Create in `CreateLeagueSection.tsx` |
| Secondary | `bg-secondary hover:bg-secondary-hover text-on-fill …` | Cancel in `FormModal.tsx` |
| Danger | `bg-semantic-danger-fill hover:bg-semantic-danger-fill-hover disabled:bg-disabled text-on-fill …` | `RESET_BUTTON_CLASS` in `ResetTournamentSection.tsx`; Clean and Delete Season |
| Info | `bg-semantic-info-fill hover:bg-semantic-info-fill-hover text-on-fill …` | `PREVIEW_BUTTON_CLASS` in `UploadRostersSection.tsx`, `SAVE_LINK_BUTTON_CLASS` in `PlayerLinksPanel.tsx` |
| Raised | `bg-surface-raised hover:bg-surface-raised-hover text-ink-strong px-3 py-1.5 rounded text-sm` | Log out in `AdminPage.tsx` |
| Outline | `bg-surface-card border border-control-border rounded px-3 py-2 text-sm hover:bg-surface-raised` | `DOWNLOAD_BUTTON_CLASS` in `WeeklyReportPage.tsx` |
| Text | `text-semantic-info hover:text-semantic-info-hover text-xs` | `CreateSeasonSection.tsx`, `PlayerLinksPanel.tsx` |

Compact buttons in a header row or panel row (`FINALIZE_BUTTON_CLASS` and `LINKS_BUTTON_CLASS` in `ScoreboardPage.tsx`, `SAVE_LINK_BUTTON_CLASS`) drop to `px-3 py-1 text-xs`. A disabled filled button switches to `{colors.disabled}`; a disabled outline or text button takes `disabled:opacity-50`.

Every button, every variant and the compact ones included, adds `TAP_TARGET_CLASS` (`min-h-11`, 44px) from `formFields.tsx`. The compact padding narrows a button; it never makes it shorter than a thumb.

### Form fields — `ui/src/shared/components/formFields.tsx`
`FIELD_CLASS` is `bg-surface-raised border border-control-border rounded px-3 py-2 text-base min-h-11 w-full`, built on `CONTROL_CLASS` and `TAP_TARGET_CLASS` and shared by `TextField`, `DateField` and `SelectField`. `FieldShell` puts a `block text-xs text-ink-muted mb-1` label above the control and a `mt-1 text-semantic-danger text-xs` error, with `role="alert"`, below it. Size a field with the `width` prop, not by appending a width class.

A select in a page's toolbar rather than a form — the scoreboard's tournament, player rankings' Through — is `PickerRow.tsx`: an uppercase label, then the select one step down on `bg-surface-card` over the same `CONTROL_CLASS` and `TAP_TARGET_CLASS`, the outline button's surface, then any trailing controls. The row is `flex-wrap`; below `sm:` the select is `w-full min-w-0 truncate`, so a long tournament name truncates rather than scroll the page sideways. The admin payout input is `CONTROL_CLASS` at `w-16`. The schedule-multiplier input and the roster-match select copy its sizing by hand: each has a border colour of its own, and two border colours would tie on stylesheet order.

A table head that filters its column uses `ColumnHeaderFilter.tsx`: a transparent, borderless select that inherits the head's colour and turns `{colors.ink-label}` on hover or focus. It is `text-base` with `TAP_TARGET_CLASS` inside the `text-xs` head, so its column is as wide as its longest option at 16px.

### Modal — `ui/src/shared/components/ModalShell.tsx`, `FormModal.tsx`
A fixed scrim `bg-overlay/60`, centring a panel `bg-surface-card rounded-lg shadow-xl border border-hairline-strong`, capped at `max-h-[90dvh]` and scrolling inside. Header `px-5 py-4 border-b border-hairline` with a `{typography.modal-title}` and a 44px-tall `×` close button; body `px-5 py-4`. Widths are `form` (22rem) and `detail` (28rem), each capped at 92vw, and `wide` (`w-full max-w-3xl`). `FormModal` adds a primary submit stretched with `flex-1` and a secondary Cancel. Focus is trapped while open, and Escape closes it.

### List tables — `ScoreboardView.tsx`, `RankingsView.tsx`, `PlayerRankingsView.tsx`
`text-sm`, full width. Head on the scoreboard: `bg-surface-card text-ink-label text-xs uppercase tracking-wider`; standings and player rankings drop the fill and use `text-ink-soft`. Body: `divide-y divide-hairline-soft`, rows `hover:bg-surface-card/60`. A row is as tall as its tap target: where a cell holds a golfer-name button, that button's `TAP_TARGET_CLASS` sets the row's height. A row with nothing to tap keeps the dense padding.

### Report grids — `ui/src/features/weeklyReport/WeeklyReportView.tsx`, `ui/src/features/rosters/RostersView.tsx`
One column per team, one row per draft round, `text-xs border-collapse`, every cell bordered — in `{colors.hairline}` unless a row below says otherwise.
- Header row: `bg-gold text-on-gold font-bold` with `border-hairline-strong`; team names uppercase.
- Round rows: `bg-surface-card`; the round label is sticky, `font-bold text-ink-muted`. On the rosters page a side-bet round dims to `bg-surface-card/70`.
- A golfer name in a round cell is a button carrying `TAP_TARGET_CLASS`, so the round row grows to 44px; the summary rows, with nothing to tap, stay dense.
- Weekly-report cell: `{colors.gold-bright}` with `{colors.on-gold}` text when the pick earned, `{colors.side-bet-winner}` when it won the round's side bet, otherwise no fill. `roundCellBg` and `roundTextColor` in `weeklyReportModel.ts` decide which.
- Summary rows: `bg-surface-raised font-bold` with `border-hairline-strong` for highlighted totals, `bg-surface-card` for sub-rows, separated by `h-1 bg-canvas` spacer rows.
- TOTAL CASH row: `bg-gold-soft/30 font-bold text-sm`, borders `border-gold/50`, label cell `bg-gold-tint`, amounts coloured by sign — the only summary row coloured by sign.

### Money summary chips — `ScoreboardView.tsx`, `RankingsView.tsx`, `LateRowBetsView.tsx`, `WeeklyReportView.tsx` (`StatCard`)
A `flex flex-wrap gap-3 text-sm` row of `bg-surface-card rounded px-3 py-2` chips: a `{colors.ink-soft}` label (Won, Lost, Net), then a `font-semibold tabular-nums` amount coloured by the sign it prints, so a `$0` Won reads gray.

### Banners — `ScoreboardView.tsx`, `UploadScheduleSection.tsx` (`SKIPPED_BANNER_CLASS`), `PlayerLinksPanel.tsx`
`px-3 py-2` (scoreboard `px-4 py-2`) `border rounded`, in one of two pairs: complete is `bg-semantic-success-tint/30 border-semantic-success-soft text-semantic-success-ink`; in progress or needing attention is `bg-semantic-warning-tint/30` (`/40` on the scoreboard) `border-semantic-warning-soft text-semantic-warning-ink`.

### Chips — `PlayerLinksPanel.tsx`, `WeeklyReportView.tsx`
`text-xs rounded px-1.5 py-0.5`, uppercase with `tracking-wider` in the player-links panel. Neutral (partner): `bg-surface-raised text-ink-body`. Info (manual override): `bg-semantic-info-soft text-semantic-info-ink`. Live (the weekly report's LIVE — projected standings): `bg-semantic-warning-soft/50 text-semantic-warning-ink px-2`.

### Loading and errors — `ui/src/shared/components/QueryState.tsx`, `ErrorBoundary.tsx`
`QueryState` renders `Loading {label}…` in `{colors.ink-muted}` and `Failed to load {label}: …` in `{colors.semantic-danger}`, as plain paragraphs. `ErrorBoundary` replaces the app with a centred `max-w-md` card at level 3 (see Elevation), a `text-2xl font-bold` heading and a primary Reload button.

### Live overlay toggle — `ui/src/shared/components/LiveOverlayToggle.tsx`
A native checkbox, `accent-primary-control w-4 h-4`, inside a `text-xs text-ink-muted` label carrying `TAP_TARGET_CLASS`: the box is too small to hit, so the label is the target. It renders only while the selected tournament is in the season's current week.

### Rankings chart — `ui/src/features/rankings/RankingsChart.tsx`
An SVG line chart in a `p-4` card. Gridlines `stroke-hairline`, axis labels `fill-ink-muted`, one line per team in `CHART_COLORS` (see Outside the token set), and a `text-xs text-ink-muted` legend. Height steps `h-[220px] sm:h-[320px] lg:h-[400px]`.

## Patterns & Do's / Don'ts

### Do
- Name a colour by its token: `text-ink-muted`, `bg-surface-card`. A new role gets a token in `ui/src/index.css` and an entry in this file's `colors:` in the same change, plus a row in the contrast table when it colours text.
- Colour only a surface's bottom-line money, through `moneyToneClass` on the text it prints; leave every other amount in the table's ink. Set all money in `tabular-nums` or `font-mono`.
- Open a data page with the page header: a primary-ink title over a muted caption, underlined in `{colors.hairline}`.
- Use `FIELD_CLASS` (through `TextField`, `DateField`, `SelectField`, or `FieldShell` for anything else) for every form input and select, and `PickerRow` for a page toolbar's select; both build on `CONTROL_CLASS`.
- Put a wide table in `overflow-x-auto -mx-3 sm:mx-0` with a `min-w-*`, so a phone scrolls the table rather than the page.
- Give every button, link, select, input and click handler `TAP_TARGET_CLASS`. A link also needs `flex items-center` (or `inline-flex`), or its text sits at the top of the 44px; a checkbox gives it to its label.

### Don't
- Don't use a Tailwind shade, a hex, or a ramp variable in a component (`text-gray-400`, `#fff`, `var(--color-gray-400)`). `PALETTE_RESTRICTIONS` in `ui/eslint.config.js` fails the lint on each.
- Don't merge two tokens because they share a step today (`ink-soft` and `ink-secondary`, `hairline-soft` and `surface-card`). They hold different roles and move separately.
- Don't use gold outside the report grids. It means "spreadsheet header" or "earned a payout", nothing else.
- Don't add shadows to cards or borders to them. Lightness carries depth; the modal is the only floating surface.
- Don't add a light theme piecemeal: the ink steps are chosen for a dark canvas and the contrast table only measures that.

## Responsive Behavior

| Breakpoint | Behavior |
|---|---|
| Below `sm:` (<640px) | Page padding `px-3 py-4`. Wide tables scroll edge to edge. The picker's selects stack one per row. Rankings chart 220px tall. Modal panels take 92vw. |
| `sm:` to `lg:` (640–1024px) | Padding `px-4 py-6`; tables scroll inside the page margin. Picker selects sit in one row. Chart 320px. The nav is still the menu button. |
| `lg:` and up (≥1024px) | The nav shows its links inline and hides the menu button. Chart 400px. |

- The menu button is 44px square (`w-11 h-11`), the same height `TAP_TARGET_CLASS` gives every other target.
- Report grids keep their round column sticky at every width; only the team columns scroll.
- Modal height caps at `90dvh`, not `vh`: mobile `vh` ignores the on-screen keyboard and pushes the footer off-screen.

## Agent Prompt Guide

Use these when building UI here:

- **A card:** `bg-surface-card rounded-lg p-6`, opening with `<h3 className="text-sm font-semibold text-ink-label uppercase tracking-wider mb-4">`.
- **A primary button:** `bg-primary hover:bg-primary-hover disabled:bg-disabled text-on-primary px-4 py-2 rounded text-sm font-medium`.
- **A destructive button:** `bg-semantic-danger-fill hover:bg-semantic-danger-fill-hover disabled:bg-disabled text-on-fill px-4 py-2 rounded text-sm font-medium`.
- **A field:** `TextField` / `SelectField` / `DateField` from `@/shared/components/formFields`; never a hand-rolled input class.
- **A tap target:** append `TAP_TARGET_CLASS` from `@/shared/components/formFields` to any button, link, select or input; a link adds `inline-flex items-center`.
- **A toolbar select:** `PickerRow` from `@/shared/components/PickerRow`, with the row's other controls as its children.
- **A page header:** `<header className="border-b border-hairline pb-3">` with `<h2 className="text-xl font-semibold text-primary-ink">` and `<p className="text-xs text-ink-muted">`.
- **A table head:** `bg-surface-card text-ink-label text-xs uppercase tracking-wider`, body `divide-y divide-hairline-soft`.
- **A money value:** `tabular-nums`, or `font-mono` in the grids; a bottom-line amount adds `moneyToneClass` of the text it prints and keeps its table's weight.
- **A modal:** `FormModal` for a form, `ModalShell` when the body waits on a query.

Quick reference: canvas gray-900, cards gray-800, controls gray-700; text from white (`ink-strong`) down to gray-400 (`ink-muted`, `ink-faint`); brand green-700 fill, green-400 type; money green-400 / red-300; report headers yellow-600.
