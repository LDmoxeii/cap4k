$ErrorActionPreference = 'Stop'
& 'scripts/validate-local-markdown-links.ps1' -RepoRoot (Resolve-Path .).Path -Paths @('skills/cap4k-authoring') -Label 'Skill' | Out-Null
