# PadelScore

Offline padel scoring for HUAWEI WATCH 4 Pro. The application targets the
HarmonyOS/OpenHarmony API 8 FA model and produces a HAP. API 8 wearable
applications use the supported JS/HML/CSS UI stack; the pure scoring model is
kept under `shared/` as typed source and transpiled into the on-device
JavaScript modules.

Modes: Classic Match, Single Set, Tie-break, Super Tie-break, Race to N and
Americano Fixed Total Points.

## Local verification

```powershell
npm test
.\hvigorw.bat assembleHap --mode module -p product=default -p module=entry@default
```

Signing certificates, profiles, IDE state and local SDK paths are excluded
from version control.
