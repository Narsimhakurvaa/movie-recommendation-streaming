# CineVault

A personalised movie recommendation and streaming-inspired platform, built as a
production-style Java full-stack application.

**Spring Boot 3.3 · Java 21 · PostgreSQL 16 · React 19 · TypeScript · Docker**

The centrepiece is a **hybrid recommendation engine** — content-based,
collaborative, popularity and cold-start strategies blended with adaptive
weights — that produces a genuine, explainable reason for every film it
recommends. No hardcoded lists, no "same genre so you might like it".

---

## Contents

- [Quick start](#quick-start)
- [Demo accounts](#demo-accounts)
- [Features](#features)
- [How recommendations work](#how-recommendations-work)
- [Architecture](#architecture)
- [Running tests](#running-tests)
- [Configuration](#configuration)
- [Project layout](#project-layout)
- [CI](#ci)
- [Documentation](#documentation)

---

## Quick start

### With Docker (recommended)

```bash
git clone https://github.com/Narsimhakurvaa/movie-recommendation-streaming.git
cd movie-recommendation-streaming

cp .env.example .env
# JWT_SECRET is required and has no default:
echo "JWT_SECRET=$(openssl rand -base64 48)" >> .env

docker compose up --build              # Postgres + backend + frontend
# docker compose --profile redis up --build   # ... and Redis (optional)
```

| Service | URL |
| --- | --- |
| Application | http://localhost:3000 |
| API | http://localhost:8080/api |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |

nginx serves the frontend and proxies `/api` to the backend, so the browser
talks to a single origin and there is no CORS preflight in production.

### Without Docker

Requires JDK 21, Maven 3.9+, Node 20+ and PostgreSQL 16.

```bash
createdb cinevault

cd backend
export JWT_SECRET=$(openssl rand -base64 48)
mvn spring-boot:run -Dspring-boot.run.profiles=dev      # Flyway migrates + seeds

cd ../frontend
npm install
npm run dev                                              # http://localhost:5173
```

The Vite dev server proxies `/api` to `localhost:8080`.

> **No TMDB API key is needed.** The default provider (`MOVIE_PROVIDER=local`)
> serves a seeded catalogue of 61 films, 253 people and 240 keywords, so the app
> is fully functional offline. Set `MOVIE_PROVIDER=tmdb` with a key to fetch live
> metadata instead.

## Demo accounts

Seeded by the `dev` profile. Every account uses the password **`DemoPassw0rd!`**

| Email | Demonstrates |
| --- | --- |
| `admin@example.com` | Admin dashboard, moderation, user management |
| `nolan.fan@example.com` | Rich history — full hybrid personalisation |
| `twin.taste@example.com` | Near-identical taste; drives collaborative filtering |
| `animation.buff@example.com` | Strong single-genre affinity |
| `crime.watcher@example.com` | Distinct genre profile |
| `newcomer@example.com` | One rating — sparse-profile handling |
| `blank.slate@example.com` | No activity at all — cold start |

Sign in as `nolan.fan` and then `blank.slate` to see the engine behave completely
differently for the same catalogue.

## Features

**Accounts & security** — Registration and login with BCrypt (cost 12). Stateless
JWT access tokens plus rotating opaque refresh tokens, stored only as SHA-256
digests. Refresh-token reuse is treated as theft and revokes every session.
Role-based access control, rate-limited auth endpoints, and password-reset /
email-verification flows.

**Catalogue** — Browse and search with combinable filters (genre, year, rating,
language, runtime) and allowlisted sorting. Debounced type-ahead search as an
accessible ARIA combobox. Filter state lives in the URL, so any view is
shareable and survives a refresh.

**Recommendations** — Personalised home feed, "more like this", trending,
popular, top-rated and new releases. Every result carries a human-readable
reason derived from the signals that actually contributed to its score.

**Interactions** — 1–5 star ratings (one per user per film, enforced by a unique
index), watchlist with optimistic UI and rollback, reviews with spam heuristics
and admin moderation, and watch history that feeds the engine.

**Admin** — Dashboard statistics, user search and enable/disable, review
moderation queue, and catalogue sync.

**Frontend** — React 19, TanStack Query, React Hook Form + Zod, Tailwind v4,
dark/light theme applied before first paint, route-level code splitting, and
accessibility treated as a requirement rather than a polish step.

## How recommendations work

Four strategies, blended by a hybrid that **renormalises its weights over
whichever strategies can actually contribute**:

| Strategy | Signal | Base weight |
| --- | --- | --- |
| Content-based | Genre, keyword, cast, crew and language affinity | 0.40 |
| Collaborative | Mean-centred cosine similarity between users | 0.35 |
| Popularity | Bayesian-shrunk quality, log popularity, recency decay | 0.15 |
| User preference | Onboarding genre picks | 0.10 |

A user with only one rating cannot support collaborative filtering, so the
weights become `content 0.727 / popularity 0.273` — the content signal gets
*stronger* rather than the ranking collapsing towards generic popularity.

Three details that matter:

- **Ratings are mean-centred** before comparison. A user who rates everything 4–5
  and one who rates everything 1–2 can have identical taste; raw cosine misses it.
- **Quality is Bayesian-shrunk**, so an 8.5 from 30,000 voters outranks a 10.0
  from one. Shrinkage is what stops the top of the list being statistical noise.
- **Explanations are derived, never invented.** If no personal signal cleared the
  evidence threshold, the engine falls back to an honest generic reason instead
  of claiming a preference it cannot support.

Full detail, including worked examples with real numbers, is in
[`docs/recommendation-engine.md`](docs/recommendation-engine.md).

## Architecture

A **modular monolith** — one deployable, internally divided into modules with
enforced dependency directions.

This is a deliberate choice. The recommendation engine needs a user's ratings,
history, watchlist and the catalogue *together* to score candidates in one query
plan. Split across services, that becomes a network fan-out per page load and
joins PostgreSQL does in milliseconds become application-level merges.

```
com.cinevault
├── common/          DTOs, exceptions, security, configuration
├── user/            accounts, authentication, profile
├── catalogue/       movies, genres, people, search
├── interaction/     ratings, reviews, watchlist, history
├── recommendation/  the scoring engine  ← framework-free core
├── provider/        MovieMetadataProvider abstraction (local | TMDB)
└── admin/           statistics and moderation
```

The engine's `model`, `scoring`, `strategy` and `explain` packages contain **zero**
framework imports, so the algorithm can be compiled, executed and reasoned about
in complete isolation. The build enforces this boundary.

See [`docs/architecture.md`](docs/architecture.md).

## Running tests

```bash
# Backend: unit + web-layer tests (no Docker required)
cd backend && mvn test

# Backend: adds Testcontainers integration tests and coverage gates (needs Docker)
mvn verify

# Frontend
cd frontend
npm run typecheck
npm run lint
npm test
npm run test:coverage
```

JaCoCo gates coverage at **55% overall** and **80% for the recommendation
engine**, and the build fails below either threshold.

### Offline verification

This project was developed without access to Maven Central or Docker, so it
carries a self-contained harness that **executes** what it can prove:

```bash
tools/offline-verify/run-verification.sh
```

| Gate | Result |
| --- | --- |
| Full-grammar parse + conventions, all backend sources | 122/122 |
| Compile the framework-free engine | 13 sources |
| **Execute** the engine against a fixed catalogue and clock | 111/111 assertions |
| **Execute** migrations, seeds and behaviour on real PostgreSQL 16 | 20/20 checks |

It boots a genuine PostgreSQL 16, applies every migration, and verifies the
rating trigger, constraints, cascade behaviour and index usage. Details, and an
honest list of what it does *not* cover, are in
[`docs/verification.md`](docs/verification.md).

## Configuration

Every setting is an environment variable; see [`.env.example`](.env.example).

| Variable | Default | Notes |
| --- | --- | --- |
| `JWT_SECRET` | *none* | **Required.** Must decode to ≥32 bytes |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/cinevault` | |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | `cinevault` | |
| `MOVIE_PROVIDER` | `local` | `local` or `tmdb` |
| `TMDB_API_KEY` | *empty* | Only for `tmdb`; falls back to `local` if absent |
| `CACHE_TYPE` | `memory` | `redis` only when running 2+ instances |
| `REDIS_URL` | `redis://localhost:6379` | Only used when `CACHE_TYPE=redis` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,...` | Exact origins; never `*` |
| `FRONTEND_URL` | `http://localhost:5173` | Base for reset/verification links |
| `VITE_API_BASE_URL` | *(unset)* | Frontend build-time API URL; unset = same-origin `/api` |

No secret has a default value. The application refuses to start without a real
`JWT_SECRET` rather than falling back to something insecure.

**Profiles:** `dev` (seeds data, Swagger on, debug logging) · `prod` (no seed, no
Swagger, in-memory cache by default, real mail sender required) · `test` (H2) ·
`integration` (Testcontainers).

**Redis is optional.** The default `CACHE_TYPE=memory` needs no Redis at all;
switch to `redis` only when you run more than one instance. See
[`docs/deployment.md`](docs/deployment.md).

## Project layout

```
backend/          Spring Boot application
  src/main/java/com/cinevault/    122 sources
  src/main/resources/db/          Flyway migrations + seed data
  src/test/java/                  JUnit 5, Mockito, MockMvc, Testcontainers
frontend/         React + TypeScript + Vite
docker/           Dockerfiles and nginx config
docs/             Architecture, engine, API, database, verification
tools/offline-verify/   Self-contained verification harness
```

## CI

The pipeline lives at [`.github/workflows/ci.yml`](.github/workflows/ci.yml) and
runs on every push and pull request:

1. **Backend** — compile, unit tests, integration tests against a PostgreSQL 16
   service container, JaCoCo coverage gates.
2. **Frontend** — typecheck, lint, Vitest with coverage, production build.
3. **Docker** — both images build.
4. **Security** — dependency vulnerability scan.

> **Note for maintainers.** The workflow file is committed locally but may not be
> pushed if the authenticating token lacks the GitHub `workflows` permission
> (GitHub rejects such pushes outright). If your push is rejected, either push
> the final commit with a token that has the `workflow` scope, or add the file
> through the GitHub web UI. The commit hash containing it is reported at the end
> of the build log.

## Documentation

| Document | Contents |
| --- | --- |
| [`docs/architecture.md`](docs/architecture.md) | Module map, request lifecycle, design decisions |
| [`docs/recommendation-engine.md`](docs/recommendation-engine.md) | Algorithms, constants, worked examples |
| [`docs/api.md`](docs/api.md) | Every endpoint, error codes, conventions |
| [`docs/database.md`](docs/database.md) | Schema, ER diagram, constraints, indexing |
| [`docs/deployment.md`](docs/deployment.md) | Deploying to real infrastructure, env vars, troubleshooting |
| [`docs/verification.md`](docs/verification.md) | What was verified, how, and what was not |

## Deploying

The frontend and backend deploy independently:

- **Frontend** → any static host. `frontend/vercel.json` and
  `frontend/netlify.toml` are committed with SPA rewrites and cache headers.
  Set `VITE_API_BASE_URL` at build time.
- **Backend** → any container or JVM host (Render, Railway, Fly, ECS, a VM).
  Not Vercel: it needs a persistent process, not a serverless function.
- **PostgreSQL 16+**, no extensions. Flyway migrates automatically at startup.
- **Redis** only if you scale beyond one instance.

Full instructions, the complete environment-variable table, a smoke-test script
and a troubleshooting matrix are in [`docs/deployment.md`](docs/deployment.md).

## Licence

Released under the MIT Licence.
