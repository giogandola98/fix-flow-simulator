#!/usr/bin/env bash
# FIX Flow Simulator launcher — opens a terminal if run via double-click
# Usage: ./launch.sh [java-jar-path]

JAR="${1:-fix-flow-api/target/fix-flow-api-*.jar}"
JAR=$(ls $JAR 2>/dev/null | sort -V | tail -1)

if [ -z "$JAR" ]; then
  echo "ERROR: No JAR found. Run 'mvn clean package -DskipTests' first." >&2
  exit 1
fi

# If stdin is NOT a TTY, we were double-clicked — re-launch inside a terminal
if [ ! -t 0 ]; then
  if command -v gnome-terminal &>/dev/null; then
    gnome-terminal -- bash -c "java -jar \"$JAR\"; read -p 'Press Enter to close...'"
  elif command -v xterm &>/dev/null; then
    xterm -e bash -c "java -jar \"$JAR\"; read -p 'Press Enter to close...'"
  elif command -v x-terminal-emulator &>/dev/null; then
    x-terminal-emulator -e bash -c "java -jar \"$JAR\"; read -p 'Press Enter to close...'"
  else
    java -jar "$JAR"
  fi
else
  java -jar "$JAR"
fi
