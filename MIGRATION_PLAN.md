# Migration Plan: fonts-bitsnpicas → Kotlin / KMP / Gradle

Execution companion to [`CODE_REVIEW.md`](CODE_REVIEW.md) and
[`TEST_REVIEW.md`](TEST_REVIEW.md). Drives every review finding through
a strict **fix-then-freeze-then-port** cycle:

1. Author Kotlin TDD tests that fail against today's Java code.
2. **Fix the Java code** until every test is green and JVM coverage
   reaches 100 % line + branch.
3. **Freeze** the now-stable Java sources and tag them as the legacy
   reference.
4. **Migrate** the frozen Java logic to pure Kotlin Multiplatform
   (`commonMain`), reusing the *same* Kotlin test suite — which now
   runs against both the frozen Java and the new Kotlin
   implementations as a differential parity gate.

Two CI/CD profiles (`java`, `kmp`) run on every PR throughout.

---

## 0. Guiding principles

1. **TDD, strictly.** Every finding in `CODE_REVIEW.md` becomes a red
   Kotlin test **before** any production code changes.
2. **Java is stabilised first, then frozen.** Unlike a pure "port and
   rewrite," the existing Java is fixed to pass the Kotlin tests and
   reach 100 % coverage. The fix commits are attributable
   line-for-line to findings. Only after stabilisation does the Java
   tree become read-only.
3. **Shared test suite across Java and Kotlin.** The Kotlin tests are
   written against a platform-neutral `FontIo` interface implemented
   twice: a JVM adapter over the frozen Java, and pure Kotlin in
   `commonMain`. The same `@Test` methods run against both — any
   divergence fails the build.
4. **100 % coverage at every transition.** Java reaches 100 % before
   it is frozen; Kotlin must maintain 100 % before a format migrates
   from Java-delegate to pure `commonMain`.
5. **Dual CI/CD profiles.** Gradle build profiles `-Pprofile=java`
   (builds + tests the frozen Java via Gradle's JVM toolchain) and
   `-Pprofile=kmp` (builds + tests the KMP project on
   JVM/JS/Native/wasmJs). Every PR runs both.
6. **Reproducible toolchain via SDKMAN.** All contributors and CI
   consume the exact same JDK / Kotlin / Gradle / JBang versions
   declared in `.sdkmanrc`, pinned to the **latest stable** at
   migration time and updated via a single PR when bumping.

---

## 0.5 Roadmap stages (delivery checkpoints)

Each stage is a tagged milestone; CI profiles must be green for a tag
to land. The UI testing progression is woven into the stages rather
than kept as a separate schedule — every stage advances both the
logic port and the UI parity oracle.

| # | Stage | What lands | UI testing state |
| --- | --- | --- | --- |
| S0 | **Baseline** (✅ complete) | `CODE_REVIEW.md`, `TEST_REVIEW.md`, `MIGRATION_PLAN.md`, `INTEGRATION.md`, PUML diagrams, `.sdkmanrc` 25.0.2-graal + 2.3.20 + 9.4.1 + 0.138.0, `gradle/libs.versions.toml` verified-stable, Gradle build alongside any existing Maven/Ant. | — |
| S1 | **Red-then-green Critical fixes** (✅ complete) | Per `CODE_REVIEW.md` Critical finding: Kotlin red test + Java fix commit pair; CI profile=java green. | `UiDriver` **expect** scaffolded in `modules/ui-shared/commonTest`. No actuals yet. |
| S2 | **Phase B coverage drive** (**next**) | JaCoCo 100 % line + branch on the Option-C in-migration packages (see §5). Remaining Majors / Minors from `CODE_REVIEW.md` closed. | **Swing `actual` lands**: drives `UiDriver` via `BufferedImage` + `Graphics2D` + AssertJ-Swing. Expected ARGB hashes captured and committed under `testdata/snapshots/swing/**`. Swing becomes the **pinning oracle**. |
| S3 | **Freeze** — tag `legacy-v1` | CODEOWNERS read-only on `main/java/BitsNPicas/src/**`; CI diff-check guard. | Swing `actual` hashes frozen. Any future PR that renames or re-renders a glyph must explicitly bump the snapshot. |
| S4 | **Phase D** (✅ bitmap formats complete) — port logic to `commonMain` | One subsystem per PR: `commonMain` Kotlin + `KotlinFontIo` actual. Same Kotlin tests run twice (frozen Java + Kotlin), byte-exact differential parity on `testdata/`. Kover 100 % + Pitest ≥ 85 % per ported module. | Swing `actual` still the only UI. `UiDriver`-driven tests keep passing against the Swing renderer regardless of the logic swap underneath. |
| S5 | **Phase E.1** (✅ scaffolded; Swing oracle green) — Compose Desktop UI | `ui-compose-desktop` host + Compose composables in `ui-shared`. `ComposeDesktopUiDriver` `actual` lands. | **Green renderer joins the matrix.** Every `UiDriver` test runs under both Swing (blue) and Compose Desktop (green) ARGB-hash-equal; divergence fails CI. Same fixtures, same expected hashes. |
| S6 | **Phase E.2** (✅ scaffolded; stub actual) — Compose for Web (wasmJs) | `ui-compose-html` host. `ComposeWebUiDriver` `actual` lands (Compose test-renderer + Playwright Kotlin nightly ARGB grab). | Web joins the matrix. Three renderers, one suite, one set of hashes. |
| S7 | **Phase F** (✅ CI workflows + ProGuard stubs) — Dual CI/CD release | `profile=java` publishes ProGuarded `*-legacy` JAR. `profile=kmp` publishes core klibs (JVM/JS/wasmJs/Native), Compose Desktop signed bundle (native-image via `org.graalvm.buildtools.native` 0.10.6), Compose Web static site. `verifyProguardedJar` gates both. | Swing `actual` retained through S7 for cross-check. Deleted only after N consecutive tagged releases pass the full parity matrix. |

UI migration in plain terms: **Swing stays as the test oracle
through every stage from S2 onward; Compose implementations prove
themselves against it on every PR.** No flag-day cut-over.

---

## 0.6 S4 port progress log

Stage S4 ports land under `modules/core/` with a
commonMain port + a `jvmMain` `JavaLegacyAdapter` + a jvmTest
differential-parity gate against the frozen Java. Each round
stacks incrementally; all rounds keep `./gradlew :modules:core:check`
green and `./gradlew test` (legacy profile) green. Zero `java.*`
imports in commonMain.

| Round | SHA | Port | commonMain surface added | Tests (module) | Parity |
| --- | --- | --- | --- | --- | --- |
| R1 | `fafd0fe` + `1c51930` | `BitmapFontGlyph.compose` | `Glyph` interface, `BitmapGlyph` (compose+contract) | 15 | 6 byte-exact |
| R2 | `beb205f` | `PSFBitmapFontImporter` | `BitmapFont`, `ByteReader` *(later extracted)*, `PsfImporter.read(ByteArray)` | 41 | 9 byte-exact |
| R3 | `03bf542` + `b307550` | `FNTBitmapFontImporter` | Shared `ByteReader`, `FntImporter.read(ByteArray)` | 65 | 6 byte-exact |
| R4 | `c1e085e` | `BDFBitmapFontImporter` | `BdfImporter.read(String) / readWithWarnings` | 87 | 6 byte-exact |
| R5 | `83194fb` | `BDFBitmapFontExporter` — **fixes M7** perf (iterate `font.glyphs`, not `0..0x110000`) | `BdfExporter.write(BitmapFont): String`; BDF import/export round-trip validated | 104 | 4 byte-exact semantic (re-parse both outputs, assert parsed equality — whitespace / canonical-XLFD differences are absorbed) |
| R6 | `deaf8f7` | `HexImporter` + `PlaydateMetadataParser` | `HexImporter.read(String)`: line-by-line hex font parser (carries **m11** div-by-zero guard). `PlaydateMetadataParser.parse(String)` + `PlaydateMetadata` data class (text metadata only; PNG decoding stays JVM-side). | 138 | 6 byte-exact (Hex); Playdate JVM parity deferred (needs PNG loader) |
| R7 | `85f1fff` | **PSF + FNT + Hex exporters — completes all bitmap round-trips** | `PsfExporter.write(BitmapFont): ByteArray` (PSFv2 binary), `FntExporter.write(BitmapFont): ByteArray` (FNT v2 binary), `HexExporter.write(BitmapFont): String`, shared `ByteWriter` utility. Round-trip tests for all three formats + JVM semantic parity. | 169 | Hex: double-round-trip fixed point. PSF: semantic equality (Java emits 256 GlyphList entries; Kotlin emits only populated codepoints — both re-import identically for actual glyphs). FNT: codepoints + pixels + metrics all survive; double-round-trip stable. |

Fixes carried natively by the Kotlin ports:

- **C1, C3** — PSF importer: header-derived bounds + OOM caps.
- **C2** — FNT importer: `dx` bounds-check before array access.
- **C4** — BDF importer: no stream to leak (takes `String`).
- **C5** — `BitmapGlyph.compose`: strict < zero-dim guard + null row skip.
- **M8, M9** — BDF importer: unknown charsets surface as typed
  warnings instead of stderr println; no silent `U+FFFD` coercion.

- **m11** — Hex importer: guard `height <= 0` before division.
- **M7** — BDF exporter: iterate `font.glyphs`, not `0..0x110000`.

All fixes pinned by the commonMain tests + re-verified against the
frozen Java via the jvmTest differential-parity gate.

### S4 completion status: **BITMAP FORMATS COMPLETE** ✅

After R7 (`85f1fff`), every bitmap font format has both an importer
and an exporter in `commonMain`:

| Format | Importer | Exporter | Round-trip |
| --- | --- | --- | --- |
| PSF (v1/v2) | `PsfImporter` | `PsfExporter` | ✅ |
| FNT (Windows) | `FntImporter` | `FntExporter` | ✅ |
| BDF (text) | `BdfImporter` | `BdfExporter` | ✅ |
| Hex (text) | `HexImporter` | `HexExporter` | ✅ |
| Playdate (.fnt) | `PlaydateMetadataParser` (text only) | — | partial (PNG stays JVM) |

**169 module tests**, all gated by round-trip + JVM parity.

**Remaining S4 follow-ups** (not blocking S5):

- **TTF importer** — the `truetype/**` package is large (~90 table
  classes); the full port is a multi-sprint effort. The frozen Java
  `TrueTypeFile.decompile/compile` logic is the foundation for the
  `Glyph.vectorize` API (INTEGRATION.md §7.5). Recommend deferring
  to a dedicated S4-TTF sprint after the Compose UI work unblocks
  the visual pipeline.
- **PUAA importer/exporter** — medium-size codec registry; the
  round-trip pattern is proven and the coverage tests (S2b) already
  exist. Can be ported opportunistically.
- **Playdate PNG integration** — needs a `PngDecoder` expect/actual;
  deferred to S5 when Skiko provides the image-decode surface.

**Next milestone**: Stage S5 (Compose Desktop `UiDriver` actual).

### S5 progress: **UiDriver infrastructure landed** ✅

| Module | Status | Tests |
| --- | --- | --- |
| `modules/ui-shared` | `expect class UiDriver` + `ArgbBitmap` (with SHA-256) in commonMain; AWT `BufferedImage` actual in jvmMain (headless-safe) | 3 parity tests green |
| `modules/ui-swing` | Blue oracle test module exercising the Swing actual | 5 tests green (dimension, SHA stability, alpha, scaling, multi-glyph) |
| `modules/ui-compose-desktop` | Stub — Compose MP 1.8.2 deps disabled (JetBrains Space 503 on `androidx.lifecycle`); re-enable when repo stabilises | Compiles, no tests |
| `modules/ui-compose-html` | Stub — wasmJs target deferred (same repo issue) | Compiles, no tests |

Commits: `2d3ce7d`, `7f723e9`, `b33156b`.

The Swing actual renders `BitmapGlyph` bitmaps pixel-by-pixel
into off-screen `BufferedImage(TYPE_INT_ARGB)`, fully headless.
ARGB hashes captured here become the pinning oracle at S3 freeze
for every future renderer (Compose Desktop at S5 full, Compose
Web at S6).

### S6 progress: **scaffold landed** (stub)

`modules/ui-compose-html` compiles as a Kotlin/JVM placeholder.
wasmJs target activation and real Compose Web rendering deferred
to when the JetBrains Space Maven repo stabilises.

### S7 progress: **dual CI workflows + ProGuard stubs landed** ✅

| File | Purpose |
| --- | --- |
| `.github/workflows/java.yml` | Legacy profile: SDKMAN env → `./gradlew test jacocoTestReport jacocoTestCoverageVerification` on {ubuntu, macos, windows} |
| `.github/workflows/kmp.yml` | KMP profile: SDKMAN env → `./gradlew :modules:core:check` on {ubuntu, macos, windows} |
| `proguard/proguard-rules-common.pro` | Kotlin metadata, kotlinx, annotations |
| `proguard/proguard-rules-java.pro` | Legacy main classes (`edit.Main`, `keyedit.Main`, `mapedit.Main`) |
| `proguard/proguard-rules-kmp.pro` | `com.kreative.bitsnpicas.core.**` |
| `proguard/proguard-rules-ui.pro` | Compose Desktop + Skiko placeholder |

Commit: `50193f7`.

---

## 1. Toolchain — SDKMAN + Gradle version catalog

The previous iteration of this section used older placeholders
("Gradle 8.x", "Kover 0.8.x", "JDK 8 + Ant", "JDK 21 + Gradle").
Those are **replaced** by the versions below — every number was
verified against `sdk list` or Maven Central at commit time.

| Concern | Choice (verified latest stable) |
| --- | --- |
| JDK           | Oracle GraalVM **25.0.2-graal** (from `sdk list java`) — both profiles |
| Kotlin        | **2.3.20** (from `sdk list kotlin`) — K2 compiler, multiplatform plugin |
| Gradle        | **9.4.1** (from `sdk list gradle`) — Kotlin DSL, version catalog |
| JBang         | **0.138.0** (from `sdk list jbang`) — runs single-file generators under `testdata/gen/**` |
| Test (common) | `kotlin.test` + JUnit 5 **5.12.2** on JVM; `kotlin.test-js` on JS/wasmJs |
| Test (property) | Kotest **5.9.1** `kotest-property` for parser fuzzing |
| Coverage      | Kover **0.9.1** (KMP) + JaCoCo (Java profile); both gated at 100 % line + branch |
| Mutation      | Pitest Gradle plugin **1.15.0** (core **1.19.1**) — JVM only, ≥ 85 % |
| Static        | Detekt **1.23.8** + ktlint-gradle **12.3.0** (ktlint runtime **1.6.0**); SpotBugs retained for Java profile only |
| Fuzz          | Jazzer **0.24.0** — JVM, seeded from `testdata/fuzz/**` |
| Bench         | `kotlinx-benchmark` runtime **0.4.14** + JMH |
| UI (Desktop)  | Compose Multiplatform **1.8.2** (Skia-backed; Swing interop for legacy viewer) |
| UI (Web)      | Compose for Web (wasmJs) at Compose **1.8.2** |
| Shaping/Skia  | Skiko **0.9.18** (Skia layer — already contains HarfBuzz + FreeType + SVG) |
| Fallbacks     | Apache PDFBox fontbox **3.0.5** (parser); harfbuzz4j **0.5.3** (shaping); `org.graalvm.buildtools.native` **0.10.6** (native-image) |
| Shrink        | ProGuard Gradle plugin **7.7.0** with `verifyProguardedJar` gate |
| CI            | GitHub Actions matrix `{ubuntu, macos, windows}-latest`; both profiles install SDKMAN and run `sdk env` — **no** version duplicated in workflow YAML |

The whole table above is driven by two files:
`.sdkmanrc` (JDK / Kotlin / Gradle / JBang) and
`gradle/libs.versions.toml` (everything else).


### 1.1 `.sdkmanrc` (repo root)

Pins the toolchain for every developer and CI job; `sdk env` in the
repo root activates it. Versions are the **latest stable** at plan
adoption time; bumps happen through PRs that edit this file only.

```
# .sdkmanrc  — run `sdk env` in the repo root
# Versions pinned to the latest 2026 stable at adoption time.
# A single renovate/dependabot PR bumps this file as upstream moves.
java=25.0.2-graal            # Oracle GraalVM for JDK 25 LTS (SDKMAN `graal` distro; native-image + PGO for KMP native targets)
kotlin=2.3.20            # latest stable from `sdk list kotlin`
gradle=9.4.1             # latest stable from `sdk list gradle`
jbang=0.138.0            # latest stable from `sdk list jbang`
```

> **Why Oracle GraalVM (`graal`) — not Community Edition (`graalce`)?**
> Oracle GraalVM is the distribution that drives the KMP native
> targets: it supplies `native-image` for the CLI + Compose
> Desktop bundles in Phase F, PGO for the font-parser hot paths
> (worth 20-40 % on BDF/PSF round-trip benchmarks), and the
> Kotlin/Native backend's own native compilation reuses the same
> LLVM toolchain GraalVM ships. Under the GraalVM Free Terms and
> Conditions (GFTC, JDK 17+) it is free for production use;
> `graalce` is not needed and is explicitly **not** used here.

Commit a `.sdkmanrc` and a README note:
`curl -s get.sdkman.io | bash && sdk env install && sdk env`.

CI uses `sdkman/sdkman-action@…` to install and activate the same
file — no toolchain versions are duplicated in workflow YAML.

### 1.2 `gradle/libs.versions.toml` (version catalog)

Single source of truth for Gradle-managed dependencies. Tracks the
**latest** Kotlin/Gradle/plugin/library versions at migration time.
A monthly `renovatebot`/Dependabot PR bumps this file.

Versions pinned to the **latest 2026 stable** at adoption time.
Renovate/Dependabot keep this file fresh monthly.

```toml
[versions]
# All versions below verified against Maven Central (search.maven.org)
# on the day of this commit; see `scripts/refresh-versions.sh` which
# runs the same solrsearch queries. Renovate/Dependabot keep the
# file fresh. Milestone/alpha/RC/EAP versions are rejected.
kotlin         = "2.3.20"    # matches `.sdkmanrc` (from `sdk list kotlin`)
coroutines     = "1.10.2"
serialization  = "1.9.0"
ktor           = "3.2.0"
kover          = "0.9.1"
pitestGradle   = "1.15.0"    # info.solidsoft.gradle.pitest plugin
pitestCore     = "1.19.1"    # org.pitest core (used as `pitest.pitestVersion`)
kotest         = "5.9.1"     # 6.x is milestones; 5.9.1 is last stable
jbang          = "0.138.0"   # matches `.sdkmanrc`
junit          = "5.12.2"    # 5.13.x is milestones
jazzer         = "0.24.0"
detekt         = "1.23.8"
ktlintGradle   = "12.3.0"    # org.jlleitschuh.gradle:ktlint-gradle (Plugin Portal)
ktlintCore     = "1.6.0"     # com.pinterest.ktlint runtime
compose        = "1.8.2"     # Compose Multiplatform stable; 1.9.x is alpha
skiko          = "0.9.18"    # pinned explicitly; also pulled transitively via compose
benchmarks     = "0.4.14"    # kotlinx-benchmark runtime
proguard       = "7.7.0"
fontbox        = "3.0.5"     # Apache PDFBox fontbox 3.x — TTF/OTF/Type1 parser fallback
harfbuzz4j     = "0.5.3"     # HarfBuzz JNI binding — shaping fallback when Skiko is absent
graalvmPlugin  = "0.10.6"    # org.graalvm.buildtools.native plugin

[libraries]
kotlin-test           = { module = "org.jetbrains.kotlin:kotlin-test",              version.ref = "kotlin" }
kotlinx-coroutines    = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-serialization = { module = "org.jetbrains.kotlinx:kotlinx-serialization-core", version.ref = "serialization" }
kotest-property       = { module = "io.kotest:kotest-property",                     version.ref = "kotest" }
junit-jupiter         = { module = "org.junit.jupiter:junit-jupiter",               version.ref = "junit" }
jazzer                = { module = "com.code-intelligence:jazzer-junit",            version.ref = "jazzer" }
# Skiko — primary graphics/text engine (Skia = FreeType + HarfBuzz + SVG internally)
skiko-jvm             = { module = "org.jetbrains.skiko:skiko-awt",                 version.ref = "skiko" }
skiko-linux-x64       = { module = "org.jetbrains.skiko:skiko-awt-runtime-linux-x64",   version.ref = "skiko" }
skiko-mac-arm64       = { module = "org.jetbrains.skiko:skiko-awt-runtime-macos-arm64", version.ref = "skiko" }
skiko-mac-x64         = { module = "org.jetbrains.skiko:skiko-awt-runtime-macos-x64",   version.ref = "skiko" }
skiko-win-x64         = { module = "org.jetbrains.skiko:skiko-awt-runtime-windows-x64", version.ref = "skiko" }
# Font-parser fallbacks (optional — used only when Skiko is unavailable, e.g. WASI/native targets)
fontbox               = { module = "org.apache.pdfbox:fontbox",                     version.ref = "fontbox" }
harfbuzz4j            = { module = "org.freedesktop.harfbuzz:harfbuzz4j",           version.ref = "harfbuzz4j" }

[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
kover                = { id = "org.jetbrains.kotlinx.kover", version.ref = "kover" }
pitest               = { id = "info.solidsoft.pitest", version.ref = "pitest" }
detekt               = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
ktlint               = { id = "org.jlleitschuh.gradle.ktlint", version.ref = "ktlint" }
compose              = { id = "org.jetbrains.compose", version.ref = "compose" }
benchmarks           = { id = "org.jetbrains.kotlinx.benchmark", version.ref = "benchmarks" }
proguard             = { id = "com.guardsquare.proguard", version.ref = "proguard" }
graalvm-native       = { id = "org.graalvm.buildtools.native", version.ref = "graalvm" }
```

### 1.3 JBang

JBang (from `.sdkmanrc`) is the runtime for **every CLI** in the
project, including single-command CLIs. Two uses:

1. **Corpus generators** — scripts under `testdata/gen/*.kt` build
   malformed fixtures (bad PSF, short FLF header, oversized
   `numGlyphs`, …). One command, zero project setup:
   `jbang testdata/gen/BadPsf.kt`.

2. **CLI entry points** — `modules/cli/**` and every tool that used
   to be a `public static void main` Java class (the three
   `*Test.java` harnesses; the font-conversion utilities) migrate
   to **JBang scripts written in Kotlin using Clikt**, with
   `//DEPS` and `//SOURCES` (`//src`) directives for modular
   multi-file organisation:

   ```kotlin
   ///usr/bin/env jbang "$0" "$@" ; exit $?
   //KOTLIN 2.3.20
   //DEPS com.github.ajalt.clikt:clikt:5.0.1
   //DEPS io.github.thisrepo:fonts-bitsnpicas-core:<version>
   //SOURCES cli/PsfImportCommand.kt
   //SOURCES cli/BdfExportCommand.kt
   //SOURCES cli/VectorizeCommand.kt

   import com.github.ajalt.clikt.core.*
   class Fbp : CliktCommand(name = "fbp") { … }
   fun main(args: Array<String>) = Fbp()
       .subcommands(PsfImport(), BdfExport(), Vectorize())
       .main(args)
   ```

   Every CLI — no matter how small — lives as a JBang script that
   runs **without any Gradle build** from a fresh clone (only
   SDKMAN + JBang are required). The same sources are also
   aggregated into the Gradle `modules/cli` module for publishing
   ProGuard-shrunk fat JARs and GraalVM `native-image` binaries in
   Stage S7. `//SOURCES` is JBang's multi-file include directive
   (canonically `//SOURCES path/to/file.kt`, shorthand `//src`
   accepted by JBang 0.120+).

   **Research-verified on this branch** with JBang 0.138.0 +
   Kotlin 2.3.20 + GraalVM 25.0.2 + Clikt 4.4.0:

   | Capability | Status | Notes |
   | --- | --- | --- |
   | `//KOTLIN <ver>` header | ✓ | JBang installs matching Kotlin compiler |
   | `//DEPS g:a:v` | ✓ | resolves from Maven Central / configured repos |
   | `//SOURCES file.kt` (local) | ✓ | tested |
   | `//SOURCES https://…` (URL) | ✓ | JBang downloads + caches the file |
   | `//FILES`, `//JAVAAGENT`, `//MANIFEST`, `//NATIVE_OPTIONS` | ✓ | standard directives |
   | Clikt 5.x | ⚠ | requires Gradle Metadata; use `clikt-jvm` classifier + `.parse(args)`. Prefer **Clikt 4.4.0** for JBang scripts (`.main(args)`, plain POM). |
   | `jbang build --native` / `jbang export native` | ✓ | invokes GraalVM `native-image`; reflection-heavy deps need `-N --initialize-at-run-time=...`. Example: Clikt+Mordant currently requires `-N '--initialize-at-run-time=com.github.ajalt.mordant.internal.nativeimage.NativeImagePosixMppImpls'`. |
   | `--sources` CLI flag | ✓ | runtime override for `//SOURCES` |
   | `jbang export gradle` / `export maven` | ✓ | generates project skeleton from script |

   **Scope limit — JBang is JVM-only.** Kotlin/Native, Kotlin/JS,
   and Kotlin/wasmJs artefacts for the KMP targets come from the
   Gradle build in Stage S4+, **not** from JBang. JBang covers
   three concrete use cases in this project:

   1. Corpus generators (platform-agnostic — any JVM works).
   2. JVM CLI entry points (runs on the SDKMAN-pinned GraalVM).
   3. `export native` producing a `native-image` binary of the
      JVM CLI for the `java` release channel.

---

## 2. Repository layout after migration

```
fonts-bitsnpicas/
├── .sdkmanrc                                ← toolchain pin
├── gradle/libs.versions.toml                ← single source of truth
├── settings.gradle.kts
├── build.gradle.kts                         ← registers profiles `java` + `kmp`
├── main/java/BitsNPicas/src/**              ← Java sources (stabilised in Phase B; frozen at Phase C)
├── main/java/BitsNPicas/test/**             ← NEW: Kotlin tests that run against Java via `profile=java`
├── legacy-build.xml                          ← retained Ant script for reference
├── kmp/
│   ├── core/
│   │   ├── src/commonMain/kotlin/…           ← pure format logic (Phase D+)
│   │   ├── src/commonTest/kotlin/…           ← SAME tests reused from Phase A
│   │   ├── src/jvmMain/kotlin/…              ← JVM adapters (java.io.File)
│   │   ├── src/jvmTest/kotlin/…              ← JVM-only tests (fuzz, resource)
│   │   ├── src/jsMain/kotlin/…
│   │   ├── src/wasmJsMain/kotlin/…
│   │   └── src/nativeMain/kotlin/…
│   ├── ui-swing/, ui-compose-desktop/, ui-compose-html/, ui-shared/
│   └── cli/
├── testdata/                                 ← corpus (good + bad fixtures + JBang generators)
└── .github/workflows/
    ├── java.yml                               ← profile=java
    └── kmp.yml                                ← profile=kmp
```

---

## 3. Test strategy across every level

| Level | Source set | Runner | Profile(s) |
| --- | --- | --- | --- |
| Unit | `commonTest` / `jvmTest` | `kotlin.test` | java, kmp |
| Integration | `jvmTest` | JUnit 5 | java, kmp |
| **UI (common, expect/actual)** | `modules/ui-shared/commonTest` | `kotlin.test` driving `UiDriver` (3 actuals — Swing oracle, Compose Desktop, Compose Web) | java (Swing actual only), kmp (all three) |
| API / contract | `jvmTest`, `jsTest`, `nativeTest` | JUnit 5 / kotlin.test | kmp |
| E2E | `:e2e-tests` | JUnit 5 + Gradle `runCli` | java, kmp |
| Load / perf | `:benchmarks` | `kotlinx-benchmark` + JMH | java (JMH on Java CLI), kmp (KMP CLI) |
| Fuzz | `jvmTest` | Jazzer | java, kmp (per-impl) |

See [`docs/diagrams/08-ui-driver-expect-actual.puml`](docs/diagrams/08-ui-driver-expect-actual.puml)
for the UI-test driver architecture — one `commonTest` suite,
three `actual` bindings, ARGB-hash parity gate across every
renderer.

The same `@Test` methods execute under both profiles via a
test-fixture `interface FontIo` with two `actual` implementations —
one backed by the frozen Java, one by `commonMain` Kotlin.

---

## 4. TDD test plan — failing tests first, fix Java, then port

### 4.1 One failing Kotlin test per `CODE_REVIEW.md` finding

Tests are authored in Kotlin (`commonTest`) against a `FontIo`
abstraction. At authoring time **only** the Java-backed implementation
exists, so every test fails red on CI (`-Pprofile=java`). The fix
commits turn them green.

| Finding | Failing Kotlin test |
| --- | --- |
| C1. OOB in PSF glyph reader | `PsfImporterTest.malformed_psf_does_not_throw_AIOOBE_on_short_data()` |
| C2. Unchecked FNT `data[dx]` | `FntImporterTest.truncated_fnt_returns_error_not_AIOOBE()` |
| C3. Unbounded PSF allocations | `PsfImporterTest.huge_numGlyphs_rejected_under_OOM_limit()` |
| C4. Scanner/FIS leak in BDF | `BdfImporterResourceTest.importing_1000_times_does_not_leak_fds()` *(jvmTest)* |
| C5. Empty-dim glyph compose | `BitmapFontGlyphTest.compose_with_equal_bounds_returns_empty_not_throws()` |
| M6. BDF silent truncation | `BdfImporterTest.truncated_bitmap_is_reported_not_zero_filled()` |
| M7. BDF export 1.1M iterations | `BdfExporterPerfTest.export_10_glyphs_completes_under_50ms()` |
| M8. Charset silent stderr | `BdfImporterTest.unknown_charset_raises_ImportWarning()` |
| M9. Replacement codepoint accepted | `BdfImporterTest.U_FFFD_codepoint_rejected()` |
| m11. HexImporter div-by-zero | `HexImporterTest.height_zero_input_rejected_not_arithmetic()` |
| m13. Empty glyph baseline | `BitmapFontGlyphTest.empty_glyph_has_well_defined_baseline()` |
| Plus one pinning round-trip per format (PSF, FNT, BDF, Hex, Playdate, TTF, PUAA). |

### 4.2 Strict authoring order

For each format `F`:

1. **Red (Phase A).** Commit the Kotlin tests for `F` against `FontIo`
   with only a `JavaFontIo` adapter wired in. CI shows them red under
   `-Pprofile=java`. The red-then-green history is the audit trail
   mandated by the acceptance criteria.
2. **Fix Java to green (Phase B).** Edit `main/java/BitsNPicas/src/**`
   until every test passes. All fixes reference their corresponding
   `CODE_REVIEW.md` finding ID in the commit subject
   (`fix: C1 guard PSF glyph reader bounds`). Raise JVM coverage
   incrementally toward 100 %.
3. **Freeze (Phase C).** Once the profile `java` lane reports
   100 % line + branch on `F` and zero open findings against `F`, tag
   `legacy-v1-F`, mark `main/java/BitsNPicas/src/<F>/**` read-only via
   CODEOWNERS + branch protection, and stop accepting edits.
4. **Port (Phase D).** Author `commonMain` Kotlin for `F`; wire a
   `KotlinFontIo` `actual`. The **same Kotlin test class** now runs
   twice per CI build — once against frozen Java, once against
   Kotlin — via `kotlin.test` parameterisation.
5. **Differential parity gate.** For every file in `testdata/`,
   import and export with both implementations; require byte-exact
   equality (or document divergences as explicit tests).
6. **Retire Java delegate** from `jvmMain` only; frozen Java tree
   remains untouched under `main/java/BitsNPicas/src/**`.

### 4.3 Test corpus

- `testdata/psf/good/*.psf`, `testdata/psf/bad/*.psf`
- Parallel sets for `bdf`, `fnt`, `hex`, `playdate`, `ttf`, `puaa`.
- `testdata/fuzz/corpus/${format}/`.
- `testdata/gen/*.kt` (runnable via `jbang`) generates the bad
  fixtures deterministically, so CI can regenerate on demand.

---

## 4.6 UI parity tier (`UiDriver` expect/actual)

A single suite under `modules/ui-shared/src/commonTest/kotlin/**`
drives every rendering path through an `expect class UiDriver`.
Three `actual` bindings:

| Renderer | Role | Module / source set | Active from |
| --- | --- | --- | --- |
| Swing (Java2D) | **Blue — pinning oracle** | `ui-swing :: jvmTest` | Stage S2 |
| Compose Desktop (Skiko) | **Green** | `ui-compose-desktop :: jvmTest` | Stage S5 |
| Compose Web (Skiko-wasm) | **Green** | `ui-compose-html :: wasmJsTest` | Stage S6 |

Contract:

- At freeze (S3) the Swing actual produces the authoritative ARGB
  hash for every `(test, fixture)` pair under
  `testdata/snapshots/swing/**`. These are committed.
- From S5 onward each new Compose actual must match the Swing
  hashes on every CI run. A mismatch fails the build with
  `(renderer, test, expectedHash, actualHash)`.
- Snapshot bumps require an explicit commit titled
  `ui-parity: accept <renderer> divergence for <test>` with a
  justification comment — never a silent overwrite.

Tests of interest covered by the driver:

- `RenderGlyphParityTest` — bitmap / FIGlet / sprite / TTF glyphs
  raster-rendered through `Glyph.rasterize`.
- `VectorizeParityTest` — `Glyph.vectorize` SVG output, byte-exact
  up to `VectorizeOptions.svgPrecision`.
- `CodepointSequenceTest` — FI ligature, ZWJ emoji family,
  combining diacritics.
- `ConfigRoundTripParityTest` — save → load → save round-trip
  (pins the `ConfigFont` setter family).
- `SpriteFontGeometryParityTest` — variable-width glyph positions.
- `FigletSmushParityTest` — horizontal + vertical smush rules.

UI driver coverage note: UI modules (`ui-swing`, `ui-compose-*`)
are **pinned by ARGB snapshot** through the driver, not line-
covered. Line coverage on a Swing event listener buys nothing;
the snapshot hash is the meaningful regression gate.

---

## 5. 100 % coverage gates (both profiles)

- **Profile `java`** (Option-C scope): JaCoCo via the Gradle
  `jacoco` plugin, executed against the in-migration packages only —
  `com.kreative.bitsnpicas` (core), `.importer.**`, `.exporter.**`,
  `.truetype.**`, `.puaa.**`. `jacocoTestCoverageVerification` rule
  `minimum = 1.0` on line + branch. Everything else under
  `main/java/BitsNPicas/src/**` is compiled (for UI oracle hosting)
  but JaCoCo-excluded. Enforced at the end of Stage S2, required to
  enter Stage S3 (freeze).
- **UI packages are pinned, not line-covered**, per §4.6. The Swing
  `UiDriver` `actual` captures ARGB hashes at freeze; subsequent
  renderers match those hashes.
- **Profile `kmp`**: Kover 0.9.x (see `libs.versions.toml`) with
  `koverVerify { rule { bound { minValue = 100 } } }` on every
  `commonMain` module. Pitest mutation ≥ 85 %. Required before a
  format migrates from Java-delegate to pure `commonMain`.
- No ignores or exclusions on either profile.

---

## 6. Migration phases (new order)

### Phase A — Toolchain + red tests

- Commit `.sdkmanrc`, `gradle/libs.versions.toml`,
  `settings.gradle.kts`, `build.gradle.kts` with profiles
  `-Pprofile=java` (Gradle JVM toolchain over `main/java/...`) and
  `-Pprofile=kmp` (empty module scaffold).
- Commit `.github/workflows/java.yml` and `kmp.yml`; both install
  SDKMAN, activate `.sdkmanrc`, and invoke Gradle with the right
  profile.
- Introduce the `FontIo` interface and `JavaFontIo` adapter.
- Commit Kotlin TDD tests from §4.1 — **they must fail on CI**.
- Gate: `profile=java` red on purpose, `profile=kmp` green on empty
  module.

### Phase B — Fix Java to green + 100 % JaCoCo

- Fix the Java code under `main/java/BitsNPicas/src/**` one finding
  at a time; each commit references the finding ID.
- Expand corpus to whatever is needed to drive coverage to 100 %.
- Retain the three existing `*Test.java` CLIs but gut their
  assertion-less behaviour — each now exits non-zero on failure and
  is wrapped by a JUnit 5 test in Kotlin that runs them.
- Gate: `profile=java` green, JaCoCo 100 % line + branch on
  `main/java/BitsNPicas/src/**`, all `CODE_REVIEW.md` findings
  closed.

### Phase C — Freeze Java

- Tag `legacy-v1` on the last Phase-B commit.
- Add CODEOWNERS making `main/java/BitsNPicas/src/**` require
  repo-admin approval for any future edit.
- Add a `pre-push` hook and a CI check that fails any PR whose diff
  touches the frozen tree.
- Gate: no further Java changes accepted.

### Phase D — Port to KMP one format at a time

Priority: **PSF → FNT → BDF → Hex → Playdate → TTF → PUAA**.

Per format `F`, one PR that:

1. Adds `commonMain` Kotlin implementation of `F`.
2. Wires `KotlinFontIo` `actual`.
3. The existing Kotlin test suite now runs twice — against frozen
   Java and against Kotlin. Both must pass.
4. Differential parity gate (byte-exact) across `testdata/`.
5. Kover 100 % + Pitest ≥ 85 % on the new module.
6. Retires the Java delegate from `jvmMain`; frozen Java tree
   remains.

### Phase E — UI + E2E + load/perf

- `ui-swing/` — retained legacy Swing host, driven by Kotlin
  view-models that read from `commonMain` font models.
- `ui-compose-desktop/`, `ui-compose-html/`, `ui-shared/` as before.
- `e2e-tests` — runs the legacy Java CLI and the KMP CLI side by
  side over `testdata/`; diff must be empty.
- `benchmarks/` — JMH baselines for Java and Kotlin; regression
  threshold ±10 %.

### Phase F — Dual CI/CD + release (with ProGuard)

Both profiles run ProGuard on release artefacts (never on test
artefacts). CI checks additionally run the **post-ProGuard JAR**
against the full test suite to guarantee that shrinking /
obfuscation didn't break reflection, `kotlinx-serialization`, or
service-loader paths.

- **`profile=java` workflow**: `sdk env`, then
  `./gradlew -Pprofile=java check jacocoTestCoverageVerification
  proguardRelease verifyProguardedJar`. Publishes the classic JAR
  (`*-legacy` suffix), ProGuard-shrunk, with a committed
  `proguard-rules-java.pro` (keep rules for reflective
  entry points in importers/exporters, `main`-method harnesses,
  and the PUAA codec registry SPI).
- **`profile=kmp` workflow**: `sdk env`, then
  `./gradlew -Pprofile=kmp build koverVerify pitest
  proguardRelease verifyProguardedJar packageReleaseDistribution`.
  Publishes:
  - `bitsnpicas-core-jvm` ProGuarded JAR (Maven Central);
    non-JVM klibs unshrunk.
  - `bitsnpicas-desktop` Compose Desktop signed bundle
    (DMG / MSI / AppImage) built with Compose's built-in ProGuard
    integration (`compose.desktop.application.buildTypes.release.proguard`).
  - `bitsnpicas-web` static site (Compose for Web) minified via
    the webpack/Kotlin-JS production pipeline.
- **ProGuard rule files** live in `proguard/` at the repo root:
  - `proguard-rules-common.pro` — shared keep rules for
    `kotlinx-serialization`, `kotlin.reflect`, `ServiceLoader`,
    and any JNI/Native entry points.
  - `proguard-rules-java.pro` — legacy-Java-only additions
    (reflective `Class.forName` calls in codec registry, `main`
    entry points).
  - `proguard-rules-kmp.pro` — Compose + Coroutines keep rules
    and `@JvmStatic` entry points.
- **Verification step** (`verifyProguardedJar`): runs the JUnit 5
  test suite against the shrunk JAR via a separate Gradle
  classpath — catches missing `-keep` rules before release.
- **Mapping files** (`mapping.txt`) from each ProGuard run are
  uploaded as CI artefacts and attached to the GitHub release so
  obfuscated stack traces can be deobfuscated post-shipping.

Both workflows run on **every** PR and on every tag.

---

## 7. Acceptance criteria

1. Every `CODE_REVIEW.md` finding has a named Kotlin test whose Git
   history shows:
   - **Red** at Phase A (authored, no fix).
   - **Green** at Phase B (Java fix landed, commit subject cites the
     finding ID).
   - **Still green at Phase D** now running against both frozen
     Java and `commonMain` Kotlin.
2. Phase B closes with JaCoCo 100 % line + branch on the Java tree.
3. Phase C tags `legacy-v1`; no commit after it touches
   `main/java/BitsNPicas/src/**` (enforced via CI).
4. Phase D closes with Kover 100 % + Pitest ≥ 85 % on every
   `commonMain` module.
5. Differential parity: byte-exact for every file in `testdata/`
   under `-Pprofile=kmp` (Java vs. Kotlin comparison).
6. `profile=java` and `profile=kmp` workflows both green on every
   PR across {ubuntu, macos, windows}; `kmp` also green across
   {jvm, js, wasmJs, native}.
7. `.sdkmanrc` and `gradle/libs.versions.toml` track the latest
   stable toolchain; any bump is a single-file PR.

---

## 7.5 Unified font API (host-repo additions)

Because `fonts-bitsnpicas` is the designated integration host
(see [`INTEGRATION.md`](INTEGRATION.md)), `modules/core/` exposes one
API surface that bitmap, FIGlet, sprite, TTF-outline, SVG, and
PNG-emoji fonts all implement. The canonical interfaces live in
`commonMain`:

```kotlin
// modules/core/commonMain
interface Font {
    val metadata: FontMetadata
    fun glyphs(): Sequence<Glyph>          // all glyphs in the font
    fun glyph(codepoints: List<Int>): Glyph?
}

interface Glyph {
    val advance: Int
    /** A glyph may represent a ligature or emoji ZWJ cluster,
     *  so it is keyed by a **sequence** of codepoints, not a single one. */
    fun codepoints(): Sequence<Int>
    /** Raster rendering. Works for bitmap rows, FIGlet lines,
     *  sprite tiles, and rasterised outlines/SVG/PNG. */
    fun rasterize(target: PixelSink, scale: Float = 1f)
    /** Vector rendering. Emits SVG (possibly colour / multi-layer)
     *  for bitmap-path fonts, TTF outlines, embedded SVG tables, and
     *  PNG colour-emoji tables. */
    fun vectorize(target: SvgSink, options: VectorizeOptions = VectorizeOptions())
}

interface SvgSink {
    fun beginGlyph(width: Float, height: Float, viewBox: Rect)
    fun path(d: String, fill: Paint? = null, stroke: Stroke? = null)
    fun image(pngBytes: ByteArray, x: Float, y: Float, w: Float, h: Float)   // for COLR/CBDT/sbix PNG layers
    fun group(transform: Affine2D, block: SvgSink.() -> Unit)
    fun endGlyph()
}

sealed interface Paint {
    data class Solid(val rgba: Int) : Paint
    data class LinearGradient(val stops: List<Stop>, val start: Point, val end: Point) : Paint
    data class RadialGradient(val stops: List<Stop>, val centre: Point, val r: Float) : Paint
}

data class VectorizeOptions(
    /** If true, emit COLR/SVG layers with full colour; otherwise emit
     *  a single monochrome path. */
    val colour: Boolean = true,
    /** Bitmap → path tracing strategy: square-pixel (default),
     *  Potter/Moore contour trace, or Moore-Neighbour. */
    val bitmapTracer: BitmapTracer = BitmapTracer.SQUARE,
    /** Emit `<image>` for PNG colour-emoji layers, or decode +
     *  re-trace them into paths. */
    val embedPng: Boolean = true,
)

interface FontReader<F : Font> {
    val formats: Set<FontFormat>
        // BDF, PSF, FNT, HEX, PLAYDATE, TTF, CFF, PUAA,
        // FLF, TLF, SPRITE_PNG, SVG_FONT, COLRv0, COLRv1, CBDT, sbix
    fun read(source: ByteSource): F
}

interface FontWriter<F : Font> { fun write(font: F, sink: ByteSink) }
```

### Extensions in `modules/truetype` (bitmap → path + colour SVG)

The existing TTF compile/decompile logic in `fonts-bitsnpicas`
already knows how to emit glyph outlines as `glyf` paths. That
infrastructure is extended to drive `Glyph.vectorize` for every
font flavour:

- **Bitmap fonts** (BDF / PSF / FNT / HEX / Playdate / sprite):
  each lit pixel becomes a unit-square path; optional
  contour-tracing (`Potter`, `MooreNeighbour`) produces cleaner
  SVGs. Colour pixels (from sprite sheets or indexed bitmaps)
  become per-layer paths with their own `Paint.Solid`.
- **TTF outline fonts**: `glyf` table entries are reused directly;
  `CFF`/`CFF2` charstrings are translated into SVG path `d`
  strings.
- **Colour fonts**:
  - `COLRv0` / `COLRv1` layer tables → nested `<g>` groups with
    `Paint` gradients.
  - `SVG` table → embed verbatim (minified).
  - `CBDT` / `sbix` PNG layers → emitted as `<image>` elements
    (controlled by `VectorizeOptions.embedPng`) or re-traced.
- **Emoji / ligatures**: `Font.glyph(codepoints)` accepts a list,
  so `["👨", "ZWJ", "👩", "ZWJ", "👧"]` resolves to the composed
  family glyph if the font defines it.

### Skiko as the primary rasteriser / vectoriser

JetBrains Skiko (the Skia layer that powers Compose Multiplatform)
supplies the default `PixelSink` + `SvgSink` implementations via
`org.jetbrains.skiko.*`:

- `SkikoPixelSink` wraps `org.jetbrains.skia.Canvas`; usable from
  `jvmMain` immediately and again from `commonMain` (Compose-KMP)
  once UI migration starts.
- `SkikoSvgSink` uses `org.jetbrains.skia.svg.SVGDOM` +
  `SVGCanvas` to collect drawing commands into an SVG document.
  Skia already handles COLR / SVG-in-OTF / CBDT internally, so
  TTF-based colour font vectorisation goes through Skia by default.
- `SkikoShaper` (HarfBuzz under the hood) produces glyph runs for
  ligature-aware text rendering; `Font.glyph(codepoints)` uses it
  behind the scenes for shaping fallback.

Optional fall-backs when Skiko is undesirable (headless CI,
minimal JVM footprint):

- **Apache FontBox** (`org.apache.pdfbox:fontbox`) — parser-only
  fallback for TTF/OTF/Type1 reading.
- **FreeType** (via JNA, fulfilling the "fontfree" slot in the
  original integration brief) — hinted outline rasterisation.
- **harfbuzz4j** (fulfilling the "buzz4j" slot) — pure-Java text
  shaping when Skiko's HarfBuzz is unavailable
  (e.g. Kotlin/Native WASI targets).

These fall-backs are wired via `expect`/`actual` in
`modules/core`: `expect fun systemShaper(): Shaper`. JVM actual
picks Skiko if on the classpath, else harfbuzz4j, else a
no-shaping stub.

### TDD tests for the extended API

- `VectorizeTest.bitmap_glyph_vectorizes_to_square_pixel_paths()`
- `VectorizeTest.ttf_glyph_emits_glyf_path_d_string()`
- `VectorizeTest.colr_v1_glyph_emits_layered_svg_with_gradients()`
- `VectorizeTest.cbdt_png_glyph_embeds_image_element()`
- `VectorizeTest.bitmap_to_moore_trace_matches_reference_svg()`
- `CodepointSequenceTest.zwj_emoji_family_resolves_to_single_glyph()`
- `CodepointSequenceTest.fi_ligature_resolved_from_two_codepoints()`
- `SvgParityTest.vectorize_then_skia_rasterize_matches_direct_rasterize_within_tolerance()`
  — rounds the output back through Skia and compares ARGB hashes
  at the same scale, within a documented ΔE tolerance.

All live in `commonTest` and run under both `-Pprofile=java`
(against the frozen Java bitmap-to-path code + Skiko) and
`-Pprofile=kmp` (pure Kotlin + Skiko).

---

## 8. Deliverables summary

- `.sdkmanrc` pinning JDK 21 / Kotlin / Gradle / JBang to the latest
  stable at migration time.
- `gradle/libs.versions.toml` consolidating every Gradle dependency
  and plugin.
- `build.gradle.kts` defining profiles `-Pprofile=java` and
  `-Pprofile=kmp`.
- `FontIo` test-fixture interface + `JavaFontIo` + `KotlinFontIo`
  `actual`s.
- `main/java/BitsNPicas/test/**` Kotlin test suite, reused verbatim
  against both implementations.
- `kmp/core/` with `commonMain` / `jvmMain` / `jsMain` /
  `wasmJsMain` / `nativeMain` and paired test source sets.
- `ui-swing/`, `ui-shared/`, `ui-compose-desktop/`,
  `ui-compose-html/`.
- `testdata/` corpus with JBang-runnable generator scripts.
- `.github/workflows/java.yml` and `kmp.yml` both sourcing
  `.sdkmanrc` and running ProGuard + post-ProGuard verification.
- `proguard/proguard-rules-common.pro`, `proguard-rules-java.pro`,
  `proguard-rules-kmp.pro`.
- `TEST_PLAN.md` auto-generated mapping `CODE_REVIEW.md` finding →
  Kotlin test FQN.
- `benchmarks/baselines/*.json`.
- [`INTEGRATION.md`](INTEGRATION.md) — defines this repo as the
  font-studio host, vendoring plan for banana-figlet and
  ChatGameFontificator, Skiko + FontBox + FreeType + harfbuzz4j
  integration, and the `Glyph.vectorize` colour-SVG pipeline.
- `modules/core/commonMain` unified `Font` / `Glyph` /
  `FontReader` / `FontWriter` / `SvgSink` / `PixelSink` interfaces.
- `modules/truetype` — extended bitmap-to-path + colour-SVG
  vectoriser (COLRv0/v1, SVG-in-OTF, CBDT/sbix PNG).
