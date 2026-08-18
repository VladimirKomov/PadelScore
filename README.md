# PadelScore

Offline padel scoring for HUAWEI WATCH 4 Pro. The primary application targets
the HarmonyOS/OpenHarmony API 8 FA model and produces a signed HAP. API 8
wearable applications use the supported JS/HML/CSS UI stack; the pure scoring
model is kept under `shared/` as typed source and transpiled into the on-device
JavaScript modules.

Modes: Classic Match, Single Set, Tie-break, Super Tie-break, Race to N and
Americano Fixed Total Points. Every mode supports persistent state and Undo.
The watch UI also provides serve tracking, confirmations, round history and
large tap zones designed for a circular display.

## User guide

The Russian watch UI guide covers every game mode, match setting, scoring
control, menu item, Americano history and state recovery workflow:
[`docs/USER_GUIDE.md`](docs/USER_GUIDE.md).

## Local verification

```powershell
npm test
.\hvigorw.bat assembleHap --mode module -p product=default -p module=entry@default
```

## Watch firmware compatibility

`compat-android/` contains a platform-only Java compatibility package for
retail Watch 4 firmware that exposes the Android application container but
does not provide the OpenHarmony HAP gateway service. It mirrors the scoring
engine and round UI from the primary HAP and is built without network access or
third-party runtime dependencies.

```powershell
.\compat-android\build.ps1 `
  -AndroidJar <path-to-android.jar> `
  -BuildTools <path-to-build-tools> `
  -JavaHome <path-to-jdk> `
  -KeyStore <path-to-keystore> `
  -D8Jar <optional-stable-r8.jar>
```

Signing certificates, profiles, IDE state, build outputs and local SDK paths
are excluded from version control.

## Deployment

The repeatable build, signing and Watch 4 installation workflow is documented
in [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md). On a configured workstation the
complete test/build/deploy/smoke cycle is one command:

```powershell
.\scripts\deploy-watch.ps1
```
