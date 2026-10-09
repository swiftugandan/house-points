# House Points

[![CI](https://github.com/swiftugandan/house-points/actions/workflows/ci.yml/badge.svg)](https://github.com/swiftugandan/house-points/actions/workflows/ci.yml)

A family bank that lives on the parents' Android phones.
- Children earn points for jobs and for behaviour that reflects the family's values.
- Points earn weekly interest on the smallest balance held all week.
- Points can be cashed out for real money, which a parent hands over.

There's no account, no server and no internet: parents' phones sync with each other directly over the home Wi-Fi, or over Bluetooth.

**Website:** https://swiftugandan.github.io/house-points/ · **Download:** [latest release](https://github.com/swiftugandan/house-points/releases/latest)

## How it's built

Every balance is a pure function of an append-only log of operations. Phones exchange the operations they're missing, and two phones holding the same operations always show the same balances, to the micropoint.

| Module | What it is |
|---|---|
| `contracts` | The frozen vocabulary: IDs, quantities, the op envelope and payloads, and the codec (pure JVM) |
| `ledger` | Ops in, family state out: interest, recording rules, chores, goal projections (pure JVM) |
| `sync` | Op factory, version vectors, and the authenticated, encrypted sync protocol (pure JVM) |
| `data` | SQLite op log, Keystore-wrapped family key, device identity, encrypted export (Android) |
| `lan` | Same-Wi-Fi discovery (mDNS) and TCP transport (Android) |
| `nearby` | Bluetooth fallback through Nearby Connections (Android) |
| `app` | Jetpack Compose UI in the "Passbook" design (Android) |

## Design documents

| Document | Purpose |
|---|---|
| [VISION.md](VISION.md) | One paragraph |
| [CONCEPT.md](CONCEPT.md) | The idea, for parents |
| [SPEC.md](SPEC.md) | Requirements and the worked interest examples, which are also the tests |
| [SAD.md](SAD.md) | Architecture |
| [CONTRACTS.md](CONTRACTS.md) | The contracts module |
| [PLAN.md](PLAN.md) | Build order and acceptance |
| [PRODUCT.md](PRODUCT.md), [DESIGN.md](DESIGN.md) | Product context and the visual system |

## Building

You need JDK 17 and an Android SDK with platform 35 (set `sdk.dir` in `local.properties`).

```sh
./gradlew test                    # contracts, ledger, sync on the JVM
./gradlew testDebugUnitTest       # Android modules' JVM tests
./gradlew :app:installDebug       # installs "House Points (debug)" beside any release install
./gradlew :app:assembleRelease    # signed if housepoints.signing.* are set in ~/.gradle/gradle.properties
```

The release signing key is never in this repository. Release APKs are signed with the key whose SHA-256 certificate fingerprint is:

`D1:B3:C7:CB:46:85:7C:2A:2D:39:82:B5:D4:77:CA:11:BE:C0:26:DB:3C:E1:F5:35:5E:0B:8B:DD:CB:A8:B3:E9`

## Fonts

Bricolage Grotesque and Atkinson Hyperlegible (Next and Mono) are bundled under the SIL Open Font License. See [licenses/](licenses/).

## Licence

MIT. See [LICENSE](LICENSE). The bundled fonts keep their own SIL Open Font License.
