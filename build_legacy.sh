#!/bin/bash
# Legacy APK Build Script using old aapt (not aapt2)
# More compatible with Termux environment

set -e

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  AI Secretary - Legacy APK Build      ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════╝${NC}"
echo ""

# Paths
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="$PROJECT_ROOT/app/src/main"
BUILD_DIR="$PROJECT_ROOT/build_legacy"
ANDROID_JAR="$HOME/android-sdk/platforms/android-28/android.jar"

# Clean
echo -e "${BLUE}[1/7] Cleaning build directory...${NC}"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"/{gen,obj,classes}
echo -e "  ${GREEN}✓${NC} Clean"

# Generate R.java with old aapt
echo -e "\n${BLUE}[2/7] Generating R.java with aapt...${NC}"
aapt package -f -m \
    -J "$BUILD_DIR/gen" \
    -M "$SRC_DIR/AndroidManifest.xml" \
    -S "$SRC_DIR/res" \
    -I "$ANDROID_JAR" \
    2>&1 | head -20

if [ ! -f "$BUILD_DIR/gen/com/aisecretary/taskmaster/R.java" ]; then
    echo -e "${RED}Failed to generate R.java${NC}"
    exit 1
fi

echo -e "  ${GREEN}✓${NC} R.java generated"

# Compile Java
echo -e "\n${BLUE}[3/7] Compiling Java sources...${NC}"
find "$SRC_DIR/java" -name "*.java" > "$BUILD_DIR/sources.txt"
JAVA_COUNT=$(wc -l < "$BUILD_DIR/sources.txt")
echo -e "  ${YELLOW}→${NC} Compiling $JAVA_COUNT Java files..."

ecj -source 1.8 -target 1.8 \
    -cp "$ANDROID_JAR" \
    -d "$BUILD_DIR/classes" \
    @"$BUILD_DIR/sources.txt" \
    "$BUILD_DIR/gen/com/aisecretary/taskmaster/R.java" \
    2>&1 | grep -E "(error|warning|compiled)" || true

if [ ! -d "$BUILD_DIR/classes/com/aisecretary/taskmaster" ]; then
    echo -e "${RED}Java compilation failed${NC}"
    exit 1
fi

echo -e "  ${GREEN}✓${NC} Java compiled"

# Convert to DEX
echo -e "\n${BLUE}[4/7] Converting to DEX...${NC}"
dx --dex \
    --output="$BUILD_DIR/classes.dex" \
    "$BUILD_DIR/classes" \
    2>&1 | grep -v "^$" | head -10 || true

echo -e "  ${GREEN}✓${NC} DEX created"

# Package APK with resources
echo -e "\n${BLUE}[5/7] Packaging resources into APK...${NC}"
aapt package -f \
    -M "$SRC_DIR/AndroidManifest.xml" \
    -S "$SRC_DIR/res" \
    -I "$ANDROID_JAR" \
    -F "$BUILD_DIR/app-unsigned.apk" \
    2>&1 | head -20 || true

echo -e "  ${GREEN}✓${NC} Resources packaged"

# Add DEX to APK
echo -e "\n${BLUE}[6/7] Adding DEX to APK...${NC}"
cd "$BUILD_DIR"
aapt add app-unsigned.apk classes.dex 2>&1 | head -5 || true
cd "$PROJECT_ROOT"

echo -e "  ${GREEN}✓${NC} DEX added"

# Sign APK
echo -e "\n${BLUE}[7/7] Signing APK...${NC}"

# Generate debug keystore if needed
KEYSTORE="$HOME/.android/debug.keystore"
mkdir -p "$HOME/.android"

if [ ! -f "$KEYSTORE" ]; then
    echo -e "  ${YELLOW}→${NC} Creating debug keystore..."
    keytool -genkeypair -v \
        -keystore "$KEYSTORE" \
        -alias androiddebugkey \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass android \
        -keypass android \
        -dname "CN=Android Debug,O=Android,C=US" \
        2>&1 | head -5
fi

# Sign
apksigner sign \
    --ks "$KEYSTORE" \
    --ks-key-alias androiddebugkey \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out "$BUILD_DIR/taskmaster-debug.apk" \
    "$BUILD_DIR/app-unsigned.apk" \
    2>&1 | head -10 || true

# Verify
if [ -f "$BUILD_DIR/taskmaster-debug.apk" ]; then
    apksigner verify "$BUILD_DIR/taskmaster-debug.apk" 2>&1 | head -5
    APK_SIZE=$(du -h "$BUILD_DIR/taskmaster-debug.apk" | cut -f1)

    echo -e "  ${GREEN}✓${NC} APK signed and verified"

    # Copy to accessible location
    cp "$BUILD_DIR/taskmaster-debug.apk" /storage/emulated/0/Download/taskmaster-debug.apk 2>/dev/null || true

    echo ""
    echo -e "${GREEN}╔════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║         BUILD SUCCESSFUL! 🎉           ║${NC}"
    echo -e "${GREEN}╚════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "📦 ${BLUE}APK Location:${NC}"
    echo -e "   $BUILD_DIR/taskmaster-debug.apk"
    echo -e "   ${GREEN}ALSO COPIED TO:${NC}"
    echo -e "   /storage/emulated/0/Download/taskmaster-debug.apk"
    echo ""
    echo -e "📊 ${BLUE}APK Size:${NC} $APK_SIZE"
    echo ""
    echo -e "🚀 ${BLUE}Installation:${NC}"
    echo -e "   1. Open Files/Downloads app"
    echo -e "   2. Tap 'taskmaster-debug.apk'"
    echo -e "   3. Allow 'Install from Unknown Sources' if needed"
    echo -e "   4. Tap 'Install'"
    echo ""
else
    echo -e "${RED}✗ APK signing failed${NC}"
    exit 1
fi
