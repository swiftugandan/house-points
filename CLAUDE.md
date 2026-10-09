# Project: House Points

@import ./ENGINEERING_DISCIPLINE.md

## Overview

An Android app that runs only on parents' phones and keeps a family "bank" for children. Children earn points for chores and for behaviour that reflects family values. Points earn weekly interest while they stay in the account and can be cashed out for real money, which parents hand over. Parents' phones sync with each other peer-to-peer with no server and no internet. Every balance is calculated from an append-only log of operations, so phones that hold the same log always show the same numbers.

## Current phase

Phase 6, implementation. State as of 2026-10-09 (0.2.0: SPEC Amendment 1, contracts 0.2.0 frozen):
- `:contracts` is frozen at 0.2.0 (Amendment 1, backward-compatible with 0.1.0 readers; see CrossVersionTest).
- `:ledger`, `:sync` and `:data` are live-verified. `:ledger` was verified on the JVM against the spec examples; `:sync` and `:data` against the app on a TCL T1 Pro, through a desktop peer over `adb forward`.
- `:lan` has been verified server-side over USB only. The test Wi-Fi had client isolation.
- `:nearby` has been tested against mocks only. It needs two phones.
- `:app` is refactored: installed and exercised on the device (light and dark mode, 200% text), with JVM tests for its pure layer. Polish since then (jar scaling, backdating) was done test-first.

Open items are in the final section of `PLAN.md` and in "Notes for future sessions" below.

## Quick status

Run these to get a snapshot:

```sh
# Phase artifacts that have been tagged
git tag --list 'spec/*' 'sad/*' 'contracts/*' 'plan/*'

# Modules and where they are in their TDD cycle
git tag --list 'module/*'

# What is live-verified
git tag --list 'module/*/live-verified'
```

## Key documents

- `ENGINEERING_DISCIPLINE.md`: how engineering work is done here (imported above, loaded in every session)
- `VISION.md`: one-paragraph problem statement and definition of done
- `CONCEPT.md`: plain-language product concept for parents and other families (not a spec)
- `SPEC.md`: functional and non-functional requirements with citations, including the ledger and interest rules and their worked examples
- `PRODUCT.md`, `DESIGN.md`: product context and visual system in impeccable.style conventions
- `SAD.md`: software architecture document (Phase 3, not yet written)
- `CONTRACTS.md`: prose documentation of the contracts module (Phase 4, not yet written)
- `PLAN.md`: decomposition with dependency DAG and acceptance criteria (Phase 5, not yet written)

## Notes for future sessions

- **Language and platform:** Kotlin, Android, Jetpack Compose. The protocol's "contracts crate" is a **pure-JVM Kotlin module** (planned name `:contracts`) with no Android dependencies, so contract and ledger tests run on the plain JVM. Do not look for `crates/`.
- **Kotlin equivalents of the forbidden smells** in `ENGINEERING_DISCIPLINE.md`:
  - `!!` stands in for `unwrap()`.
  - `error()` and `require()` on recoverable paths stand in for panics. Return a sealed result type instead.
  - Also forbidden: `GlobalScope`, `runBlocking` on the main thread, `lateinit` used to dodge initialisation order, and catching `Throwable`/`Exception` without rethrowing or modelling the failure.
- **Determinism is the core invariant.** The balance and interest functions are pure: `(operation set, as-of instant) → balances`. Never introduce floating point, wall-clock reads, locale-dependent formatting, or iteration over unordered collections into that path.
- **The interest adjustment after a sync is a display-only difference** between a locally stored snapshot of the last shown balance and the new one. It is never a ledger entry.
- **Running on the phone:** set `JAVA_HOME=/Users/p.munaawa/Library/Java/JavaVirtualMachines/jdk-17.0.20.1+1/Contents/Home`, then run `./gradlew :app:installDebug`. The SDK is at `~/Library/Android/sdk` (`local.properties`).
- **Live LAN test:**
  1. Run `adb forward tcp:<port> tcp:<port>`. Find the port in `/proc/net/tcp6` for the app's uid, or in the NsdService logcat.
  2. Run `./gradlew :lan:testDebugUnitTest --tests '*LiveDesktopPeerTest*' -Php.peer=127.0.0.1:<port> -Php.pairing=<code> --no-configuration-cache`.
- **Process deviation, recorded honestly:** `:app` code was written before its tests, which breaks the red-first rule. Its JVM tests came after, and new app work should go back to red-first.
- **The `.env*` rule in the global instructions applies here too.**
