# Contracts

The `:contracts` module (`contracts/`, package `dev.housepoints.contracts`) is the single source of truth for every type that crosses a module boundary or leaves the device. This document explains it. The code is authoritative.

**Version:** `0.2.0`, frozen at `contracts/v0.2.0-frozen`. This is a minor, additive amendment to `0.1.0` (frozen at `contracts/v0.1.0-frozen`); see "Amendment 0.2.0" below. After the freeze, changes need a `contracts/amendment-<desc>` branch and a version bump. Anything that changes stored bytes or payload meaning is a major bump.

## Policies

- **Typed identifiers.** `FamilyId`, `DeviceId`, `ChildId`, `ChoreId`, `ValueId`, `GoalId`, `EntryId` and `OpId` are value classes over `java.util.UUID`. A raw UUID never crosses a module boundary.
  - Ordering is `Uuids.compare`: unsigned and byte-wise, so it's identical everywhere.
  - `java.util.UUID.compareTo` is signed and **must not** be used for tie-breaks.
- **Quantities.**
  - `Points` holds whole points. `Micropoints` is 10⁻⁶ of a point. Both use checked `Long` arithmetic, so an overflow throws `ArithmeticException` and never wraps.
  - Displayed points are `Micropoints.floorPoints()`, which rounds toward negative infinity.
  - `RateBp` is in basis points per week. `ExchangeRate(points, minorUnits)` converts exactly or not at all.
  - `MinorUnits` holds money. `CurrencyCode` is ISO 4217.
  - There's no floating point anywhere.
- **Time.** `InstantMs` is epoch milliseconds, UTC. Calendar days are `java.time.LocalDate` written as ISO-8601. Zones are IANA IDs. Week days are `DayOfWeek` names.
- **Ordering for conflicts.** `Op.CAUSAL_ORDER` is `(lamport, originDevice)`. It's the only order used for last-writer-wins and for choosing the canonical entry.
- **Storage and transfer order.** `Op.ORIGIN_ORDER` is `(originDevice, originSeq)`.

## Op envelope

```
Op(opId, familyId, originDevice, originSeq ≥ 1, lamport ≥ 1, schemaVersion, type, body)
```

- `body` is the payload's UTF-8 JSON **exactly as first written**. It is never re-encoded.
- **Binary form** (`OpCodec.encode`/`decode`): `0x01 ‖ opId ‖ familyId ‖ originDevice ‖ originSeq i64 ‖ lamport i64 ‖ schemaVersion i32 ‖ u32 len ‖ type ‖ u32 len ‖ body`, big-endian, at most 64 KiB.
- `decode` never throws. Bad input comes back as `DecodeResult.Malformed`.

## Payload JSON policy

- The `kotlinx.serialization` config is `ignoreUnknownKeys = true`, `explicitNulls = false`, `encodeDefaults = false`.
- **Property names are the Kotlin property names (camelCase)**, without renaming. Renaming a property is a breaking change.
- Sealed sub-hierarchies (`Recurrence`, `Policy`) use the default `"type"` discriminator, with the `@SerialName` values listed below.
- Value classes serialise as their underlying value: `Points` is a JSON number, and IDs are UUID strings.
- In an upsert, a field that is **absent** means "not written by this op". That's what makes per-field last-writer-wins possible (SPEC FR-35).

## Payload catalogue (schemaVersion 1)

| `type` | Class | Notes |
|---|---|---|
| `family.created` | `FamilyCreated(name, currency, zone, weekStart)` | One per family. If there are duplicates, the canonical one is the earliest by `CAUSAL_ORDER`. Currency, zone and week start can never change. |
| `device.upsert` | `DeviceUpsert(deviceId, name?)` | Display name, e.g. "Sam's phone" |
| `device.removed` | `DeviceRemoved(deviceId)` | SPEC FR-4 |
| `child.upsert` | `ChildUpsert(childId, name?, colorIndex?, displayStyle?, archived?)` | |
| `chore.upsert` | `ChoreUpsert(choreId, title?, icon?, kind?, points?, assignees?, recurrence?, archived?)` | `recurrence` is one of `daily`, `weekdays{days}`, `weekly`, `once` |
| `value.upsert` | `ValueUpsert(valueId, name?, icon?, archived?)` | |
| `goal.upsert` | `GoalUpsert(goalId, childId?, title?, icon?, target?, status?)` | |
| `ledger.entry` | `EntryRecorded(entryId, childId, kind, points, effectiveAt, note, chore?, valueId?, reverses?, cashOut?)` | Sign rules in the KDoc. A reversal's effect is the negation of its target. |
| `policy.set` | `PolicySet(policy, effectiveFrom)` | `policy` is one of `exchange{rate}`, `interest{rate, cap?}`, `penalty{mode}`, `minCashOut{minimum}` |
| `tick.set` | `TickSet(choreId, childId, day, done)` | |
| `reward.upsert` (0.2.0) | `RewardUpsert(rewardId, title?, icon?, price?, archived?)` | SPEC FR-54 |
| `policy.lock` (0.2.0) | `LockPolicySet(bonus, effectiveFrom)` | SPEC FR-48. A new payload type rather than a `Policy` variant, so v0.1.0 keeps it as unknown instead of malformed |
| *(anything else)* | `UnknownPayload(type, json)` | Kept and forwarded (SPEC FR-39) |
| *(known type, unreadable)* | `MalformedPayload(type, json, reason)` | Kept and forwarded, and affects nothing |

## Deterministic IDs (SPEC FR-18)

`EntryIds` uses UUIDv5 under the fixed namespace `6f1d3c2a-8b47-4f0e-9a51-2c7e5d9b4a13`:

| Function | Name hashed |
|---|---|
| `recurringChore` | `chore:{choreId}:{childId}:{yyyy-mm-dd}` |
| `onceChore` | `chore:{choreId}:{childId}` |
| `reversal` | `reversal:{entryId}` |
| `lockPayout` (0.2.0) | `lock-payout:{lockEntryId}` |

The namespace and these name formats are part of the contract.

## Ports (defined in `:sync`, listed here because they cross module boundaries)

`OpLog` and `Transport`, as in SAD §4.4. They use only `:contracts` types.

## Compatibility rules

1. **Adding a payload type** is a minor bump. Older apps keep it as `UnknownPayload`.
2. **Adding an optional field** to an existing payload is a minor bump. Older apps ignore it when reading, and the stored body keeps it.
3. **Removing or renaming a field, changing a type string, changing the envelope bytes, or changing a deterministic-ID name format** is a major bump. It needs a new `schemaVersion` or envelope format byte, with migration rules written here first.

## Amendment 0.2.0

**Additive only.** `CURRENT_SCHEMA` stays 1.

- **New optional fields on `EntryRecorded`:**
  - `lock: LockTerms(weeks, rate, cap)`: on the lock adjustment.
  - `lockPayout: EntryId`: on the payout or early-break adjustment, pointing at the lock.
  - `rewardId: RewardId`: on a redemption adjustment.
- **New ID type:** `RewardId`.
- **New payload types:** `reward.upsert` and `policy.lock`.

**Why balances can't diverge between versions.** Every balance-changing use of the new fields sits on an `ADJUSTMENT` entry, whose effect is just its signed `points`. A v0.1.0 phone ignores the unknown keys (`ignoreUnknownKeys = true`) and applies the same effect. The ledger's cross-version test (SPEC NFR-DET-4) projects every log twice, once as stored and once with the 0.2.0 additions stripped, and requires identical balances.

**Frozen lock terms.** The payout is computed from the lock entry alone (SPEC FR-47 and FR-49). That keeps payouts recorded on different phones byte-identical under their deterministic ID.
