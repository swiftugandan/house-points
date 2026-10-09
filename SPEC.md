# Specification

> Every non-obvious claim, threshold, or constraint in this document traces to a citation in [References](#references) or to an explicit "chosen because" rationale. The key words MUST, MUST NOT, SHOULD and MAY are used as described in RFC 2119 [R1].

## Problem statement

Parents want to run a points economy for their children that teaches three things through repetition: work earns money, leaving money alone makes it grow, and the record is fair. Existing pocket-money apps depend on a server, a bank account or a child's own device. This product runs only on parents' Android phones, works with no internet connection, and keeps two or more parents' phones in exact agreement by syncing peer-to-peer. It must suit any family, any number of children, and any currency. See `VISION.md` and the plain-language `CONCEPT.md`.

## Glossary

- **Family:** the unit of sharing. One family per install. It has a fixed currency, timezone and week start day.
- **Device:** one installation on one parent's phone. It has a permanent random identity that is never backed up.
- **Child:** a profile inside a family. Children are not users of the app.
- **Point:** the unit children see. Always shown as a whole number, rounded down.
- **Micropoint (µpt):** the internal unit. 1 point = 1,000,000 µpt. All arithmetic is done on integer µpt.
- **Operation (op):** an immutable record appended to the family log by one device. Everything that changes state is an op.
- **Ledger entry:** an op that changes one child's balance.
- **Policy change:** an op that sets the exchange rate, the interest rules or the penalty mode, from a stated effective instant onward.
- **Period / week:** the half-open interval `[start, start + 1 week)`, where `start` is local midnight on the family's week start day, in the family's timezone.
- **Payday:** the end instant of a period, when that period's interest is credited.
- **Reversal:** a ledger entry that cancels an earlier entry as if it had never happened.
- **Display style:** per child, either **Picture** or **Number** (see `DESIGN.md`).

## Functional requirements

### Family, devices and children

- **FR-1:** A parent MUST be able to create a family by choosing:
  - a name
  - a currency, as an ISO 4217 code [R2]
  - a timezone, as an IANA tz identifier [R3]
  - a week start day
  - initial policies, using the defaults in FR-30 unless changed.

  Currency, timezone and week start day are fixed for the life of the family (see NG-8).
  - *Rationale:* period boundaries depend on timezone and week start. Changing either would move every past boundary and silently recalculate all historic interest.
- **FR-2:** A device MUST generate a random device identity on first launch and store it where Android Auto Backup never includes it (`noBackupFilesDir`) [R4].
  - *Rationale:* if a backup is restored onto a second phone while the first is still in use, two devices would share an identity and their per-device sequence numbers would collide.
- **FR-3:** A family MUST have a 256-bit family key, generated when the family is created. Another phone joins by scanning a QR code, shown by an existing device, that carries the family identity and key. Any number of devices MAY join.
- **FR-4:** A parent MUST be able to remove a device (for example, a lost phone). Removal generates a new family key, and the remaining devices re-pair by scanning the new QR code. Ops created by the removed device before removal stay in the log.
- **FR-5:** A parent MUST be able to add, rename and archive children, and set each child's avatar and display style. Archiving hides a child but MUST NOT delete any of their history.

### Chores, values and goals

- **FR-6:** Chores have a title, an icon, and one of three kinds:
  - **expected:** never paid
  - **assigned:** paid; has a points value, a set of assigned children, and a recurrence of `daily`, `weekdays{…}`, `weekly` or `once`
  - **bounty:** paid; has a points value; any child can do it, any number of times.
- **FR-7:** A parent MUST be able to tick off expected chores per child per day. These ticks MUST NOT create ledger entries.
- **FR-8:** A family MUST have an editable list of values, each with a name and an icon. The default list is kindness, honesty, effort, courage, helpfulness.
- **FR-9:** A parent MUST be able to create one active savings goal per child: a title, a target in points, and an icon from a built-in set.
- **FR-10:** For each active goal, the app MUST show two projected dates by running the interest rules (FR-24 to FR-29) forward from the current balance:
  - **interest only:** no further entries
  - **at current pace:** adds, each week, the mean net non-interest change over the last 4 completed weeks.

  If fewer than 4 completed weeks exist, the mean is taken over those that do exist. With no completed weeks, the app shows "not yet" for the at-current-pace date. If a projection doesn't reach the target within 520 weeks, the app MUST show "more than 10 years" for it, and "not at current pace" when that pace is zero or negative.
  - *Rationale:* 4 weeks smooths out a single unusual week while still reflecting recent behaviour. The 10-year horizon bounds the calculation, and nothing beyond it means anything to a child.

### Recording

- **FR-11:** A parent MUST be able to record a **chore credit** for one or more children in a single action. The entry stores the chore's points value at the time of recording; later edits to the chore don't change past entries.
- **FR-12:** A parent MUST be able to record a **behaviour award** with a child, a points value, a value tag and a note. The note is required.
  - *Rationale:* written, specific acknowledgement is the form of reward the research associates with increased, not reduced, intrinsic motivation [R5].
- **FR-13:** A parent MUST be able to record a **deduction** with a reason, subject to the penalty mode in force (FR-30):
  - `NONE`: deductions cannot be recorded.
  - `CURRENT_WEEK`: a deduction can't exceed the child's chore credits plus awards in the current week, minus deductions already recorded this week, as seen on the recording device.
  - `ANY`: a deduction can't exceed the child's current balance as seen on the recording device.
- **FR-14:** A parent MUST be able to record a **cash-out**: a whole number of points `P` and a money amount `M` in the currency's minor units, at the exchange rate `X points = Y minor units` in force (FR-30).
  - The app MUST only offer amounts where `P × Y = M × X` exactly, so no rounding is ever needed.
  - `P` MUST NOT exceed the recording device's view of `floor(balance)`, and MUST be at least the minimum cash-out (FR-30).
  - The entry stores `P`, `M`, `X` and `Y`.
- **FR-15:** A parent MUST be able to record an **adjustment** (credit or debit) with a required note. This covers corrections that aren't reversals, such as an opening balance carried over from a paper system.
- **FR-16:** A parent MUST be able to **reverse** any ledger entry, including a reversal, with a reason. A reversal takes effect at the same instant as the entry it cancels, with the opposite amount. Each entry can be reversed at most once (enforced by FR-19).
- **FR-17:** Every ledger entry MUST record:
  - the child
  - its type
  - a signed amount in whole points
  - its **effective instant** (when it happened, defaulting to now; a parent MAY backdate it)
  - the recording device
  - references: the chore and occurrence, the value, or the entry it reverses
  - a note.

### Entry identity and duplicate recording

- **FR-18:** Entry IDs are UUIDs [R6]. Most entries get a random, time-ordered ID (UUIDv7). The following get a **deterministic** name-based ID (UUIDv5), so that the same real event recorded on two phones collapses into one entry:

  | Event | Name hashed into the UUIDv5 |
  |---|---|
  | Assigned recurring chore | `chore:{choreId}:{childId}:{occurrenceDate}`. The occurrence date is the due date for `daily` and `weekdays`, and the period's start date for `weekly`. |
  | Assigned `once` chore | `chore:{choreId}:{childId}` |
  | Reversal | `reversal:{reversedEntryId}` |

  Bounty completions, awards, deductions, cash-outs and adjustments use random IDs. These are legitimately repeatable, so a deterministic key would merge genuine repeats.
- **FR-19:** If the log contains several ops with the same entry ID, exactly one is canonical: the one with the lowest `(lamport, deviceId)` (see FR-33). The others are kept in the log but have no effect on balances.
- **FR-20:** After a sync, the app MUST flag *possible duplicates* for a parent to review. A possible duplicate is two random-ID entries with the same child, type, amount and local date, recorded on different devices, neither reversed. Resolving one means reversing it or dismissing the flag. The app MUST NOT merge them automatically.

### Balance and interest (the core rules)

- **FR-21:** A child's balance at instant `t` is the sum of every canonical ledger entry's amount with effective instant `< t`, plus every interest credit for periods whose payday is `≤ t`.
  - **Interest credits are never stored.** They are recalculated from the log on demand (see NFR-DET-1).
- **FR-22:** Entries with the **same effective instant** for the same child MUST be applied together, as one net change.
  - *Rationale:* this makes the lowest-balance rule independent of the order entries were recorded or synced in.
- **FR-23:** Interest is calculated for each child, period by period, in order, starting from the period that contains the child's first ledger entry.
- **FR-24:** The **interest base** of a period is the lowest of:
  - the opening balance at the period start, which includes the previous period's interest
  - the balance after each net change (FR-22) that falls inside the period.
- **FR-25:** The interest credited for a period is:

  `interest_µpt = floor( clamp(base_µpt, 0, cap_µpt) × rate_bp / 10_000 )`

  - `rate_bp` and `cap` come from the interest policy in force at the **start** of the period (FR-31).
  - A base at or below zero earns nothing. Children are never charged interest.
  - All arithmetic MUST use checked 64-bit integers. An overflow is an error that gets reported, never a value that wraps around.
  - *Rationale for the period-start rule:* the child is saving at the rate they were told when the week began. A change made in the middle of a week takes effect from the next week, which is simple to explain and needs no pro-rating.
  - *Rationale for rounding:* rounding happens once per child per period, always downward, so the result is reproducible on every device.
- **FR-26:** Interest for a period is credited at that period's payday, and counts towards the opening balance (and so the interest base) of the next period. This is how interest compounds.
- **FR-27:** The displayed balance is `floor(balance_µpt / 1_000_000)` points. The amount available to cash out is that same figure.
- **FR-28:** Only completed periods earn interest. For the current period, the app MAY show *expected interest so far*, clearly labelled as not yet paid.
- **FR-29:** Period boundaries MUST be calculated as local midnight in the family's timezone using the IANA tz database [R3]. So a period that spans a daylight-saving change is 167 or 169 hours long, and that is correct.

### Policies

- **FR-30:** The policies and their defaults are:

  | Policy | Default | Chosen because |
  |---|---|---|
  | Exchange rate | 1 point = 1 minor unit (100 points = £1) | A child only has one conversion to learn: points are pennies. |
  | Interest rate | 100 bp (1%) per week | 1.01^52 ≈ 1.68, so a balance grows about 68% in a year. That's visible weekly (2 points on 200) without being absurd. |
  | Interest cap | 2,000 points | Limits the cost to parents to 20 points per child per week (≈ £10.40 a year at the default rate). |
  | Penalty mode | `CURRENT_WEEK` | Savings stay safe, so the incentive to save is never undermined. See `CONCEPT.md`. |
  | Minimum cash-out | 100 points | One whole unit of currency. Avoids trips to the shop for 3p. |

- **FR-31:** Every policy change is a dated op with an `effectiveFrom` instant. The policy in force at instant `t` is the change of that kind with the greatest `effectiveFrom ≤ t`. If two changes share the same `effectiveFrom`, the one with the greater `(lamport, deviceId)` wins. The app MUST show superseded changes in the policy history.
- **FR-32:** Changing the exchange rate MUST NOT change any points balance or any past cash-out. Before confirming, the app MUST show the parent the change in money value of each child's current balance.

### Sync

- **FR-33:** Every op MUST carry:
  - `opId`
  - `originDevice`
  - `originSeq`: a contiguous counter per device, starting at 1
  - `lamport`: a Lamport clock [R7], set to one more than the highest value the device has seen
  - `schemaVersion`
  - a payload.

  Ordering and conflict resolution MUST use `(lamport, deviceId)` and MUST NOT use wall-clock time.
- **FR-34:** Two paired devices MUST be able to sync without the internet over either link:
  - **Same Wi-Fi (default, amended 2026-10-09):** while House Points is open on both phones on the same local network, they find each other with mDNS/DNS-SD and sync automatically over a direct TCP connection between home-network addresses. Families confirmed their phones always share home Wi-Fi.
  - **Fallback:** Nearby Connections over Bluetooth, BLE and Wi-Fi Direct [R8], started from the Sync screen.

  Each side sends a version vector (`deviceId → highest contiguous originSeq`) and receives the ops it is missing. Applying ops MUST be idempotent, commutative and associative, so that any order and any repetition of syncs between any number of devices ends in the same state. These are the convergence conditions for a grow-only-set CRDT [R9].
- **FR-35:** Child, chore, value and goal records, and expected-chore ticks, are **last-writer-wins registers per field**, ordered by `(lamport, deviceId)` [R9].
- **FR-36:** Merging MUST NOT reject any op. Rules that apply at the moment of recording (FR-13, FR-14) are checked only on the device doing the recording. If merged state breaks a rule (a negative balance, possible duplicates), the app MUST show it as a flag for a parent. It MUST NOT correct it automatically.
- **FR-37:** After each sync, for each child whose displayed balance changed for any reason other than the new entries themselves, the app MUST show the size of the interest recalculation, e.g. "Interest recalculated: −1".
  - This is worked out by comparing against a **locally stored snapshot** of the last balance shown, and it MUST NOT be written to the log.
- **FR-38:** The app MUST show, for each paired device, when it last synced with that device. The cash-out screen MUST show this before a payout is confirmed.
- **FR-39:** A device MUST store, unchanged, any op whose `schemaVersion` or type it doesn't understand, and pass it on in syncs. Such ops have no effect on its balances. It MUST tell the parent that another phone is running a newer version.
  - *Rationale:* other families will run mixed app versions. Dropping unknown ops would lose data the next time those phones sync.

### Child view, payday and sharing

- **FR-40:** A parent MUST be able to open a child view for one child. Leaving it MUST require the parent to authenticate with BiometricPrompt or the device PIN [R10]. On first use the app SHOULD guide the parent to turn on Android screen pinning [R11].
- **FR-41:** In the child view, Picture style MUST show:
  - the balance as a jar of coins
  - the goal as an icon, with how far there is to go
  - this week's chores as icons with ticks.

  None of this may depend on the child being able to read. Number style MUST show:
  - the balance and its money value
  - the history grouped by week, with each week's interest line
  - the two goal projections (FR-10)
  - a "what if I wait N weeks" projection.
- **FR-42:** After each payday, the app MUST produce a weekly statement for each child: opening balance, money in, money out, interest, closing balance. It MUST be shareable as an image through the Android share sheet.
- **FR-43:** The app SHOULD post a local notification at payday ("Payday: statements ready"). No network is involved.
- **FR-44:** The app SHOULD provide a home-screen widget showing each child's displayed balance.

### Backup and export

- **FR-45:** The op log and records MUST be eligible for Android Auto Backup. The device identity MUST NOT be (FR-2). Where the platform supports it, cloud backup SHOULD be limited to devices that can encrypt it end-to-end [R4].
- **FR-46:** A parent MUST be able to export the whole op log to a file encrypted with a passphrase, and import it on a new device. Imported ops merge exactly like a sync.

## Worked examples (acceptance tests)

These examples are normative. Each one becomes an acceptance test of the ledger core, and they must hold on every device.

**Common setup:**
- Family timezone `Europe/London`, weeks start on Monday.
- Defaults from FR-30: 100 bp/week interest, 2,000-point cap, 1 point = 1p.
- Child: Ada.
- Periods:
  - W1: Mon 5 Oct 2026 00:00 to Mon 12 Oct
  - W2: 12 to 19 Oct
  - W3: 19 to 26 Oct. UK clocks go back on Sun 25 Oct [R3], so W3 is 169 hours.
  - W4: 26 Oct to 2 Nov.
- Amounts are in points. `2.02` means 2,020,000 µpt.

### Example 1: compounding and the lowest-balance rule

| When | Entry | Balance |
|---|---|---|
| Mon 5 Oct 17:00 | CHORE +200 | 200 |
| Wed 7 Oct 18:00 | AWARD +100 (id E) | 300 |
| Fri 9 Oct 16:00 | CASH_OUT −50 | 250 |
| **W1 payday** | Lowest balance 0 (the opening balance) → interest 0 | **250** |
| Tue 13 Oct 17:00 | CHORE +100 | 350 |
| Thu 15 Oct 16:00 | CASH_OUT −150 | 200 |
| **W2 payday** | Lowest of 250, 350, 200 = 200 → 200 × 1% = 2 | **202** |
| **W3 payday** | Lowest 202 → 2.02 | **204.02** (shown as 204) |
| **W4 payday** | Lowest 204.02 → 2.0402 | **206.0602** (shown as 206) |

The award on Wed 7 Oct earned nothing in W1, because the lowest balance that week was the opening 0. Points have to stay in for a whole week to earn interest.

### Example 2: a late sync changes past interest

This starts from Example 1, as seen on phone A.
- Phone B last synced on Mon 12 Oct at 08:00, when Ada's balance was 250.
- On Wed 14 Oct at 12:00, B records CASH_OUT −100. This passes FR-14 on B, because B sees a balance of 250.
- On Tue 20 Oct, the phones sync.

On both phones after the merge:

| W2 | Balance |
|---|---|
| Opening | 250 |
| Tue +100 | 350 |
| Wed −100 | 250 |
| Thu −150 | 100 |
| Lowest 100 → interest 1.00 | **101** |

Phone A showed 202 before the sync and shows 101 after. Of the difference, −100 is the new entry. Phone A MUST show "Interest recalculated: −1" (FR-37). No interest entry is written anywhere.

### Example 3: both parents reverse the same mistake

This starts from Example 1. Award E (+100, Wed 7 Oct 18:00) was a mistake. While apart, both parents reverse it.
- Each phone creates an entry with ID `UUIDv5("reversal:" + E)`, effective Wed 7 Oct 18:00, amount −100.
- After the sync, the log holds two ops with that ID. The one with the lowest `(lamport, deviceId)` is canonical (FR-19), so the reversal counts once.
- At Wed 18:00, the +100 and −100 apply together as one net change of 0 (FR-22).

| | W1 | W2 |
|---|---|---|
| Changes | +200, net 0, −50 → 150 | Opening 150, +100 → 250, −150 → 100 |
| Lowest | 0 | 100 |
| Interest | 0 | 1 |
| Close | **150** | **101** |

### Example 4: an interest rate change in the middle of a week

This starts from Example 1. On Tue 20 Oct, a parent sets the interest rate to 200 bp, effective Wed 21 Oct 00:00 (during W3).
- W3 uses the policy in force at its start, Mon 19 Oct: 100 bp. Interest 2.02, close 204.02.
- W4 uses the policy in force at Mon 26 Oct: 200 bp. Interest is 204.02 × 2% = 4.0804, close **208.1004** (shown as 208).

### Example 5: both parents pay out the same savings

This starts from Example 1 at the start of W3 (balance 202).
- While apart, phone A records CASH_OUT −200 on Sat 24 Oct at 10:00, and phone B records CASH_OUT −200 on Sat 24 Oct at 15:00. Each was valid on its own phone.
- After the sync, W3 runs 202 → 2 → −198. The lowest balance is −198, which is at or below zero, so the interest is 0. The balance is **−198**.
- Both phones MUST flag Ada as overdrawn and show both cash-outs (FR-36).
- Every following week earns 0 interest until its lowest balance is above zero. Resolving it is the parents' decision: reverse one cash-out if the money comes back, or let Ada earn her way back.

### Example 6: the cap

Ada holds 3,000 points all week. The interest base is clamped to 2,000, so the interest is **20**.

### Example 7: entries with the same instant

Opening balance 300. A CASH_OUT −300 and an AWARD +300 are both effective Thu 12:00. Applied together, the net change is 0, so the lowest balance is 300 and the interest is **3**. If they were applied one at a time, the result would depend on the order (the lowest balance could be 0 or 300). FR-22 removes that ambiguity.

### Example 8: two policy changes for the same instant

While apart, phone A (lamport 40) sets the interest rate to 150 bp and phone B (lamport 42) sets it to 50 bp, both effective Mon 2 Nov 00:00. After the sync, both phones use **50 bp** from W5 onward (FR-31), and the policy history shows A's change as superseded.

## Amendment 1 (v0.2.0): locked savings, rewards shop, home-screen widget

Added 2026-10-09. Every new balance-changing entry is an **adjustment** (`ledger.entry`, kind `ADJUSTMENT`) carrying new *optional* fields. So a phone still on v0.1.0, which ignores those fields, computes exactly the same balances and interest (FR-39 and NFR-DET-1 across versions). New non-balance data uses new payload types, which v0.1.0 keeps as unknown.

### Locked savings

- **FR-47:** A parent MAY lock part of a child's balance for 4, 8 or 12 weeks. The lock is an adjustment of `−P` points, made when the parent records it. It carries **frozen terms**: `weeks`, `rate` and `cap`. The rate is the interest rate in force plus the lock bonus (FR-48). The cap is the interest cap in force. `P` MUST be at least 100 points and at most the recording phone's view of `floor(balance)`.
  - *Rationale for freezing:* a payout recorded by any phone MUST be a pure function of the lock entry alone, so two phones never record different amounts under the same ID.
- **FR-48:** The lock bonus is a dated setting (payload `policy.lock`, default +100 bp). It only affects the terms of **new** locks.
- **FR-49:** The pot does not earn during the week the lock was made. It earns for exactly `weeks` whole periods, starting with the next one: `B₀ = P`, then each week `Bₖ = Bₖ₋₁ + floor(clamp(Bₖ₋₁, 0, cap) × rate / 10 000)` in micropoints. At the end of the last of those weeks (the **maturity payday**) the payout is `floor(B_weeks)` whole points.
- **FR-50:** At or after maturity, a phone running v0.2.0 or later MUST record the payout. It is an adjustment of `+payout` at the maturity instant, with the deterministic ID `UUIDv5("lock-payout:{lockEntryId}")` and `lockPayout = lockEntryId`. Two phones recording it independently therefore produce one entry. A payout effective at the start of a week does not earn main-account interest until the next week, as with any entry effective at a period start (FR-24).
- **FR-51:** Breaking a lock early returns `+P` at the moment it's recorded, with `lockPayout = lockEntryId` and no interest. It is not a reversal, because a reversal would retroactively pay main-account interest for the locked weeks. Once any in-force entry references a lock through `lockPayout`, no maturity payout is due for it.
- **FR-52:** A lock with an in-force payout or break MUST NOT be reversed on its own. Reversing it reverses the lock and every in-force `lockPayout` entry for it, in one recorded action. A payout whose lock is reversed while the payout is not is flagged for a parent (FR-36), never corrected automatically.
- **FR-53:** Wherever a balance is shown, the locked amount MUST be shown next to it: "Locked away: 500 · back Mon 16 Nov with about 41 more". Children must never think points have vanished.

### Rewards shop

- **FR-54:** A family MAY keep a catalogue of rewards (payload `reward.upsert`: title, icon, price, archived; per-field last-writer-wins).
- **FR-55:** Redeeming a reward is an adjustment of `−price`, with the note `Reward: {title}` and `rewardId`. It is checked like a cash-out against the recording phone's balance, but has no minimum. On v0.1.0 it reads as "Adjustment · Reward: …" with the same effect.

### Widget

- **FR-44 (now built):** The home-screen widget lists each non-archived child's name and displayed balance, in creation order and never sorted by balance (DESIGN.md bans comparing siblings). Tapping it opens the app.

### Compatibility invariant

- **NFR-DET-4:** For every op log, projecting it as v0.2.0 and projecting it with every v0.2.0 addition removed (the new optional fields stripped, the new payload types dropped) MUST give identical balances and period summaries for every child. This is a permanent test.

### Worked examples (acceptance tests)

Same setup as before (Europe/London, Monday weeks, 1% a week, cap 2,000, 1 point = 1p), with the lock bonus at its default +100 bp.

#### Example L1: a four-week lock

- Ada holds 1,000 from before 12 Oct, with interest switched off before 12 Oct so the figures stay round.
- On Wed 14 Oct at 12:00 a parent locks 500 for 4 weeks.
- The terms are frozen: rate 200 bp, cap 2,000.

| | Main account | Pot |
|---|---|---|
| Week of 12 Oct | 1,000 → 500 on Wed. Lowest 500, interest 5, closes 505. | Not earning yet |
| Week of 19 Oct | | +10 → 510 |
| Week of 26 Oct | | +10.2 → 520.2 |
| Week of 2 Nov | | +10.404 → 530.604 |
| Week of 9 Nov | | +10.61208 → 541.21608 |
| Mon 16 Nov 00:00 | Payout **541** (`floor`) credited. It earns main interest from the week of 23 Nov. | Closed |

Recording the payout twice, once on each phone, leaves one entry.

#### Example L2: breaking the lock early

Same lock. On Wed 28 Oct at 18:00 a parent breaks it:
- An adjustment of +500 is recorded at that instant.
- No pot interest is paid.
- No payout falls due on 16 Nov.
- In the week of 26 Oct the main account's lowest balance is unaffected by the break, because the break raises the balance.

#### Example L3: reversing a matured lock

After L1's payout, reversing the lock produces two reversals: the −500 and the +541, both cancelled at their own instants. The main account is then as if the lock never happened, and that week's interest is recalculated. Trying to reverse only the lock is refused.

#### Example R1: a reward

Tom has 120 points. Redeeming "Screen time, 30 minutes" (price 50) records an adjustment of −50 with the note "Reward: Screen time, 30 minutes". He now has 70. A redemption of more than his balance is refused.

## Non-functional requirements

### Determinism and correctness

- **NFR-DET-1:** Balance calculation MUST be a pure function of `(set of canonical ops, as-of instant)`. Two devices with the same op set and the same as-of instant MUST produce identical balances in µpt for every child. The path MUST NOT use floating point, the device clock, the device locale, or iteration over unordered collections.
- **NFR-DET-2:** Property-based tests MUST show that merging is idempotent, commutative and associative over randomly generated op sets and random sync orders between at least three devices [R9].
- **NFR-DET-3:** All worked examples above MUST pass as acceptance tests on the plain JVM, with no Android runtime.

### Performance

- **NFR-PERF-1:** Recalculating every balance from scratch for the sizing dataset MUST take ≤ 1 s on a mid-range reference phone (to be fixed in the SAD), and MUST NOT run on the main thread.
  - Sizing dataset: 8 children × 10 years × 40 entries per child per week ≈ 166,400 entries. *Chosen as* a generous upper bound on any family's use.
  - *Rationale for 1 s:* that is about the limit before a user's flow of thought is interrupted [R12]. Main-thread work also risks Android's 5-second input ANR [R13].
- **NFR-PERF-2:** An incremental sync of ≤ 500 ops MUST finish within 10 s of the connection being established.
  - *Chosen because* 500 ops is more than a busy week for 8 children. 10 s is about as long as two parents will hold their phones together without giving up.
  - This is unverified against Nearby Connections' real throughput (see OQ-4).

### Durability and storage

- **NFR-DUR-1:** An op MUST be committed to on-device storage in a single transaction before the UI confirms it was recorded.
- **NFR-DUR-2:** For a typical family (3 children × 10 years × 20 entries per child per week ≈ 31,200 entries plus records), the stored log SHOULD stay under 10 MB, so it fits inside the 25 MB Auto Backup quota with room to spare [R4]. Families beyond that rely on the export in FR-46.

### Security and privacy

- **NFR-SEC-1:** Sync sessions MUST mutually authenticate both devices as holders of the family key, and MUST protect every message with authenticated encryption keyed from the family key. Nearby Connections' own encryption [R8] counts only as transport protection, because it isn't tied to the family key.
  - *Threat model:* spoofing and information disclosure (STRIDE) by any nearby Bluetooth or Wi-Fi device.
- **NFR-SEC-2:** No data MUST leave the device except to authenticated paired devices, through Android backup when the user has turned it on, or through an export the parent starts. The app MUST NOT contain analytics, advertising, crash reporting to a network, or accounts.
  - *Amended 2026-10-09:* the local-network link needs Android's `INTERNET` permission, which Android requires for any socket. So this NFR is verified by code review and by the link's address rules, not by the manifest. The link listens on and connects to site-local, link-local and unique-local addresses only, it advertises a hash of the family id rather than the id, and it carries only sessions encrypted under the family key (NFR-SEC-1).
- **NFR-SEC-3:** Data on the device relies on Android's file-based encryption [R14], kept in app-private storage.
- **NFR-SEC-4:** The child view limits what a child can do *inside the app*. It does not stop a child leaving the app, because an app that isn't managed by a device administrator can only request screen pinning, and the user can exit that [R11]. The app MUST NOT claim otherwise.

### Accessibility

- **NFR-A11Y-1:** Text MUST meet WCAG 2.2 contrast of 4.5:1 for normal text and 3:1 for large text and UI components [R15]. Touch targets MUST be at least 48 × 48 dp [R16].
- **NFR-A11Y-2:** Every screen MUST stay usable with system font scaling up to 200% [R15], and with TalkBack. Picture-style screens MUST have spoken descriptions.
- **NFR-A11Y-3:** Animations MUST respect the system "remove animations" setting.

### Operability

- **NFR-OPS-1:** Minimum Android version: 9 (API 28).
  - *Chosen because* it is the lowest version with end-to-end encrypted Auto Backup [R4] and BiometricPrompt [R10].
- **NFR-OPS-2:** All user-facing strings MUST be externalised for translation, and all money MUST be formatted using the family's currency and the device locale. Version 1 ships in English.
- **NFR-OPS-3:** The app MUST include a diagnostics screen showing the device identity, paired devices, the version vector, op counts, and the last 20 sync attempts with their outcomes. Nothing is sent anywhere.

## Non-goals

- **NG-1:** Children's own devices, logins or accounts. *Because* the users are parents, and child accounts bring authentication and a much larger trust surface for no teaching benefit.
- **NG-2:** Servers, cloud sync or user accounts. *Because* none are needed (FR-34), and having none removes cost, operations work and a privacy risk.
- **NG-3:** Holding, moving or linking real money. *Because* holding customer funds is regulated e-money activity, and the teaching value is in the record, not the transfer.
- **NG-4:** iOS. *Because* sync depends on Nearby Connections through Google Play Services.
- **NG-5:** Locked (fixed-term) savings. *Deferred to v2*; the model allows for it as a second pot per child.
- **NG-6:** A non-cash rewards shop. *Deferred to v2.*
- **NG-7:** Tiered interest rates. *Because* a single rate with a cap is enough to keep costs bounded, and is easier for a child to understand.
- **NG-8:** Changing a family's currency, timezone or week start day. *Because* it would rewrite past period boundaries (FR-1).
- **NG-9:** More than one family per install, or one child shared between two households.
- **NG-10:** Limited roles such as a grandparent who can award but not configure. All devices are equal parents in v1.
- **NG-11:** A cooling-off period before cash-outs.
- **NG-12:** Photos, whether as proof of chores or as goal pictures. *Because* syncing images over Nearby Connections costs bandwidth and storage that the core doesn't need.
- **NG-13:** Streaks, badges, levels and celebration effects. *Because* they turn a predictable bank into a reward game, and expected rewards are the kind associated with reduced intrinsic motivation [R5].

## Constraints

- Android only, and Google Play Services is required for Nearby Connections. Phones without Google services (some de-Googled builds) are not supported.
- The app has to work fully with no network connection.
- Nearby Connections needs Bluetooth, Wi-Fi and nearby-device runtime permissions, which vary by Android version. These are to be listed in the SAD.

## Open questions

Decided by default on the user's go-ahead (2026-10-09). Revisit before any release to other families:

- [x] **OQ-1:** Distribution: a sideloaded, signed APK. The Play Store is deferred, and Play policy declarations will be made then.
- [x] **OQ-2:** The FR-30 defaults are accepted as written.
- [x] **OQ-3:** The expected-chore tick list (FR-7) is in v1.
- [x] **OQ-6:** The NFR-SEC-4 limitation is accepted.

Still open. These are technical spikes and don't block the rules:

- [ ] **OQ-4 (spike):** Nearby Connections throughput on two real phones (NFR-PERF-2). Needs a second device.
- [x] **OQ-5:** Superseded by the local-network link (FR-34 amendment), which needs `INTERNET`. See NFR-SEC-2.

## References

1. **[R1]** Bradner, S. *RFC 2119: Key words for use in RFCs to Indicate Requirement Levels.* IETF, 1997. https://www.rfc-editor.org/rfc/rfc2119
2. **[R2]** ISO 4217: *Codes for the representation of currencies.* https://www.iso.org/iso-4217-currency-codes.html
3. **[R3]** IANA Time Zone Database. https://www.iana.org/time-zones. UK daylight saving time ends on the last Sunday of October (25 Oct 2026).
4. **[R4]** Android Developers, *Back up user data with Auto Backup*: the 25 MB per-app quota, end-to-end encryption on Android 9+ with a screen lock, and the automatic exclusion of `getNoBackupFilesDir()`. https://developer.android.com/identity/data/autobackup
5. **[R5]** Deci, E. L., Koestner, R., & Ryan, R. M. (1999). A meta-analytic review of experiments examining the effects of extrinsic rewards on intrinsic motivation. *Psychological Bulletin*, 125(6), 627–668.
6. **[R6]** Davis, K., Peabody, B., & Leach, P. *RFC 9562: Universally Unique IDentifiers (UUIDs).* IETF, 2024 (UUIDv5 §5.5, UUIDv7 §5.7). https://www.rfc-editor.org/rfc/rfc9562
7. **[R7]** Lamport, L. (1978). Time, clocks, and the ordering of events in a distributed system. *Communications of the ACM*, 21(7), 558–565.
8. **[R8]** Google, *Nearby Connections overview*: works fully offline, uses Bluetooth, BLE and Wi-Fi, and connections are encrypted. https://developers.google.com/nearby/connections/overview
9. **[R9]** Shapiro, M., Preguiça, N., Baquero, C., & Zawirski, M. (2011). Conflict-free replicated data types. *SSS 2011*, LNCS 6976, 386–400.
10. **[R10]** Android Developers, *Show a biometric authentication dialog* (BiometricPrompt, API 28+). https://developer.android.com/identity/sign-in/biometric-auth
11. **[R11]** Android Developers, *Lock task mode*: an app that isn't allowlisted gets screen pinning, which the user can exit. https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode. Google Help, *Pin screens*: https://support.google.com/android/answer/9455138
12. **[R12]** Nielsen, J. (1993). *Usability Engineering*, ch. 5, response-time limits (0.1 s / 1 s / 10 s). Morgan Kaufmann.
13. **[R13]** Android Developers, *ANRs*: input dispatch timeout of 5 seconds. https://developer.android.com/topic/performance/vitals/anr
14. **[R14]** Android Open Source Project, *File-based encryption.* https://source.android.com/docs/security/features/encryption/file-based
15. **[R15]** W3C, *Web Content Accessibility Guidelines (WCAG) 2.2*, SC 1.4.3 Contrast (Minimum), 1.4.4 Resize Text, 1.4.11 Non-text Contrast. https://www.w3.org/TR/WCAG22/
16. **[R16]** Android Developers, *Make apps more accessible*: touch targets of at least 48 dp. https://developer.android.com/guide/topics/ui/accessibility/apps

---

## Confidence gate

```
Confidence: high (for the rules this spec governs)
Why: every rule that decides a balance is pinned down to the integer, with eight worked examples; the user
  decisions are recorded; the remaining unknowns are performance spikes (OQ-4) and a manifest check (OQ-5),
  which can't change any rule, only whether an NFR is met.
Residual risks:
  - NFR-PERF-2 (sync time) is unmeasured until a second phone is available.
  - NFR-PERF-1 (1 s full recalculation) is unmeasured until ledger-core exists; the SAD plans a fallback.
Known gaps: none that block architecture.
```
