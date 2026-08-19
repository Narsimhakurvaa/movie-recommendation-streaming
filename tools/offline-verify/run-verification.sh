#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Offline verification runner.
#
# Compiles and EXECUTES the framework-free core of the backend (the
# recommendation engine and its scoring primitives) plus the SQL schema,
# without needing Maven Central. See docs/verification.md for why this exists.
#
# Usage:  tools/offline-verify/run-verification.sh
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TOOLCHAIN="${TOOLCHAIN_HOME:-$HOME/.cache/cinevault-toolchain}"
BUILD="$ROOT/.offline-build"

cd "$ROOT"

if [ ! -x "$TOOLCHAIN/jdk/jdk4py/java-runtime/bin/java" ]; then
  echo "Toolchain missing; bootstrapping..."
  TOOLCHAIN_HOME="$TOOLCHAIN" "$ROOT/tools/offline-verify/setup-toolchain.sh"
fi

JAVA="$TOOLCHAIN/jdk/jdk4py/java-runtime/bin/java"
ECJ="$TOOLCHAIN/ecj.jar"
PYTHON="$TOOLCHAIN/pgvenv/bin/python"

ecj() {
  "$JAVA" -cp "$ECJ" org.eclipse.jdt.internal.compiler.batch.Main \
    -source 21 -target 21 -proc:none "$@"
}

echo "=============================================================="
echo " CineVault offline verification"
echo "=============================================================="
"$JAVA" -version 2>&1 | head -1

# --- 1. Syntax + convention check across the whole backend -----------------
echo
echo ">> Parsing all backend sources with a full Java grammar"
if [ ! -d tools/offline-verify/syntax/node_modules ]; then
  ( cd tools/offline-verify/syntax && npm install --silent )
fi
node tools/offline-verify/syntax/check-java-syntax.js backend/src/main/java
SYNTAX_STATUS=$?

# --- 2. Compile the framework-free engine ----------------------------------
echo
echo ">> Compiling recommendation engine (framework-free core)"
rm -rf "$BUILD"
mkdir -p "$BUILD/classes" "$BUILD/testclasses"
# Only the framework-free core: model, scoring, strategy and explain carry no
# Spring, JPA or Jackson imports and so can be compiled and executed without
# Maven Central. The dto/, service/ and web/ subpackages are deliberately
# excluded because they are framework-bound by design.
find backend/src/main/java/com/cinevault/recommendation/model \
     backend/src/main/java/com/cinevault/recommendation/scoring \
     backend/src/main/java/com/cinevault/recommendation/strategy \
     backend/src/main/java/com/cinevault/recommendation/explain \
     -name '*.java' > "$BUILD/srcs.txt"

# Guard the boundary: if a framework import ever leaks into the core, the
# offline verification would silently stop covering it. Fail loudly instead.
if grep -rlE '^import (org\.springframework|jakarta|io\.swagger|com\.fasterxml)' \
     $(cat "$BUILD/srcs.txt") 2>/dev/null | grep . ; then
  echo "   ERROR: a framework import leaked into the framework-free engine core"
  exit 1
fi
ecj -warn:+unused -d "$BUILD/classes" @"$BUILD/srcs.txt"
echo "   compiled $(wc -l < "$BUILD/srcs.txt") source files, \
$(find "$BUILD/classes" -name '*.class' | wc -l) classes"

# --- 3. Compile and run the engine verification ----------------------------
echo
echo ">> Running recommendation engine verification"
ecj -nowarn -cp "$BUILD/classes" -d "$BUILD/testclasses" \
  tools/offline-verify/MiniTest.java \
  tools/offline-verify/RecommendationEngineVerification.java
"$JAVA" -cp "$BUILD/classes:$BUILD/testclasses" tools.RecommendationEngineVerification
ENGINE_STATUS=$?

# --- 4. Database schema + behaviour ----------------------------------------
# --- 3b. Type-check the JUnit fixture against the real engine API ----------
echo
echo ">> Type-checking the JUnit test fixture against the engine"
ecj -nowarn -cp "$BUILD/classes" -d "$BUILD/fixtureclasses" \
  backend/src/test/java/com/cinevault/recommendation/support/MovieFixtures.java
echo "   MovieFixtures compiles against the engine API"

echo
echo ">> Verifying database schema against real PostgreSQL"
( cd tools/offline-verify && "$PYTHON" verify_database.py )
DB_STATUS=$?

echo
echo "=============================================================="
if [ "$SYNTAX_STATUS" -eq 0 ] && [ "$ENGINE_STATUS" -eq 0 ] && [ "$DB_STATUS" -eq 0 ]; then
  echo " ALL OFFLINE VERIFICATION PASSED"
  echo "=============================================================="
  exit 0
fi
echo " VERIFICATION FAILED (syntax=$SYNTAX_STATUS engine=$ENGINE_STATUS database=$DB_STATUS)"
echo "=============================================================="
exit 1
