# Test Review: Bits&Picas

## Inventory

| Type | Present? | Notes |
| --- | --- | --- |
| Unit tests       | **No** | — |
| Integration tests | **No (see below)** | Three files named `*Test.java` exist, but they are CLI utilities, not automated tests. |
| UI tests          | No | — |
| API/contract tests | No | — |
| End-to-end tests  | No | — |
| Load/perf tests   | No | — |

The repository has no `test` / `src/test` / `*_test` source root, no JUnit
(or TestNG) dependency, and no build file wiring tests into CI. There is no
automated test suite at all.

## The "Test" files that exist

Three classes named `*Test.java` live alongside production code:

| File | What it is |
| --- | --- |
| `main/java/BitsNPicas/src/com/kreative/mapedit/RewriteTest.java` | `public static void main` CLI that reads a Mapping, rewrites it, shells out to `diff`, and prints the result. |
| `main/java/BitsNPicas/src/com/kreative/bitsnpicas/main/TTFRewriteTest.java` | `public static void main` CLI that round-trips a TTF (`decompile` → `compile`) and prints `SAME` / `DIFF` / `ERROR`. |
| `main/java/BitsNPicas/src/com/kreative/bitsnpicas/puaa/PuaaCodecTest.java` | `public static void main` CLI that round-trips PUAA codec files and prints `PASS` / `FAIL` / `ERROR`. |

None of them is a test in the usual sense: they have no assertions that fail
the build, no test-runner integration, and no discovery mechanism. They are
developer helpers invoked by hand.

### Findings in the existing "Test" utilities

#### Critical

**T1. Results are printed, not asserted.**
`TTFRewriteTest` prints `SAME`/`DIFF` to stdout; `PuaaCodecTest` prints
`PASS`/`FAIL`; `RewriteTest` prints `diff` output. A human has to eyeball
the terminal to know whether anything broke. There is no exit code signal:
both files exit `0` regardless of whether every file round-tripped.

*Fix:* Track a failure counter in `main`, call
`System.exit(failures == 0 ? 0 : 1)`, and fail loud when any file mismatches.

**T2. `RewriteTest` shells out to the `diff` binary.**
`main/java/BitsNPicas/src/com/kreative/mapedit/RewriteTest.java:33`

```java
Process p = Runtime.getRuntime().exec(a);
```

This makes the "test" non-portable (no `diff` on stock Windows), fragile
(depends on `$PATH`, GNU-vs-BSD differences), and vulnerable to hostile
input paths if ever extended.

*Fix:* Compare `byte[]`/`String` in-process (e.g. `Arrays.equals`, or
`java.nio.file.Files.readAllBytes` + compare) and print only a summary.

#### Major

**T3. Resource leaks in the helpers.**

- `RewriteTest.java:20-23,33-37`: `FileOutputStream o` and the `Process`
  `InputStream` are closed manually; on exception either leaks. The temp
  file `t` is deleted after the diff prints but never in a `finally` block.
- `TTFRewriteTest.java:52-54`: `FileInputStream in = new FileInputStream(…);
  in.read(originalData); in.close();` — not try-with-resources, and
  `read()` return value is ignored (partial reads possible for very large
  TTFs).
- `PuaaCodecTest.java:46-48`: `Scanner in = new Scanner(new FileInputStream…)`
  closed manually.

*Fix:* Convert all three to try-with-resources; check `read()` return
values or use `Files.readAllBytes`.

**T4. `TTFRewriteTest` silently truncates files > 2 GiB.**
`TTFRewriteTest.java:49` throws for `length > Integer.MAX_VALUE`, which is
correct, but a smaller file whose `read()` returns fewer bytes than
requested will be silently truncated because the return value isn't
checked.

*Fix:* Use `DataInputStream.readFully` or `Files.readAllBytes`.

**T5. No test inputs are committed.**
None of these harnesses ships a sample `.ttf`/`.puaa`/mapping corpus, so
running them requires the developer to source their own. There's no way to
reproduce a previous run.

*Fix:* Commit a small corpus under `fonts/` (or a new `testdata/`
directory) and a thin wrapper script that runs each harness across it.

#### Minor

**T6. `PuaaCodecTest` silently skips files without a registered codec.**
`main/java/BitsNPicas/src/com/kreative/bitsnpicas/puaa/PuaaCodecTest.java:31-34`

If the registry lookup returns `null`, the file is silently ignored. A
developer running the harness on a mis-named corpus can't tell the
difference between "passed" and "wasn't tested."

*Fix:* Print `SKIP (no codec)` so coverage gaps surface.

**T7. `RewriteTest` hides non-zero `diff` exits.**
`p.waitFor()` is called but the exit code is discarded. A user sees the
diff text scroll by but can't distinguish "identical" from "different" at
a glance without also parsing the output.

*Fix:* Print `SAME`/`DIFF` tag per file, driven by `p.waitFor() == 0`.

---

## What is missing

Given the [code review](CODE_REVIEW.md) findings, the top test gaps are:

1. **Format parser fuzz / negative tests.** There is no test that the
   PSF / FNT / BDF / Hex importers reject malformed headers rather than
   crashing with `ArrayIndexOutOfBoundsException` or `OutOfMemoryError`.
   Every critical finding in `CODE_REVIEW.md` would have been caught by a
   handful of targeted negative tests.
2. **Round-trip coverage for each importer/exporter pair.** Only TTF and
   PUAA have a round-trip harness today. BDF, PSF, FNT, Hex, Playdate, and
   the other formats have none.
3. **Unit tests for `BitmapFontGlyph.compose`.** The empty-dimension bug
   is entirely driven by an edge case (`y1 == y0`) that a single-line
   unit test would cover.
4. **Glyph iteration correctness.** `BDFBitmapFontExporter` iterating
   `0x110000` codepoints is a perf regression with no guard test.

## Recommendations

1. **Add JUnit 5 + a `test` source root** under
   `main/java/BitsNPicas/test`, and wire a minimal build target
   (`build.xml` or a Maven/Gradle port) that runs the suite.
2. **Promote the three `*Test.java` harnesses** into actual
   JUnit-parameterised tests driven by the corpus in `fonts/`. Keep a
   thin `main` entry point around each if you still want a CLI, but have
   it delegate to the same logic as the JUnit test.
3. **Add negative-input tests** for every importer (malformed header,
   truncated body, impossibly large size field, empty file). These
   double as regression fences for the critical findings in
   `CODE_REVIEW.md`.
4. **Add exit-code signalling** to every `main`-style harness so CI can
   run them without human supervision.

## Summary

| Severity | Count |
| --- | --- |
| Critical | 2 (no assertions, shell-out to `diff`) |
| Major    | 3 (resource leaks, silent truncation, no committed corpus) |
| Minor    | 2 (silent skip, hidden exit code) |

The single most valuable next step is adding a real test framework and a
small suite of negative-input tests around the binary importers — that
converts the font parsers from "crash surface" to "validated API."

---

## Execution plan (KMP / Gradle / TDD / 100 % coverage)

The concrete execution of this review lives in
[`MIGRATION_PLAN.md`](MIGRATION_PLAN.md). In short:

- Each level (unit, integration, UI, API, E2E, load, fuzz) is wired to
  a Gradle KMP source set (§3 of the plan).
- Every `CODE_REVIEW.md` finding becomes one named Kotlin test in
  `commonTest` / `jvmTest`, authored **red** against a JVM delegate
  over the legacy Java code (§4 of the plan).
- The three existing `*Test.java` CLIs are promoted into
  JUnit-parameterised tests under `jvmTest`, with exit-code
  signalling and no shell-out to `diff`.
- 100 % Kotlin line + branch coverage (Kover) and ≥ 85 % mutation
  coverage (Pitest) are enforced per-format before it migrates from
  `jvmMain` (Java-delegating) to pure `commonMain`.
- Legacy Java under `main/java/BitsNPicas/src/**` stays frozen; the
  `java-legacy` CI lane keeps building and running it, while the
  `kmp` lane builds JVM / JS / Native targets and the Compose
  Desktop / Compose HTML UIs.
