# AGENTS.md

## Purpose

This repository contains PadelScore, an offline padel scoring application for
round Huawei watch displays. These instructions apply to every coding agent
working anywhere in the repository.

## Read first

Before changing code, read:

1. `README.md` for product scope and compatibility status.
2. `docs/USER_GUIDE.md` for user-visible behavior.
3. `docs/DEPLOYMENT.md` before building, signing, or touching a watch.
4. `git status --short` and the relevant diff. Preserve unrelated and
   pre-existing user changes.

The only device verified end to end is a retail HUAWEI WATCH 4 Pro MDS-AL00
with a 466×466 display. Do not claim support for another model without device
evidence.

## Runtime variants and source map

The repository intentionally maintains two implementations:

- `compat-android/`: platform-only Java APK. This is the variant installed on
  the verified retail WATCH 4 Pro firmware.
- `entry/`: HarmonyOS/OpenHarmony API 8 FA HAP using JS/HML/CSS. It builds and
  signs successfully, but the verified retail firmware lacks the HAP gateway
  container required to install it.
- `shared/`: typed match engine and persistence sources used as the logical
  reference for the HAP variant.

Important files:

| Concern | HAP/shared | Compatibility APK |
|---|---|---|
| Scoring rules | `shared/engine/*.ts`, mirrored JS under `entry/` | `compat-android/src/com/vekom/padelprobe/MatchEngine.java` and `ScoringStrategy.java` |
| Model/defaults | `shared/engine/Types.ts`, `Defaults.ts`, mirrored JS | `MatchModel.java` |
| Persistence | `shared/persistence/*`, mirrored JS | `MatchStore.java` |
| Watch UI | `entry/src/main/js/MainAbility/pages/index/` | `MainActivity.java` |
| Tests | `tests/*.test.ts` | `compat-android/test/.../EngineSelfTest.java` |

Keep equivalent behavior synchronized between variants unless the task is
explicitly platform-specific. Never silently update only one scoring engine.

## Product invariants

Preserve these behaviors unless the user explicitly requests a change:

- offline operation; do not add network permissions or analytics;
- automatic state persistence and `Resume Match`;
- no data loss during ordinary upgrades;
- Undo, serve tracking, Americano round history, and all documented modes;
- portrait orientation and layout designed on a 466×466 circular canvas;
- large readable text and touch targets near the circular safe area;
- rotary scrolling and the app-owned left-to-right back gesture;
- the score screen keeps the display awake, including after match completion;
- non-score screens may use the normal system screen timeout;
- system navigation, calls, critical alerts, and deliberate app switching must
  not be trapped by kiosk-like behavior.

## Validation

Run focused tests while iterating and the full relevant set before handoff.

```powershell
npm test
.\compat-android\test.ps1 -JavaHome '<path-to-jdk>'
```

The current expected baseline is 40 Node tests and at least 138 Java engine
assertions. If the count changes intentionally, explain why.

For a release or device handoff, use the configured full workflow:

```powershell
.\scripts\deploy-watch.ps1
```

At minimum, verify:

- both test suites pass;
- APK/HAP builds requested by the task succeed;
- APK package ID and signer match the local deployment configuration;
- `git diff --check` reports no whitespace errors;
- the smoke screenshot is legible and not clipped by the round display;
- state restoration still works when persistence code changes.

## Deployment and device safety

Deployment changes external device state. Confirm that installation is within
the user's request and verify the exact target before sending files.

- Copy `watch-deploy.example.psd1` to ignored
  `watch-deploy.local.psd1`; never commit the local file.
- Never hardcode a user's IP address, username, SDK path, passwords, certificate
  digest, or device serial in tracked files.
- Use the `HdcExternal.exe` configured for the device. On the verified firmware,
  OpenHarmony API 8 `hdc.exe` can reach the TCP port but cannot establish the
  required session.
- If the watch is unreachable, ask the user to wake it, confirm Wi-Fi/HDB, and
  accept any pairing prompt. Do not weaken security settings.
- Never uninstall the package to work around
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE` without explicit approval. Uninstalling
  deletes saved match data. Restore the correct signing key instead.
- Do not change `com.vekom.padelprobe` or its signing identity without a written
  migration plan.
- Use the repository deployment script; it creates a one-shot HDB key, installs
  with `pm install -r`, expires the key, removes temporary files, and captures a
  smoke screenshot.
- Do not use root, disable package verification, or modify secure settings.
- Do not commit build outputs, SDKs, keystores, certificates, provisioning
  profiles, generated local configs, or passwords.

## Adapting to another watch model

Do not assume that another Huawei watch accepts the same APK or HAP. Before
modifying or installing, collect and record:

```powershell
$hdc = '<path-to-compatible-HdcExternal.exe>'
$target = '<watch-ip>:5555'

& $hdc list targets -v
& $hdc -t $target shell "getprop ro.product.model"
& $hdc -t $target shell "getprop ro.product.name; getprop ro.build.version.sdk"
& $hdc -t $target shell "wm size; wm density"
```

Then determine:

1. Whether the firmware exposes the Android application container, an HAP
   gateway, or neither.
2. Whether developer mode and wireless HDB/HDC installation are supported.
3. Display resolution, density, shape, and safe-area clipping.
4. Rotary input behavior and system back-gesture conflicts.
5. Android API compatibility (`minSdkVersion` is 23; `targetSdkVersion` is 31).
6. Whether the existing package/signature is already installed.

Prefer a no-code compatibility test using the existing signed APK before
introducing model-specific layout branches. If changes are required, isolate
them behind explicit device or capability checks and document the result in the
README compatibility table.

## Documentation and release hygiene

- Update `README.md`, `docs/USER_GUIDE.md`, and screenshots when user-visible
  behavior changes.
- Keep examples generic. Use placeholders such as `<watch-ip>` and
  `<path-to-jdk>` in tracked documentation.
- Increment Android `versionCode` and `versionName` for a distributable release.
- Distribute a release APK signed with a stable private key. Never publish the
  key; users need the same signer for in-place updates.
- Report artifact SHA-256 values and the tested watch model in release notes.
- The project is MIT licensed; retain license notices when redistributing.
