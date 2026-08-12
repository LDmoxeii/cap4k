[CmdletBinding()]
param(
    [string] $OutputFile = "build/cap4k/capability-contract-facts.json"
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$outputPath = if ([System.IO.Path]::IsPathRooted($OutputFile)) {
    [System.IO.Path]::GetFullPath($OutputFile)
} else {
    [System.IO.Path]::GetFullPath((Join-Path $repoRoot $OutputFile))
}
$outputDirectory = Split-Path -Parent $outputPath
if ($outputDirectory) {
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
}

$gradle = if ($IsWindows) { Join-Path $repoRoot 'gradlew.bat' } else { Join-Path $repoRoot 'gradlew' }
if (-not (Test-Path -LiteralPath $gradle -PathType Leaf)) {
    throw "Missing Gradle wrapper: $gradle"
}

Push-Location -LiteralPath $repoRoot
try {
    & $gradle ':cap4k-plugin-pipeline-gradle:exportCapabilityContractFacts' "-PcapabilityContractFactsOutput=$outputPath" '--console=plain' '-q'
    if ($LASTEXITCODE -ne 0) {
        throw "Capability contract facts export failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}

if (-not (Test-Path -LiteralPath $outputPath -PathType Leaf)) {
    throw "Capability contract facts exporter did not create: $outputPath"
}

Write-Output $outputPath