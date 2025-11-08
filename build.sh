#!/bin/bash
# Build script for AI Secretary Android App
# Builds APK directly using Android Build Tools (no Gradle needed)

set -e  # Exit on error

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== AI Secretary Build Script ===${NC}"

# Project directories
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$PROJECT_ROOT/app"
SRC_DIR="$APP_DIR/src/main"
BUILD_DIR="$PROJECT_ROOT/build"
OUTPUT_DIR="$BUILD_DIR/outputs"

# Create build directories
echo -e "${BLUE}Creating build directories...${NC}"
mkdir -p "$BUILD_DIR/gen"
mkdir -p "$BUILD_DIR/obj"
mkdir -p "$BUILD_DIR/classes"
mkdir -p "$OUTPUT_DIR"

# Android SDK paths
ANDROID_JAR="$PREFIX/lib/android-sdk/platforms/android-35/android.jar"

# Check if Android platform is available
if [ ! -f "$ANDROID_JAR" ]; then
    echo -e "${RED}Error: Android platform JAR not found${NC}"
    echo "You need to download the Android SDK platform files"
    echo "For now, we'll create a placeholder build configuration"
    exit 1
fi

# Step 1: Compile resources
echo -e "${BLUE}Step 1: Compiling resources...${NC}"
aapt2 compile \
    --dir "$SRC_DIR/res" \
    -o "$BUILD_DIR/compiled_resources.zip"

# Step 2: Link resources
echo -e "${BLUE}Step 2: Linking resources...${NC}"
aapt2 link \
    -I "$ANDROID_JAR" \
    --manifest "$SRC_DIR/AndroidManifest.xml" \
    -o "$BUILD_DIR/app.apk.res" \
    --java "$BUILD_DIR/gen" \
    "$BUILD_DIR/compiled_resources.zip"

# Step 3: Compile Java sources
echo -e "${BLUE}Step 3: Compiling Java sources...${NC}"
find "$SRC_DIR/java" -name "*.java" > "$BUILD_DIR/sources.txt"
javac -source 1.8 -target 1.8 \
    -bootclasspath "$ANDROID_JAR" \
    -d "$BUILD_DIR/classes" \
    @"$BUILD_DIR/sources.txt" \
    "$BUILD_DIR/gen/com/aisecretary/taskmaster/R.java"

# Step 4: Convert to DEX
echo -e "${BLUE}Step 4: Converting to DEX format...${NC}"
# Note: d8 tool would be used here, but it's not available in basic termux
# We'll document this step for now
echo -e "${RED}Note: DEX conversion requires d8 tool from Android SDK${NC}"
echo "This will be set up in the next phase"

# For now, create a placeholder APK
echo -e "${BLUE}Creating placeholder build info...${NC}"
cat > "$OUTPUT_DIR/BUILD_INFO.txt" <<EOF
AI Secretary Build Information
================================

Build Date: $(date)
Project: AI Secretary - Taskmaster
Version: 1.0.0
Package: com.aisecretary.taskmaster

Status: Partial build completed
- Resources compiled: ✓
- Resources linked: ✓
- Java sources compiled: ✓
- DEX conversion: Requires Android SDK tools
- APK packaging: Pending DEX files

Next Steps:
1. Install Android SDK command-line tools
2. Use d8 for DEX conversion
3. Package and sign APK

Source files ready at: $PROJECT_ROOT
EOF

echo -e "${GREEN}=== Build Info Created ===${NC}"
echo -e "Build information saved to: $OUTPUT_DIR/BUILD_INFO.txt"
echo ""
echo -e "${BLUE}Current Status:${NC}"
echo "✓ Project structure created"
echo "✓ Java sources compiled"
echo "✓ Resources processed"
echo "⚠ Full APK build requires additional Android SDK tools"
echo ""
echo -e "${GREEN}You can now start developing the app!${NC}"
echo "Java source files are in: $SRC_DIR/java/com/aisecretary/taskmaster/"
