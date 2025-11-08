#!/bin/bash
# Complete APK Build Script for AI Secretary
# Builds a signed, installable APK using Termux tools

set -e  # Exit on error

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   AI Secretary - APK Build Script     ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════╝${NC}"
echo ""

# Project paths
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$PROJECT_ROOT/app"
SRC_DIR="$APP_DIR/src/main"
BUILD_DIR="$PROJECT_ROOT/build"
OUTPUT_DIR="$BUILD_DIR/outputs"

# Android SDK
ANDROID_JAR="$PREFIX/lib/android-sdk/platforms/android-35/android.jar"

# Check prerequisites
echo -e "${BLUE}[1/8] Checking prerequisites...${NC}"
MISSING=0

for tool in aapt2 ecj dx apksigner zipalign; do
    if ! which $tool > /dev/null 2>&1; then
        echo -e "  ${RED}✗${NC} $tool not found"
        MISSING=1
    else
        echo -e "  ${GREEN}✓${NC} $tool"
    fi
done

if [ ! -f "$ANDROID_JAR" ]; then
    echo -e "  ${RED}✗${NC} android.jar not found"
    MISSING=1
else
    echo -e "  ${GREEN}✓${NC} android.jar"
fi

if [ $MISSING -eq 1 ]; then
    echo -e "${RED}Missing prerequisites. Exiting.${NC}"
    exit 1
fi

# Clean build directory
echo -e "\n${BLUE}[2/8] Cleaning build directory...${NC}"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"/{gen,obj,classes,compiled_res}
mkdir -p "$OUTPUT_DIR"
echo -e "  ${GREEN}✓${NC} Build directory cleaned"

# Compile resources
echo -e "\n${BLUE}[3/8] Compiling resources...${NC}"
aapt2 compile \
    --dir "$SRC_DIR/res" \
    -o "$BUILD_DIR/compiled_res/" \
    2>&1 | grep -v "^$"

echo -e "  ${GREEN}✓${NC} Resources compiled"

# Link resources
echo -e "\n${BLUE}[4/8] Linking resources...${NC}"
aapt2 link \
    -I "$ANDROID_JAR" \
    --manifest "$SRC_DIR/AndroidManifest.xml" \
    -o "$BUILD_DIR/app.apk.unaligned" \
    --java "$BUILD_DIR/gen" \
    -R "$BUILD_DIR/compiled_res/*.flat" \
    --auto-add-overlay \
    2>&1 | grep -v "^$"

echo -e "  ${GREEN}✓${NC} Resources linked"

# Compile Java sources
echo -e "\n${BLUE}[5/8] Compiling Java sources...${NC}"
find "$SRC_DIR/java" -name "*.java" > "$BUILD_DIR/sources.txt"
JAVA_FILE_COUNT=$(wc -l < "$BUILD_DIR/sources.txt")
echo -e "  ${YELLOW}→${NC} Found $JAVA_FILE_COUNT Java files"

ecj -source 1.8 -target 1.8 \
    -cp "$ANDROID_JAR" \
    -d "$BUILD_DIR/classes" \
    @"$BUILD_DIR/sources.txt" \
    "$BUILD_DIR/gen/com/aisecretary/taskmaster/R.java" \
    2>&1 | head -50

if [ ${PIPESTATUS[0]} -ne 0 ]; then
    echo -e "${RED}Java compilation failed${NC}"
    exit 1
fi

echo -e "  ${GREEN}✓${NC} Java sources compiled"

# Convert to DEX
echo -e "\n${BLUE}[6/8] Converting to DEX format...${NC}"
dx --dex \
    --output="$BUILD_DIR/classes.dex" \
    "$BUILD_DIR/classes" \
    2>&1 | grep -v "^$"

echo -e "  ${GREEN}✓${NC} DEX files created"

# Add DEX to APK
echo -e "\n${BLUE}[7/8] Packaging APK...${NC}"
cd "$BUILD_DIR"
cp app.apk.unaligned app.apk.unsigned
cd "$PROJECT_ROOT"

# Add classes.dex to APK
aapt add "$BUILD_DIR/app.apk.unsigned" "$BUILD_DIR/classes.dex" 2>&1 | grep -v "^$"

echo -e "  ${GREEN}✓${NC} APK packaged"

# Sign APK
echo -e "\n${BLUE}[8/8] Signing APK...${NC}"
apksigner sign \
    --ks-pass pass:android \
    --out "$OUTPUT_DIR/taskmaster-debug.apk" \
    "$BUILD_DIR/app.apk.unsigned" \
    2>&1 | grep -v "^$"

# Verify signature
apksigner verify "$OUTPUT_DIR/taskmaster-debug.apk" 2>&1

APK_SIZE=$(du -h "$OUTPUT_DIR/taskmaster-debug.apk" | cut -f1)

echo -e "  ${GREEN}✓${NC} APK signed and verified"

# Summary
echo ""
echo -e "${GREEN}╔════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║         BUILD SUCCESSFUL! 🎉           ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════╝${NC}"
echo ""
echo -e "📦 ${BLUE}APK Location:${NC}"
echo -e "   $OUTPUT_DIR/taskmaster-debug.apk"
echo ""
echo -e "📊 ${BLUE}APK Size:${NC} $APK_SIZE"
echo ""
echo -e "🚀 ${BLUE}Installation:${NC}"
echo -e "   Option 1: termux-media-scan $OUTPUT_DIR/taskmaster-debug.apk"
echo -e "   Option 2: Find APK in Downloads and tap to install"
echo ""
echo -e "📍 ${BLUE}Quick Copy to Downloads:${NC}"
echo -e "   cp $OUTPUT_DIR/taskmaster-debug.apk /storage/emulated/0/Download/"
echo ""
