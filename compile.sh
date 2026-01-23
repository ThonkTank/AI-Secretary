#!/bin/bash
# Kompiliert alle Java-Dateien nach bin/

cd "$(dirname "$0")"
mkdir -p bin

javac -d bin \
  -sourcepath src \
  -cp ".:sqlite-jdbc.jar:slf4j-api.jar:slf4j-simple.jar" \
  src/data/*.java \
  src/entities/*.java \
  src/repository/*.java \
  src/repository/parser/*.java \
  src/controller/*.java \
  src/usecases/**/*.java \
  2>&1

if [ $? -eq 0 ]; then
  echo "Build erfolgreich"
else
  echo "Build fehlgeschlagen"
  exit 1
fi
