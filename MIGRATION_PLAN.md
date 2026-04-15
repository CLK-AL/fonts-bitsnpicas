# Migration Plan: fonts-bitsnpicas → Kotlin / KMP / Gradle

This plan is the execution companion to [`CODE_REVIEW.md`](CODE_REVIEW.md)
and [`TEST_REVIEW.md`](TEST_REVIEW.md). It turns every reviewed finding
into a failing Kotlin test (TDD), drives the repository to **100 % Kotlin
test coverage on the JVM**, then migrates production code to Kotlin
Multiplatform (KMP) common source, while preserving the existing Java
sources untouched for reference and shipping a **dual Java-legacy /
KMP** CI/CD pipeline.

---

## 0. Guiding principles

1. **Legacy Java is read-only.** The existing
   `main/java/BitsNPicas/src/**` tree is frozen. No edits; it stays as
   the reference implementation and for the legacy Java build.
2. **TDD, strictly.** Every finding in `CODE_REVIEW.md` is first encoded
   as a **failing** Kotlin test driven against a new KMP target, *then*
   fixed in the new Kotlin source. The legacy Java code is never
   patched.
3. **Bytewise round-trip parity.** The new KMP code must reproduce the
   legacy Java code's output byte-for-byte for every corpus font
   before we declare a format "migrated." Differential tests compare
   legacy Java import/export against new Kotlin import/export.
4. **Dual CI/CD.** Two pipelines run on every PR:
   - `java-legacy` — builds the legacy Ant project and runs the
     retained `*Test` CLIs in assertion mode.
   - `kmp` — builds the new Gradle KMP project and runs the Kotlin
     test suite on JVM, JS (Compose HTML), and Native targets.
5. **Migration gate: 100 % Kotlin line + branch coverage on JVM**
   before a given format moves from Java-delegate to pure `commonMain`.

---

## 1. Repository layout after migration

```
fonts-bitsnpicas/
├── main/java/BitsNPicas/src/**            ← legacy Java (frozen)
├── legacy-build.xml                        ← renamed from build.xml
├── build.gradle.kts                        ← new KMP root
├── settings.gradle.kts
├── kmp/
│   ├── core/                               ← KMP library
│   │   ├── src/commonMain/kotlin/…         ← pure format logic
│   │   ├── src/commonTest/kotlin/…         ← JUnit 5 / kotlin.test
│   │   ├── src/jvmMain/kotlin/…            ← JVM adapters (java.io.File)
│   │   ├── src/jvmTest/kotlin/…            ← JVM-only tests
│   │   ├── src/jsMain/kotlin/…             ← Browser/Node adapters
│   │   └── src/nativeMain/kotlin/…         ← Native adapters
│   ├── ui-swing/                           ← new KMP Compose Desktop host
│   ├── ui-compose-html/                    ← Compose for Web (wasm/js)
│   └── ui-shared/                          ← shared Compose UI
├── testdata/                               ← corpus (small committed fonts)
├── .github/workflows/
│   ├── java-legacy.yml
│   └── kmp.yml
```

The legacy Ant/Eclipse project is preserved verbatim so the existing
Java codebase remains buildable. The Gradle build lives alongside it.

---

## 2. Toolchain

| Concern | Choice |
| --- | --- |
| Build          | Gradle 8.x Kotlin DSL, version catalog in `gradle/libs.versions.toml` |
| Kotlin         | 2.x with K2 compiler, `kotlin.mpp.stability.nowarn=true` |
| Test framework | `kotlin.test` + JUnit 5 platform on JVM; `kotlin.test-js` on JS; Kotest for property-based tests |
| Coverage       | Kover 0.8.x with per-module rules (`coverage { minBound = 100 }`) |
| Mutation       | Pitest (`pitest-kotlin`), JVM module only, quality gate 85 % |
| Property tests | Kotest `property` for fuzzing the parsers (see §4) |
| Static         | Detekt + ktlint + SpotBugs (retained for Java legacy only) |
| Fuzz           | Jazzer (JVM) driven from `jvmTest`, seed corpus from `testdata/` |
| Load / bench   | `kotlinx-benchmark` + JMH for BDF/PSF/FNT throughput |
| UI (JVM)       | Compose Multiplatform Desktop (Swing interop for legacy viewer) |
| UI (Web)       | Compose HTML / Compose for Web (wasm) |
| CI             | GitHub Actions matrix: `java-legacy` (JDK 8 + Ant) and `kmp` (JDK 21 + Gradle, `macos-latest`, `ubuntu-latest`, `windows-latest`) |

---

## 3. Test strategy across every level

The seven test levels map to concrete Gradle source sets. Every level
is required before a format is considered "migrated."

| Level | Gradle source set | Runner | Purpose |
| --- | --- | --- | --- |
| Unit | `commonTest` / `jvmTest` | `kotlin.test` | Per-function parser/writer/model behaviour. |
| Integration | `jvmTest` | JUnit 5 | Round-trip import→export across `testdata/` corpus. |
| UI (Desktop) | `ui-swing:jvmTest` | Compose UI test | Glyph editor renders / interactions. |
| UI (Web) | `ui-compose-html:jsTest` | Compose Web test | Browser renders a glyph grid. |
| API (contract) | `jvmTest` / `jsTest` | JUnit 5 | Public API surface of `BitmapFont` / importers / exporters is stable. Kotlin/Native header smoke tests. |
| E2E | `:e2e-tests` | JUnit 5 + Gradle `runKmpCli` | Run the KMP-built CLI over `testdata/`, diff against legacy Java CLI output. |
| Load / perf | `:benchmarks` | `kotlinx-benchmark` | Throughput for 1 MiB PSF, 1 MiB BDF; regression gate ±10 %. |
| Fuzz | `jvmTest` | Jazzer | Harness per importer; runs 60 s/format in CI, 24 h nightly. |

---

## 4. TDD test plan — one failing test per `CODE_REVIEW.md` finding

Each finding becomes a named Kotlin test that **fails** against a thin
Kotlin wrapper over the frozen Java code and **passes** only after the
new Kotlin implementation is written correctly. This proves the fix
exists and protects against regressions.

### 4.1 Critical findings → failing tests

| Finding | Failing Kotlin test | Source |
| --- | --- | --- |
| 1. OOB in PSF glyph reader | `PsfImporterTest.`​`malformed_psf_does_not_throw_AIOOBE_on_short_data()` | `commonTest` |
| 2. Unchecked FNT `data[dx]` | `FntImporterTest.`​`truncated_fnt_returns_error_not_AIOOBE()` | `commonTest` |
| 3. Unbounded PSF allocations | `PsfImporterTest.`​`huge_numGlyphs_rejected_under_OOM_limit()` (asserts `ImportException`) | `commonTest` |
| 4. Scanner/FIS leak in BDF | `BdfImporterResourceTest.`​`importing_1000_times_does_not_leak_fds()` (JVM-only, `/proc/self/fd` probe) | `jvmTest` |
| 5. Empty-dim glyph compose | `BitmapFontGlyphTest.`​`compose_with_equal_bounds_returns_empty_or_null_never_throws()` | `commonTest` |

### 4.2 Major findings → failing tests

| Finding | Failing Kotlin test |
| --- | --- |
| 6. BDF silent truncation | `BdfImporterTest.truncated_bitmap_is_reported_not_zero_filled()` |
| 7. BDF export 1.1 M iterations | `BdfExporterPerfTest.export_with_10_glyphs_completes_under_50_ms()` (JMH perf gate) |
| 8. Charset lookup silent stderr | `BdfImporterTest.unknown_charset_raises_ImportWarning()` |
| 9. Replacement codepoint accepted | `BdfImporterTest.U_FFFD_codepoint_rejected()` |
| 10. `StringBuffer` in FNT | covered by mutation / Kover — no functional test. |

### 4.3 Minor / nit findings → failing tests

- `HexImporterTest.height_zero_input_is_rejected_not_arithmetic_exception()`
- `FntImporterTest.unsigned_byte_range_firstChar_lastChar_roundtrip()`
- `PsfImporterTest.row_width_inconsistency_is_reported()`
- `BitmapFontGlyphTest.empty_glyph_has_well_defined_baseline()`

All tests above are authored in `commonTest` where possible so the same
suite runs on JVM, JS, and Native. File-system access lives in
`jvmTest` behind an `expect`/`actual` `TestFixtureLoader`.

### 4.4 Test sequencing (strict TDD)

For every format `F` (PSF, FNT, BDF, Hex, Playdate, TTF, PUAA, …):

1. **Port the Java → Kotlin delegate** in `jvmMain` (`class
   ${F}ImporterJavaAdapter` that calls through to the legacy Java
   importer). Exists purely to host tests.
2. **Write the negative tests red** against the Java delegate. They
   will fail because the legacy Java code has the known bugs.
3. **Write the positive round-trip tests** against the Java delegate.
   They pass — this pins the current correct behaviour.
4. **Author pure Kotlin `commonMain` implementation.** Run both suites
   against it.
5. The Kotlin implementation must pass **both** the negative suite
   (the bugs are fixed) **and** the positive suite (no regressions
   vs. legacy).
6. **Differential test:** for every file in `testdata/`, import with
   Java and Kotlin, assert glyph-exact equality; export with both,
   assert byte-exact equality for deterministic formats.
7. **Kover gate 100 %** on the new Kotlin code before merging.
8. Retire the Java delegate for `F`. Legacy Java remains only as a
   reference corpus.

### 4.5 Test corpus

A committed corpus under `testdata/` (minimal, license-clean samples):

- `testdata/psf/good/*.psf` — at least one PSFv1 and one PSFv2.
- `testdata/psf/bad/*.psf` — truncated, zero-glyph, oversized-header,
  Integer.MAX_VALUE-numGlyphs, mismatched-width-row.
- Mirror sets for `bdf/`, `fnt/`, `hex/`, `playdate/`, `ttf/`,
  `puaa/`.
- `testdata/fuzz/corpus/${format}/` — seed for Jazzer.

The **bad** corpus is what makes the critical-finding tests
executable. Each bad file is generated by a small Kotlin fixture
script committed under `testdata/gen/`.

---

## 5. 100 % Kotlin coverage gate (per format)

- Kover rule per module:
  `koverVerify { rule { bound { minValue = 100; metric = LINE }; bound { minValue = 100; metric = BRANCH } } }`
- Pitest mutation quality threshold 85 % on `commonMain` + JVM
  adapters.
- No ignores, no `// LCOV_EXCL`. Coverage exclusions for generated
  code only, and only from generated-code source sets.
- CI fails the PR if coverage drops below threshold on any module.

Format `F` is considered "migrated" only when (a) the Kotlin
implementation exists in `commonMain`, (b) both the negative and
positive suites pass, (c) the differential suite is byte-exact for
the full corpus, (d) coverage gates pass, (e) the Java delegate has
been deleted from `jvmMain`.

---

## 6. Migration phases

### Phase A — scaffolding (1 PR)

- Land Gradle KMP root, `settings.gradle.kts`, version catalog.
- Preserve existing Ant build as `legacy-build.xml`.
- Land `.github/workflows/{java-legacy,kmp}.yml`.
- Commit empty `testdata/` tree with README describing the corpus
  contract.
- Gate: both CI lanes green on the empty Kotlin module.

### Phase B — JVM bridge + corpus (1 PR)

- Add `jvmMain` adapters that delegate every importer/exporter to
  legacy Java.
- Commit minimal `testdata/` corpus (known-good + known-bad).
- Write the full **positive** round-trip suite against the bridge —
  this pins today's correct behaviour.
- Gate: all positive tests green against Java bridge, coverage >= 80 %
  on the bridge module.

### Phase C — TDD-fix one format at a time

For each format in priority order
(**PSF → FNT → BDF → Hex → Playdate → TTF → PUAA**):

1. Write **negative** Kotlin tests from §4.1–4.3. Expected **red**.
2. Implement `commonMain` version. Tests go **green**.
3. Run differential suite against corpus. Must be byte-exact (or
   document the intentional divergence as a separate test).
4. Raise Kover gate to 100 % on that format's module.
5. Delete the Java bridge for that format.

Each format is one PR; merges are gated on all three Kover lanes
(`jvm`, `js`, `native`) plus the `java-legacy` lane continuing to be
green.

### Phase D — UI migration

- `ui-swing/` — wrap the existing Swing editor in a thin adapter so
  the new Kotlin font models back it. Keep the Swing host, migrate
  the view-model to Kotlin.
- `ui-shared/` — extract Compose Multiplatform components
  (`GlyphGrid`, `FontList`, `CodepointPicker`).
- `ui-compose-html/` — Compose for Web wasm host reusing
  `ui-shared`.
- UI tests:
  - JVM: `@Composable` tests via Compose UI testing (`runDesktopUiTest`).
  - Web: Compose Web `test-renderer` asserts DOM snapshot.
- E2E (Phase E) drives both hosts.

### Phase E — E2E and load/perf

- `e2e-tests` module: drives the KMP CLI (`./gradlew :cli:installDist`)
  against `testdata/`, compares output to the legacy Java CLI. Any
  divergence fails the build.
- `benchmarks/`: JMH-driven throughput gates. Baselines committed
  under `benchmarks/baselines/*.json`; regressions > 10 % fail PRs.
- Browser E2E: Playwright Kotlin drives the Compose Web build hosted
  by a GitHub Actions service.

### Phase F — dual CI/CD & release

- `java-legacy` workflow: builds the Ant project, runs the (now
  assertion-enabled) legacy `*Test` CLIs, publishes the classic JAR
  under the `legacy` release channel.
- `kmp` workflow: builds the Gradle project, runs the full
  Unit/Integration/UI/API/E2E/Load/Fuzz matrix, publishes:
  - `bitsnpicas-core-jvm` to Maven Central.
  - `bitsnpicas-core-js` to npm.
  - `bitsnpicas-desktop` signed DMG/MSI/AppImage via
    `compose-desktop`.
  - `bitsnpicas-web` static site to GitHub Pages.
- A release tag publishes both channels simultaneously; the legacy
  channel is versioned with a `-legacy` suffix.

---

## 7. Acceptance criteria

The repository is considered migrated when **all** of the following
hold:

1. Every `CODE_REVIEW.md` finding has a matching **named** Kotlin test
   whose Git history shows it merged **red**, then **green** after the
   Kotlin fix (verifiable via `git log -p` on the fix commit).
2. `./gradlew koverVerify` reports 100 % line + branch coverage on
   every non-UI module.
3. `./gradlew pitestReport` reports >= 85 % mutation coverage.
4. The differential suite reports byte-exact parity with legacy Java
   for every file in the committed corpus.
5. The `java-legacy` lane still passes on every PR (no Java sources
   modified).
6. `kmp` lane passes on all three OSes + all three targets (JVM, JS,
   Native).
7. Legacy Java sources remain under `main/java/BitsNPicas/src/**`
   untouched since Phase A.

---

## 8. Deliverables summary

- `build.gradle.kts`, `settings.gradle.kts`, `libs.versions.toml`.
- `kmp/core/` with `commonMain`, `jvmMain`, `jsMain`, `nativeMain`
  and paired `*Test` source sets.
- `ui-swing/`, `ui-shared/`, `ui-compose-html/` Compose modules.
- `testdata/` corpus with generator scripts.
- `.github/workflows/java-legacy.yml` and
  `.github/workflows/kmp.yml`.
- `TEST_PLAN.md` cross-referencing every `CODE_REVIEW.md` finding to
  its Kotlin test's fully-qualified name (auto-generated from
  `@DisplayName` annotations).
- `benchmarks/baselines/*.json` throughput baselines.
