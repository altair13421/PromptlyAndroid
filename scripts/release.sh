#!/usr/bin/env bash
#
# release.sh — bump version, tag, build signed release APK, publish a GitHub Release.
#
# Usage:
#   ./scripts/release.sh 1.0.1                # bump + tag + push + build + gh release create
#   ./scripts/release.sh 1.0.1 --no-push      # don't push the tag/branch
#   ./scripts/release.sh 1.0.1 --no-build     # bump + tag + push only; let CI build
#   ./scripts/release.sh 1.0.1 --no-release   # bump + tag + build, don't create gh release
#   ./scripts/release.sh 1.0.1 --draft        # create as a DRAFT GitHub release (not public)
#   ./scripts/release.sh 1.0.1 --prerelease   # create as a PRE-RELEASE
#   ./scripts/release.sh --current            # print current version
#   ./scripts/release.sh --help
#
# What it does (in order):
#   1. Validates the version string (must be X.Y.Z)
#   2. Reads current versionCode + versionName from app/build.gradle.kts
#   3. Bumps versionCode by 1, sets versionName to the arg
#   4. Commits the bump with a "Release vX.Y.Z" message
#   5. Tags the commit as vX.Y.Z (annotated tag)
#   6. Pushes main + the tag (unless --no-push)
#   7. Builds the signed release APK (unless --no-build)
#      ─ requires keystore.properties or PROMPTLY_* env vars
#      ─ see RELEASE.md for setup
#   8. Creates a GitHub Release via `gh release create` (unless --no-release)
#      ─ attaches app-release.apk + mapping.txt
#      ─ supports --draft / --prerelease flags
#   9. Prints the public URL of the release

set -euo pipefail

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

info() { echo -e "${GREEN}✓${NC} $1"; }
warn() { echo -e "${YELLOW}!${NC} $1"; }
die()  { echo -e "${RED}✗${NC} $1"; exit 1; }
step() { echo -e "\n${BLUE}▶${NC} $1"; }

# Resolve project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

BUILD_GRADLE="app/build.gradle.kts"

# --- Subcommand: --help ----------------------------------------------------

if [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
  grep -E '^#(\s|$)' "$0" | sed 's/^#//'
  exit 0
fi

# --- Subcommand: --current -------------------------------------------------

if [ "${1:-}" = "--current" ]; then
  CURRENT_CODE=$(grep -E '^\s*versionCode' "$BUILD_GRADLE" | head -1 | grep -oE '[0-9]+')
  CURRENT_NAME=$(grep -E '^\s*versionName' "$BUILD_GRADLE" | head -1 | grep -oE '"[^"]+"' | tr -d '"')
  echo "versionCode = $CURRENT_CODE"
  echo "versionName = $CURRENT_NAME"
  exit 0
fi

# --- Parse args ------------------------------------------------------------

if [ $# -lt 1 ]; then
  die "Usage: $0 <version-name> [flags]. Run $0 --help for details."
fi

NEW_VERSION="$1"
shift

PUSH_TAG=1
BUILD_APK=1
CREATE_RELEASE=1
RELEASE_VISIBILITY=""   # "" / --draft / --prerelease

while [ $# -gt 0 ]; do
  case "$1" in
    --no-push)    PUSH_TAG=0;          shift ;;
    --no-build)   BUILD_APK=0;         shift ;;
    --no-release) CREATE_RELEASE=0;    shift ;;
    --draft)      RELEASE_VISIBILITY="--draft";       shift ;;
    --prerelease) RELEASE_VISIBILITY="--prerelease";  shift ;;
    *) die "Unknown arg: $1" ;;
  esac
done

# --- Validate version name -------------------------------------------------

if ! echo "$NEW_VERSION" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+$'; then
  die "Version must be X.Y.Z (e.g. 1.0.1). Got: $NEW_VERSION"
fi

# --- Sanity checks ---------------------------------------------------------

[ -f "$BUILD_GRADLE" ] || die "Not in project root (can't find $BUILD_GRADLE)."
command -v ./gradlew >/dev/null 2>&1 || die "gradlew not found. Run ./setup.sh first."

if [ $BUILD_APK -eq 1 ] && [ ! -f "keystore.properties" ]; then
  warn "keystore.properties not found — release APK will be UNSIGNED."
  warn "See RELEASE.md → 'One-time setup' to enable signing."
fi

if [ $CREATE_RELEASE -eq 1 ]; then
  command -v gh >/dev/null 2>&1 || die "gh CLI not found. Install: https://cli.github.com/"
  gh auth status >/dev/null 2>&1 || die "gh not authenticated. Run: gh auth login"
fi

# --- Read current version --------------------------------------------------

CURRENT_CODE=$(grep -E '^\s*versionCode' "$BUILD_GRADLE" | head -1 | grep -oE '[0-9]+')
CURRENT_NAME=$(grep -E '^\s*versionName' "$BUILD_GRADLE" | head -1 | grep -oE '"[^"]+"' | tr -d '"')

if [ -z "$CURRENT_CODE" ] || [ -z "$CURRENT_NAME" ]; then
  die "Could not read version from $BUILD_GRADLE"
fi

info "Current: v$CURRENT_NAME (code $CURRENT_CODE)"
info "New:      v$NEW_VERSION (code $((CURRENT_CODE + 1)))"

# --- Confirm before proceeding ---------------------------------------------

echo ""
read -r -p "Proceed? [y/N] " CONFIRM
if [ "$CONFIRM" != "y" ] && [ "$CONFIRM" != "Y" ]; then
  die "Aborted."
fi

# --- Bump version ----------------------------------------------------------

step "Bumping version in $BUILD_GRADLE"

sed -i.bak -E \
  -e "s/^(\s*versionCode\s*=\s*)[0-9]+/\1$((CURRENT_CODE + 1))/" \
  -e "s/^(\s*versionName\s*=\s*)\"[^\"]+\"/\1\"$NEW_VERSION\"/" \
  "$BUILD_GRADLE"
rm -f "$BUILD_GRADLE.bak"

# Verify
VERIFY_CODE=$(grep -E '^\s*versionCode' "$BUILD_GRADLE" | head -1 | grep -oE '[0-9]+')
VERIFY_NAME=$(grep -E '^\s*versionName' "$BUILD_GRADLE" | head -1 | grep -oE '"[^"]+"' | tr -d '"')
if [ "$VERIFY_CODE" != "$((CURRENT_CODE + 1))" ] || [ "$VERIFY_NAME" != "$NEW_VERSION" ]; then
  die "Version bump failed verification. Check $BUILD_GRADLE manually."
fi
info "Bumped to v$NEW_VERSION (code $VERIFY_CODE)"

# --- Git commit + tag ------------------------------------------------------

step "Committing + tagging"

git add "$BUILD_GRADLE"
git commit -m "Release v$NEW_VERSION

- versionCode: $CURRENT_CODE -> $((CURRENT_CODE + 1))
- versionName: $CURRENT_NAME -> $NEW_VERSION" >/dev/null

TAG="v$NEW_VERSION"
git tag -a "$TAG" -m "Release $TAG"
info "Committed + tagged as $TAG"

if [ $PUSH_TAG -eq 1 ]; then
  if git remote get-url origin >/dev/null 2>&1; then
    CURRENT_BRANCH=$(git branch --show-current 2>/dev/null || echo "")
    if [ -z "$CURRENT_BRANCH" ]; then
      warn "Could not detect current branch. Skipping branch push."
    else
      info "Detected branch: $CURRENT_BRANCH"
      git push origin "$CURRENT_BRANCH"
    fi
    git push origin "$TAG"
    info "Pushed to origin"
  else
    warn "No 'origin' remote — skipping push. Run: git remote add origin <url>"
  fi
else
  warn "Skipping push (--no-push). Don't forget:"
  warn "  git push origin main && git push origin $TAG"
fi

# --- Build release APK -----------------------------------------------------

APK_PATH="app/build/outputs/apk/release/app-release.apk"
MAPPING_PATH="app/build/outputs/mapping/release/mapping.txt"

if [ $BUILD_APK -eq 1 ]; then
  step "Building release APK"
  if ./gradlew assembleRelease --console=plain; then
    [ -f "$APK_PATH" ] || die "Build reported success but APK not found at $APK_PATH"
    SIZE=$(du -h "$APK_PATH" | cut -f1)
    info "APK ready: $APK_PATH ($SIZE)"
    [ -f "$MAPPING_PATH" ] && info "Mapping: $MAPPING_PATH"
  else
    die "Release build failed. See errors above."
  fi
fi

# --- Create GitHub Release -------------------------------------------------

if [ $CREATE_RELEASE -eq 1 ]; then
  step "Creating GitHub Release"

  # Build release notes — pull from CHANGELOG.md if present, else use a default.
  NOTES_FILE="$(mktemp)"
  trap 'rm -f "$NOTES_FILE"' EXIT

  if [ -f "CHANGELOG.md" ]; then
    # Extract the section for this version from CHANGELOG.md
    awk -v ver="$NEW_VERSION" '
      /^## \[?v?[0-9]/ { capture = ($0 ~ "v?" ver) }
      capture { print }
    ' CHANGELOG.md > "$NOTES_FILE"
  fi

  if [ ! -s "$NOTES_FILE" ]; then
    cat > "$NOTES_FILE" <<EOF
Release v$NEW_VERSION

## What's new
- (Add your release notes here)

## Fixes
-

## Known issues
-
EOF
  fi

  # Assemble the gh release create command
  GH_ARGS=(
    release create "$TAG"
    --title "$TAG"
    --notes-file "$NOTES_FILE"
  )

  if [ -n "$RELEASE_VISIBILITY" ]; then
    GH_ARGS+=("$RELEASE_VISIBILITY")
  fi

  # Attach assets
  if [ $BUILD_APK -eq 1 ] && [ -f "$APK_PATH" ]; then
    GH_ARGS+=("$APK_PATH")
    [ -f "$MAPPING_PATH" ] && GH_ARGS+=("$MAPPING_PATH")
  fi

  if gh "${GH_ARGS[@]}"; then
    info "GitHub Release created"
    echo ""
    # Print the URL
    REPO_URL=$(gh repo view --json url -q .url 2>/dev/null || echo "")
    if [ -n "$REPO_URL" ]; then
      echo "  ${REPO_URL}/releases/tag/${TAG}"
    fi
  else
    die "gh release create failed."
  fi
fi

# --- Done ------------------------------------------------------------------

step "Done"
echo ""
info "Released v$NEW_VERSION"
if [ $BUILD_APK -eq 1 ]; then
  echo "  APK:        $APK_PATH"
fi
if [ $CREATE_RELEASE -eq 1 ]; then
  echo "  Releases:   https://github.com/$(gh repo view --json nameWithOwner -q .nameWithOwner)/releases"
fi
echo ""
if [ $BUILD_APK -eq 0 ] && [ $CREATE_RELEASE -eq 1 ]; then
  echo "  CI will build the APK and attach it to the release shortly."
fi
