# Promptly — Android development Makefile
#
# Common dev-loop commands. Run `make` (no target) to see the help.
#
# Requirements:
#   - JDK 17 on PATH (or JAVA_HOME pointing at it)
#   - Android SDK at ANDROID_HOME
#   - adb on PATH (comes with platform-tools)
#   - One connected device or running emulator for `install` / `run`

.PHONY: help bootstrap build release install run uninstall clean \
	logcat logcat-clear test lint format check devices \
	emu emu-wipe kill-emu release release-draft release-current

# ---- Variables ----------------------------------------------------------

GRADLE      := ./gradlew
PKG         := com.promptly.app.debug
ACTIVITY    := com.promptly.app.MainActivity
ADB         := adb
EMU_AVD     ?= promptly-test
JAVA_HOME  ?= $(shell echo $$JAVA_HOME)
ANDROID_HOME ?= $(shell echo $$ANDROID_HOME)

# ---- Help (default target) ---------------------------------------------

help:
	@echo "Promptly — common commands"
	@echo ""
	@echo "Setup:"
	@echo "  make bootstrap       Run setup.sh (installs JDK/SDK if missing, generates wrapper)"
	@echo ""
	@echo "Build:"
	@echo "  make build           Assemble debug APK"
	@echo "  make release         Bump version + tag + build + publish GitHub Release"
	@echo "  make release-draft   Same as release but creates as DRAFT"
	@echo "  make release-current Print current versionCode + versionName"
	@echo "  make clean           ./gradlew clean"
	@echo "  make lint            Run Android lint"
	@echo "  make test            Run unit tests"
	@echo ""
	@echo "Install / Run:"
	@echo "  make install         Build + install on connected device/emulator"
	@echo "  make run             Install + launch the MainActivity"
	@echo "  make reinstall       Uninstall + install (force clean state)"
	@echo "  make uninstall       Remove the app from connected device"
	@echo ""
	@echo "Devices / Emulator:"
	@echo "  make devices         List connected devices/emulators"
	@echo "  make emu             Start the emulator (AVD name: $(EMU_AVD))"
	@echo "  make emu-wipe        Start emulator with -wipe-data (factory reset)"
	@echo "  make kill-emu        Kill any running emulator"
	@echo ""
	@echo "Debugging:"
	@echo "  make logcat          Stream logcat for our app only"
	@echo "  make logcat-clear    Clear logcat buffer then stream"
	@echo ""
	@echo "Combo:"
	@echo "  make fresh           clean + build + reinstall + run"
	@echo ""

# ---- Setup --------------------------------------------------------------

bootstrap:
	@./setup.sh

# ---- Build --------------------------------------------------------------

build:
	@$(GRADLE) assembleDebug --console=plain

# Release: bumps version, tags, builds signed APK, publishes GitHub Release.
# Usage: make release VERSION=1.0.1
# Requires: keystore.properties + gh CLI auth (see RELEASE.md)
release:
	@if [ -z "$(VERSION)" ]; then \
	  echo "Usage: make release VERSION=1.0.1"; \
	  exit 1; \
	fi
	@./scripts/release.sh "$(VERSION)"

# Same as release but creates a DRAFT GitHub Release (not visible to users
# until you click "Publish" in the GitHub UI).
release-draft:
	@if [ -z "$(VERSION)" ]; then \
	  echo "Usage: make release-draft VERSION=1.0.1"; \
	  exit 1; \
	fi
	@./scripts/release.sh "$(VERSION)" --draft

release-current:
	@./scripts/release.sh --current

clean:
	@$(GRADLE) clean --console=plain

lint:
	@$(GRADLE) lintDebug --console=plain

test:
	@$(GRADLE) testDebugUnitTest --console=plain

# ---- Install / Run ------------------------------------------------------

install: build
	@$(GRADLE) installDebug --console=plain
	@echo ""
	@echo "Installed on: $$($(ADB) devices | grep -v 'List of devices' | grep device | awk '{print $$1}')"

run: install
	@$(ADB) shell am start -n $(PKG)/$(ACTIVITY)

reinstall: uninstall install

uninstall:
	@$(ADB) uninstall $(PKG) 2>/dev/null || true

# ---- Devices / Emulator -------------------------------------------------

devices:
	@$(ADB) devices -l

emu:
	@echo "Starting emulator $(EMU_AVD)..."
	@$(ANDROID_HOME)/emulator/emulator -avd $(EMU_AVD) -no-snapshot-load &

emu-wipe:
	@echo "Starting emulator $(EMU_AVD) with wiped data..."
	@$(ANDROID_HOME)/emulator/emulator -avd $(EMU_AVD) -wipe-data -no-snapshot-load &

kill-emu:
	@$(ADB) emu kill 2>/dev/null || true

# ---- Debugging ----------------------------------------------------------

logcat:
	@$(ADB) logcat --pid=$$($(ADB) shell pidof -s com.promptly.app.debug 2>/dev/null || echo 0) \
	  | grep -vE '^(.* Choreographer|.* ViewRootImpl|.* EGL_emulation|.* OpenGLRenderer)'

logcat-clear:
	@$(ADB) logcat -c
	@$(MAKE) logcat

# ---- Combo --------------------------------------------------------------

fresh: clean build reinstall run
	@echo ""
	@echo "Fresh build installed and launched."
