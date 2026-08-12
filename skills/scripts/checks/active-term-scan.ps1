$ErrorActionPreference = 'Stop'
$skillRoot = 'skills/cap4k-authoring'
$files = Get-ChildItem -LiteralPath $skillRoot -Recurse -File |
  Where-Object { $_.Extension -in @('.md','.yaml','.yml','.ps1') -and $_.Name -ne 'active-term-scan.ps1' }
$patterns = @(
  'cap4kBootstrapPlan','cap4kBootstrap','cap4k\.bootstrap','bootstrap-plan\.json','cap4k-plugin-pipeline-bootstrap','cap4k-bootstrap:managed-',
  'cap4k-business-discovery','cap4k-tactical-modeling','cap4k-technical-design','cap4k-generator-inputs','cap4k-generation-review','cap4k-handwritten-implementation','cap4k-verification-audit','cap4k-service-integration',
  'forced-rollback'
)
foreach ($file in $files) {
  $text = Get-Content -LiteralPath $file.FullName -Raw
  foreach ($pattern in $patterns) {
    if ($text -match $pattern) { throw "Retired Skill term matched: $($file.FullName): $pattern" }
  }
}
