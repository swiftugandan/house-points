# Software Architecture Document

This document turns the requirements in `SPEC.md` (tag `spec/v0.1.0`) into components, contracts and decisions. Requirement IDs (FR-x, NFR-x) refer to that specification.

---

## 1. The 30,000-ft view: context

```
            ┌─────────────────────── trust boundary: the family ───────────────────────┐
            │                                                                           │
  Parent ──▶│  House Points (phone A)  ◀── Nearby Connections (BT/BLE/Wi-Fi, no internet) ──▶  House Points (phone B)  │◀── Parent
            │        │                                                                  │
            └────────┼──────────────────────────────────────────────────────────────────┘
                     │
                     ├──▶ Android Auto Backup (user-enabled; E2E-encrypted on API 28+ with a screen lock)  [R4]
                     ├──▶ Android share sheet (statement images, encrypted exports), started by the parent
                     └──▶ Google Play Services (Nearby Connections, code scanner): on-device system services
```

**External actors.**
- **Parents** operate the app.
- **Children** look at the child view on a parent's phone. They are never authenticated users.
- **Peer devices** run House Points in the same family.

**Trust boundaries.**
1. **Between devices:** anything arriving over Nearby is untrusted until the session has proved the peer holds the family key (NFR-SEC-1).
2. **Between the parent and child modes on one phone:** leaving the child view needs device authentication (FR-40). That is an in-app boundary only (NFR-SEC-4).
3. **The app sandbox:** data at rest is protected by Android's file-based encryption (FBE) [R14]. The family key is additionally wrapped by a key held in the Android Keystore.

There are no servers, no accounts and no analytics (NG-2, NFR-SEC-2).

---

## 2. The 10,000-ft view: components

```
  :app  (Android application: Compose UI, ViewModels, navigation, platform services)
    │
    ├──▶ :data     (Android library: SQLite op log, device identity, family key vault, export file)
    ├──▶ :lan      (Android library: mDNS discovery + TCP, the default PeerLink on shared Wi-Fi)
    ├──▶ :nearby   (Android library: Nearby Connections implementation of the Transport port)
    │
    ├──▶ :sync     (pure JVM: sync protocol state machine, crypto, version vectors, OpLog & Transport ports)
    ├──▶ :ledger   (pure JVM: projection of the op log into family state; balance & interest engine; rules)
    │
    └──▶ :contracts (pure JVM: ids, value types, op envelope, payload schema, codec): the frozen contract
```

**Dependency rule.** Arrows point at what a module depends on.
- `:contracts` depends on nothing in the project.
- `:ledger` and `:sync` depend only on `:contracts`.
- `:data` depends on `:contracts` and `:sync`, because it implements the `OpLog` port.
- `:nearby` depends on `:sync`, because it implements the `Transport` port.
- `:app` depends on all of them and is the only module that wires them together.

The pure-JVM modules contain everything that decides a number, so the rules from the spec can be tested on a laptop in seconds (NFR-DET-3).

| Component | Responsibility | Produces | Consumes |
|---|---|---|---|
| `:contracts` | The vocabulary: identifiers, `Micropoints`, `Money`, `Op` envelope, payload types, codec | All shared types | — |
| `:ledger` | `(ops, asOf) → FamilyState`: balances, interest, policies, projections, flags, recording rules | `FamilyState`, `Ledger.validate…` | `Op`, payloads |
| `:sync` | Lamport clock, version vectors, authenticated encrypted sync session, op creation | `SyncSession`, `OpFactory`, ports `OpLog` and `Transport` | `Op` |
| `:data` | Durable, atomic op storage, device identity, family key custody, encrypted export and import | `SqliteOpLog : OpLog`, `DeviceIdentityStore`, `FamilyKeyVault`, `ExportCodec` | `:sync` ports |
| `:lan` | Same-Wi-Fi discovery (NSD) and TCP transport | `LanLink : PeerLink`, `SocketTransport : Transport` | `:sync` ports |
| `:nearby` | Discovery, advertising, connection, framed bytes over Nearby | `NearbyTransport : Transport`, `NearbyLink` | `:sync` ports |
| `:app` | Screens from `DESIGN.md`, ViewModels, child view gate, payday notification, statement images, permissions | The APK | everything |

---

## 3. Per-component zoom

### 3.1 `:contracts`

**Structure.**
- `ids.kt` holds value classes: `FamilyId`, `DeviceId`, `ChildId`, `ChoreId`, `ValueId`, `GoalId`, `EntryId`, `OpId`, all wrapping `Uuid`.
- `Uuids.kt` holds the UUIDv5 (SHA-1) and UUIDv7 generators [R6]. The JDK only provides v3 and v4, so these are written here and tested against the RFC 9562 vectors.
- `values.kt` holds `Micropoints(Long)` with checked arithmetic, `Points(Long)`, `RateBp(Int)`, `CurrencyCode`, `MinorUnits(Long)`, `ExchangeRate(points, minorUnits)`, `InstantMs(Long)`, `LocalDay`, `Lamport(Long)` and `Seq(Long)`.
- `Op.kt` holds the envelope (§4.1).
- `payloads.kt` holds the sealed `Payload` hierarchy.
- `OpCodec.kt` converts between envelope bytes and an `Op`, and between payload JSON and a `Payload`, keeping unknown types as `Payload.Unknown`.

**State.** None.

**Failure modes.**
- **Malformed bytes:** `OpCodec.decode` returns a typed `DecodeResult.Malformed`. It never throws.
- **Unknown payload type or newer schema:** decoded as `Payload.Unknown(type, rawJson)` and kept (FR-39).

**Observability.** Not applicable.

### 3.2 `:ledger`

**Structure.**
- `Projection`: folds canonical ops into `FamilyState`, which holds:
  - the family settings
  - children, chores, values and goals (last-writer-wins records)
  - expected-chore ticks
  - the policy timelines
  - each child's entries.
- `Canonicalizer`: when several ops share an entry ID, picks the one with the lowest `(lamport, deviceId)` (FR-19).
- `Periods`: works out period boundaries using `java.time.ZoneId` (FR-29).
- `InterestEngine`: a pure fold over periods (FR-21 to FR-28), using integer micropoints and checked arithmetic.
- `Projections`: goal dates and "what if I wait" (FR-10).
- `Rules`: the checks a phone runs before recording (FR-13, FR-14). They return a `Verdict`. They never throw and are never applied during a merge (FR-36).
- `Flags`: overdrawn balances and possible duplicates (FR-20, FR-36).

**State.** None. `FamilyState` is an immutable value calculated from `(ops, asOf)`.

**Performance (NFR-PERF-1).** A full recalculation is O(ops + children × periods). The sizing dataset has 166k entries and 8 × 520 periods. A JVM benchmark test guards this. If a phone ever misses the 1 s budget, the planned fallback is a per-period snapshot cache keyed by the op-set digest, which must be proven equal to a full recalculation by a property test. It isn't built until it's needed.

**Failure modes.** Overflow returns `LedgerError.Overflow(childId)`, shown as a diagnostics flag and never as a wrong number. A child whose entries reference an unknown chore still counts: the amount lives in the entry itself.

**Observability.** `FamilyState.flags`, and the diagnostics screen's op counts.

### 3.3 `:sync`

**Structure.**
- `OpFactory`: stamps new ops with `originSeq = last + 1` and `lamport = max seen + 1`, plus a new UUIDv7 `opId`.
- `VersionVector`: a map from `DeviceId` to the highest contiguous `Seq`.
- `SyncSession`: the protocol state machine in §4.3, written over the `Transport` port.
- `SessionCrypto`: HKDF-SHA256 key derivation [R17], AES-256-GCM [R18] and HMAC-SHA256 [R19], all through `javax.crypto`.
- **Ports:**
  - `OpLog`: `vector()`, `opsAfter(vector)`, `append(ops)` (atomic, idempotent) and `all()`.
  - `Transport`: `send(frame)` and `incoming: Flow<ByteArray>`.

**State.** Kept per session, and discarded when the session ends.

**Failure modes.**
- **Authentication failure:** the session ends with `SyncOutcome.Rejected(reason)` and nothing is applied.
- **Transport drop:** every batch committed before the drop stays committed. Batches are atomic, and the receiver's vector advances only through ops it has stored, so the next session resumes from the right place.
- **Frame tampering:** the GCM tag check fails and the session is aborted.

**Observability.** A `SyncOutcome` record (peer, start and end time, ops sent and received, result) is stored locally for the last 20 sessions (NFR-OPS-3).

### 3.4 `:data`

**Structure.**
- `SqliteOpLog`: the framework `SQLiteOpenHelper` with one table, `ops`:
  - primary key `opId`
  - `UNIQUE(origin_device, origin_seq)`
  - plus `lamport`, `schema_version`, `type`, `payload BLOB`.

  An insert that hits a duplicate is ignored, which makes `append` idempotent. Each `append` runs in one transaction (NFR-DUR-1). The database runs in WAL mode.
- `DeviceIdentityStore`: a file in `noBackupFilesDir` (FR-2), created on first launch.
- `FamilyKeyVault`: the 32-byte family key, wrapped with AES-GCM by a key that never leaves the Android Keystore, and stored in `noBackupFilesDir`.
  - *Consequence by design:* a restored backup has the log but no key. The app detects this and asks the parent to re-pair by scanning another phone's QR code. Keystore keys can't be restored onto another device anyway.
- `SyncHistoryStore`: the last 20 `SyncOutcome`s.
- `ExportCodec`: a file encrypted with a passphrase (FR-46). It uses PBKDF2-HMAC-SHA256 with 600,000 iterations [R20] and AES-256-GCM, and holds the encoded ops.
- `backup_rules.xml` and `data_extraction_rules.xml`: include the database and exclude `noBackupFilesDir`, which Android excludes automatically anyway [R4].

**Failure modes.** A disk-full write fails the transaction and is reported, so the UI shows "Couldn't save". There is no partial state.

### 3.5 `:nearby`

**Structure.**
- `NearbyLink`: wraps `ConnectionsClient` with `Strategy.P2P_POINT_TO_POINT`, which suits exactly two devices and gives the highest bandwidth.
  - The service ID is the package name plus the family ID, so only phones in the same family discover each other.
  - Both phones advertise and discover while the Sync screen is open. The tie-break rule is that the phone with the lower `DeviceId` sends the connection request.
- `NearbyTransport`: fits frames into Nearby `BYTES` payloads, chunked to ≤ 32 KiB each.
- Nearby's own verification step (the 4-digit token) is accepted automatically, because security comes from the family key inside `SyncSession` (NFR-SEC-1).

**Permissions** [R8 get-started]:

| Android version | Permissions |
|---|---|
| ≤ 30 | `BLUETOOTH`, `BLUETOOTH_ADMIN`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, plus `ACCESS_FINE_LOCATION` (runtime) from API 29 |
| 31 | `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT` (runtime), with the Wi-Fi state permissions up to 31 |
| ≥ 32 | `NEARBY_WIFI_DEVICES` |

They are requested only on the Sync screen, with a plain explanation.

**Failure modes.** Discovery times out after 60 s with an explicit message. A connection rejected or lost mid-session is surfaced as `SyncOutcome.Interrupted`.

**Live verification needs two physical devices** (see `PLAN.md`).

### 3.5a `:lan`

**Structure.**
- `LanLink : PeerLink`: while `peers()` is collected, it listens on an ephemeral TCP port and registers `_housepoints._tcp` with NSD. The TXT attributes are `f` (the family tag: 16 hex characters of SHA-256 over the family id), `d` (the device id) and `n` (the name). It discovers the same service type, resolves matches one at a time, and keeps peers with the same tag and a home-network address. It holds a Wi-Fi multicast lock while discovering.
- `SocketTransport : Transport`: one TCP connection carrying `u32 length ‖ frame`. A frame over 1 MiB closes the connection.
- `LanAddresses`: the address rule (site-local, link-local, loopback and IPv6 unique-local only) and the family tag.

**Who connects.** The phone with the lower `DeviceId` dials. The other only accepts. This holds for Nearby too, so two phones never open sessions to each other at the same moment.

**Automatic sync.** While the app is on screen, `SyncController.startAutomatic` runs the local-network link. The dialling phone syncs:
- when the other phone appears
- 3 s after anything is recorded
- every 60 s while the other phone is visible.

**Failure modes.** A network without multicast (some guest Wi-Fi) means no peers are found, so the Sync screen offers Bluetooth. A connection lost mid-session is the same `Interrupted` outcome as on any transport.

### 3.6 `:app`

**Structure.**

```
app/
  di/AppGraph.kt          manual constructor injection (no framework); one graph per process
  family/FamilyRepository  OpLog + OpFactory + Projection; exposes StateFlow<FamilyState>; record*(…) commands
  ui/theme/               tokens from DESIGN.md (Color, Type, Shape), fonts in res/font
  ui/components/          LedgerRow, PaydayLine, ChildRow, Avatar, Jar, Segmented, OutcomeButton …
  ui/onboarding/          create family / join family (QR)
  ui/home/ ui/record/ ui/account/ ui/payday/ ui/child/ ui/sync/ ui/settings/
  platform/               ChildViewGate (BiometricPrompt), PaydayNotifier, StatementRenderer, QrCodes
```

**State flow.** Unidirectional.
1. A screen's ViewModel collects `FamilyRepository.state`, the `StateFlow<FamilyState>`.
2. Commands append ops through `OpFactory`, then `OpLog.append`, then a recalculation.
3. The recalculation runs on `Dispatchers.Default`.

The UI never calculates a balance itself.

**Child view.**
- A dedicated `ChildActivity` hides the system bars and calls `startLockTask()`, which gives screen pinning when the user allows it [R11].
- Leaving goes through `BiometricPrompt` with `BIOMETRIC_WEAK | DEVICE_CREDENTIAL`.

**Payday.** `PaydayNotifier` uses `AlarmManager` to post a local notification at each payday boundary (FR-43). There's no network involved and no exact-alarm permission, because an inexact alarm is enough.

**Statements.** Produced by drawing to a `Bitmap` with `android.graphics` and handed to a `FileProvider` for sharing (FR-42).

**Locked savings (FR-47 to FR-53).** The ledger owns every lock calculation (`Locks`), and the app only formats it. `FamilyRepository.settle()` records payouts that are due on every start loop. Their IDs are deterministic, so two phones recording the same payout converge to a single entry. Weekly totals and statements split flows with `ledger.Flows`, which treats lock movements as transfers rather than earning or spending.

**Widget (FR-44, since 0.2).** `widget/BalancesWidget` is a Glance `GlanceAppWidget`.
- It reads through the same `FamilyRepository.refreshNow()` as the UI, so it never calculates a balance itself.
- The model comes from `HomeModels`, so the widget's lines match Home's.
- The content collects both the repository snapshot and `AppGraph.night` as state, so a live Glance session recomposes on any change.
- `AppGraph` calls `updateAll` when the model or the theme changes, which restarts ended sessions.
- Colours resolve together through `WidgetPalette`: adaptive on API 31+, and resolved once from `night` below that.
- Taps carry a `WidgetLink` (Record, or a child's account) as explicit-intent data. `MainActivity` (`singleTop`) hands it to navigation.
- It uses system type, because widgets can't load bundled fonts, and lists children in creation order.

---

## 4. Contracts in detail

### 4.1 Op envelope (wire and storage)

| Field | Type | Notes |
|---|---|---|
| `opId` | UUID (v7) | Globally unique |
| `familyId` | UUID | Ops from another family are rejected at the sync session |
| `originDevice` | UUID | |
| `originSeq` | Long ≥ 1 | Contiguous per device |
| `lamport` | Long ≥ 1 | [R7] |
| `schemaVersion` | Int | 1 in v1 |
| `type` | String | Payload discriminator, e.g. `ledger.entry` |
| `payload` | bytes | UTF-8 JSON, **opaque to the envelope** (FR-39) |

**Envelope binary form**, used for sync and export: a length-prefixed field sequence. Version byte `0x01`, then each field, with UUIDs as 16 raw bytes, Longs and Ints big-endian, and the string and payload with u32 length prefixes. Byte-exact encoding means an op's bytes are stable everywhere.

### 4.2 Payload types (schemaVersion 1)

`CONTRACTS.md` has the full catalogue. In summary:
- one `family.created`
- typed per-record upserts (`device.upsert`, `child.upsert`, `chore.upsert`, `value.upsert`, `goal.upsert`), which are per-field last-writer-wins: an absent field means "not written"
- `ledger.entry`
- `policy.set`
- `tick.set`
- `device.removed`

Unknown and unreadable payloads are kept and forwarded.

### 4.3 Sync protocol v1 (inside `SyncSession`)

```
A                                                        B
│── HELLO {proto=1, familyId, deviceId, nonce32} ─────────▶│
│◀──────────────────────────── HELLO {…} ──────────────────│
│   both: abort if familyId differs or proto unsupported
│   k = HKDF-SHA256(ikm=familyKey, salt=nonce_lo‖nonce_hi, info="hp/sync/v1"), 64 bytes
│       k_mac = k[0..32), k_enc = k[32..64)        (lo/hi ordered by deviceId)
│── AUTH {HMAC(k_mac, "auth"‖transcript‖deviceId_A)} ──────▶│   transcript = both HELLOs, ordered
│◀──────────────────────── AUTH {…B} ──────────────────────│   both verify in constant time, else abort
│   from here every frame = AES-256-GCM(k_enc, nonce = dirByte‖counter64, aad = header)
│── VECTOR {vv_A} ─────────────────────────────────────────▶│
│◀───────────────────────────────────────── VECTOR {vv_B} ─│
│── OPS {batch ≤ 256 ops, ascending (device, seq)} … ─────▶│   receiver: append batch atomically
│◀──────────────────────────────────────── OPS … ──────────│
│── DONE {sentCount} ──────────────────────────────────────▶│
│◀────────────────────────────────────────── DONE ─────────│
│   session complete when both DONEs exchanged; each side recalculates and computes display deltas (FR-37)
```

- `dirByte` is `0x01` from the lower `DeviceId` and `0x02` from the higher. Each side keeps its own counter, so a nonce is never reused.
- Each frame is `type u8 ‖ length u32 ‖ body`.
- A received op whose `familyId` doesn't match is dropped and counted as a protocol error.

### 4.4 Ports

```kotlin
interface OpLog {
    suspend fun vector(): VersionVector
    suspend fun opsAfter(vector: VersionVector): List<Op>      // ascending (originDevice, originSeq)
    suspend fun append(ops: List<Op>): AppendResult            // atomic; duplicates ignored
    suspend fun all(): List<Op>
    val changes: Flow<Unit>
}
interface Transport {
    suspend fun send(frame: ByteArray)
    val incoming: Flow<ByteArray>
    suspend fun close()
}
```

---

## 5. Architecture decisions

- **ADR-1: Event-sourced op log with derived state.**
  - *Alternative considered:* mutable tables plus a change log.
  - *Rejected because* convergence needs a grow-only set [R9], and derived balances make identical results a structural property rather than something to keep checking.
- **ADR-2: Framework SQLite instead of Room or SQLDelight.**
  - The store is one append-only table with three queries.
  - Room adds annotation processing and a migration framework for no benefit here.
  - The `OpLog` port keeps the choice replaceable.
  - Verified by instrumented tests on a device.
- **ADR-3: Manual dependency injection.**
  - A graph of about 12 objects doesn't justify Hilt's annotation processing or start-up cost.
  - The constructors are the documentation.
- **ADR-4: The protocol does its own authentication and encryption on top of Nearby.**
  - Nearby's encryption isn't tied to family membership [R8], so the family key has to be the trust anchor (NFR-SEC-1).
- **ADR-5: `P2P_POINT_TO_POINT`.**
  - Sync is always between two phones. Three or more phones converge through pairwise syncs (FR-34), so a mesh strategy adds nothing.
- **ADR-6: Pinned toolchain.**
  - AGP 8.7.3, Gradle 8.11.1, Kotlin 2.1.0, compile and target SDK 35, JDK 17.
  - These match a project that already builds on this machine (sensorbridge), which removes toolchain risk.
  - min SDK 28 (NFR-OPS-1).
- **ADR-7: Fonts bundled as resources** (OFL) under `res/font`. There's no downloadable fonts provider, so nothing touches the network.
- **ADR-8: QR pairing.**
  - Generation uses ZXing core.
  - Scanning uses the Google code scanner, which runs inside Play Services and so needs no camera permission.
  - QR payload: `hp1:` followed by base64url of `familyId(16) ‖ familyKey(32)`.

- **ADR-9: The local network is the default link, and Nearby is the fallback.**
  - The family confirmed both phones are always on the same home Wi-Fi.
  - mDNS and TCP need no Bluetooth or location permission. They can sync automatically while the app is open, and they're faster and easier to test.
  - The cost is the `INTERNET` permission (see NFR-SEC-2). It's mitigated by the address rule, by advertising a hashed family tag, and by every session being authenticated and encrypted under the family key.

---

## 6. Quality attribute traceability

| NFR | Where it's met | How it's verified |
|---|---|---|
| DET-1/2/3 | `:ledger`, `:sync` (pure JVM) | Spec examples as tests; 3-device property tests over a loopback transport |
| PERF-1 | `InterestEngine` | JVM benchmark test on the sizing dataset, then a check on the device |
| PERF-2 | `:nearby` + `SyncSession` | Timing on two devices (blocked: one phone available) |
| DUR-1 | `SqliteOpLog` transactions | Instrumented test: append then kill then reopen |
| SEC-1 | `SyncSession` | Tests: wrong key is rejected; a tampered frame aborts; a replayed nonce is rejected |
| SEC-2 | Manifest, no network clients | Merged-manifest check in CI (OQ-5) |
| A11Y-1..3 | `ui/theme`, components | Token contrast tests; 48 dp minimums in components; TalkBack pass on the device |

---

## References (additional to SPEC.md)

- **[R17]** Krawczyk, H., & Eronen, P. *RFC 5869: HMAC-based Extract-and-Expand Key Derivation Function (HKDF).* IETF, 2010.
- **[R18]** Dworkin, M. *NIST SP 800-38D: Galois/Counter Mode (GCM) and GMAC.* NIST, 2007.
- **[R19]** Krawczyk, H., Bellare, M., & Canetti, R. *RFC 2104: HMAC.* IETF, 1997.
- **[R20]** OWASP *Password Storage Cheat Sheet*: 600,000 iterations for PBKDF2-HMAC-SHA256.

---

## Confidence gate

```
Confidence: high
Why: every SPEC requirement maps to a component; every number-deciding rule is in pure JVM modules
  with a test plan; the protocol, storage and key handling are specified at byte level.
Residual risks:
  - Live Nearby verification needs a second phone (NFR-PERF-2, :nearby live-verified).
  - Google code scanner availability depends on Play Services modules downloading on first use; fallback
    is manual entry of the pairing code shown under the QR.
Known gaps: widget (FR-44, SHOULD) deferred to after v1; recorded in PLAN.md.
```
