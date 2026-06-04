$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$checkDir = Join-Path $PSScriptRoot 'checks'
$checks = @(
  'structure.ps1',
  'routing.ps1',
  'progressive-loading.ps1',
  'self-contained-runtime.ps1',
  'skeleton-gate-refs.ps1',
  'stale-terms.ps1',
  'link-check.ps1'
)

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