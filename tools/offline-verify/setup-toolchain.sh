#!/usr/bin/env bash
# Bootstraps a Java toolchain + PostgreSQL in environments WITHOUT access to
# Maven Central / apt / Docker registries (only PyPI + npm reachable).
#
#   * JRE 25 (Temurin)  <- PyPI  "jdk4py"
#   * Eclipse ECJ       <- npm   "@ctxo/lang-java-analyzer" (bundles jdt core)
#   * PostgreSQL 16     <- PyPI  "pgserver"
#
# This is ONLY a sandbox/offline verification aid. Normal development uses
# `./mvnw verify` with a real JDK 21 and Maven Central. See README.
set -euo pipefail
ROOT="${TOOLCHAIN_HOME:-$HOME/.cache/cinevault-toolchain}"
mkdir -p "$ROOT"; cd "$ROOT"
if [ ! -x "$ROOT/jdk/jdk4py/java-runtime/bin/java" ]; then
  pip3 download jdk4py --no-deps -q -d whl
  python3 -m zipfile -e whl/*.whl jdk
  chmod -R u+x jdk/jdk4py/java-runtime/bin
fi
if [ ! -f "$ROOT/ecj.jar" ]; then
  npm pack @ctxo/lang-java-analyzer >/dev/null 2>&1
  tar xzf ctxo-lang-java-analyzer-*.tgz
  cp package/jar/ctxo-jdt-analyzer.jar ecj.jar
fi
if [ ! -x "$ROOT/pgvenv/bin/python" ]; then
  python3 -m venv pgvenv
  ./pgvenv/bin/pip -q install pgserver
fi
echo "JAVA_HOME=$ROOT/jdk/jdk4py/java-runtime"
echo "ECJ_JAR=$ROOT/ecj.jar"
echo "PGVENV=$ROOT/pgvenv"
