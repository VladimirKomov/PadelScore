# Android compatibility build

This module is the compatibility package for Huawei Watch 4 firmware that exposes
the Android application container but does not include the OpenHarmony
`com.huawei.gateway` HAP container service.

The application logic mirrors the primary HarmonyOS HAP: all six scoring modes,
Undo, serve tracking, persistent state, Americano sessions, confirmations and a
round 466×466 touch UI. It intentionally uses only platform Java APIs and has no
network or third-party runtime dependency.

`build.ps1` performs a resource-free command-line build with `javac`, `d8`,
`aapt2`, `zipalign` and `apksigner`. Supply an Android platform JAR, Android build
tools, a JDK and a signing keystore. An alternate stable R8/D8 JAR can be passed
with `-D8Jar` when the SDK-bundled converter is incompatible with the host JDK.
Generated files are placed in `build/` and
are excluded from version control by the repository-wide ignore rules.

Run the pure scoring-engine checks without an Android runtime:

```powershell
.\test.ps1 -JavaHome <path-to-jdk>
```
