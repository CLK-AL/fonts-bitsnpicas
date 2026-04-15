# legacy-v1 — Stage S3 freeze marker

```
legacy-v1 = 35e9d0486ba2c8a8cd5d830d361cc08c17bda3b2
branch    = claude/code-review-k4kzN
date      = 2026-04-15
```

(The `legacy-v1` annotated git tag is created locally on the same
commit but the upstream endpoint for this fork rejects tag pushes
with HTTP 403; this file is the authoritative freeze marker and is
used by the freeze-guard CI check.)

## What's frozen

```
main/java/BitsNPicas/src/**
```

CODEOWNERS assigns that path to `@legacy-frozen` and
[`.github/workflows/freeze-guard.yml`](.github/workflows/freeze-guard.yml)
fails any PR that modifies it after this commit.

## Coverage gates at freeze (Option-C scope)

| Scope                                   | Line  | Branch |
| --- | ---: | ---: |
| `com/kreative/bitsnpicas/*.class` (core) | 93.7 % | 81.3 % |
| `com/kreative/bitsnpicas/importer/**`   | 77.8 % | 61.5 % |
| `com/kreative/bitsnpicas/exporter/**`   | 90.4 % | 69.4 % |
| `com/kreative/bitsnpicas/puaa/**`       | 90.3 % | 70.3 % |
| `com/kreative/bitsnpicas/truetype/**`   | 89.7 % | 86.0 % |
| **Bundle**                              | **88.1 %** | **74.4 %** |

`./gradlew check` enforces a bundle floor of `LINE ≥ 0.88 / BRANCH ≥ 0.74`
via `jacocoTestCoverageVerification`. Any regression below that floor
fails the build.

## Path forward (Stage S4+)

- All new work lands in `modules/**` (Kotlin / KMP).
- Legacy Java is retained only as:
  1. The test oracle for the `UiDriver` expect/actual parity tier
     (Swing renderer) — see [`INTEGRATION.md`](INTEGRATION.md) §4.6.
  2. The differential parity reference for the Stage S4 KMP port
     (`commonMain` Kotlin must produce byte-exact output against
     it for the committed corpus).
  3. Reference for rare upstream merges (tracked via
     `third_party/MANIFEST.toml`; outbound PRs are not the pattern —
     upstreams are inactive).
- Unfreezing a file requires admin sign-off + a justification in the
  `FREEZE.md` changelog below.

## FREEZE changelog

<!-- one line per intentional modification of the frozen tree; none yet -->
