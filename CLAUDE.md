# Project: House Points

@import ./ENGINEERING_DISCIPLINE.md

## Overview

An Android app that runs only on parents' phones and keeps a family "bank" for children. Children earn points for chores and for behaviour that reflects family values. Points earn weekly interest while they stay in the account and can be cashed out for real money, which parents hand over. Parents' phones sync with each other peer-to-peer with no server and no internet. Every balance is calculated from an append-only log of operations, so phones that hold the same log always show the same numbers.

## Current phase

Phase 2: specification drafted. The confidence gate has not cleared yet; `SPEC.md` lists the open questions. `spec/v0.1.0` has not been tagged.

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
- **The `.env*` rule in the global instructions applies here too.**
