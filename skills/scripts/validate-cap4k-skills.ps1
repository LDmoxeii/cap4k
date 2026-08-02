$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$checkDir = Join-Path $PSScriptRoot 'checks'
$checks = @('structure-and-routing.ps1', 'thin-surface.ps1', 'active-term-scan.ps1', 'link-check.ps1')

Push-Location -LiteralPath $repoRoot
try {
  foreach ($check in $checks) {
    $path = Join-Path $checkDir $check
    if (-not (Test-Path -LiteralPath $path)) {
      throw "Missing validation check: $path"
    }

    & $path
  }
} finally {
  Pop-Location
}

Write-Host 'cap4k skill validation passed.'
