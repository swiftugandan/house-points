# Design

The visual system for House Points, following [impeccable.style](https://impeccable.style) conventions. Product context is in `PRODUCT.md`. Every color, font, size and radius used in the app must come from this file.

## Direction: "Passbook"

The app is the family's savings passbook: ruled lines, dated rows, figures in a column, and a stamp at every payday. That gives a parent something that feels trustworthy and gives a child something that feels like a real bank. The motif is carried by **typography and rules (thin dividing lines), not imitation paper**. There is no paper texture, no stitching and no fake leather.

**Alternatives considered:**
- **Piggy:** cartoon mascot, bright fills, rounded everything. *Rejected:* it reads as a toy, which undermines the bank's credibility. Children of 10 to 14 reject it, and it invites gamification (see the bans below).
- **Fintech dashboard:** dark theme, charts, a large figure above three smaller stats. *Rejected:* it's the hero-metric template with neon-on-dark, it's illegible to a 6-year-old, and it sells performance where we want patience.

**What makes it recognisable:**
1. **The weekly ledger.** History is a column of ruled rows grouped by week. Each week closes with a double rule and a **payday line** in brass: "Payday · 1% of 200 · +2".
2. **Child colors.** Each child has one fixed identity color. It colors their avatar, their accent in the child view, and nothing else.
3. **Brass means interest, and only interest.** If something is brass, it's money the child's patience earned.

## Color

The palette is fixed. **Material dynamic color is turned off**, because a child's color and brass's meaning must be identical on both parents' phones.

Neutrals are tinted towards blue-green ink. There's no pure black, pure grey or cream. All contrast figures below are against `paper`/`ground` and were checked against WCAG 2.2 [SPEC R15].

### Light

| Token | Hex | Use | Contrast on paper |
|---|---|---|---|
| `paper` | `#F5F7F6` | App background | n/a |
| `surface` | `#FFFFFF` | Sheets, the record panel | n/a |
| `sunk` | `#E9EEEC` | Pressed states, input wells | n/a |
| `rule` | `#D3DAD7` | Ledger rules, dividers (decoration only, never the only cue) | n/a |
| `ink` | `#18232B` | Text, figures | 14.9:1 |
| `ink-muted` | `#52606A` | Dates, secondary text | 6.0:1 |
| `action` | `#1E6B4E` | The single primary action; positive confirmation | 6.0:1 (white on it 6.4:1) |
| `interest` | `#8A5A00` | Interest only: payday lines, the "expected interest" note | 5.5:1 |
| `deduct` | `#A3412A` | Deductions and the overdrawn flag only | 5.8:1 |

### Dark

| Token | Hex | Contrast on ground |
|---|---|---|
| `ground` | `#121A1F` | n/a |
| `surface` | `#1B252B` | n/a |
| `rule` | `#2C3940` | n/a |
| `ink` | `#E6EDEA` | 14.8:1 |
| `ink-muted` | `#A3B1AB` | 7.9:1 |
| `action` | `#6CC9A0` (text `#0D1A14`) | 8.8:1 |
| `interest` | `#E3B55B` | 9.2:1 |
| `deduct` | `#F08C70` | 7.3:1 |

### Child identity colors

There are six, assigned in order and reused cyclically beyond six. They are kept clear of `interest` and `deduct` in hue.

| Token | Light | Dark | White on light |
|---|---|---|---|
| `child-1` | `#2456A6` | `#8DB4F2` | 7.1:1 |
| `child-2` | `#0E6E73` | `#6FCFD3` | 6.0:1 |
| `child-3` | `#7B3F8C` | `#D3A2E0` | 7.2:1 |
| `child-4` | `#5C6B12` | `#BFD06A` | 5.9:1 |
| `child-5` | `#4A44A8` | `#ADA9F5` | 7.8:1 |
| `child-6` | `#A63A62` | `#F2A0BF` | 6.2:1 |

### Color rules

- **Cash-outs are `ink`, never red.** Spending is a legitimate use of money.
- `deduct` is only used on deduction rows and the overdrawn flag. It is never used for errors in general.
- Color is never the only signal. Credits show `+`, debits show `−`, and interest rows carry the word "Payday".
- No gradients anywhere, including on the jar.

## Typography

There are three families, all under the SIL Open Font License and bundled in the APK (no network font loading).

| Role | Family | Why |
|---|---|---|
| Display: balances, child names | **Bricolage Grotesque** 600/700 | Has character without being cute, and scales from phone to statement image. |
| Interface and body text | **Atkinson Hyperlegible Next** 400/700 | Designed by the Braille Institute to keep easily confused characters (`1 l I`, `0 O`) distinct, which matters for young readers. |
| Figures: ledger amounts, dates | **Atkinson Hyperlegible Mono** 400/600 | Columns of figures line up. Keeps the same legibility as the body text. |

### Type scale (sp)

| Token | Size / line height | Family and weight | Use |
|---|---|---|---|
| `display` | 44 / 48 | Bricolage 700 | One balance per screen |
| `headline` | 26 / 32 | Bricolage 600 | Screen titles, child name in child view |
| `title` | 18 / 24 | Atkinson Next 700 | Row titles, sheet sections |
| `body` | 16 / 24 | Atkinson Next 400 | Notes, explanations |
| `label` | 14 / 20 | Atkinson Next 700 | Buttons, segmented controls |
| `figure` | 16 / 24 | Atkinson Mono 400 | Ledger amounts and dates |
| `figure-strong` | 16 / 24 | Atkinson Mono 600 | Week totals, payday line |
| `picture-label` | 22 / 28 | Atkinson Next 700 | Picture style: any text at all |

The smallest text anywhere is 14 sp. There's no all-caps text except currency codes. Headings never use italics.

## Space and shape

- **Spacing scale (dp):** 4, 8, 12, 16, 24, 32, 48. Screen side margin is 16. Related items use 8; separate groups use 24 or more. Never space everything evenly.
- **Radius:** `r-sm` 4 dp (inputs, segmented controls), `r-md` 12 dp (sheets, buttons), `r-full` (avatars, coins). Nothing else.
- **Depth:** only bottom sheets lift, with one shadow token. Everything else is separated by tone (`paper` vs `surface`) or rules. Never combine a hairline border with a wide shadow.
- **No cards in lists.** Children and ledger entries are ruled rows. A card appears only when something stands alone (the payday statement), and never inside another card.

## Iconography

Material Symbols Rounded, weight 400, filled for chores and values. UI icons are 24 dp and chore icons are 32 dp; in Picture style, chore icons are 56 dp inside a 72 dp target. Icons never sit in a tinted rounded tile above a heading.

The jar, coins and payday stamp are a **bespoke illustration set**:
- geometric
- 2 dp strokes in `ink`
- flat fills from the child's color.

They are drawn once, properly. No placeholder blobs.

## Components

**Child row (home).** From left to right: avatar disc (child color, initial in `headline`), name (`title`), "+40 this week" (`body`, `ink-muted`), then the balance right-aligned (`figure-strong`). If chores are due, they appear on a second line as a single line of icons ("Due: 🗑 🛏 2 more"), not a cluster of chips. Tapping a chore icon records it straight away, with an undo snackbar.

**Record sheet.** A bottom sheet, worked from top to bottom:
1. **Who:** avatar toggles. More than one child can be selected for shared chores.
2. **What:** a segmented control with Chore, Award, Cash out and Take away. Take away only appears when the penalty mode allows it.
3. **Detail:** a chore list with its value, a value picker with a required note, or the amount.
4. **Confirm.** One `action` button whose label states the outcome: "Add 20 to Tom", "Pay Ada £2.00". Never "Submit" or "Save".

Cash out shows "Last synced with Sam's phone: 3 days ago" above the button whenever the last sync is older than 24 h (SPEC FR-38).

**Ledger row.**
- Date (`figure`, `ink-muted`) on the left.
- Title and note in the middle.
- Signed amount (`figure`) on the right.
- A cash-out adds "£0.50 paid" below its amount.
- A deduction shows its reason in `deduct`.
- A reversal and the entry it cancels both show a strike line and "Reversed: reason".

**Payday line.** Closes each week: a double rule, then "Payday", the base ("1% of 200"), and the amount, all in `interest`. Tapping it opens the explanation in plain words.

**Sync line.** One line at the top of the home screen: "Synced with Sam's phone 3 days ago · Sync". There's no pulsing dot and no spinner unless a sync is actually running.

**Flags.** Overdrawn balances, possible duplicates and recalculated interest appear as plain text rows in the affected child's ledger, each with one action ("Review"). They are never toasts or modal dialogs.

## Screens and what wins each one

| Screen | Wins the eye | Supporting |
|---|---|---|
| **Home (parent)** | The Record button (bottom, full width, `action`) | Child rows, the sync line |
| **Record sheet** | The outcome button ("Add 20 to Tom") | Who / What / Detail |
| **Child account (parent)** | The balance (`display`) | The weekly ledger, the goal line |
| **Payday statement** | The interest line | Opening, in, out, closing |
| **Child view: Picture** | The jar | Goal line, this week's chore icons |
| **Child view: Number** | The balance and its money value | Goal projections, "what if I wait" slider, ledger |
| **Sync** | The "Sync with…" button | What came in, the device list |
| **Policies** | The plain-language preview ("At 1% a week, 200 points becomes 335 in a year") | The rate fields |

## Child view: Picture style

- **The jar.** One coin = 10 points. Coins stack in tens, so a full stack is 100 points, or £1 at the default rate. A part-coin is drawn as an empty coin outline with "6 more points to the next coin".
- **The goal** is a horizontal line drawn on the jar at the target height, labelled with the goal's icon. When the coins reach it, the child can have the thing.
- **This week** is a row of chore icons with ticks. No text is needed.
- The only text on screen is the child's name and one sentence, at `picture-label` size. The sentence is read aloud on tap.

## Child view: Number style

- The balance, with its money value underneath ("204 points · £2.04").
- **The goal**, with two dates: "Interest only: 14 March", "At your usual pace: 9 January".
- **What if I wait?** A slider from 1 to 52 weeks shows the balance with interest only, plus "of which interest: 17". It never projects earnings the child hasn't made.
- **The ledger**, as for parents, but read-only.

## Motion

Motion is restrained and serves a purpose. Every animation respects the system "remove animations" setting.

| Moment | Motion |
|---|---|
| Sheet open/close | Standard Material sheet motion, 250 ms, emphasised deceleration |
| New ledger row | Fades in over 150 ms. Doesn't move the rows around it. |
| **Payday stamp** (the one moment of delight) | The interest line appears as a stamp: opacity 0→1 and scale 1.06→1.0 over 220 ms, ease-out, no overshoot. The closing figure counts up over 600 ms. |
| **Payday coins** (Picture style) | Up to 3 new coins drop into the jar, 360 ms each, staggered by 120 ms, ease-in then settling, no bounce |

**Banned motion:** confetti, bouncy or elastic easing, pulsing status dots, shimmer placeholders on local data (it loads instantly), and animated size changes that move neighbouring elements.

## Copy patterns

- Buttons say what will happen: "Add 20 to Tom", "Pay Ada £2.00", "Reverse this entry".
- Numbers carry units at least once per screen ("204 points").
- Interest is always described as "1% a week on the smallest amount you had all week".
- Use a hyphen or "to" for ranges. Keep dashes rare.

## Bans

The general impeccable slop checks apply. These bans are specific to this app:

- Streaks, badges, levels, XP, star ratings, leaderboards, comparing siblings.
- Confetti, fireworks, trophies, mascots.
- Red or warning styling on cash-outs.
- Generic praise in the interface ("Great job!", "Awesome!"). Praise lives in parents' notes.
- Gradients, glassmorphism, glows, neon on dark, purple-to-cyan palettes, cream or beige surfaces.
- Cards inside cards. Icon tiles above headings. Eyebrow chips. Numbered section labels.
- Hero-metric templates (a large number above three small stats).
- Text in Picture style other than the name and one sentence.
- Inter, Roboto or other system fonts as the visible face.
