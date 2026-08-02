$ErrorActionPreference = 'Stop'
$base = @('skills/cap4k-authoring/SKILL.md','skills/cap4k-authoring/routing.yaml') | ForEach-Object { Get-Item -LiteralPath $_ }
$bytes = ($base | Measure-Object Length -Sum).Sum
$lines = ($base | ForEach-Object { (Get-Content -LiteralPath $_.FullName | Measure-Object -Line).Lines } | Measure-Object -Sum).Sum
if ($base.Count -ne 2 -or $bytes -gt 7000 -or $lines -gt 150) { throw "Always-read surface too large: files=$($base.Count) bytes=$bytes lines=$lines" }
$all = Get-ChildItem -LiteralPath skills/cap4k-authoring -Recurse -File
if (($all | Measure-Object Length -Sum).Sum -gt 16000) { throw 'Thin skill total surface exceeds 16 KB.' }
Write-Host "thin-surface files=$($all.Count) bytes=$(($all | Measure-Object Length -Sum).Sum) alwaysReadBytes=$bytes alwaysReadLines=$lines"
