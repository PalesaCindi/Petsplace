#!/usr/bin/env sh
# Compiles and starts the PetPlace server (Mac / Linux).
cd "$(dirname "$0")" || exit 1
mkdir -p out
javac -encoding UTF-8 -d out src/*.java || { echo "The code did not compile."; exit 1; }
exec java -cp out Main
