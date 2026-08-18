param(
    [Parameter(Mandatory = $true)]
    [string]$JavaHome
)

$ErrorActionPreference = "Stop"
$moduleRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
$outputRoot = Join-Path $moduleRoot "build\test-classes"
if (Test-Path -LiteralPath $outputRoot) {
    Remove-Item -LiteralPath $outputRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null

$sources = @(
    (Join-Path $moduleRoot "src\com\vekom\padelprobe\MatchModel.java"),
    (Join-Path $moduleRoot "src\com\vekom\padelprobe\ScoringStrategy.java"),
    (Join-Path $moduleRoot "src\com\vekom\padelprobe\MatchEngine.java"),
    (Join-Path $moduleRoot "test\com\vekom\padelprobe\EngineSelfTest.java")
)

& (Join-Path $JavaHome "bin\javac.exe") -encoding UTF-8 --release 8 -d $outputRoot $sources
if ($LASTEXITCODE -ne 0) { throw "compatibility engine compilation failed" }

& (Join-Path $JavaHome "bin\java.exe") -cp $outputRoot com.vekom.padelprobe.EngineSelfTest
if ($LASTEXITCODE -ne 0) { throw "compatibility engine tests failed" }
