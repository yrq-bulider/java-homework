#!/bin/bash
# Smoke test for Member A's commands. Assumes server is already running.
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EASY_DB="$DIR/easy-db"

echo "== smoke test =="

echo -n "PING ......... "
out=$("$EASY_DB" -s ping)
[ "$out" = "PONG" ] && echo "OK" || { echo "FAIL: $out"; exit 1; }

echo -n "SET name ..... "
out=$("$EASY_DB" set name zhangsan)
[ "$out" = "OK" ] && echo "OK" || { echo "FAIL: $out"; exit 1; }

echo -n "SET user:001 . "
out=$("$EASY_DB" set user:001 alice)
[ "$out" = "OK" ] && echo "OK" || { echo "FAIL: $out"; exit 1; }

echo -n "MSET ........ "
out=$("$EASY_DB" mset k1 v1 k2 v2)
[ "$out" = "OK" ] && echo "OK" || { echo "FAIL: $out"; exit 1; }

echo -n "DEL k1 ....... "
out=$("$EASY_DB" del k1)
[ "$out" = "(integer) 1" ] && echo "OK" || { echo "FAIL: $out"; exit 1; }

echo -n "FLUSH ........ "
out=$("$EASY_DB" flush)
[ "$out" = "OK" ] && echo "OK" || { echo "FAIL: $out"; exit 1; }

echo
echo "All smoke tests passed."
