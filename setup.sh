#!/usr/bin/env bash
#
# setup.sh — bootstrap the Promptly Android project on a fresh machine.
#
# What it does, in order:
#   1. Verifies JDK 17 is installed (and JAVA_HOME is set)
#   2. Verifies / installs the Android SDK command-line tools
#   3. Installs required SDK packages: platform-tools, platforms;android-35, build-tools;35.0.0
#   4. Accepts all SDK licenses
#   5. Generates the Gradle wrapper JAR (gradlew + gradlew.bat + gradle-wrapper.jar)
#      — this is what Android Studio normally does for you on first open
#   6. Writes local.properties with the SDK path (gitignored, never committed)
#   7. Runs ./gradlew assembleDebug to verify everything works
#
# Tested on:
#   - macOS 14+ (Homebrew)
#   - Ubuntu 22.04+ / Arch / CachyOS
#   - Windows 11 under WSL2 (Ubuntu)
#
# Usage:
#   chmod +x setup.sh
#   ./setup.sh
#
# Re-running is safe — each step is idempotent.

set -euo pipefail

# Pretty colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}✓${NC} $1"; }
warn()  { echo -e "${YELLOW}!${NC} $1"; }
die()   { echo -e "${RED}✗${NC} $1"; exit 1; }

# Detect OS
OS="$(uname -s)"
case "$OS" in
  Darwin*) OS_MAC=1 ;;
  Linux*)  OS_LINUX=1 ;;
  MINGW*|MSYS*) OS_WIN=1 ;;
  *) die "Unsupported OS: $OS" ;;
esac

# Resolve project root (directory this script lives in)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ========================================================================
# 1. JDK 17 check
# ========================================================================
echo ""
echo "=== Step 1/7: Verifying JDK 17 ==="

if ! command -v java >/dev/null 2>&1; then
  warn "Java not found. Install JDK 17:"
  echo "  macOS:   brew install openjdk@17"
  echo "  Ubuntu:  sudo apt install openjdk-17-jdk"
  echo "  Arch:    sudo pacman -S jdk17-openjdk"
  die "JDK 17 required (AGP 8.7 needs Java 17)."
fi

JAVA_VER="$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)"
if [ "$JAVA_VER" != "17" ]; then
  warn "Found Java $JAVA_VER, but AGP 8.7 wants exactly Java 17."
  warn "Things may still work — proceeding anyway. If you hit weird errors, install JDK 17."
fi

if [ -z "${JAVA_HOME:-}" ]; then
  # Try to auto-detect
  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home)"
  else
    export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(which java)")")")"
  fi
  warn "JAVA_HOME was not set; using auto-detected: $JAVA_HOME"
fi
info "JAVA_HOME=$JAVA_HOME"

# ========================================================================
# 2. Android SDK check + install cmdline-tools if missing
# ========================================================================
echo ""
echo "=== Step 2/7: Verifying Android SDK ==="

# Pick a default SDK location if ANDROID_HOME is not set
if [ -z "${ANDROID_HOME:-}" ]; then
  if [ -n "${OS_MAC:-}" ]; then
    ANDROID_HOME="$HOME/Library/Android/Sdk"
  elif [ -n "${OS_LINUX:-}" ]; then
    ANDROID_HOME="$HOME/Android/Sdk"
  else
    ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"
  fi
  warn "ANDROID_HOME was not set; defaulting to $ANDROID_HOME"
fi
export ANDROID_HOME
mkdir -p "$ANDROID_HOME"
info "ANDROID_HOME=$ANDROID_HOME"

CMDLINE_TOOLS_DIR="$ANDROID_HOME/cmdline-tools/latest"
SDKMANAGER="$CMDLINE_TOOLS_DIR/bin/sdkmanager"

if [ ! -x "$SDKMANAGER" ]; then
  warn "cmdline-tools not found. Downloading..."

  TMP_ZIP="$(mktemp -d)/cmdline-tools.zip"
  if [ -n "${OS_MAC:-}" ]; then
    URL="https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip"
  elif [ -n "${OS_LINUX:-}" ]; then
    URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  else
    URL="https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
  fi

  echo "  Downloading $URL"
  if command -v wget >/dev/null 2>&1; then
    wget -q -O "$TMP_ZIP" "$URL"
  elif command -v curl >/dev/null 2>&1; then
    curl -fsSL -o "$TMP_ZIP" "$URL"
  else
    die "Neither wget nor curl found. Install one and re-run."
  fi

  mkdir -p "$ANDROID_HOME/cmdline-tools"
  unzip -q "$TMP_ZIP" -d "$ANDROID_HOME/cmdline-tools"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$CMDLINE_TOOLS_DIR"
  rm -f "$TMP_ZIP"
  info "cmdline-tools installed."
else
  info "cmdline-tools already present."
fi

# Add platform-tools (adb) to PATH for this script
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/emulator:$PATH"

# ========================================================================
# 3. Install required SDK packages
# ========================================================================
echo ""
echo "=== Step 3/7: Installing SDK packages ==="

PACKAGES=(
  "platform-tools"
  "platforms;android-35"
  "build-tools;35.0.0"
)

for pkg in "${PACKAGES[@]}"; do
  if "$SDKMANAGER" --list_installed 2>/dev/null | grep -q "$pkg"; then
    info "$pkg already installed."
  else
    echo "  Installing $pkg..."
    "$SDKMANAGER" "$pkg" >/dev/null 2>&1
    info "$pkg installed."
  fi
done

# ========================================================================
# 4. Accept licenses
# ========================================================================
echo ""
echo "=== Step 4/7: Accepting SDK licenses ==="

if yes | "$SDKMANAGER" --licenses >/dev/null 2>&1; then
  info "All licenses accepted."
else
  warn "License acceptance failed — you may need to run sdkmanager --licenses manually."
fi

# ========================================================================
# 5. Generate Gradle wrapper
# ========================================================================
echo ""
echo "=== Step 5/7: Generating Gradle wrapper ==="

# gradle-wrapper.properties already exists in the repo — that's just the
# version pin. We need the binary gradle-wrapper.jar + the gradlew scripts,
# which are normally generated by `gradle wrapper`.

if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
  warn "gradle-wrapper.jar missing. Generating..."

  if command -v gradle >/dev/null 2>&1; then
    gradle wrapper --gradle-version 8.10.2 --distribution-type bin
    chmod +x gradlew
    info "Wrapper generated via system gradle."
  else
    # No system gradle — download the wrapper jar directly
    warn "System gradle not found. Downloading gradle-wrapper.jar directly."
    WRAPPER_JAR_URL="https://raw.githubusercontent.com/gradle/gradle/v8.10.2/gradle/wrapper/gradle-wrapper.jar"
    mkdir -p gradle/wrapper
    if command -v wget >/dev/null 2>&1; then
      wget -q -O gradle/wrapper/gradle-wrapper.jar "$WRAPPER_JAR_URL"
    elif command -v curl >/dev/null 2>&1; then
      curl -fsSL -o gradle/wrapper/gradle-wrapper.jar "$WRAPPER_JAR_URL"
    fi

    # And the gradlew scripts
    curl -fsSL -o gradlew "https://raw.githubusercontent.com/gradle/gradle/v8.10.2/gradlew"
    curl -fsSL -o gradlew.bat "https://raw.githubusercontent.com/gradle/gradle/v8.10.2/gradlew.bat"
    chmod +x gradlew
    info "Wrapper downloaded."
  fi
else
  info "gradle-wrapper.jar already present."
fi

# ========================================================================
# 6. local.properties
# ========================================================================
echo ""
echo "=== Step 6/7: Writing local.properties ==="

cat > local.properties <<EOF
# Auto-generated by setup.sh — DO NOT COMMIT
sdk.dir=$ANDROID_HOME
EOF
info "local.properties written."

# ========================================================================
# 7. First build to verify
# ========================================================================
echo ""
echo "=== Step 7/7: Verifying with ./gradlew assembleDebug ==="

echo "  (First build downloads ~250MB of dependencies — this can take 5–10 minutes.)"
if ./gradlew assembleDebug --console=plain; then
  echo ""
  info "Build succeeded! APK at: app/build/outputs/apk/debug/app-debug.apk"
  echo ""
  echo "Next steps:"
  echo "  make run        # install + launch on a connected device"
  echo "  make help       # see all available commands"
else
  die "Build failed. See errors above."
fi
