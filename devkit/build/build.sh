#!/data/data/com.termux/files/usr/bin/bash

# ========================================
# AI Secretary - Local Build Script
# ========================================
#
# PURPOSE:
#   Quick local APK build for testing. Compiles resources, Java code, creates DEX,
#   and signs APK with debug keystore. TESTING ONLY - no external library support.
#
# USAGE:
#   cd ~/AI-Secretary-latest
#   devkit/build/build.sh
#
# OUTPUT:
#   - app_signed.apk in project root
#   - Compiled artifacts in compiled_res/, gen/, classes/
#
# INTEGRATION:
#   Used for quick local testing only. Production builds MUST use GitHub Actions
#   (.github/workflows/build-and-release.yml) which has full Gradle support.
#
# LIMITATIONS:
#   - No external libraries (AAR dependencies not supported)
#   - Manual resource compilation (only basic layouts)
#   - Debug signing only (not suitable for release)
#
# DOCUMENTATION:
#   - Project guide: ~/AI-Secretary-latest/CLAUDE.md
#   - Build workflow: ~/AI-Secretary-latest/.github/workflows/build-and-release.yml
#   - Home guide: ~/CLAUDE.md
#
# CREATED: 2025-11-09
# LAST UPDATED: 2025-11-13
# ========================================

set -e  # Bei Fehlern abbrechen

echo "===== AI Secretary Hello World Build ====="
echo ""

# Verzeichnisse
PROJECT_DIR="$(pwd)"
ANDROID_JAR="$PREFIX/share/android-sdk/platforms/android-33/android.jar"

# Bleibe im aktuellen Verzeichnis

# Aufräumen
echo "[1/8] Aufräumen vorheriger Builds..."
rm -rf compiled_res/*.flat gen/com classes/com *.apk *.dex 2>/dev/null || true

# Schritt 1: Ressourcen kompilieren
echo "[2/8] Kompiliere Ressourcen mit AAPT2..."
aapt2 compile \
  res/values/strings.xml \
  res/layout/activity_main.xml \
  -o compiled_res/

# Schritt 2: Ressourcen verlinken
echo "[3/8] Verlinke Ressourcen und erstelle APK..."
aapt2 link \
  -o app_unsigned.apk \
  -I "$ANDROID_JAR" \
  --manifest AndroidManifest.xml \
  -R compiled_res/*.flat \
  --java gen/ \
  --auto-add-overlay

# Schritt 3: Java kompilieren
echo "[4/8] Kompiliere Java-Code..."
# Note: build.sh is in devkit/build/, called from project root: devkit/build/build.sh
javac -source 8 -target 8 \
  -d classes/ \
  -classpath "$ANDROID_JAR" \
  -sourcepath src/:gen/ \
  $(find src/com/secretary -name "*.java") \
  gen/com/secretary/helloworld/R.java

# Schritt 4: DEX erstellen
echo "[5/8] Erstelle DEX-Datei..."
dx --dex --output=classes.dex classes/

# Schritt 5: DEX zur APK hinzufügen
echo "[6/8] Füge DEX zur APK hinzu..."
zip -uj app_unsigned.apk classes.dex

# Schritt 6: APK alignieren
echo "[7/8] Aligniere APK..."
zipalign -v -p 4 app_unsigned.apk app_aligned.apk

# Schritt 7: APK signieren
echo "[8/8] Signiere APK..."
if [ ! -f ~/.android/debug.keystore ]; then
    echo "Erstelle Debug-Keystore..."
    mkdir -p ~/.android
    keytool -genkey -v \
        -keystore ~/.android/debug.keystore \
        -alias androiddebugkey \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass android \
        -keypass android \
        -dname "CN=Android Debug,O=Android,C=US"
fi

apksigner sign \
  --ks ~/.android/debug.keystore \
  --ks-key-alias androiddebugkey \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out app_signed.apk \
  app_aligned.apk

# Verifizierung
echo ""
echo "===== Build erfolgreich! ====="
echo ""
apksigner verify -v app_signed.apk
echo ""
aapt dump badging app_signed.apk | head -5
echo ""
echo "APK Größe: $(ls -lh app_signed.apk | awk '{print $5}')"
echo "Speicherort: $PROJECT_DIR/app_signed.apk"
echo ""
echo "Installation:"
echo "1. cp app_signed.apk ~/storage/downloads/HelloWorld.apk"
echo "2. termux-open ~/storage/downloads/HelloWorld.apk"
echo ""
echo "Oder öffne die APK direkt in deinem Dateimanager."
