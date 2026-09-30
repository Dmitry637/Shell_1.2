#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "=========================================="
echo "       Shell Emulator - Stage 2"
echo "=========================================="
echo

mkdir -p out

echo "Compiling..."

javac -encoding UTF-8 -d out src/Shell.java

if [ $? -ne 0 ]; then
    echo
    echo "ERROR: Compilation failed!"
    exit 1
fi

echo "Compilation successful."
echo

java -cp out Shell "$@"