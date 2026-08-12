$ErrorActionPreference = 'Stop'
$root = 'skills/cap4k-authoring'
$allowed = @('SKILL.md','routing.yaml','references/ownership-boundaries.md','references/runtime-analysis-boundaries.md','references/tactical-carriers.md')
$actual = @(Get-ChildItem -LiteralPath $root -Recurse -File | ForEach-Object { [IO.Path]::GetRelativePath((Resolve-Path $root), $_.FullName).Replace('\','/') })
$unexpected = @($actual | Where-Object { $_ -notin $allowed })
$missing = @($allowed | Where-Object { -not (Test-Path -LiteralPath (Join-Path $root $_) -PathType Leaf) })
if ($unexpected -or $missing) { throw "Thin skill structure mismatch. unexpected=$($unexpected -join ',') missing=$($missing -join ',')" }
$routing = Get-Content -LiteralPath "$root/routing.yaml" -Raw
$ids = @([regex]::Matches($routing, '(?m)^  - id: (\S+)\r?$') | ForEach-Object { $_.Groups[1].Value })
$expected = @('inspect-project','select-carrier-input','plan-generate','implement-owned-logic','inspect-analysis','verify-diagnose')
$actualRouteIds = (($ids | Sort-Object) -join ',')
$expectedRouteIds = (($expected | Sort-Object) -join ',')
if ($actualRouteIds -ne $expectedRouteIds) { throw "Unexpected route ids: $($ids -join ',')" }
if (($ids | Sort-Object -Unique).Count -ne $ids.Count) { throw 'Duplicate route id.' }
foreach ($forbidden in @('route_first:','then_chain:','workflow:','rollback_targets:','specialist_handoffs:','verification_mode:')) { if ($routing.Contains($forbidden)) { throw "Forbidden orchestration field: $forbidden" } }
$factsFile = $env:CAP4K_CAPABILITY_CONTRACT_FACTS
if ([string]::IsNullOrWhiteSpace($factsFile) -or -not (Test-Path -LiteralPath $factsFile -PathType Leaf)) {
  throw 'CAP4K_CAPABILITY_CONTRACT_FACTS must point to code-derived capability facts.'
}
$facts = Get-Content -LiteralPath $factsFile -Raw -Encoding UTF8 | ConvertFrom-Json
$sections = @($facts.agentSections | ForEach-Object id)
foreach ($m in [regex]::Matches($routing, '(?m)^    agent_sections: \[(?<v>[^]]*)\]$')) { foreach ($v in $m.Groups['v'].Value.Split(',').Trim()) { if ($v -notin $sections) { throw "Unknown Agent API section: $v" } } }
foreach ($m in [regex]::Matches($routing, 'references/[a-z-]+\.md')) { if (-not (Test-Path -LiteralPath (Join-Path $root $m.Value))) { throw "Missing required read: $($m.Value)" } }
