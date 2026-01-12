#!/data/data/com.termux/files/usr/bin/bash

# AI Secretary - Local Build Script
# For testing only - production builds use GitHub Actions

set -e

echo "=== AI Secretary Local Build ==="

# Check Android SDK
if [ -z "$ANDROID_SDK_ROOT" ]; then
    export ANDROID_SDK_ROOT=$PREFIX/lib/android-sdk
fi

if [ ! -d "$ANDROID_SDK_ROOT" ]; then
    echo "Error: Android SDK not found at $ANDROID_SDK_ROOT"
    exit 1
fi

echo "Android SDK: $ANDROID_SDK_ROOT"

# Clean previous build
echo "Cleaning previous build..."
rm -rf build/ compiled_res/ obj/ *.apk *.apk.idsig

# Create directories
mkdir -p build/gen build/obj compiled_res

# Compile resources
echo "Compiling resources..."
$ANDROID_SDK_ROOT/build-tools/33.0.2/aapt2 compile \
    res/values/strings.xml \
    -o compiled_res/

# Link resources
echo "Linking resources..."
$ANDROID_SDK_ROOT/build-tools/33.0.2/aapt2 link \
    -I $ANDROID_SDK_ROOT/platforms/android-33/android.jar \
    --manifest AndroidManifest.xml \
    -o build/app.apk \
    --java build/gen \
    compiled_res/*.flat

# Compile Java
echo "Compiling Java..."
javac -source 8 -target 8 \
    -d build/obj \
    -classpath $ANDROID_SDK_ROOT/platforms/android-33/android.jar \
    -sourcepath src \
    src/com/secretary/MainActivity.java \
    build/gen/com/secretary/R.java

# Create DEX
echo "Creating DEX..."
$ANDROID_SDK_ROOT/build-tools/33.0.2/d8 \
    --lib $ANDROID_SDK_ROOT/platforms/android-33/android.jar \
    --output build/ \
    build/obj/com/secretary/*.class

# Add DEX to APK
echo "Adding DEX to APK..."
cd build
zip -u app.apk classes.dex
cd ..

# Align APK
echo "Aligning APK..."
$ANDROID_SDK_ROOT/build-tools/33.0.2/zipalign -f 4 build/app.apk build/app_aligned.apk

# Sign APK
echo "Signing APK..."
if [ ! -f "release.keystore" ]; then
    echo "Creating keystore..."
    keytool -genkeypair -v \
        -keystore release.keystore \
        -alias release \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass android \
        -keypass android \
        -dname "CN=AI Secretary, OU=Dev, O=ThonkTank, L=Unknown, ST=Unknown, C=US"
fi

$ANDROID_SDK_ROOT/build-tools/33.0.2/apksigner sign \
    --ks release.keystore \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out app_signed.apk \
    build/app_aligned.apk

echo ""
echo "=== Build Complete ==="
echo "Output: app_signed.apk"
ls -lh app_signed.apk
