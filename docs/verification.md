# Verification

This project was built in an environment with **no access to Maven Central**, no
Docker, no apt, and no JDK compiler — only npm, PyPI and GitHub were reachable.
A full `mvn verify` was therefore impossible during development.

Rather than treat the backend as unverifiable, the work was structured so that
as much of it as possible could be *executed and proven* offline. This document
explains what was verified, how, and what remains for CI.

## Running it

```bash
tools/offline-verify/run-verification.sh
```

Bootstraps its own toolchain on first run (into `~/.cache/cinevault-toolchain`,
outside the repository) and executes four gates.

## The four gates

### 1. Syntax and conventions — 122/122 sources

Every backend source file is parsed with a **full Java grammar** (`java-parser`),
not a regex. On top of a successful parse it enforces:

- package declaration matches directory
- filename matches the public type
- no wildcard imports, no unused imports
- no `System.out` / `System.err` (the project uses SLF4J)
- no hardcoded secrets, with a narrow allowlist for BCrypt prefixes,
  `example.com`, and obvious placeholders

### 2. Compile the framework-free engine — 13 sources

The `model`, `scoring`, `strategy` and `explain` packages contain **zero**
Spring, JPA, Jackson or Swagger imports. That is a deliberate architectural
property, and it is what makes the algorithm compilable in isolation with the
Eclipse Compiler for Java.

The script **enforces the boundary**: if a framework import ever appears in
those four packages, the build fails with an explicit message rather than
silently dropping the engine from offline coverage.

### 3. Execute the engine — 111/111 assertions

The compiled engine is run against a fixed 14-film catalogue and a frozen clock
(`2026-08-18T00:00:00Z`), covering:

- similarity primitives (Jaccard, cosine, Bayesian average, exponential decay)
- all four strategies and the hybrid blend
- cold start, sparse profiles, and profiles matching nothing
- collaborative filtering, including zero-variance raters
- similar-film lookup, including the correct empty result

This gate also type-checks the shared JUnit fixture (`MovieFixtures`) against
the real engine API, so the Maven test suite cannot drift from the code it
tests without the offline run noticing.

### 4. Database — 20/20 checks

A real **PostgreSQL 16** server (from the `pgserver` wheel) is booted, every
Flyway migration and seed script is applied, and 20 behavioural checks run:

- the rating trigger on `INSERT`, `UPDATE` and `DELETE`
- every check constraint and unique index, by name
- cascade deletion, including aggregate refresh afterwards
- index usage, confirmed by reading `EXPLAIN` output

## What this caught

Verification was not a formality. Real defects found and fixed:

| Defect | How it surfaced |
| --- | --- |
| `UNIQUE (…, COALESCE(x,''))` is invalid in PostgreSQL | Migration failed on the live server |
| Zero-vector neighbours produced `NaN` similarities | Executed collaborative scenario |
| Explanations claimed personal signals that had not fired | Executed explanation assertions |
| A lambda captured a reassigned local | ECJ compile error |
| The offline runner stopped compiling the engine after Spring-bound subpackages were added | Runner failed after a later milestone |

Writing the JUnit suite, the engine assertions were **executed first** and the
expected values taken from the output. Three assumptions turned out to be wrong:

- For a user with one `Interstellar` rating, content scoring ranks *Arrival* and
  *Dune* above *The Prestige* — shared genre plus keyword outweighs the director
  signal alone.
- Weights for a single-rating user renormalise to `0.727 / 0.273`, not the base
  `0.40 / 0.15`.
- `HybridRecommendationStrategy.CONTENT_WEIGHT` is package-private and
  unreachable from the test's package, so the test derives its baseline from
  the engine instead.

Similarly, the web-layer test originally asserted `CONSTRAINT_VIOLATION` for a
parameter validation failure. Reading `GlobalExceptionHandler` showed it returns
`VALIDATION_FAILED`. Had the assertion been left as written, it would have
failed on the first real CI run.

## Frontend verification

Fully executable in the sandbox, and all of it was run:

```bash
cd frontend
npx tsc -b        # clean
npm run lint      # 0 errors
npx vitest run    # 58 passing
npx vite build    # 23 code-split chunks
```

Two tests were **mutation-tested** to prove they are load-bearing:

1. Removing the shared `refreshPromise` from the API client made the concurrent
   401 test fail with `expected 1 times, but got 5 times` — the single-flight
   guard genuinely prevents the refresh-token reuse that would revoke a user's
   sessions.
2. Removing the rollback from `useWatchlistToggle`'s `onError` made the
   rollback test fail — the optimistic update genuinely reverts on failure.

## What is NOT covered offline

Honest limitations. These require a normal environment with Maven Central:

- **Full compilation of all 122 backend sources.** Spring, JPA and Jackson jars
  are unavailable, so ~1200 symbols cannot resolve. Only parse-level and
  convention checks run against the framework-bound code.
- **The JUnit / Mockito / MockMvc suite.** Written and signature-checked against
  the real sources by inspection, but not executed here.
- **Testcontainers integration tests.** No Docker daemon.
- **Docker image builds.** No registry access.

Every one of these runs in CI (`.github/workflows/ci.yml`) on a normal runner.

## Toolchain

| Component | Source | Purpose |
| --- | --- | --- |
| Temurin 25 JRE | `jdk4py` (PyPI) | Runs ECJ and the compiled engine |
| ECJ | `@ctxo/lang-java-analyzer` (npm) | Java 21 batch compiler |
| PostgreSQL 16 | `pgserver` (PyPI) | Real database for schema verification |
| `java-parser` | npm | Full-grammar parsing for the syntax gate |

Two traps worth recording: the `jdk4py` binaries ship without the execute bit,
and `java -jar ecj.jar` runs the wrong main class — the compiler must be invoked
as `java -cp ecj.jar org.eclipse.jdt.internal.compiler.batch.Main`.
