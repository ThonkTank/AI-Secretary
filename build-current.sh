#!/data/data/com.termux/files/usr/bin/bash

# AI Secretary Build Script
# Builds current version in AI-Secretary-temp

set -e

echo "===== AI Secretary v0.0.22 Build ====="
echo ""

PROJECT_DIR="$(pwd)"
ANDROID_JAR="/data/data/com.termux/files/usr/lib/android-sdk/platforms/android-33.jar"

# Aufräumen
echo "[1/8] Aufräumen..."
rm -rf compiled_res gen classes *.apk *.dex 2>/dev/null || true
mkdir -p compiled_res gen classes

# Ressourcen kompilieren
echo "[2/8] Kompiliere Ressourcen..."
aapt2 compile \
  res/values/strings.xml \
  res/layout/activity_main.xml \
  res/layout/dialog_settings.xml \
  res/layout/dialog_logs.xml \
  res/menu/main_menu.xml \
  -o compiled_res/

# Ressourcen verlinken
echo "[3/8] Verlinke Ressourcen..."
aapt2 link \
  -o app_unsigned.apk \
  -I "$ANDROID_JAR" \
  --manifest AndroidManifest.xml \
  -R compiled_res/*.flat \
  --java gen/ \
  --auto-add-overlay

# Java kompilieren
echo "[4/8] Kompiliere Java..."
javac -source 8 -target 8 \
  -d classes/ \
  -classpath "$ANDROID_JAR" \
  -sourcepath src/:gen/ \
  src/com/secretary/MainActivity.java \
  src/com/secretary/UpdateChecker.java \
  src/com/secretary/UpdateInstaller.java \
  src/com/secretary/AppLogger.java \
  gen/com/secretary/helloworld/R.java

# DEX erstellen
echo "[5/8] Erstelle DEX..."
d8 --min-api 28 --output . classes/**/*.class

# DEX hinzufügen
echo "[6/8] Füge DEX zur APK hinzu..."
zip -uj app_unsigned.apk classes.dex

# Alignieren
echo "[7/8] Aligniere APK..."
zipalign -v -p 4 app_unsigned.apk app_aligned.apk

# Signieren
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
  --out AISecretary-v0.0.22.apk \
  app_aligned.apk

echo ""
echo "===== Build erfolgreich! ====="
echo ""
apksigner verify -v AISecretary-v0.0.22.apk
echo ""
aapt dump badging AISecretary-v0.0.22.apk | head -5
echo ""
echo "APK: $(ls -lh AISecretary-v0.0.22.apk | awk '{print $5}')"
echo "Location: $PROJECT_DIR/AISecretary-v0.0.22.apk"
