"""
Offline PostgreSQL harness.

Boots a real PostgreSQL 16 server (via the `pgserver` wheel, the only route to a
database in a sandbox with no apt/Docker access) and applies the project's
Flyway migration scripts in version order using `psql -v ON_ERROR_STOP=1`.

This exists purely to VERIFY the SQL in `backend/src/main/resources/db/migration`
outside of a full Maven build. Real deployments run these same files through
Flyway. Because the files are plain versioned SQL with no Flyway-specific
placeholders, applying them with psql is faithful to what Flyway executes.
"""
from __future__ import annotations

import pathlib
import subprocess
import sys
import tempfile

import pgserver

# The wheel ships its own PostgreSQL build; locate the bundled client binaries.
POSTGRES_BIN_PATH = pathlib.Path(pgserver.__file__).parent / "pginstall" / "bin"

# Anchored to the repository root so the harness works from any CWD.
REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
MIGRATIONS = REPO_ROOT / "backend/src/main/resources/db/migration"
SEEDS = REPO_ROOT / "backend/src/main/resources/db/seed"


class Harness:
    def __init__(self, keep: bool = False) -> None:
        self.dir = tempfile.mkdtemp(prefix="cinevault-pg-")
        self.server = pgserver.get_server(self.dir)
        self.uri = self.server.get_uri()
        self.keep = keep

    def run_sql_file(self, path: pathlib.Path) -> None:
        """Execute a .sql file, aborting on the first error."""
        subprocess.run(
            [str(POSTGRES_BIN_PATH / "psql"), self.uri,
             "-v", "ON_ERROR_STOP=1", "--quiet", "-f", str(path)],
            check=True, capture_output=True,
        )

    def query(self, sql: str, tuples_only: bool = True) -> str:
        args = [str(POSTGRES_BIN_PATH / "psql"), self.uri, "-v", "ON_ERROR_STOP=1", "--quiet"]
        if tuples_only:
            args += ["-t", "-A", "-F", "|"]
        out = subprocess.run(args + ["-c", sql], check=True, capture_output=True)
        return out.stdout.decode().strip()

    def migrate(self) -> list[str]:
        applied = []
        for f in sorted(MIGRATIONS.glob("V*.sql"), key=lambda p: int(p.name.split("__")[0][1:])):
            try:
                self.run_sql_file(f)
                applied.append(f.name)
            except subprocess.CalledProcessError as exc:
                print(f"  \033[31mFAILED\033[0m  {f.name}")
                print(exc.stderr.decode())
                raise SystemExit(1)
        return applied

    def seed(self) -> list[str]:
        applied = []
        for f in sorted(SEEDS.glob("*.sql")):
            try:
                self.run_sql_file(f)
                applied.append(f.name)
            except subprocess.CalledProcessError as exc:
                print(f"  \033[31mFAILED\033[0m  {f.name}")
                print(exc.stderr.decode())
                raise SystemExit(1)
        return applied

    def cleanup(self) -> None:
        if not self.keep:
            try:
                self.server.cleanup()
            except Exception:
                pass


def main() -> int:
    h = Harness()
    print(f"PostgreSQL {h.query('SHOW server_version;')}  ->  {h.dir}")
    print("\n[migrations]")
    for name in h.migrate():
        print(f"  \033[32mapplied\033[0m  {name}")

    if SEEDS.exists() and any(SEEDS.glob("*.sql")):
        print("\n[seed data]")
        for name in h.seed():
            print(f"  \033[32mapplied\033[0m  {name}")

    print("\n[schema]")
    tables = h.query(
        "SELECT table_name FROM information_schema.tables "
        "WHERE table_schema='public' ORDER BY table_name;")
    for t in tables.splitlines():
        cols = h.query(
            "SELECT count(*) FROM information_schema.columns "
            f"WHERE table_schema='public' AND table_name='{t}';")
        rows = h.query(f'SELECT count(*) FROM "{t}";')
        print(f"  {t:<26} cols={cols:<4} rows={rows}")

    idx = h.query("SELECT count(*) FROM pg_indexes WHERE schemaname='public';")
    fks = h.query("SELECT count(*) FROM information_schema.table_constraints "
                  "WHERE constraint_schema='public' AND constraint_type='FOREIGN KEY';")
    cks = h.query("SELECT count(*) FROM information_schema.table_constraints "
                  "WHERE constraint_schema='public' AND constraint_type='UNIQUE';")
    print(f"\n  indexes={idx}  foreign_keys={fks}  unique_constraints={cks}")
    h.cleanup()
    return 0


if __name__ == "__main__":
    sys.exit(main())
