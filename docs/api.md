# API reference

Base URL `/api`. All requests and responses are JSON. An interactive Swagger UI
is available at `/swagger-ui.html` in the `dev` profile (disabled in `prod`).

The endpoint list below was generated from the controller mappings, so it
matches the code exactly.

## Conventions

### Authentication

Protected endpoints expect an access token:

```
Authorization: Bearer <accessToken>
```

Access tokens are short-lived (15 minutes by default). When one expires the API
returns `401` with code `AUTHENTICATION_FAILED`; the client exchanges its refresh
token at `POST /api/auth/refresh` and replays the original request.

> **Refresh exactly once.** Refresh tokens rotate on use, and presenting an
> already-rotated token is treated as theft and revokes every session for that
> user. A client firing concurrent refreshes will log its user out.

### Errors

Every error, without exception, uses this shape:

```json
{
  "timestamp": "2026-08-19T10:15:30Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/auth/register",
  "correlationId": "b7f1c2d3",
  "validationErrors": [
    { "field": "password", "message": "Password must contain a digit" }
  ]
}
```

`validationErrors` is present only for validation failures. `correlationId` is an
8-character id also written to the server log, so a user-reported error can be
traced without exposing a stack trace. Error bodies never contain stack traces,
exception class names or SQL.

| Code | Status | Meaning |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | Body or parameter failed validation |
| `BAD_REQUEST` | 400 | Semantically invalid request |
| `MALFORMED_REQUEST` | 400 | Unparseable JSON or wrong type |
| `AUTHENTICATION_FAILED` | 401 | Bad credentials, or expired/invalid token |
| `AUTHENTICATION_REQUIRED` | 401 | No credentials supplied |
| `ACCESS_DENIED` | 403 | Authenticated but not authorised |
| `RESOURCE_NOT_FOUND` | 404 | No such entity |
| `ENDPOINT_NOT_FOUND` | 404 | No such route |
| `METHOD_NOT_ALLOWED` | 405 | Wrong HTTP verb |
| `RESOURCE_ALREADY_EXISTS` | 409 | Duplicate (e.g. re-reviewing a film) |
| `CONSTRAINT_VIOLATION` | 409 | Database constraint rejected the write |
| `RATE_LIMIT_EXCEEDED` | 429 | Too many attempts; see `Retry-After` |
| `EXTERNAL_PROVIDER_ERROR` | 502 | TMDB failed |
| `INTERNAL_ERROR` | 500 | Unexpected fault |

### Pagination

Paginated endpoints accept `page` (0-based) and `size` (1–100), and return a
stable envelope. A Spring `Page` is **never** serialised directly, because its
JSON shape is an internal detail that changes between Spring versions.

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 137,
  "totalPages": 7,
  "first": true,
  "last": false
}
```

### Rate limiting

Authentication endpoints allow 20 requests per minute per client, keyed on the
first hop of `X-Forwarded-For`. Exceeding it returns `429` with `Retry-After`.

---

## Authentication — `/api/auth`

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `POST` | `/register` | — | Create an account; returns a token pair |
| `POST` | `/login` | — | Exchange credentials for a token pair |
| `POST` | `/refresh` | — | Rotate a refresh token |
| `POST` | `/logout` | — | Revoke one session |
| `POST` | `/logout-all` | User | Revoke every session |
| `POST` | `/password-reset/request` | — | Begin password reset |
| `POST` | `/password-reset/confirm` | — | Complete password reset |
| `POST` | `/verify-email/request` | User | Send a verification token |
| `POST` | `/verify-email/confirm` | — | Confirm an email address |
| `POST` | `/change-password` | User | Change password while signed in |

**Password policy** (enforced server-side): 12–128 characters, with at least one
uppercase letter, one lowercase letter and one digit.

<details>
<summary><code>POST /api/auth/register</code></summary>

```json
{
  "email": "ada@example.com",
  "password": "Str0ngPassphrase!",
  "displayName": "Ada Lovelace",
  "favouriteGenreSlugs": ["science-fiction", "drama"]
}
```

`favouriteGenreSlugs` is optional and seeds cold-start recommendations; earlier
picks are weighted more heavily.

`201 Created`:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "3Qk9...opaque",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": 1,
    "email": "ada@example.com",
    "displayName": "Ada Lovelace",
    "roles": ["ROLE_USER"],
    "emailVerified": false,
    "onboardingCompleted": true
  }
}
```
</details>

Both a wrong password and an unknown account return the identical message
`"Invalid email or password"`, and the endpoint performs comparable work in each
case, so it cannot be used to discover which emails are registered.

## Movies — `/api/movies`

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/` | Optional | Browse with filters and sorting |
| `GET` | `/search` | Optional | Title search (`query` required) |
| `GET` | `/suggest` | Optional | Type-ahead suggestions |
| `GET` | `/{movieId}` | Optional | Full detail |
| `GET` | `/slug/{slug}` | Optional | Full detail by slug |

Filters: `genres`, `matchAllGenres`, `yearFrom`, `yearTo`, `minRating`,
`maxRating`, `languages`, `minRuntime`, `maxRuntime`, `includeAdult`.

Sort values: `popularity` (default), `rating`, `releaseDate`, `oldest`, `title`,
`runtime`. Sorting is restricted to this allowlist — user input never reaches an
`ORDER BY` clause.

Signing in changes nothing about *which* films are returned, but each card gains
`inWatchlist` and `userRating`. Viewing a detail page as a signed-in user records
a `VIEWED_DETAILS` interaction, which feeds the recommendation engine.

## Genres — `/api/genres`

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/` | — | All genres with film counts |
| `GET` | `/{slug}/movies` | Optional | Films in a genre |

## Recommendations — `/api/recommendations`

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/` | Optional | Home feed: hero + themed rails |
| `GET` | `/personalized` | User | Full personalised, paginated |
| `GET` | `/similar/{movieId}` | Optional | "More like this" |
| `GET` | `/trending` | Optional | Trending (18-month window) |
| `GET` | `/popular` | Optional | Popular now |
| `GET` | `/top-rated` | Optional | Top rated (min. 500 votes) |
| `GET` | `/new-releases` | Optional | Recent releases |

Every recommendation carries a `reason` derived from the signals that actually
contributed to its score:

```json
{
  "movie": { "id": 14, "title": "The Prestige" },
  "score": 0.87,
  "reason": "Because you liked movies directed by Christopher Nolan",
  "type": "HYBRID"
}
```

Anonymous callers receive popularity-based results. `similar` never consults the
user profile, so it behaves identically signed in or out — and returns an empty
list when nothing is genuinely similar, rather than padding with weak matches.

See [`recommendation-engine.md`](recommendation-engine.md) for the algorithm.

## Watchlist — `/api/watchlist`

All require authentication.

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/` | The user's watchlist |
| `POST` | `/{movieId}` | Save a film |
| `DELETE` | `/{movieId}` | Remove a film |
| `GET` | `/{movieId}/status` | Cheap membership check |

Adding a film already saved returns `409`. This includes the case where two
concurrent requests race past the pre-check — the unique index is the real
guarantee, and the resulting integrity violation is translated to `409`, not a
`500`.

## Ratings — `/api/movies/{movieId}/ratings`

All require authentication.

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/` | Rate a film (1–5) |
| `PUT` | `/` | Update the rating |
| `DELETE` | `/` | Remove the rating |

`POST` and `PUT` are both upserts; re-rating a film is a normal action, not a
conflict. The response includes the film's recomputed `averageRating` and
`ratingCount`, re-read after the database trigger has run.

## Reviews

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/movies/{movieId}/reviews` | Optional | Published reviews |
| `POST` | `/api/movies/{movieId}/reviews` | User | Write a review |
| `PUT` | `/api/reviews/{reviewId}` | Owner | Edit |
| `DELETE` | `/api/reviews/{reviewId}` | Owner or admin | Delete |
| `GET` | `/api/reviews/mine` | User | The user's own reviews |

Ownership is enforced in the service layer, not the controller. Reviews must be
20–5000 characters and additionally pass low-effort checks: at least 5 distinct
alphanumeric characters, and no single character exceeding 40% of the text. This
rejects `"aaaaaaaaaaaaaaaaaaaaaa"`, which a length check alone would accept.

## Watch history — `/api/history`

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/` | User | Interaction history |
| `POST` | `/movies/{movieId}` | User | Record an interaction |

Interaction types: `VIEWED_DETAILS`, `WATCHED_TRAILER`, `STARTED_WATCHING`,
`COMPLETED`, `ADDED_TO_WATCHLIST`, `RATED`.

## Profile — `/api/profile`

All require authentication.

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/` | Profile, stats and favourite genres |
| `PUT` | `/` | Update display name, avatar, biography |
| `PUT` | `/preferences` | Theme, language, adult content, favourite genres |
| `GET` | `/recommendation-history` | What has been recommended, and why |

## Admin — `/api/admin`

Every endpoint requires `ROLE_ADMIN`, enforced by both the URL-level rule in
`SecurityConfiguration` and a method-level `@PreAuthorize`.

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/statistics` | Dashboard counts and top lists |
| `GET` | `/users` | Search and page users |
| `PATCH` | `/users/{userId}/enabled` | Enable or disable an account |
| `GET` | `/reviews` | Moderation queue |
| `PATCH` | `/reviews/{reviewId}` | Set review status |
| `POST` | `/catalogue/sync` | Trigger a provider sync |
| `GET` | `/provider` | Which provider is active |

Moderation sets a review's status rather than deleting it, so a moderation
decision is reversible and the author's words are never destroyed.

## Health and metrics

| Path | Auth | Purpose |
| --- | --- | --- |
| `/actuator/health` | — | Liveness and readiness |
| `/actuator/info` | — | Build info |
| `/actuator/metrics` | Admin | Micrometer metrics |
| `/actuator/prometheus` | Admin | Prometheus scrape endpoint |

`health` shows details only to authenticated administrators. The Redis health
indicator is deliberately disabled: the cache is optional, and its absence must
not mark the service unhealthy.
