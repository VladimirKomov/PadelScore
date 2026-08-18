[CmdletBinding()]
param(
    [string]$ConfigPath = "",
    [switch]$SkipTests,
    [switch]$SkipHap,
    [switch]$ReuseApk,
    [switch]$NoLaunch
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $ConfigPath = Join-Path $repoRoot "watch-deploy.local.psd1"
}
if (-not [System.IO.Path]::IsPathRooted($ConfigPath)) {
    $ConfigPath = Join-Path $repoRoot $ConfigPath
}
if (-not (Test-Path -LiteralPath $ConfigPath -PathType Leaf)) {
    throw "Local deployment config not found: $ConfigPath. Copy watch-deploy.example.psd1 first."
}

$config = Import-PowerShellDataFile -LiteralPath $ConfigPath

function Get-RequiredSetting {
    param([string]$Name)
    if (-not $config.ContainsKey($Name) -or [string]::IsNullOrWhiteSpace([string]$config[$Name])) {
        throw "Missing required setting '$Name' in $ConfigPath"
    }
    return [string]$config[$Name]
}

function Resolve-ConfiguredPath {
    param([string]$Value)
    $expanded = [Environment]::ExpandEnvironmentVariables($Value)
    if ([System.IO.Path]::IsPathRooted($expanded)) {
        return [System.IO.Path]::GetFullPath($expanded)
    }
    return [System.IO.Path]::GetFullPath((Join-Path $repoRoot $expanded))
}

function Assert-File {
    param([string]$Path, [string]$Label)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "$Label not found: $Path"
    }
}

function Assert-Directory {
    param([string]$Path, [string]$Label)
    if (-not (Test-Path -LiteralPath $Path -PathType Container)) {
        throw "$Label not found: $Path"
    }
}

function Assert-LastExitCode {
    param([string]$Operation)
    if ($LASTEXITCODE -ne 0) {
        throw "$Operation failed with exit code $LASTEXITCODE"
    }
}

function Get-Sha256Text {
    param([string]$Value)
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [Text.Encoding]::UTF8.GetBytes($Value)
        return ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace("-", "").ToLowerInvariant()
    } finally {
        $sha.Dispose()
    }
}

$target = Get-RequiredSetting "Target"
$packageName = Get-RequiredSetting "PackageName"
$activityName = Get-RequiredSetting "ActivityName"
$expectedSigner = (Get-RequiredSetting "ExpectedSignerSha256").Replace(":", "").ToUpperInvariant()
$hdc = Resolve-ConfiguredPath (Get-RequiredSetting "Hdc")
$javaHome = Resolve-ConfiguredPath (Get-RequiredSetting "JavaHome")
$npm = Resolve-ConfiguredPath (Get-RequiredSetting "Npm")
$androidJar = Resolve-ConfiguredPath (Get-RequiredSetting "AndroidJar")
$buildTools = Resolve-ConfiguredPath (Get-RequiredSetting "BuildTools")
$d8Jar = Resolve-ConfiguredPath (Get-RequiredSetting "D8Jar")
$keyStore = Resolve-ConfiguredPath (Get-RequiredSetting "KeyStore")
$keyAlias = Get-RequiredSetting "KeyAlias"
$keyStorePassword = Get-RequiredSetting "KeyStorePassword"
$keyPassword = Get-RequiredSetting "KeyPassword"

if (-not $SkipHap) {
    $devEcoNode = Resolve-ConfiguredPath (Get-RequiredSetting "DevEcoNode")
    $hapSignTool = Resolve-ConfiguredPath (Get-RequiredSetting "HapSignTool")
    $hapKeyStore = Resolve-ConfiguredPath (Get-RequiredSetting "HapKeyStore")
    $hapKeyAlias = Get-RequiredSetting "HapKeyAlias"
    $hapKeyStorePassword = Get-RequiredSetting "HapKeyStorePassword"
    $hapKeyPassword = Get-RequiredSetting "HapKeyPassword"
    $hapAppCert = Resolve-ConfiguredPath (Get-RequiredSetting "HapAppCert")
    $hapProfile = Resolve-ConfiguredPath (Get-RequiredSetting "HapProfile")
}

$java = Join-Path $javaHome "bin\java.exe"
$javac = Join-Path $javaHome "bin\javac.exe"
$apksignerJar = Join-Path $buildTools "lib\apksigner.jar"
$aapt = Join-Path $buildTools "aapt.exe"
$apk = Join-Path $repoRoot "compat-android\build\padelscore-compat-signed.apk"
$hap = Join-Path $repoRoot "entry\build\default\outputs\default\entry-default-signed.hap"
$helperRoot = Join-Path $repoRoot "tools\hdb-key-helper"
$helperSource = Join-Path $helperRoot "src\com\vekom\hdbtool\SetHdbKey.java"

Assert-File $hdc "HdcExternal"
Assert-Directory $javaHome "Java home"
Assert-File $java "java.exe"
Assert-File $javac "javac.exe"
Assert-File $npm "npm"
Assert-File $androidJar "Android platform JAR"
Assert-Directory $buildTools "Android build tools"
Assert-File $d8Jar "R8/D8 JAR"
Assert-File $apksignerJar "apksigner JAR"
Assert-File $aapt "aapt"
Assert-File $keyStore "APK signing keystore"
Assert-File $helperSource "HDB helper source"

if (-not $SkipHap) {
    Assert-File $devEcoNode "DevEco Node.js"
    Assert-File $hapSignTool "HAP signing tool"
    Assert-File $hapKeyStore "HAP signing keystore"
    Assert-File $hapAppCert "HAP application certificate chain"
    Assert-File $hapProfile "HAP provisioning profile"
}

Write-Host "== PadelScore deployment ==" -ForegroundColor Cyan
Write-Host "Target: $target"

if (-not $SkipTests) {
    Write-Host "`n[1/6] HarmonyOS tests" -ForegroundColor Cyan
    Push-Location $repoRoot
    try {
        & $npm test
        Assert-LastExitCode "HarmonyOS tests"
    } finally {
        Pop-Location
    }

    Write-Host "`n[2/6] Compatibility engine tests" -ForegroundColor Cyan
    & (Join-Path $repoRoot "compat-android\test.ps1") -JavaHome $javaHome
}

if (-not $SkipHap) {
    Write-Host "`n[3/6] Signed HAP build" -ForegroundColor Cyan
    $legacyWrapper = Join-Path $repoRoot ".local-tools\hvigor-wrapper.cjs"
    Copy-Item -LiteralPath (Join-Path $repoRoot "hvigor\hvigor-wrapper.js") `
        -Destination $legacyWrapper -Force
    Push-Location $repoRoot
    try {
        & $devEcoNode $legacyWrapper assembleHap --mode module `
            -p product=default -p module=entry@default
        Assert-LastExitCode "HAP build"
    } finally {
        Pop-Location
    }
    $unsignedHap = Join-Path $repoRoot `
        "entry\build\default\outputs\default\entry-default-unsigned.hap"
    Assert-File $unsignedHap "Unsigned HAP"
    if (Test-Path -LiteralPath $hap) {
        Remove-Item -LiteralPath $hap -Force
    }
    & $java -jar $hapSignTool sign-app `
        -mode localSign `
        -keyAlias $hapKeyAlias `
        -keyPwd $hapKeyPassword `
        -appCertFile $hapAppCert `
        -profileFile $hapProfile `
        -inFile $unsignedHap `
        -signAlg SHA256withECDSA `
        -keystoreFile $hapKeyStore `
        -keystorePwd $hapKeyStorePassword `
        -outFile $hap
    Assert-LastExitCode "HAP signing"
    Assert-File $hap "Signed HAP"

    $hapVerifyRoot = Join-Path $repoRoot "entry\build\sign-verify"
    if (Test-Path -LiteralPath $hapVerifyRoot) {
        Remove-Item -LiteralPath $hapVerifyRoot -Recurse -Force
    }
    New-Item -ItemType Directory -Path $hapVerifyRoot -Force | Out-Null
    & $java -jar $hapSignTool verify-app `
        -inFile $hap `
        -outCertChain (Join-Path $hapVerifyRoot "certificate-chain.cer") `
        -outProfile (Join-Path $hapVerifyRoot "profile.p7b")
    Assert-LastExitCode "HAP signature verification"
    Assert-File (Join-Path $hapVerifyRoot "certificate-chain.cer") `
        "Verified HAP certificate chain"
    Assert-File (Join-Path $hapVerifyRoot "profile.p7b") `
        "Verified HAP profile"
}

if (-not $ReuseApk) {
    Write-Host "`n[4/6] Signed compatibility APK build" -ForegroundColor Cyan
    $buildArguments = @{
        AndroidJar = $androidJar
        BuildTools = $buildTools
        JavaHome = $javaHome
        KeyStore = $keyStore
        D8Jar = $d8Jar
        KeyAlias = $keyAlias
        KeyStorePassword = $keyStorePassword
        KeyPassword = $keyPassword
    }
    & (Join-Path $repoRoot "compat-android\build.ps1") @buildArguments
}
Assert-File $apk "Signed compatibility APK"

Write-Host "`n[5/6] APK identity and signature" -ForegroundColor Cyan
$badging = @(& $aapt dump badging $apk 2>&1)
$badgingExit = $LASTEXITCODE
$packageLine = $badging | Where-Object { "$_" -match "^package:" } | Select-Object -First 1
if ($badgingExit -ne 0 -or $null -eq $packageLine) {
    throw "Unable to read APK package metadata"
}
$packageMatch = [regex]::Match("$packageLine", "name='([^']+)'")
if (-not $packageMatch.Success -or $packageMatch.Groups[1].Value -ne $packageName) {
    throw "APK package mismatch. Expected $packageName, got: $packageLine"
}
Write-Host $packageLine

$verifyOutput = @(& $java -jar $apksignerJar verify --verbose --print-certs $apk 2>&1)
$verifyExit = $LASTEXITCODE
$verifyOutput | ForEach-Object { Write-Host $_ }
if ($verifyExit -ne 0) {
    throw "APK signature verification failed"
}
$signerMatch = [regex]::Match(
    ($verifyOutput -join "`n"),
    "Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]+)")
if (-not $signerMatch.Success) {
    throw "Unable to read APK signer SHA-256"
}
$actualSigner = $signerMatch.Groups[1].Value.ToUpperInvariant()
if ($actualSigner -ne $expectedSigner) {
    throw "Unexpected APK signer. Expected $expectedSigner, got $actualSigner"
}

Write-Host "`nBuilding one-shot HDB helper" -ForegroundColor Cyan
$helperBuild = Join-Path $helperRoot "build"
$helperClasses = Join-Path $helperBuild "classes"
$helperDex = Join-Path $helperBuild "dex"
if (Test-Path -LiteralPath $helperBuild) {
    Remove-Item -LiteralPath $helperBuild -Recurse -Force
}
New-Item -ItemType Directory -Path $helperClasses, $helperDex -Force | Out-Null

& $javac -g:none -encoding UTF-8 --release 8 -d $helperClasses $helperSource
Assert-LastExitCode "HDB helper javac"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$helperClassesJar = Join-Path $helperBuild "classes.jar"
[System.IO.Compression.ZipFile]::CreateFromDirectory($helperClasses, $helperClassesJar)
& $java -Xmx512M -cp $d8Jar com.android.tools.r8.D8 `
    --lib $androidJar --min-api 23 --output $helperDex $helperClassesJar
Assert-LastExitCode "HDB helper D8"
$helperJar = Join-Path $helperBuild "hdb-helper.jar"
[System.IO.Compression.ZipFile]::CreateFromDirectory($helperDex, $helperJar)
Assert-File $helperJar "Built HDB helper"

Write-Host "`n[6/6] Install on watch" -ForegroundColor Cyan
$targets = @(& $hdc list targets -v 2>&1)
$targetsExit = $LASTEXITCODE
$targets | ForEach-Object { Write-Host $_ }
if ($targetsExit -ne 0 -or ($targets -join "`n") -notmatch [regex]::Escape($target)) {
    throw "HDC target $target is not connected"
}

$remoteApk = "/data/local/tmp/padelscore.apk"
$remoteHelper = "/data/local/tmp/padelscore-hdb-helper.jar"
$temporaryFilesMayExist = $false
$helperTransferred = $false
try {
    $temporaryFilesMayExist = $true
    & $hdc -t $target file send $apk $remoteApk
    Assert-LastExitCode "APK transfer"
    & $hdc -t $target file send $helperJar $remoteHelper
    Assert-LastExitCode "HDB helper transfer"
    $helperTransferred = $true

    $hdbKey = [Guid]::NewGuid().ToString("N")
    $hdbDigest = Get-Sha256Text ($hdbKey + "=" + $remoteApk)
    & $hdc -t $target shell `
        "CLASSPATH=$remoteHelper app_process /system/bin com.vekom.hdbtool.SetHdbKey $hdbKey"
    Assert-LastExitCode "Transient HDB key delivery"

    $installOutput = @(& $hdc -t $target shell `
        "pm install -r --hwhdb $hdbDigest $remoteApk" 2>&1)
    $installExit = $LASTEXITCODE
    $installOutput | ForEach-Object { Write-Host $_ }
    if ($installExit -ne 0 -or ($installOutput -join "`n") -notmatch "Success") {
        throw "Watch package installation failed"
    }
} finally {
    if ($helperTransferred) {
        $expiredKey = [Guid]::NewGuid().ToString("N")
        & $hdc -t $target shell `
            "CLASSPATH=$remoteHelper app_process /system/bin com.vekom.hdbtool.SetHdbKey $expiredKey" `
            *> $null
    }
    if ($temporaryFilesMayExist) {
        & $hdc -t $target shell `
            "rm -f $remoteHelper $remoteApk" *> $null
    }
}

if (-not $NoLaunch) {
    Write-Host "`nLaunching smoke test" -ForegroundColor Cyan
    & $hdc -t $target shell "input keyevent 224"
    Assert-LastExitCode "Wake display"
    & $hdc -t $target shell "am force-stop $packageName"
    Assert-LastExitCode "Force-stop before smoke test"
    & $hdc -t $target shell "am start -W -n $packageName/$activityName"
    Assert-LastExitCode "Application launch"
    Start-Sleep -Milliseconds 900

    $remoteScreenshot = "/data/local/tmp/padelscore-smoke.png"
    $localScreenshot = Join-Path $repoRoot "compat-android\build\smoke-latest.png"
    try {
        & $hdc -t $target shell "screencap -p $remoteScreenshot"
        Assert-LastExitCode "Watch screenshot"
        & $hdc -t $target file recv $remoteScreenshot $localScreenshot
        Assert-LastExitCode "Screenshot transfer"
    } finally {
        & $hdc -t $target shell "rm -f $remoteScreenshot" *> $null
    }
    Write-Host "Smoke screenshot: $localScreenshot"
}

$apkHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $apk).Hash
Write-Host "`nDeployment completed" -ForegroundColor Green
Write-Host "APK: $apk"
Write-Host "APK SHA-256: $apkHash"
if (Test-Path -LiteralPath $hap -PathType Leaf) {
    $hapHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $hap).Hash
    Write-Host "HAP: $hap"
    Write-Host "HAP SHA-256: $hapHash"
}
