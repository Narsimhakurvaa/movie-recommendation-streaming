"""
Database behaviour tests executed against a real PostgreSQL 16 instance.

These assert the guarantees the application relies on but which live in the
schema rather than in Java: trigger-maintained aggregates, uniqueness
constraints, check constraints, cascade behaviour and index usage.
"""
from __future__ import annotations

import subprocess
import sys

from pgharness import Harness

PASS, FAIL = "\033[32mPASS\033[0m", "\033[31mFAIL\033[0m"
results: list[tuple[bool, str, str]] = []


def check(name: str, ok: bool, detail: str = "") -> None:
    results.append((ok, name, detail))
    print(f"  [{PASS if ok else FAIL}] {name}" + (f"  ({detail})" if detail and not ok else ""))


def expect_error(h: Harness, sql: str) -> str | None:
    """Run SQL that must fail; return the SQLSTATE-bearing message, else None."""
    try:
        h.query(sql)
        return None
    except subprocess.CalledProcessError as exc:
        return exc.stderr.decode()


def main() -> int:
    h = Harness()
    h.migrate()
    h.seed()
    print(f"\nPostgreSQL {h.query('SHOW server_version;')} - behavioural checks\n")

    # --- Trigger: rating aggregates ---------------------------------------
    print("[rating aggregate trigger]")
    mid = h.query("SELECT id FROM movies WHERE title = 'Dune';")
    uid = h.query("SELECT id FROM users WHERE email = 'newcomer@example.com';")
    before_avg = h.query(f"SELECT average_rating FROM movies WHERE id = {mid};")
    before_cnt = h.query(f"SELECT rating_count FROM movies WHERE id = {mid};")

    h.query(f"INSERT INTO ratings (user_id, movie_id, score) VALUES ({uid}, {mid}, 1);")
    after_cnt = h.query(f"SELECT rating_count FROM movies WHERE id = {mid};")
    check("INSERT increments rating_count",
          int(after_cnt) == int(before_cnt) + 1, f"{before_cnt}->{after_cnt}")

    expected = h.query(f"SELECT ROUND(AVG(score)::numeric,2) FROM ratings WHERE movie_id = {mid};")
    actual = h.query(f"SELECT average_rating FROM movies WHERE id = {mid};")
    check("INSERT recomputes average_rating", float(expected) == float(actual), f"{expected} vs {actual}")

    h.query(f"UPDATE ratings SET score = 5 WHERE user_id = {uid} AND movie_id = {mid};")
    expected = h.query(f"SELECT ROUND(AVG(score)::numeric,2) FROM ratings WHERE movie_id = {mid};")
    actual = h.query(f"SELECT average_rating FROM movies WHERE id = {mid};")
    check("UPDATE recomputes average_rating", float(expected) == float(actual), f"{expected} vs {actual}")

    h.query(f"DELETE FROM ratings WHERE user_id = {uid} AND movie_id = {mid};")
    final_cnt = h.query(f"SELECT rating_count FROM movies WHERE id = {mid};")
    final_avg = h.query(f"SELECT average_rating FROM movies WHERE id = {mid};")
    check("DELETE restores rating_count", final_cnt == before_cnt, f"{final_cnt} vs {before_cnt}")
    check("DELETE restores average_rating", float(final_avg) == float(before_avg))

    # A movie with zero ratings must report 0, never NULL or a stale value.
    orphan = h.query("SELECT id FROM movies WHERE rating_count = 0 LIMIT 1;")
    if orphan:
        zero_avg = h.query(f"SELECT average_rating FROM movies WHERE id = {orphan};")
        check("unrated movie has average_rating 0", float(zero_avg) == 0.0)

    # --- Constraints -------------------------------------------------------
    print("\n[constraints]")
    uid2 = h.query("SELECT id FROM users WHERE email = 'nolan.fan@example.com';")

    err = expect_error(h, f"INSERT INTO ratings (user_id, movie_id, score) VALUES ({uid2}, "
                          f"(SELECT id FROM movies WHERE title='Interstellar'), 4);")
    check("one rating per (user, movie)", err is not None and "uq_ratings_user_movie" in err)

    err = expect_error(h, f"INSERT INTO ratings (user_id, movie_id, score) VALUES ({uid}, {mid}, 9);")
    check("rating score must be 1-5", err is not None and "ck_ratings_score" in err)

    err = expect_error(h, f"INSERT INTO watchlist_items (user_id, movie_id) VALUES "
                          f"({uid2}, (SELECT id FROM movies WHERE title='Dune'));")
    check("watchlist rejects duplicates", err is not None and "uq_watchlist_user_movie" in err)

    err = expect_error(h, "INSERT INTO users (email, password_hash, display_name) VALUES "
                          "('nolan.fan@example.com', 'x', 'Dup');")
    check("email is unique", err is not None and "uq_users_email" in err)

    err = expect_error(h, "INSERT INTO users (email, password_hash, display_name) VALUES "
                          "('NOLAN.FAN@example.com', 'x', 'Dup');")
    check("email uniqueness is case-insensitive",
          err is not None and "idx_users_email_lower" in err)

    err = expect_error(h, "INSERT INTO watch_history (user_id, movie_id, interaction_type) "
                          f"VALUES ({uid}, {mid}, 'TELEPORTED');")
    check("watch_history rejects unknown interaction type",
          err is not None and "ck_watch_history_type" in err)

    err = expect_error(h, f"INSERT INTO reviews (user_id, movie_id, body) VALUES ({uid2}, "
                          "(SELECT id FROM movies WHERE title='Interstellar'), 'dup');")
    check("one review per (user, movie)", err is not None and "uq_reviews_user_movie" in err)

    err = expect_error(h, "INSERT INTO ratings (user_id, movie_id, score) VALUES (999999, 1, 3);")
    check("rating requires an existing user", err is not None and "fk_ratings_user" in err)

    # --- Cascades ----------------------------------------------------------
    print("\n[cascade behaviour]")
    h.query("INSERT INTO users (email, password_hash, display_name) VALUES "
            "('cascade.probe@example.com', 'x', 'Cascade Probe');")
    cid = h.query("SELECT id FROM users WHERE email = 'cascade.probe@example.com';")
    h.query(f"INSERT INTO ratings (user_id, movie_id, score) VALUES ({cid}, {mid}, 4);")
    h.query(f"INSERT INTO watchlist_items (user_id, movie_id) VALUES ({cid}, {mid});")
    h.query(f"INSERT INTO refresh_tokens (user_id, token_hash, expires_at) VALUES "
            f"({cid}, repeat('a',64), NOW() + INTERVAL '1 day');")
    h.query(f"DELETE FROM users WHERE id = {cid};")
    leftovers = h.query(
        f"SELECT (SELECT count(*) FROM ratings WHERE user_id={cid}) + "
        f"(SELECT count(*) FROM watchlist_items WHERE user_id={cid}) + "
        f"(SELECT count(*) FROM refresh_tokens WHERE user_id={cid});")
    check("deleting a user cascades to their data", int(leftovers) == 0, f"{leftovers} rows left")
    # And the aggregate must have been repaired by the cascade-fired trigger.
    post_cnt = h.query(f"SELECT rating_count FROM movies WHERE id = {mid};")
    check("cascade delete refreshes rating aggregate", post_cnt == before_cnt)

    # --- Index usage on the hot discovery paths ----------------------------
    print("\n[query plans]")
    h.query("ANALYZE;")
    plans = {
        "popularity sort uses index":
            ("EXPLAIN (FORMAT TEXT) SELECT id FROM movies ORDER BY popularity DESC LIMIT 20;",
             "idx_movies_popularity"),
        "case-insensitive title lookup uses index":
            ("EXPLAIN (FORMAT TEXT) SELECT id FROM movies WHERE LOWER(title) = 'dune';",
             "idx_movies_title_lower"),
        "genre reverse lookup uses index":
            ("EXPLAIN (FORMAT TEXT) SELECT movie_id FROM movie_genres WHERE genre_id = 1;",
             "idx_movie_genres_genre"),
        "user history lookup uses index":
            (f"EXPLAIN (FORMAT TEXT) SELECT * FROM watch_history WHERE user_id = {uid2} "
             "ORDER BY occurred_at DESC LIMIT 20;", "idx_watch_history_user_time"),
    }
    for name, (sql, expected_idx) in plans.items():
        plan = h.query(sql)
        # On a 61-row seed table the planner correctly prefers a sequential
        # scan. To prove the index is genuinely usable for this predicate (the
        # property that matters at production scale) we re-plan with seqscan
        # disabled. Both statements must share one session, hence one call.
        if expected_idx not in plan:
            plan = h.query("SET enable_seqscan = off; SET enable_sort = off; " + sql)
        check(name, expected_idx in plan, plan.replace("\n", " ")[:100])

    print()
    failed = [r for r in results if not r[0]]
    print(f"{len(results) - len(failed)}/{len(results)} database checks passed")
    h.cleanup()
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
