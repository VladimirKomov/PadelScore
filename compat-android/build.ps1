param(
    [Parameter(Mandatory = $true)]
    [string]$AndroidJar,
    [Parameter(Mandatory = $true)]
    [string]$BuildTools,
    [Parameter(Mandatory = $true)]
    [string]$JavaHome,
    [Parameter(Mandatory = $true)]
    [string]$KeyStore,
    [string]$D8Jar = "",
    [string]$KeyAlias = "androiddebugkey",
    [string]$KeyStorePassword = "android",
    [string]$KeyPassword = "android"
)

$ErrorActionPreference = "Stop"
$moduleRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
$buildRoot = Join-Path $moduleRoot "build"
$classesRoot = Join-Path $buildRoot "classes"
$dexRoot = Join-Path $buildRoot "dex"

if (Test-Path -LiteralPath $buildRoot) {
    Remove-Item -LiteralPath $buildRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $classesRoot, $dexRoot -Force | Out-Null

$javac = Join-Path $JavaHome "bin\javac.exe"
$javaSources = Get-ChildItem -LiteralPath (Join-Path $moduleRoot "src") -Filter *.java -Recurse -File |
    Select-Object -ExpandProperty FullName

& $javac -g:none -encoding UTF-8 --release 8 -classpath $AndroidJar -d $classesRoot $javaSources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

$java = Join-Path $JavaHome "bin\java.exe"
if ([string]::IsNullOrWhiteSpace($D8Jar)) {
    $D8Jar = Join-Path $BuildTools "lib\d8.jar"
}
$classesJar = Join-Path $buildRoot "classes.jar"
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::CreateFromDirectory($classesRoot, $classesJar)
& $java -Xmx1024M -cp $D8Jar com.android.tools.r8.D8 `
    --lib $AndroidJar --min-api 23 --output $dexRoot $classesJar
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

$aapt2 = Join-Path $BuildTools "aapt2.exe"
$baseApk = Join-Path $buildRoot "padelscore-base.apk"
$resourceRoot = Join-Path $moduleRoot "res"
$compiledResources = Join-Path $buildRoot "resources.zip"
$linkArguments = @(
    "link",
    "--manifest", (Join-Path $moduleRoot "AndroidManifest.xml"),
    "-I", $AndroidJar,
    "-o", $baseApk
)

if (Test-Path -LiteralPath $resourceRoot -PathType Container) {
    & $aapt2 compile --dir $resourceRoot -o $compiledResources
    if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }
    $linkArguments += @("-R", $compiledResources)
}

& $aapt2 @linkArguments
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Push-Location $dexRoot
try {
    & (Join-Path $BuildTools "aapt.exe") add -f $baseApk "classes.dex"
    if ($LASTEXITCODE -ne 0) { throw "aapt add classes.dex failed" }
} finally {
    Pop-Location
}

$alignedApk = Join-Path $buildRoot "padelscore-aligned.apk"
& (Join-Path $BuildTools "zipalign.exe") -f 4 $baseApk $alignedApk
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

$apksignerJar = Join-Path $BuildTools "lib\apksigner.jar"
$signedApk = Join-Path $buildRoot "padelscore-compat-signed.apk"
& $java -jar $apksignerJar sign `
    --ks $KeyStore `
    --ks-key-alias $KeyAlias `
    --ks-pass "pass:$KeyStorePassword" `
    --key-pass "pass:$KeyPassword" `
    --out $signedApk `
    $alignedApk
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

& $java -jar $apksignerJar verify --verbose --print-certs $signedApk
if ($LASTEXITCODE -ne 0) { throw "APK verification failed" }

Write-Host "Built $signedApk"
