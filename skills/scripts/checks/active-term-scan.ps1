$ErrorActionPreference = 'Stop'
$files = @(Get-ChildItem -LiteralPath skills -Recurse -File | Where-Object { $_.Extension -in @('.md','.yaml','.yml','.ps1') -and $_.Name -ne 'active-term-scan.ps1' }) + @(Get-Item AGENTS.md)
$patterns = @(
  'cap4kBootstrapPlan','cap4kBootstrap','cap4k\.bootstrap','bootstrap-plan\.json','cap4k-plugin-pipeline-bootstrap','cap4k-bootstrap:managed-',
  'cap4k-business-discovery','cap4k-tactical-modeling','cap4k-technical-design','cap4k-generator-inputs','cap4k-generation-review','cap4k-handwritten-implementation','cap4k-verification-audit','cap4k-service-integration',
  'forced-rollback'
)
foreach ($file in $files) { $text = Get-Content -LiteralPath $file.FullName -Raw; foreach ($pattern in $patterns) { if ($text -match $pattern) { throw "Retired active term matched: $($file.FullName): $pattern" } } }

$publicFiles = @(Get-ChildItem -LiteralPath docs/public -Recurse -File -Filter '*.md') + @(
  Get-Item -LiteralPath 'README.md','cap4k-plugin-pipeline-gradle/README.md'
)
$publicPatterns = @(
  'cap4kBootstrapPlan','cap4kBootstrap','cap4k\.bootstrap','bootstrap-plan\.json','cap4k-plugin-pipeline-bootstrap','cap4k-bootstrap:managed-',
  'skills/cap4k-business-discovery','skills/cap4k-tactical-modeling','skills/cap4k-technical-design','skills/cap4k-generator-inputs','skills/cap4k-generation-review','skills/cap4k-handwritten-implementation','skills/cap4k-verification-audit','skills/cap4k-service-integration',
  'forced-rollback'
)
foreach ($file in $publicFiles) { $text = Get-Content -LiteralPath $file.FullName -Raw; foreach ($pattern in $publicPatterns) { if ($text -match $pattern) { throw "Retired public term matched: $($file.FullName): $pattern" } } }
