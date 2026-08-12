[CmdletBinding()]
param(
  [string] $FactsFile
)

$ErrorActionPreference = 'Stop'

# Skill-only dispatcher: repository-wide Public Docs and AgentFacts alignment belongs to
# scripts/validate-capability-contract.ps1.

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$checkDir = Join-Path $PSScriptRoot 'checks'
$checks = @('structure-and-routing.ps1', 'thin-surface.ps1', 'active-term-scan.ps1', 'link-check.ps1')
$ownsFactsFile = $false
if ([string]::IsNullOrWhiteSpace($FactsFile)) {
  $FactsFile = Join-Path ([System.IO.Path]::GetTempPath()) ("cap4k-skill-contract-" + [System.Guid]::NewGuid() + '.json')
  $ownsFactsFile = $true
}
$previousFactsFile = $env:CAP4K_CAPABILITY_CONTRACT_FACTS

Push-Location -LiteralPath $repoRoot
try {
  if (-not (Test-Path -LiteralPath $FactsFile -PathType Leaf)) {
    & (Join-Path $repoRoot 'scripts/export-capability-contract-facts.ps1') -OutputFile $FactsFile | Out-Null
  }
  $resolvedFactsFile = (Resolve-Path -LiteralPath $FactsFile).Path
  $env:CAP4K_CAPABILITY_CONTRACT_FACTS = $resolvedFactsFile

  foreach ($check in $checks) {
    $path = Join-Path $checkDir $check
    if (-not (Test-Path -LiteralPath $path)) {
      throw "Missing validation check: $path"
    }

    & $path
  }
} finally {
  $env:CAP4K_CAPABILITY_CONTRACT_FACTS = $previousFactsFile
  if ($ownsFactsFile -and (Test-Path -LiteralPath $FactsFile)) {
    Remove-Item -LiteralPath $FactsFile -Force
  }
  Pop-Location
}

Write-Host 'cap4k skill validation passed.'