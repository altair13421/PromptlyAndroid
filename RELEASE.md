# Releasing Promptly (GitHub Releases)

> Complete guide: from "the app works on my phone" to "downloadable APK on your GitHub repo's Releases page".

No Play Store involved. Users download the APK straight from your GitHub Releases page and sideload it. This is the simplest possible release flow for an indie / open-source Android app.

---

## Part 1 — One-time setup (do this before your first release)

### Step 1: Generate a release keystore

Every release APK must be signed with a cryptographic key. Without one, Android refuses to install the APK (and refuses to upgrade an installed app unless the new APK is signed with the same key as the old one).

**Lose this keystore = users can't upgrade.** Back it up to multiple safe places.

```bash
# Pick a location OUTSIDE the repo so it can never be committed.
mkdir -p ~/.android/keystores
cd ~/.android/keystores

# Generate the keystore — replace the dname / passwords with your own.
keytool -genkeypair \
  -v \
  -keystore promptly-release.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias promptly \
  -dname "CN=Your Name, OU=Mobile, O=Your Org, L=Karachi, ST=Sindh, C=PK" \
  -storepass YOUR_STORE_PASSWORD \
  -keypass YOUR_KEY_PASSWORD

# Verify
keytool -list -v -keystore promptly-release.jks -storepass YOUR_STORE_PASSWORD
```

**Back it up now:**
- USB stick in a drawer
- Encrypted copy in a password manager (1Password / Bitwarden)
- Print the passwords on paper, lock in a safe

### Step 2: Create `keystore.properties` in the project root

This file is gitignored — never committed. It points Gradle at your keystore.

```bash
cat > /path/to/Promptly/keystore.properties << 'EOF'
# Local-only — NEVER COMMIT. Already in .gitignore.
storeFile=/home/youruser/.android/keystores/promptly-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=promptly
keyPassword=YOUR_KEY_PASSWORD
EOF

chmod 600 /path/to/Promptly/keystore.properties
```

### Step 3: Add the same secrets to GitHub (for CI-built releases)

If you want CI to build signed releases automatically, add the keystore as a repo secret:

1. **Base64-encode the keystore:**
   ```bash
   base64 -w 0 ~/.android/keystores/promptly-release.jks > keystore.b64
   ```

2. **In your GitHub repo:** Settings → Secrets and variables → Actions → New repository secret. Add these four secrets:

   | Name | Value |
   |---|---|
   | `PROMPTLY_KEYSTORE_BASE64` | contents of `keystore.b64` |
   | `PROMPTLY_STORE_PASSWORD` | your store password |
   | `PROMPTLY_KEY_ALIAS` | `promptly` |
   | `PROMPTLY_KEY_PASSWORD` | your key password |

   Then delete `keystore.b64` locally — it's now in GitHub.

3. The CI workflow decodes the base64 back into a keystore file on every release-tag push, signs the APK, and attaches it to a GitHub Release.

### Step 4: Install the `gh` CLI (for local releases)

The `scripts/release.sh` script uses `gh` to create the GitHub Release + upload the APK. One-time setup:

```bash
# macOS
brew install gh

# Ubuntu/Debian
sudo apt install gh

# Arch / CachyOS
sudo pacman -S github-cli

# Then sign in (opens a browser)
gh auth login
```

Verify:
```bash
gh auth status
```

---

## Part 2 — Every release (do this for v1.0.0, v1.0.1, etc.)

### The easy way: `./scripts/release.sh 1.0.1`

One command does everything:

1. Bumps `versionCode` (+1) and `versionName` (to `1.0.1`) in `app/build.gradle.kts`
2. Commits the bump
3. Tags the commit as `v1.0.1` and pushes it
4. Builds the signed release APK
5. Creates a GitHub Release titled `v1.0.1`
6. Uploads the APK + the R8 mapping file as release assets
7. Prints the public URL of the release

```bash
./scripts/release.sh 1.0.1
```

That's it. Users can now download the APK from:
```
https://github.com/YOUR_USERNAME/Promptly/releases/tag/v1.0.1
```

### The manual way (if you want to control each step)

```bash
# 1. Bump version in app/build.gradle.kts:
#    versionCode = 2
#    versionName = "1.0.1"

# 2. Commit + tag
git add app/build.gradle.kts
git commit -m "Release v1.0.1"
git tag -a v1.0.1 -m "Release v1.0.1"
git push origin main
git push origin v1.0.1

# 3. Build the signed release APK
./gradlew assembleRelease

# 4. The APK lands at:
#    app/build/outputs/apk/release/app-release.apk

# 5. Create a GitHub Release + attach the APK
gh release create v1.0.1 \
  app/build/outputs/apk/release/app-release.apk \
  --title "v1.0.1" \
  --notes "What's new:
- Fixed the snooze button sometimes not advancing recurring reminders
- Improved widget refresh on app cold-start"
```

### What to put in release notes

A good release note has three sections:

```markdown
## What's new
- New: per-reminder ringtone override (see Settings → Sound)
- New: 4 bundled tones (chime, bell, marimba, warm chord)

## Fixes
- Fixed: snooze button on lock-screen notification now works for recurring reminders
- Fixed: widget not refreshing after device reboot

## Known issues
- Notification sound may not play on devices with aggressive battery optimization (workaround: whitelist Promptly in Settings → Battery)
```

---

## How users install the APK

GitHub Releases gives users a downloadable `.apk` file. They have to **sideload** it:

1. On their Android phone, open the GitHub Releases URL in a browser
2. Tap `app-release.apk` to download
3. Tap "Open" in the download notification
4. Android shows a permission dialog: "For your security, your phone is not allowed to install unknown apps from this source"
5. Tap Settings → enable "Allow from this source"
6. Tap Install
7. App icon appears in launcher

**For subsequent releases** — Android will allow the upgrade silently (no "unknown source" prompt) because the new APK is signed with the same key as the installed one. This is why keeping the keystore safe matters: without it, users can't upgrade.

---

## How the CI release pipeline works (optional but recommended)

The `.github/workflows/release.yml` workflow triggers automatically when you push a `v*` tag. It:

1. Checks out the code at the tag
2. Sets up JDK 17 + Android SDK
3. Decodes `PROMPTLY_KEYSTORE_BASE64` secret back into a `.jks` file
4. Sets the env vars (`PROMPTLY_STORE_PASSWORD`, etc.) so Gradle can sign
5. Runs `./gradlew assembleRelease`
6. Runs `gh release create` to create the GitHub Release
7. Uploads the signed APK + mapping.txt as assets

This means **once you've set up the secrets**, the entire release flow is:

```bash
./scripts/release.sh 1.0.1 --no-build   # just bump + tag + push
# CI does the rest — appears at https://github.com/.../releases/tag/v1.0.1 in ~5 min
```

The `--no-build` flag tells the script to skip the local build and let CI handle it. Useful if your local machine is slow or you want the build to be reproducible.

---

## Emergency: "I lost my keystore"

If you lose the keystore file:
- **Existing installs CANNOT be upgraded** by you. Android requires the upgrade APK to be signed with the same key.
- Users have to **uninstall the old app first**, then install the new signed APK. They lose all their reminder data (unless they made a JSON backup).
- Going forward, generate a new keystore and use that for all future releases.

There's no recovery path — Google's Play Console has a "key upgrade" feature for this exact scenario, but GitHub Releases sideloaders don't have that lifeline. **Back up your keystore.**

---

## Quick reference — release checklist

```
[ ] Tested the latest main branch on a real device
[ ] Ran ./gradlew lintDebug  (no new errors)
[ ] Decided on a version number following semver (1.0.0 → 1.0.1 for fixes, 1.1.0 for features)
[ ] Ran ./scripts/release.sh 1.0.1
   OR
[ ]   Bumped versionCode + versionName in app/build.gradle.kts
[ ]   git commit + git tag v1.0.1 + git push --tags
[ ]   ./gradlew assembleRelease
[ ]   gh release create v1.0.1 app/build/outputs/apk/release/app-release.apk
[ ] Verified the release shows up at https://github.com/YOU/Promptly/releases
[ ] Downloaded the APK on a test phone + installed + verified it works
[ ] Backed up the keystore (if not done recently)
```

---

## Where things live

| File / Path | Purpose |
|---|---|
| `~/.android/keystores/promptly-release.jks` | Your release signing key — **never commit, back up offsite** |
| `keystore.properties` (project root) | Local-only pointer to the keystore — gitignored |
| `app/build.gradle.kts` | Reads `keystore.properties` OR env vars; wires up `signingConfigs.release` |
| `app/proguard-rules.pro` | R8 keep rules — release builds use these |
| `scripts/release.sh` | Automates: bump → tag → push → build → `gh release create` |
| `app/build/outputs/apk/release/app-release.apk` | The signed APK — uploaded to GitHub Releases |
| `app/build/outputs/mapping/release/mapping.txt` | R8 obfuscation map — useful for crash deobfuscation |
| `.github/workflows/release.yml` | CI workflow that builds + publishes releases on tag push |
