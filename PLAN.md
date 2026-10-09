# Implementation plan

Modules are listed in dependency order. Each one follows the cycle in `ENGINEERING_DISCIPLINE.md`: red → green → refactor → e2e-mock → live, with tags `module/<name>/<phase>`.

```
            :contracts  (frozen 0.1.0)
             ▲      ▲
       :ledger      :sync
             ▲      ▲   ▲
             │   :data  :nearby
             │      ▲     ▲
             └──── :app ──┘
```

There are no cycles. `:ledger` and `:sync` are independent of each other, and only `:app` combines them.

## 1. `:ledger` (pure JVM)

- **Depends on:** `:contracts`.
- **Consumes:** `List<Op>` and an `asOf: InstantMs`.
- **Produces:**
  - `FamilyState`: settings, records, policies, per-child balances, period history, flags
  - `Rules` verdicts for recording
  - goal projections.
- **Acceptance (live e2e at its boundary):**
  - SPEC worked examples 1 to 8 pass as tests that feed ops into `Projection.project(ops, asOf)`.
  - A property test shows that any permutation or duplication of the op list gives an identical `FamilyState`.
  - A benchmark test shows the sizing dataset (166k entries) projects within budget on the JVM.
- **Live peers:** none. Since it's a pure function, e2e-mock and live are the same suite.

## 2. `:sync` (pure JVM)

- **Depends on:** `:contracts`.
- **Consumes:** the `OpLog` and `Transport` ports and a family key.
- **Produces:** `OpFactory`, `VersionVector`, `SyncSession` (SAD §4.3), `SyncOutcome`.
- **Acceptance:**
  - Three in-memory devices with random op creation and random pairwise syncs over a loopback `Transport` always end with identical op sets (NFR-DET-2).
  - A wrong family key is rejected before any op is exchanged.
  - A tampered frame aborts the session.
  - An interrupted session resumes without loss or duplication.

## 3. `:data` (Android library)

- **Depends on:** `:contracts`, `:sync`.
- **Produces:** `SqliteOpLog : OpLog`, `DeviceIdentityStore`, `FamilyKeyVault`, `SyncHistoryStore`, `ExportCodec`.
- **Acceptance (instrumented, on the connected phone):**
  - `SqliteOpLog` passes the same `OpLog` contract test suite as the in-memory log.
  - Appends are atomic and idempotent across a process restart.
  - The vault round-trips through the Keystore.
  - An export that is then imported gives the same op set.

## 4. `:nearby` (Android library)

- **Depends on:** `:sync`.
- **Produces:** `NearbyTransport : Transport` and `NearbyLink` (discovery, advertising, connection).
- **Acceptance:**
  - The framing and chunking unit tests pass.
  - With mocked peers: `SyncSession` over a paired in-process `Transport` built on the same framing code.
  - **Live:** two physical phones converge (NFR-PERF-2 measured). *Blocked until a second phone is available*, so the `live-verified` tag waits until then.

## 4a. `:lan` (Android library, added with SAD ADR-9)

- **Depends on:** `:sync`, using the `PeerLink` and `Transport` ports.
- **Produces:** `LanLink : PeerLink`, `SocketTransport : Transport`, `LanAddresses`.
- **Acceptance:**
  - Framing tests over real sockets.
  - The address rule.
  - A full `SyncSession` over two `SocketTransport`s converging.
  - **Live:** a desktop peer (a JVM `SyncSession` over a socket) syncing with the app on the phone over home Wi-Fi.
  - **Phone-to-phone:** still needs a second phone.

## 5. `:app` (Android application)

- **Depends on:** everything.
- **Milestones:**
  1. Theme tokens and fonts, `FamilyRepository`, onboarding (create a family), and the Home, Record and Child account screens.
  2. Payday statement with share, child view (Picture and Number) with the biometric exit, and settings (children, chores, values, policies).
  3. Pairing by QR and the Sync screen.
  4. Payday notification, export and import, diagnostics.
- **Acceptance:**
  - Installed on the connected phone, a parent can create a family, add three children and chores, record entries, see balances and interest matching `:ledger`, open both child views, and share a statement.
  - Both themes are checked with screenshots.

## Deferred (recorded, not forgotten)

- **`:lan` live over Wi-Fi between two phones:** the test network had client isolation.

- Home-screen widget (FR-44, SHOULD).
- `:nearby` live verification and the OQ-4 measurement: these need a second phone.

---

## Confidence gate

```
Confidence: high
Why: the DAG is acyclic and matches SAD; every module has a boundary acceptance test; the pure modules
  carry all the arithmetic, and the platform modules are thin adapters behind ports with contract tests.
Residual risks:
  - Only one physical phone is available, so the Nearby live-verified tag is blocked (tracked above).
Known gaps: none blocking implementation.
```
