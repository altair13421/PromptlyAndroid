# Promptly

> Friendly reminders, on time.

Promptly is a Kotlin + Jetpack Compose reminder app for Android 10+ (API 29+). Built around three ideas: reminders fire **exactly on time**, every reminder can carry **a picture** so you remember *what* to pick up, and the whole thing is **friendly and minimal** — dark mode, custom ringtones, no clutter.

---

## Quick start (no Android Studio required)

```bash
# 1. Bootstrap the project (installs JDK 17 + Android SDK if missing,
#    generates the Gradle wrapper, writes local.properties, runs first build)
./setup.sh

# 2. Plug in a phone (USB debugging on) or start an emulator
make emu                  # starts the emulator named in $EMU_AVD

# 3. Install + launch
make run
```

If you already have JDK 17 + the Android SDK installed, `setup.sh` will skip the install steps and just verify everything is in place.

## Common commands (Makefile)

Run `make` (no target) to see the help. The most-used targets:

| Command | What it does |
|---|---|
| `make build` | `./gradlew assembleDebug` — produces the APK |
| `make install` | Build + install on connected device |
| `make run` | Install + launch MainActivity |
| `make reinstall` | Uninstall + install (forces clean app state) |
| `make fresh` | clean + build + reinstall + run (use when something's weird) |
| `make clean` | `./gradlew clean` |
| `make logcat` | Stream logcat, filtered to our app's PID only |
| `make logcat-clear` | Clear the logcat buffer, then stream |
| `make devices` | `adb devices -l` — list connected devices/emulators |
| `make emu` | Start the emulator (set `EMU_AVD=your-avd-name` to pick which one) |
| `make emu-wipe` | Start emulator with `-wipe-data` (factory reset) |
| `make kill-emu` | Kill the running emulator |
| `make lint` | Run Android lint |
| `make test` | Run unit tests |

The Makefile is just sugar over `./gradlew` + `adb` — read it to see exactly what each target does.

## CI (GitHub Actions)

A workflow lives at `.github/workflows/ci.yml`. It runs on every push and pull request to `main`/`master` and:

1. Sets up JDK 17 + Android SDK 35
2. Caches Gradle dependencies (~3× speedup on subsequent runs)
3. Runs `./gradlew assembleDebug` — fails the build if the project doesn't compile
4. Runs `lintDebug` — currently non-blocking (`continue-on-error: true`) so existing warnings don't gate CI
5. Runs `testDebugUnitTest` — currently a no-op since we have no tests yet
6. Uploads the debug APK as a downloadable artifact (kept for 14 days)
7. Uploads lint HTML reports as artifacts (kept for 7 days)

After every push you'll find the built APK in the GitHub Actions run page → Artifacts section. Useful for sharing a build with a tester without giving them build tooling.

To enable CI on your fork: just push the `.github/` folder to GitHub. Nothing else to configure.

## Releasing

Releases are GitHub Releases — users download the APK from your repo's Releases page. No Play Store.

```bash
# One-time setup: generate a keystore + add keystore.properties (see RELEASE.md)
make release VERSION=1.0.1
#  → bumps version, tags v1.0.1, builds signed APK, publishes GitHub Release

make release-draft VERSION=1.0.1
#  → same but creates as DRAFT (not public until you click Publish in GitHub)

make release-current
#  → prints current versionCode + versionName
```

The full guide (keystore setup, CI integration, manual flow, emergency recovery) lives in [`RELEASE.md`](RELEASE.md).

---


## What's in the box

### Core features
- **Create reminders** with title, notes, time, recurrence, and an optional attached picture
- **Recurring reminders** — once / daily / weekly (with weekday picker) / monthly / every-N-days custom interval
- **Snooze** — built into the notification itself (`Snooze 10m` action) AND the detail screen
- **Done** — built into the notification AND the detail screen. For recurring reminders, "Done" advances to the next occurrence automatically.
- **Custom ringtones** — bundle tones in `app/src/main/res/raw/` or import audio files from the reminder editor
- **Per-reminder ringtone override** + **per-reminder vibration override**
- **Dark mode** — follow system / always on / always off (Material 3)
- **Material You dynamic colors** on Android 12+ (toggle in Settings)
- **Settings screen** with all the above

### Things you'd forget to plan
- **Home-screen widget** showing the next 3 active reminders
- **Lock screen notification actions** — Done + Snooze right from the lock screen
- **Boot receiver** — re-arms every alarm after device reboot AND fires anything that became overdue while the phone was off
- **JSON backup / restore** — reminders, attached pictures, and imported reminder sounds in a single `.zip`
- **Battery-optimization whitelist** helper — Android's Doze mode will silently defer your alarms otherwise
- **Exact-alarm permission** helper — Android 12+ requires explicit user opt-in for `setAlarmClock`
- **Notification permission** runtime request — Android 13+
- **Missed-reminder handling** — the boot receiver re-fires anything overdue
- **Time-zone-aware trigger times** — stored as UTC millis + Olson TZ id, formatted in user's local tz
- **Live alarm re-arming** — a Flow watcher in `PromptlyApplication` re-arms AlarmManager automatically whenever the set of active reminders changes; no callsite has to remember to call `scheduler.schedule(...)`

### Tech stack
| Concern | Choice |
|---|---|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose (Material 3, Compose BOM 2024.10) |
| Architecture | MVVM — `ViewModel` + `StateFlow` + Repository |
| DI | Hilt |
| Storage | Room (DB) + app-private files dir (pictures) + DataStore (settings) |
| Scheduling | `AlarmManager.setAlarmClock` (most reliable for user-visible alarms — exempt from Doze) |
| Background work | `WorkManager` (currently unused; available for future sync) |
| Image loading | Coil |
| Serialization | kotlinx.serialization |
| Min SDK | 29 (Android 10) |
| Target SDK | 35 (Android 15) |

---

## How to build

### Prerequisites
- **Android Studio Ladybug (2024.2.1) or newer** — older versions may not support AGP 8.7 + Kotlin 2.0
- **JDK 17** — bundled with Android Studio, or install separately
- **Internet connection** — Gradle needs to download dependencies on first build

### Steps

1. **Open Android Studio → `Open` → select the `Promptly/` folder** (the one containing `settings.gradle.kts`). Android Studio will:
   - Generate the Gradle wrapper JAR (`gradle/wrapper/gradle-wrapper.jar`)
   - Download dependencies
   - Run a Gradle sync

2. **(Optional) Bundle ringtone files.** Put royalty-free audio files in `app/src/main/res/raw/` using lowercase names with underscores (for example, `soft_chime.ogg`). Add each filename without its extension to `bundledResources` in `RingtoneHelper.kt`. Imported sounds are available from each reminder's sound picker and are copied into app storage.

3. **Plug in an Android device** (Android 10+) or start an emulator.

4. **Click ▶ Run.** The first build will take 2–5 minutes.

### Build from the command line
```bash
cd Promptly
./gradlew assembleDebug          # produces app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # installs on a connected device
```

---

## Project structure

```
Promptly/
├── settings.gradle.kts              # Module declarations
├── build.gradle.kts                 # Root Gradle file
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml           # Version catalog (single source of truth for deps)
│   └── wrapper/
│       └── gradle-wrapper.properties
└── app/
    ├── build.gradle.kts             # App module config
    ├── proguard-rules.pro
    ├── playstore_icon.png           # 512×512 hi-res icon
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/promptly/app/
        │   ├── PromptlyApplication.kt     # Hilt graph + live-arm watcher
        │   ├── MainActivity.kt            # Single-activity, hosts Compose nav
        │   ├── data/
        │   │   ├── db/                    # Room: PromptlyDatabase, ReminderDao, Converters
        │   │   ├── model/                 # ReminderEntity, ReminderStatus, RecurrenceRule
        │   │   ├── repository/            # ReminderRepository, SettingsRepository
        │   │   ├── storage/               # PictureStorage (app-private dir)
        │   │   ├── backup/                # BackupManager (zip export/restore)
        │   │   └── ringtone/              # RingtoneHelper (bundled + device)
        │   ├── di/
        │   │   └── AppModule.kt           # Hilt @Provides
        │   ├── notifications/
        │   │   ├── NotificationHelper.kt  # Channels + notification builder
        │   │   ├── ReminderScheduler.kt   # AlarmManager arming + Flow watcher
        │   │   ├── ReminderReceiver.kt    # Receives alarm fire
        │   │   ├── DoneActionReceiver.kt  # Notif "Done" button
        │   │   ├── SnoozeActionReceiver.kt # Notif "Snooze 10m" button
        │   │   └── BootReceiver.kt        # Re-arms alarms after reboot
        │   ├── widget/
        │   │   ├── PromptlyWidgetProvider.kt
        │   │   └── WidgetUpdater.kt
        │   ├── ui/
        │   │   ├── theme/                 # Color.kt, Theme.kt, Type.kt
        │   │   ├── navigation/            # PromptlyNavHost.kt
        │   │   ├── components/            # ReminderCard, EmptyState
        │   │   └── screens/
        │   │       ├── home/              # Home + 4-tab filter
        │   │       ├── create/            # Create / Edit reminder
        │   │       ├── detail/            # Detail view + Done/Snooze/Delete
        │   │       └── settings/          # Appearance, Sound, Data, About
        │   └── util/                      # TimeUtils, Permissions, Extensions
        └── res/
            ├── drawable/                  # Vector icons (launcher fg/bg, brand, empty-state)
            ├── mipmap-*/                  # PNG launcher icons at all densities
            ├── mipmap-anydpi-v26/         # Adaptive icon XMLs
            ├── values/                    # colors, strings, themes (light)
            ├── values-night/              # themes (dark)
            ├── xml/                       # backup rules, file_paths, widget_info
            ├── raw/                       # Drop ringtone .mp3s here (see README.txt)
            └── layout/
                └── widget_reminder.xml    # RemoteViews layout for the widget
```

---

## How reminders actually fire on time

Android is hostile to background work. We use the most reliable mechanism available:

1. **AlarmManager.setAlarmClock()** — the same API used by the system Clock app. Exempt from Doze. Shows an alarm icon in the status bar. Survives app force-stop (until reboot).
2. **BootReceiver** — re-arms every alarm after device reboot. Fires anything that became overdue.
3. **Live Flow watcher** in `PromptlyApplication` — every DB mutation anywhere in the app triggers `ReminderScheduler.reArmAll()` automatically. No callsite can forget to re-arm.
4. **Per-reminder sound channels** — Android 8+ requires a separate notification channel for each sound; Promptly creates channels per sound and vibration combination.
5. **Lock screen action buttons** — Done / Snooze work without unlocking the phone.

### Permission flow on first launch
- **Android 10–12:** Post-notification permission is implicit. We do prompt the user once to whitelist the app from battery optimization (Doze).
- **Android 13+:** Runtime `POST_NOTIFICATIONS` prompt. Plus the user must manually grant `SCHEDULE_EXACT_ALARM` via a system settings page we deep-link to.
- The `Permissions` util in `util/Permissions.kt` has helpers for all three.

---

## Recurrence rules

Implemented in `data/model/RecurrenceRule.kt` + `util/TimeUtils.kt#nextOccurrence()`:

| Frequency | Behavior |
|---|---|
| `NONE` | Fires once, done. |
| `DAILY` | Fires every N days at the same local time. |
| `WEEKLY` | Fires every N weeks on the selected weekdays (bitmask Sun–Sat). |
| `MONTHLY` | Fires every N months on the same day-of-month. |
| `CUSTOM_DAYS` | Alias for DAILY with a custom interval (for the "every 3 days" picker choice). |

`endDate` and `maxOccurrences` are supported but not yet exposed in the UI — wire them up in `CreateReminderScreen` if you want caps.

---

## Backup format

A `.zip` containing:
- `meta.json` — `{ "appVersion", "schemaVersion", "createdAt" }`
- `reminders.json` — full list of `ReminderEntity` rows (including recurrence rule + picture path)
- `pictures/<uuid>.jpg` — each attached picture

Restore:
1. Wipe current reminders table
2. Restore pictures to `filesDir/pictures/`
3. Re-insert reminders with picture paths remapped to the new locations
4. The live-arm watcher picks up the new rows and arms fresh alarms

---

## Known TODOs (deliberately not in v1)

- `WorkManager` is on the classpath but unused. Could be wired up for periodic "is everything still armed?" safety checks.
- Backup export currently writes to `cacheDir`. UI should share it via `FileProvider` + `ACTION_SEND`. The `file_paths.xml` is already configured.
- Wear OS module — not included (per the user's MVP scope).
- Cloud sync — not included (per the user's MVP scope).
- `endDate` and `maxOccurrences` not exposed in UI.
- `NotificationCompat.BigPictureStyle` with decoded bitmap — currently using `BigTextStyle`. A future enhancement could decode the attached picture and show it big in the notification shade.

---

## Unit tests — what they'd cover (and don't yet)

The project ships with no tests. Here's what would be worth covering, ranked by ROI (return on investment = bug-catching power ÷ effort to write):

### Tier 1 — pure functions, easy wins, high bug-catching power

**`TimeUtils.formatTrigger(atMillis)`** — the "Today, 3:00 PM" / "Tomorrow, 8:00 AM" / "Mon, Mar 5, 9:30 AM" formatter.
- **What it would do:** feed a known millis timestamp, assert the formatted string.
- **What it would catch:** off-by-one in "today vs tomorrow" boundary (especially around midnight), locale-specific date format regressions, AM/PM formatting bugs, year-boundary cases ("Dec 31, 2026" vs "Jan 1, 2027").
- **Tricky part:** the formatter uses the device's default timezone. Tests would need to pin `TimeZone.setDefault(TimeZone.getTimeZone("UTC"))` in a `@Before` block to be deterministic.

**`TimeUtils.nextOccurrence(fromMillis, rule, now)`** — the recurrence engine.
- **What it would do:** given a known start time + recurrence rule + "now" reference, assert the next firing time.
- **What it would catch:** daily-interval edge cases (every 1 day vs every 3 days), weekly weekday-mask skips (e.g. "Mon/Wed/Fri" jumping over weekends correctly), monthly day-of-month rollover at end of month (Feb 28 → Mar 28, but Feb 31 doesn't exist), DST transitions (2 AM fires twice or zero times in fall/spring), `endDate` and `maxOccurrences` exhaustion paths, the safety-valve limit (365 attempts max).
- **Tricky part:** same timezone pinning as above. Also, "now" is a parameter so it's testable — but you'd want to test the case where `fromMillis < now` (should skip to the next future occurrence, not return a past time).

**`TimeUtils.todayRangeUtc()`** — the "today from midnight to midnight in user's tz, expressed as UTC millis" calculator.
- **What it would do:** mock the device timezone, assert the returned `[start, end)` pair.
- **What it would catch:** timezone conversion bugs that would cause the "Today" tab to show reminders from yesterday or tomorrow.

### Tier 2 — logic that's mostly pure but touches one Android thing

**`RecurrenceRule` parsing/serialization** — the JSON round-trip through Room's `Converters`.
- **What it would do:** construct a `RecurrenceRule`, serialize via the converter, deserialize, assert equality.
- **What it would catch:** schema migration bugs (if you add a field to `RecurrenceRule`, old DB rows with the old JSON should still deserialize), `ignoreUnknownKeys = true` regression.

**`BackupManager` round-trip** — export to zip → import from zip → assert reminders match.
- **What it would do:** construct a few `ReminderEntity` rows with attached picture files, export, wipe in-memory state, restore, assert the rows match (with picture paths remapped).
- **What it would catch:** zip-entry path bugs (the `pictures/` prefix handling), JSON schema drift between export and import, picture path remapping logic.
- **Tricky part:** needs a `@Before` that creates a temp dir for `PictureStorage` to use, plus a Robolectric context (or `ApplicationProvider.getApplicationContext()`) so Room + DataStore can run on the JVM without a device.

### Tier 3 — UI tests (Compose), high effort, lower ROI for solo project

**`CreateReminderViewModel` state transitions** — title change, time change, validation errors, save.
- **What it would do:** instantiate the VM with a fake repository, drive state changes via `onTitleChange` etc., assert the resulting `uiState`.
- **What it would catch:** validation logic (empty title, past time), state-merge bugs in `_uiState.update`, save vs update branching.

**`ReminderCard` composable snapshot** — render with a sample reminder, snapshot-test the result.
- **What it would do:** use `createComposeRule()` + a snapshot library (e.g. `paparazzi`), render the card, compare against a golden PNG.
- **What it would catch:** layout regressions, color tint regressions (urgent vs calm), text truncation bugs.
- **Tricky part:** snapshot tests are flaky across OS/font version changes. Worth it only when the design has stabilized.

### Tier 4 — instrumentation tests (need a device/emulator)

**`ReminderScheduler` end-to-end** — schedule a reminder 5 seconds out, assert the notification fires.
- **What it would do:** use `@RunWith(AndroidJUnit4)`, schedule a reminder, sleep 6 seconds, assert `NotificationManager.getActiveNotifications()` contains our reminder.
- **What it would catch:** AlarmManager arming bugs, boot receiver logic, recurrence advancement on fire.
- **Tricky part:** flaky on emulators (Doze can defer alarms even with `setAlarmClock`), needs `POST_NOTIFICATIONS` permission grant via `grantRuntimePermission` in test setup.

### What we'd skip (not worth the effort)

- **Hilt DI graph tests** — if Hilt compiles, the graph is valid. Adding `@HiltAndroidTest` for the sake of it is ceremony.
- **DataStore tests** — DataStore is rock-solid library code; testing our wrapper just tests the library.
- **Room DAO tests** — Room generates the implementation from our `@Query` annotations. If the SQL is wrong, it fails at compile time. DAO tests would mostly re-test Room itself.

### Suggested starter set (if you ever do add tests)

If you ever want to add tests, start with **just `TimeUtilsTest`** — it covers the two highest-ROI pure functions in the whole codebase (`formatTrigger` + `nextOccurrence`), needs no Android context, no fakes, no Robolectric, runs in milliseconds. About 50–80 lines of code, catches the bugs that would actually bite users (wrong "today" calculation, recurrence skipping the wrong day).

---

## License

This project is provided as-is for the user to do with as they please.
