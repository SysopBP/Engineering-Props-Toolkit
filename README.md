# Engineering Props Toolkit

A Samsung and Android engineering diagnostics companion for KernelSU.

## 0.6 preview

The app now uses a dark card layout, wine-red accent, vector icons throughout, a floating five-tab navigation bar, and system-bar insets. Choose an alternate accent or Material dark appearance and adjust background brightness in Settings.

| Screen | Features |
| --- | --- |
| Overview | Device, Android version, security patch, engineering flags, verified-boot and adbd status; root refresh; quick checks; last eight session reports |
| Properties | Read-only property search by name or value; tap to copy; full inventory available in the report |
| Modules | KernelSU inventory with enabled, disabled and pending removal indicators; property-writer search; Toolkit configuration controls and backup restore |
| Diagnostics | ADB process UID and USB state, partition properties and on-disk baseline, Samsung CSC and Knox flags, SELinux denial samples, IMS package inventory, boot timeline, uptime and crash buffer |
| Settings | Themes, accent colors, background brightness, saved-baseline removal and session-report cleanup |

Reports support selectable text, copy, timestamped export to **Downloads**, and Android's share sheet. Export uses MediaStore and does not need broad storage permissions. Sharing a report also saves it in Downloads. Review device identifiers and log details before sharing.

### Engineering baseline

Save a baseline on Overview to capture the current values of the selected engineering properties. Compare reads fresh values and lists changes from that baseline. This is a diagnostic comparison, not a firmware or property restore backup. The baseline stays in app-private preferences; session report history is memory-only. Android backup is disabled.

### Module controls

Rendering profile and GPU overdraw controls support the existing `GRAPHICS_PROFILE` and `GRAPHICS_OVERDRAW` keys in:

`/data/adb/modules/engineering_props_toolkit/config.conf`

Read the configuration before editing it. Every app edit saves the previous configuration as `config.conf.app-backup`, writes through a temporary file, and verifies the selected value. Missing keys are appended; duplicate target keys are consolidated. Unrelated settings and comments are retained. Restore replaces the configuration with the backup from before the most recent app edit.

**Module changes apply when the module service next runs or after reboot.** The UI reports configured values rather than claiming a rendering change is already active. This repository contains the companion APK; it does not bundle or install a KernelSU module.

### Root and reliability

Grant the app root access in KernelSU for protected diagnostics and configuration. Each root operation checks that its actual UID is 0. Commands run off the UI thread, one operation at a time, with a 25-second process timeout. Reports drain output while storing at most 256 KiB; the screen displays up to 60,000 characters, with the captured report available through copy/export. Leaving the activity cancels its worker; late callbacks do not update a destroyed screen.

No automatic ADB restart, SELinux change, security-property rewrite, partition modification, reboot or module-service execution is performed. Reported `eng` or `ro.debuggable` values do not establish that an adbd binary supports root. Inspect the actual process UID in the ADB diagnostic.

## Build and checks

The main-branch workflow runs command-runner regression checks (success, stderr/failure, bounded large output, timeout), configuration-edit checks (backup, duplicate/missing keys, restore, invalid input, missing config), Android lint and debug APK assembly. The APK is uploaded as `EngineeringProps-Companion-debug`.

Build with Java 17, Gradle 8.11.1 and Android SDK 36:

```sh
gradle :app:lintDebug :app:assembleDebug
```

The UI and firmware behavior still need device testing. The companion retains package `com.sysopbp.engineeringprops` so it can update the previous build.

## Source rights

See [RIGHTS.md](RIGHTS.md). This repository currently grants no open-source license for original code; third-party rights remain applicable.
