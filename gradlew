#!/usr/bin/env sh

# Minimal gradlew stub for CI
# This is a temporary fix. Replace with real Gradle wrapper later.

if [ -x "$(command -v gradle)" ]; then
  exec gradle "$@"
else
  echo "Gradle not found. Please add the real gradlew wrapper."
  exit 1
fi
