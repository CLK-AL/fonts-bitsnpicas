# INTEGRATION.md — fonts-bitsnpicas as the Integrated Font Studio Host

Design review for the low-level integration of **banana-figlet** and
**ChatGameFontificator** into **fonts-bitsnpicas**, producing a
single multi-format font API with a KMP GUI. This document is
authored in parallel with the Phase A+B Java-stabilisation work
happening on `claude/code-review-k4kzN` — the folder structure and
class surface defined here are load-bearing: everything that lands
after Phase C must implement these interfaces.

**Companion documents**

- [`CODE_REVIEW.md`](CODE_REVIEW.md), [`TEST_REVIEW.md`](TEST_REVIEW.md)
- [`MIGRATION_PLAN.md`](MIGRATION_PLAN.md)
- [`docs/diagrams/*.puml`](docs/diagrams/) — PlantUML sources for
  every diagram referenced below. Render with
  `jbang plantuml@plantuml/plantuml docs/diagrams/*.puml`.
- Eight PUML sources in total: `01-core-api`, `02-format-implementations`,
  `03-modules-and-hosts`, `04-vectorize-flow`,
  `05-integration-phases`, `06-dual-cicd`,
  `07-fix-then-freeze-then-port`, and
  **`08-ui-driver-expect-actual`** (the UI-test architecture
  covered in §4.6 below).

---

## 1. Host selection — why `fonts-bitsnpicas`

Recap of the decision already in the conversation log:

| Repo | Role |
| --- | --- |
| **fonts-bitsnpicas** | **Host.** Richest font domain model, existing Swing editor, most formats, TTF compile/decompile already present (foundation for `Glyph.vectorize`). |
| banana-figlet | **Vendored module.** Pure library; no UI; re-exposed as `modules/figlet/` with the same `Font` / `Glyph` surface. |
| ChatGameFontificator | **Vendored subset.** Only `sprite/` + `config/` packages; IRC + Swing windowing code stays out of the host. |

---

## 2. Integration path — real folders, staged

Full comparison is in the prior design reply; the decision is:

| Path | Verdict | Reason |
| --- | --- | --- |
| Git submodules | **No** | Three different upstream owners; KMP refactor requires atomic cross-module edits; submodules lock commits, not versions. |
| Gradle dependency (banana-figlet only) | **Phase A only** | `io.leego:banana-figlet` is JVM-only — unusable from `commonMain` once the KMP port starts. |
| **Real folders (vendored into `modules/`)** | **Phase B onward** | Single Gradle build graph; refactor-friendly; KMP-native. |

See [`docs/diagrams/05-integration-phases.puml`](docs/diagrams/05-integration-phases.puml)
for the phase-to-phase transition.

### Phase A (weeks) — weeks to an integrated GUI

- Add `io.leego:banana-figlet:<latest>` as a Gradle dep in the
  legacy Java profile. Wire a "FIGlet preview" panel in the
  existing Swing editor.
- Copy only `com.glitchcog.fontificator.sprite` +
  `com.glitchcog.fontificator.config` into
  `main/java/BitsNPicas/src/vendor/cgf/...`. Preserve upstream
  package declarations verbatim for the initial commit; rename in
  a **separate** follow-up commit so diffs are auditable.
- Record in `third_party/MANIFEST.toml`:
  ```toml
  [[vendor]]
  name     = "banana-figlet"
  origin   = "https://github.com/yihleego/banana-figlet"
  sha      = "<upstream SHA>"
  license  = "MIT"
  paths    = ["(consumed as Maven dep in Phase A)"]
  pulledAt = "YYYY-MM-DD"

  [[vendor]]
  name     = "ChatGameFontificator"
  origin   = "https://github.com/GlitchCog/ChatGameFontificator"
  sha      = "<upstream SHA>"
  license  = "MIT"
  paths    = [
    "src/main/java/com/glitchcog/fontificator/sprite/**",
    "src/main/java/com/glitchcog/fontificator/config/**",
  ]
  pulledAt = "YYYY-MM-DD"
  ```

### Phase B (post-Phase-C) — full vendoring into real folders

- Translate `io.leego.banana.BananaUtils` into pure Kotlin under
  `modules/figlet/src/commonMain/kotlin/**`. Drop the Maven dep.
- Promote the CGF vendor subset into a first-class KMP module
  `modules/sprite/`.
- Every vendor pull refreshes `third_party/MANIFEST.toml`.

---

## 3. Folder structure (target, end of Phase B)

```
fonts-bitsnpicas/
├── .sdkmanrc                                   ← 25.0.2-graal / 2.3.20 / 9.4.1 / 0.138.0
├── gradle/
│   ├── libs.versions.toml                      ← Maven-Central-verified latest
│   └── wrapper/
├── settings.gradle.kts                          ← registers every modules/* subproject
├── build.gradle.kts                             ← profiles: -Pprofile=java | kmp
├── main/java/BitsNPicas/src/**                 ← legacy Java (stabilised Phase B, frozen Phase C)
├── main/java/BitsNPicas/test/**                ← Kotlin tests; run under profile=java
├── legacy-build.xml                             ← retained Ant (reference)
├── modules/
│   ├── core/                                    ← Font / Glyph / FontReader / SvgSink / PixelSink
│   │   └── src/{commonMain,commonTest,jvmMain,jvmTest,jsMain,wasmJsMain,nativeMain}/kotlin/…
│   ├── bitmap/                                  ← BDF / PSF / FNT / HEX / PLAYDATE / PUAA
│   ├── truetype/                                ← TTF + CFF + COLRv0/v1 + CBDT + sbix + SVG-in-OTF
│   ├── figlet/                                  ← vendored from banana-figlet (.flf + .tlf)
│   ├── sprite/                                  ← vendored from ChatGameFontificator/sprite
│   ├── chat-preview/                            ← vendored from ChatGameFontificator (render-only)
│   ├── shaper/                                  ← codepoints → glyph run (ligatures + ZWJ emoji)
│   ├── svg-writer/                              ← SvgSink impls: SkikoSvgSink, PlainTextSvgSink
│   ├── cli/                                     ← Kotlin CLI (native-image via GraalVM)
│   ├── ui-shared/                               ← Compose MP: GlyphGrid, PreviewPane, etc.
│   ├── ui-swing/                                ← retained legacy editor, Kotlin view-models
│   ├── ui-compose-desktop/                      ← Compose Desktop host
│   └── ui-compose-html/                         ← Compose for Web (wasmJs)
├── proguard/
│   ├── proguard-rules-common.pro
│   ├── proguard-rules-java.pro
│   └── proguard-rules-kmp.pro
├── testdata/
│   ├── bdf/{good,bad}/  psf/…  fnt/…  hex/…  playdate/…
│   ├── ttf/{good,bad}/  colr/  svg-in-otf/  cbdt/  sbix/
│   ├── flf/{good,bad}/  tlf/{good,bad}/
│   ├── sprites/   chats/   configs/{good,bad}/
│   ├── snapshots/                               ← ARGB-hash + reference SVG output
│   ├── fuzz/corpus/                             ← Jazzer seeds per format
│   └── gen/*.kt                                 ← JBang-runnable corpus generators
├── third_party/
│   ├── MANIFEST.toml                            ← vendored SHAs + licences
│   ├── banana-figlet/LICENSE.txt
│   └── ChatGameFontificator/LICENSE.txt
├── docs/
│   ├── diagrams/*.puml                          ← 7 PlantUML sources (this doc references them)
│   ├── FONT_API.md                              ← generated from KDoc on modules/core
│   └── INTEGRATION_CHANGELOG.md                 ← per-vendor pull log
├── scripts/
│   ├── refresh-versions.sh                      ← queries Maven Central; bumps libs.versions.toml
│   └── vendor-pull.sh                           ← auditable re-pull of vendored sources
├── benchmarks/
│   └── baselines/*.json
├── .github/workflows/
│   ├── java.yml                                 ← profile=java (ProGuarded *-legacy JAR)
│   └── kmp.yml                                  ← profile=kmp (klibs, Compose bundles, wasm site)
├── CODE_REVIEW.md
├── TEST_REVIEW.md
├── MIGRATION_PLAN.md
└── INTEGRATION.md                               ← (this file)
```

The tree is intentionally shallow: every module is a direct child
of `modules/` so `settings.gradle.kts` registration is a single
`rootDir.resolve("modules").listDirectoryEntries()`.

---

## 3.5 Current S4 port surface (live)

Each subsystem listed below is ported to `commonMain` with a
`JavaLegacyAdapter` + jvmTest differential-parity gate. Numbers
reflect the state at `MIGRATION_PLAN.md §0.6` in each repo.

| Repo | Ported in `commonMain` | Module tests | Pending |
| --- | --- | --- | --- |
| **fonts-bitsnpicas** *(host)* | `Glyph` interface, `BitmapGlyph`, `BitmapFont`, `ByteReader`/`ByteWriter`, **PSF / FNT / BDF / Hex importers + exporters** (all round-trips validated), `PlaydateMetadataParser` — **bitmap formats complete**; **KMP 7-target matrix** (JVM, JS, wasmJs, linuxX64, macosX64, macosArm64, mingwX64) all compile | 169 | TTF / PUAA (large; deferred to S4-TTF sprint); Playdate PNG (needs Skiko image-decode at S5) |
| **banana-figlet** | `Layout`, `Option`, `Rule`, `Meta`, `FlfParser`, `FigletRenderer` (h+v), `SmushRules` (H1–6 + V1–5), `BananaFiglet` API (`bananaify`/`bananansi`), `Ansi`, `FontResourceLoader` — **library port structurally complete** ✅ | 168 | TLF zip path; JS/wasmJs/Native targets |
| **ChatGameFontificator** | `SpriteCharacterKey`, `ConfigFont`/`ConfigMessage`/`ConfigChat`/`ConfigColor`/`ConfigCensor` (config layer complete), `baseValidation`, `SpriteFontGeometry`, `CharacterBounds`, `SpriteFontMetrics`, `ColorRGBA` — **config + geometry complete** ✅ | 186 | `Sprite` renderer (needs `Canvas2D` at S5) |

**531 module tests** total across the three repos (169 core + 8 UI
= 177 fonts-bitsnpicas; 168 banana-figlet; 186 ChatGameFontificator),
all green, all gated by differential parity against frozen `legacy-v1`.

### S5 UiDriver infrastructure (fonts-bitsnpicas host only)

`modules/ui-shared` provides the `expect class UiDriver` +
`ArgbBitmap` (with platform-neutral SHA-256 hashing).
`modules/ui-swing` exercises the Swing actual (headless
`BufferedImage` pixel rendering). `modules/ui-compose-desktop`
has a real `ComposeGlyphRenderer` composable using Compose
Canvas API (Compose MP 1.8.2, resolved via `google()` Maven).
`modules/ui-compose-html` compiles with Compose on JVM; wasmJs
target deferred until `modules/core` adds it.

### S7 dual CI/CD (all repos)

`.github/workflows/java.yml` (legacy profile, 3-OS matrix) and
`kmp.yml` (KMP modules, 3-OS matrix) landed in all three repos.
`proguard/*.pro` stubs (common + java + kmp, per-repo adapted)
ready for wiring once the ProGuard Gradle plugin is applied.

**Divergences from Java explicitly pinned in tests** (commonMain
is *more correct* than Java for these):

- banana-figlet **§17.5** — `smushHorizontalRule5 \/ → Y` dead
  branch fixed via explicit pair matching in commonMain; Java
  retains the `indexOf`-based dispatch per the frozen-v1 contract.
- ChatGameFontificator **M4** — `SpriteCharacterKey.isBadge` uses
  `&&` in commonMain; Java still has the bitwise `&` (same boolean
  result in this case, but shorter-circuit correct in Kotlin).

Both are documented in each repo's `CODE_REVIEW.md` and pinned
in the parity test suite with assertion rationale.

---

## 4. Low-level class review

Diagram source: [`docs/diagrams/01-core-api.puml`](docs/diagrams/01-core-api.puml).
Concrete implementations: [`docs/diagrams/02-format-implementations.puml`](docs/diagrams/02-format-implementations.puml).

### 4.1 Canonical interfaces (`modules/core/commonMain`)

```kotlin
interface Font {
    val metadata: FontMetadata
    fun glyphs(): Sequence<Glyph>
    fun glyph(codepoints: List<Int>): Glyph?     // list → supports ligatures + ZWJ emoji
}

interface Glyph {
    val advance: Int
    fun codepoints(): Sequence<Int>              // 1..n codepoints (emoji ZWJ / ligature)
    fun rasterize(target: PixelSink, scale: Float = 1f)
    fun vectorize(target: SvgSink, options: VectorizeOptions = VectorizeOptions())
}

interface FontReader<F : Font>  { val formats: Set<FontFormat>; fun read(source: ByteSource): F }
interface FontWriter<F : Font>  { fun write(font: F, sink: ByteSink) }
interface FontRenderer<F : Font>{ fun render(font: F, text: String, options: RenderOptions): RenderedFrame }
```

### 4.2 Raster path (`PixelSink`)

- Works for every font flavour: bitmap rows, FIGlet rows, sprite
  tiles, TTF outlines rasterised via Skiko, PNG-emoji layers.
- Default JVM actual is `SkikoPixelSink` (wraps
  `org.jetbrains.skia.Canvas`). Second actual
  `ARGB8BitmapSink` for in-memory headless tests.

### 4.3 Vector path (`SvgSink`)

- `Glyph.vectorize(SvgSink, VectorizeOptions)` is the **new**
  surface introduced by this integration. Dispatch is per-format:

| Flavour | Vectorisation strategy |
| --- | --- |
| Bitmap (BDF / PSF / FNT / HEX / Playdate / sprite) | Lit pixels → square paths, or Moore-neighbour / Potrace-like contour trace (selectable via `VectorizeOptions.bitmapTracer`). Colour pixels → per-layer `Paint.Solid`. |
| FIGlet `.flf` / `.tlf` | Each printable cell → a unit square in the output grid; metadata (hardblank) filtered out. |
| TTF outline (`glyf`) / CFF / CFF2 | Reuse existing outline data from `modules/truetype`; emit SVG path `d` verbatim. |
| COLRv0 / COLRv1 | Nested `<g>` with per-layer `Paint` (supports linear + radial gradients). |
| SVG-in-OTF | Embedded SVG fragment written through `SvgSink.group`. |
| CBDT / sbix PNG | Emitted as `<image>` (`VectorizeOptions.embedPng = true`) or re-traced into paths. |

See [`docs/diagrams/04-vectorize-flow.puml`](docs/diagrams/04-vectorize-flow.puml)
for the dispatch sequence.

### 4.4 Bitmap → path API (extended)

The existing `fonts-bitsnpicas` TTF compile/decompile already
produces `glyf` contours from bitmap glyphs. The integration
extends that API with three new capabilities needed by `vectorize`:

1. **Colour-per-layer output.** Input ARGB bitmaps (sprite sheets,
   indexed colour bitmaps) decompose into one contour-traced path
   per distinct colour, each wearing its own `Paint.Solid`.
2. **Selectable tracer.** The default square-pixel emitter stays;
   Moore-neighbour and Potrace-like tracers produce smoother SVG
   output for display sizes > 4× the native bitmap.
3. **PNG layer embedding.** Pre-rasterised colour-emoji layers
   (CBDT / sbix / sprite-emoji) are emitted as `<image>` elements
   by default; switch `embedPng=false` to re-trace.

### 4.6 UI test architecture — `UiDriver` expect/actual

Diagram source: [`docs/diagrams/08-ui-driver-expect-actual.puml`](docs/diagrams/08-ui-driver-expect-actual.puml).

One common-code suite under `modules/ui-shared/commonTest/**`
drives every renderer through an `expect class UiDriver`. Three
`actual` bindings land at successive stages (see roadmap in
`MIGRATION_PLAN.md` §0.5):

- `SwingUiDriver` (blue, pinning oracle) — lands at **Stage S2**,
  drives legacy `java.awt.Graphics2D` via off-screen
  `BufferedImage`; AssertJ-Swing for interactions.
- `ComposeDesktopUiDriver` (green) — lands at **Stage S5**, drives
  Compose Desktop via `runComposeUiTest { onNodeWithTag(id)… }` +
  off-screen Skiko `Surface`.
- `ComposeWebUiDriver` (green) — lands at **Stage S6**, drives
  Compose Web's wasmJs test-renderer for DOM assertions, with
  nightly Playwright Kotlin runs grabbing real-browser ARGB.

ARGB-hash parity gate: at freeze (S3) the Swing actual writes
`testdata/snapshots/swing/<test>.argb.sha256`; every later
renderer must match the same hash on every CI run. Snapshot
bumps require an explicit commit
(`ui-parity: accept <renderer> divergence for <test>`), never a
silent overwrite.

Tests driven through `UiDriver`:

- `RenderGlyphParityTest` — per-format rasterisation
- `VectorizeParityTest` — per-format SVG output (byte-exact up to
  `VectorizeOptions.svgPrecision`)
- `CodepointSequenceTest` — FI ligature, ZWJ emoji family,
  combining diacritics
- `ConfigRoundTripParityTest` — pins `ConfigFont` setter family
- `SpriteFontGeometryParityTest` — variable-width glyph positions
- `FigletSmushParityTest` — horizontal + vertical smush rules

Why this shape instead of per-framework test suites:

- **Write once, run three times.** No duplicate test authorship.
- **Swing is a concrete oracle.** ARGB hashes captured at freeze
  are authoritative; every later renderer diffs against them.
- **Regressions bisect cleanly.**
  `./gradlew :ui-compose-desktop:test --tests RenderGlyphParityTest`
  pinpoints the framework that broke.
- **Compose-for-iOS/Android** (if ever added) is a fourth
  `actual` with zero new test code.

### 4.5 Shaping (`Shaper`) — ligatures + emoji clusters

`Font.glyph(codepoints)` accepts `List<Int>`, but end-users pass
free-form strings. The `Shaper` interface bridges that:

```kotlin
interface Shaper { fun shape(text: String, font: Font): List<GlyphRun> }
data class GlyphRun(val glyphs: List<Glyph>, val positions: List<Point>, val advance: Float)
```

- JVM default: Skiko's HarfBuzz (`SkikoShaper`, internal).
- JVM fallback: `harfbuzz4j` (pure Java JNI binding).
- Tests: `codepoints → glyph` resolution for:
  - `["fi"]` → FI ligature glyph.
  - `["👨", ZWJ, "👩", ZWJ, "👧"]` → family emoji glyph.
  - `[A, COMBINING_ACUTE]` → composed glyph when font declares it.
  - Fallback: unknown codepoint → `.notdef` glyph visible in output.

---

## 5. Module + host component diagram

Diagram source: [`docs/diagrams/03-modules-and-hosts.puml`](docs/diagrams/03-modules-and-hosts.puml).

- `modules/core` depends only on `kotlin.stdlib`.
- `modules/bitmap / figlet / sprite / truetype / shaper` each
  depend on `core` and may optionally add platform-specific
  fallbacks (FontBox on JVM for TTF parser cross-check;
  harfbuzz4j on JVM when Skiko is absent).
- `modules/ui-shared` depends on Compose MP + Skiko + `core`.
- `modules/ui-{swing,compose-desktop,compose-html}` consume
  `ui-shared` + the format modules they render.
- `modules/cli` pulls every format module; Phase F builds it as a
  `native-image` with the GraalVM plugin.

---

## 6. Workflow: fix-then-freeze-then-port

Diagram source: [`docs/diagrams/07-fix-then-freeze-then-port.puml`](docs/diagrams/07-fix-then-freeze-then-port.puml).

Each `CODE_REVIEW.md` finding flows through one state machine:

1. **Red** — Kotlin test committed; CI `profile=java` fails.
2. **Green (Java)** — Java minimally patched; CI `profile=java` green;
   JaCoCo coverage climbing.
3. **Freeze** — once every finding is green AND JaCoCo = 100 %
   line + branch on the Java tree, tag `legacy-v1` and lock the
   source tree via CODEOWNERS.
4. **Port** — `commonMain` Kotlin re-implementation lands under
   `modules/{bitmap,figlet,sprite,truetype,...}`; the same Kotlin
   tests run against both frozen Java and new Kotlin.
5. **Differential parity** — byte-exact SVG and ARGB hashes, or
   documented divergences.

The agents currently running on `claude/code-review-k4kzN`
are executing states 1 → 2 for the Critical findings of all three
repos.

---

## 7. Dual CI/CD — pipelines with ProGuard

Diagram source: [`docs/diagrams/06-dual-cicd.puml`](docs/diagrams/06-dual-cicd.puml).

- Both profiles start with `sdk env` driven by `.sdkmanrc`.
- Both apply ProGuard on release artefacts and re-run the full
  test suite against the shrunk JAR (`verifyProguardedJar`).
- `mapping.txt` is uploaded as a CI artefact and attached to each
  GitHub release.
- The `kmp` pipeline also produces a native-image CLI via
  `org.graalvm.buildtools.native` 0.10.6, using the same
  `25.0.2-graal` JDK that the rest of the build uses.

---

## 8. Vendoring discipline

- `third_party/MANIFEST.toml` lists every vendored SHA + licence.
- `scripts/vendor-pull.sh` is the single entry point for
  refreshing vendored code; it drops a `vendor:` prefix in commit
  messages and never touches files outside the `vendor/` (Phase A)
  or `modules/{figlet,sprite,chat-preview}` (Phase B) paths.
- One vendored file = one commit at the initial import, keeping
  the diff against upstream readable.
- Renames and refactors live in **separate** follow-up commits so
  a future maintainer can bisect upstream drift.

---

## 9. Acceptance criteria for the integration

1. Every new module implements the `modules/core` interfaces; no
   format module exposes its own `Glyph` type.
2. `Glyph.vectorize` passes `SvgParityTest` (vectorise → Skia
   rasterise → ARGB-hash within tolerance of direct `rasterize`).
3. `Font.glyph(codepoints)` resolves at minimum:
   - FI ligature, family emoji (ZWJ), composed accented char.
4. Every vendored file is traceable to `third_party/MANIFEST.toml`;
   `scripts/vendor-pull.sh` re-produces the file tree bit-exact.
5. Both CI profiles green on every PR, ProGuard-shrunk artefacts
   pass the full test suite.
6. No file under `main/java/BitsNPicas/src/**` changes after
   `legacy-v1` (CI-enforced).
7. The KMP UI on `{ubuntu, macos, windows}` × `{Swing-legacy,
   Compose-desktop, Compose-web (wasmJs)}` renders the same
   canned font gallery bit-exactly (ARGB hash per renderer, diffs
   gated). All three renderers are driven through the single
   `UiDriver` expect/actual common-test suite (§4.6).
8. JBang CLI scripts (corpus generators + entry points)
   runnable from a bare SDKMAN environment with no Gradle build;
   Gradle `modules/cli` aggregates the same sources for
   ProGuarded fat JARs and GraalVM `native-image` artefacts at
   Stage S7. JBang is **JVM-only** — Kotlin/Native / JS / wasmJs
   targets ship via Gradle, not JBang (verified capability matrix
   in `MIGRATION_PLAN.md` §1.3).

---

## 10. Open design questions (flagged for PR discussion)

- **Bitmap tracer default.** `SQUARE` vs. `MOORE_NEIGHBOUR` — the
  former produces faithful pixel art; the latter is smoother but
  diverges from the on-screen rasterisation at small sizes.
  Proposal: default `SQUARE`, expose the switch on CLI +
  Compose UI.
- **`harfbuzz4j` artefact coordinates.** Not on Maven Central as
  a publicly-indexed stable JAR; options are a jitpack build or a
  fork published to our own repo. Proposal: depend on Skiko's
  built-in HarfBuzz; treat `harfbuzz4j` as an opt-in extension
  rather than a default dependency.
- **CBDT vs sbix precedence for Apple emoji.** Fonts can carry
  both. Proposal: prefer sbix (higher-resolution PNGs) when
  available; document the fallback chain.
- **FIGlet `bananansi` colour codes.** Should `Glyph.vectorize`
  on a `FigletGlyph` emit colour? Proposal: yes, but only when
  the source glyph carries per-cell ANSI colour; otherwise plain
  black paths.
