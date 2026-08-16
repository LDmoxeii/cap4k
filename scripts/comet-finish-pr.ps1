[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$MaxInputBytes = 1048576
$MaxArtifactBytes = 524288

function Fail([string] $Message) { throw $Message }
function Invoke-GitCommand([string[]] $Arguments) {
    $text = & git @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { Fail "git $($Arguments -join ' ') failed: $(($text | Out-String).Trim())" }
    return @($text)
}
function Assert-String($Value, [string] $Name, [int] $Max, [bool] $AllowEmpty = $false) {
    if ($Value -isnot [string]) { Fail "$Name must be a string." }
    if ((-not $AllowEmpty -and [string]::IsNullOrWhiteSpace($Value)) -or $Value.Length -gt $Max -or $Value.IndexOf([char]0) -ge 0) {
        Fail "$Name is outside its allowed string boundary."
    }
    return [string]$Value
}
function Assert-PositiveInteger($Value, [string] $Name) {
    if (($Value -isnot [int]) -and ($Value -isnot [long])) { Fail "$Name must be an integer." }
    if ([long]$Value -lt 1) { Fail "$Name must be positive." }
    return [long]$Value
}
function Assert-Properties($Object, [string[]] $Required, [string[]] $Optional, [string] $Name) {
    if ($null -eq $Object -or $Object -isnot [pscustomobject]) { Fail "$Name must be an object." }
    $names = @($Object.PSObject.Properties.Name)
    foreach ($requiredName in $Required) { if ($names -cnotcontains $requiredName) { Fail "$Name is missing '$requiredName'." } }
    foreach ($actual in $names) { if (($Required + $Optional) -cnotcontains $actual) { Fail "$Name contains unsupported property '$actual'." } }
}
function FullPath([string] $Path) { return [IO.Path]::GetFullPath($Path).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar) }
function Is-Within([string] $Child, [string] $Parent) {
    $childFull = (FullPath $Child) + [IO.Path]::DirectorySeparatorChar
    $parentFull = (FullPath $Parent) + [IO.Path]::DirectorySeparatorChar
    return $childFull.StartsWith($parentFull, [StringComparison]::OrdinalIgnoreCase)
}
function Assert-NoReparseTraversal([string] $Path, [string] $Root, [string] $Name) {
    $current = FullPath $Path
    $rootFull = FullPath $Root
    if (-not (Is-Within $current $rootFull)) { Fail "$Name escapes its authorized root." }
    while ($current -cne $rootFull) {
        $item = Get-Item -LiteralPath $current -Force -ErrorAction Stop
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { Fail "$Name may not traverse a symlink or junction." }
        $current = FullPath (Split-Path -Parent $current)
    }
}
function Resolve-SafeRelativeFile([string] $Relative, [string] $Name, [string] $BaseRoot) {
    Assert-String $Relative $Name 1024 | Out-Null
    if ([IO.Path]::IsPathRooted($Relative) -or $Relative -match '(^|[\\/])\.\.([\\/]|$)') { Fail "$Name must be relative to its authorized root." }
    $candidate = FullPath (Join-Path $BaseRoot $Relative)
    if (-not (Is-Within $candidate $BaseRoot) -or -not (Test-Path -LiteralPath $candidate -PathType Leaf)) { Fail "$Name is missing or escapes its authorized root." }
    Assert-NoReparseTraversal $candidate $BaseRoot $Name
    return $candidate
}
function Sha256([string] $Path) { return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Normalize-LineEndings([string] $Text) { return $Text.Replace("`r`n", "`n").Replace("`r", "`n") }
function Normalize-Content([string] $Text) {
    $normalized = Normalize-LineEndings $Text
    if ($normalized.EndsWith("`n", [StringComparison]::Ordinal)) { return $normalized.Substring(0, $normalized.Length - 1) }
    return $normalized
}
function Get-CanonicalTextSha256([byte[]] $Bytes, [string] $Name) {
    try { $text = [Text.UTF8Encoding]::new($false, $true).GetString($Bytes) } catch [Text.DecoderFallbackException] { Fail "$Name is not valid UTF-8." }
    $canonicalBytes = [Text.UTF8Encoding]::new($false).GetBytes((Normalize-LineEndings $text))
    return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($canonicalBytes)).ToLowerInvariant()
}
function TextSha256([string] $Path, [string] $Name) { return Get-CanonicalTextSha256 ([IO.File]::ReadAllBytes($Path)) $Name }
function Check-HashAt([object] $Source, [string] $ExpectedRelative, [string] $Name, [string] $BaseRoot) {
    Assert-Properties $Source @('path','sha256') @() $Name
    if ([string]$Source.path -cne $ExpectedRelative) { Fail "$Name.path does not match the accepted change source." }
    $path = Resolve-SafeRelativeFile ([string]$Source.path) "$Name.path" $BaseRoot
    Assert-String $Source.sha256 "$Name.sha256" 64 | Out-Null
    if ($Source.sha256 -cnotmatch '^[0-9a-fA-F]{64}$' -or (TextSha256 $path $Name) -cne $Source.sha256.ToLowerInvariant()) { Fail "$Name hash does not match current content." }
    return $path
}
function Read-BoundedUtf8Stdin {
    $stream = [Console]::OpenStandardInput()
    $memory = [IO.MemoryStream]::new()
    $buffer = New-Object byte[] 8192
    try {
        while (($count = $stream.Read($buffer, 0, $buffer.Length)) -gt 0) {
            if ($memory.Length + $count -gt $MaxInputBytes) { Fail "stdin exceeds $MaxInputBytes bytes." }
            $memory.Write($buffer, 0, $count)
        }
        if ($memory.Length -eq 0) { Fail 'stdin is empty.' }
        return [Text.UTF8Encoding]::new($false, $true).GetString($memory.ToArray())
    } catch [Text.DecoderFallbackException] {
        Fail 'stdin is not valid UTF-8.'
    } finally {
        $memory.Dispose()
    }
}
function Get-YamlScalar([string] $Text, [string] $Name, [string] $Context = 'state') {
    $match = [regex]::Match($Text, '(?m)^' + [regex]::Escape($Name) + ':\s*(?<value>.*?)\s*$')
    if (-not $match.Success) { Fail "$Context is missing '$Name'." }
    $value = $match.Groups['value'].Value.Trim()
    if (($value.StartsWith("'") -and $value.EndsWith("'")) -or ($value.StartsWith('"') -and $value.EndsWith('"'))) { $value = $value.Substring(1, $value.Length - 2) }
    return $value
}
function Get-YamlSection([string] $Text, [string] $Name) {
    $lines = $Text -split "\r?\n"
    $start = -1
    for ($i = 0; $i -lt $lines.Count; $i++) { if ($lines[$i] -ceq "$Name`:") { $start = $i + 1; break } }
    if ($start -lt 0) { Fail "state is missing '$Name'." }
    $section = [Collections.Generic.List[string]]::new()
    for ($i = $start; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match '^\S') { break }
        $section.Add($lines[$i])
    }
    return ($section -join "`n")
}
function Get-SectionScalar([string] $Section, [string] $Name, [string] $Context) {
    $match = [regex]::Match($Section, '(?m)^\s+' + [regex]::Escape($Name) + ':\s*(?<value>.*?)\s*$')
    if (-not $match.Success) { Fail "$Context is missing '$Name'." }
    return $match.Groups['value'].Value.Trim().Trim('"').Trim("'")
}
function Get-ChangedPaths([string[]] $DiffLines) {
    $paths = [Collections.Generic.List[string]]::new()
    foreach ($line in $DiffLines) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $parts = $line -split "`t"
        if ($parts[0] -match '^[RC][0-9]*$' -and $parts.Count -ge 3) { $paths.Add($parts[1]); $paths.Add($parts[2]) }
        elseif ($parts.Count -ge 2) { $paths.Add($parts[1]) }
    }
    return @($paths | Sort-Object -Unique)
}
function Get-TreePaths([string] $Tree, [string] $Prefix) {
    return @(Invoke-GitCommand @('ls-tree','-r','--name-only',$Tree,'--',$Prefix) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Sort-Object -Unique)
}
function Get-TreeBlobOid([string] $Tree, [string] $Path, [bool] $Required = $true) {
    $lines = @(& git ls-tree $Tree -- $Path 2>$null)
    if ($LASTEXITCODE -ne 0) { Fail "Unable to inspect Git tree path '$Path'." }
    if ($lines.Count -eq 0) { if ($Required) { Fail "Git tree is missing required path '$Path'." }; return $null }
    if ($lines.Count -ne 1 -or $lines[0] -notmatch '^[0-7]{6}\s+blob\s+(?<oid>[0-9a-fA-F]{40,64})\t') { Fail "Git tree path '$Path' is not one regular blob." }
    return $Matches['oid'].ToLowerInvariant()
}
function Get-GitObjectBytes([string] $ObjectSpec, [int] $MaxBytes = 4194304) {
    $start = [Diagnostics.ProcessStartInfo]::new(); $start.FileName = 'git'
    [void]$start.ArgumentList.Add('cat-file'); [void]$start.ArgumentList.Add('blob'); [void]$start.ArgumentList.Add($ObjectSpec)
    $start.UseShellExecute = $false; $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $start
    try {
        if (-not $process.Start()) { Fail "Unable to read Git object '$ObjectSpec'." }
        $memory = [IO.MemoryStream]::new()
        try {
            $buffer = New-Object byte[] 8192
            while (($count = $process.StandardOutput.BaseStream.Read($buffer, 0, $buffer.Length)) -gt 0) {
                if ($memory.Length + $count -gt $MaxBytes) { Fail "Git object '$ObjectSpec' exceeds $MaxBytes bytes." }
                $memory.Write($buffer, 0, $count)
            }
            $errorText = $process.StandardError.ReadToEnd(); $process.WaitForExit()
            if ($process.ExitCode -ne 0) { Fail "Unable to read Git object '$ObjectSpec': $($errorText.Trim())" }
            return $memory.ToArray()
        } finally { $memory.Dispose() }
    } finally { $process.Dispose() }
}
function Get-TreeUtf8([string] $Tree, [string] $Path, [int] $MaxBytes = 4194304) {
    [void](Get-TreeBlobOid $Tree $Path $true); $bytes = Get-GitObjectBytes "$Tree`:$Path" $MaxBytes
    try { return [Text.UTF8Encoding]::new($false, $true).GetString($bytes) } catch [Text.DecoderFallbackException] { Fail "Git tree path '$Path' is not valid UTF-8." }
}
function Get-BytesSha256([byte[]] $Bytes) { return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($Bytes)).ToLowerInvariant() }
function Assert-ExactPathSet([string[]] $Actual, [string[]] $Expected, [string] $Name) {
    $actualSet = @($Actual | Sort-Object -Unique); $expectedSet = @($Expected | Sort-Object -Unique)
    if ($actualSet.Count -ne $expectedSet.Count -or @(Compare-Object -ReferenceObject $expectedSet -DifferenceObject $actualSet -CaseSensitive).Count -ne 0) { Fail "$Name does not exactly match Runtime-owned Archive progression. Expected [$($expectedSet -join ', ')]; actual [$($actualSet -join ', ')]." }
}

$tempFiles = [Collections.Generic.List[string]]::new()
try {
    $inputText = Read-BoundedUtf8Stdin
    try { $inputObject = $inputText | ConvertFrom-Json -Depth 32 } catch { Fail "stdin is not valid JSON: $($_.Exception.Message)" }
    Assert-Properties $inputObject @('schema','projectRoot','change','target','remote','transactionId','existingPullRequest') @() 'input'
    if ($inputObject.schema -cne 'comet.native.pull-request-finish-input.v1') { Fail 'Unsupported input schema.' }
    Assert-Properties $inputObject.change @('name','branch','headSha') @() 'change'
    Assert-Properties $inputObject.target @('branch') @() 'target'
    Assert-String $inputObject.projectRoot 'projectRoot' 4096 | Out-Null
    $changeName = Assert-String $inputObject.change.name 'change.name' 200
    $headBranch = Assert-String $inputObject.change.branch 'change.branch' 255
    $headSha = Assert-String $inputObject.change.headSha 'change.headSha' 64
    $targetBranch = Assert-String $inputObject.target.branch 'target.branch' 255
    $remoteName = Assert-String $inputObject.remote 'remote' 64
    Assert-String $inputObject.transactionId 'transactionId' 200 | Out-Null
    if ($changeName -cnotmatch '^[a-z0-9]+(?:-[a-z0-9]+)*$') { Fail 'change.name must be lowercase kebab-case.' }
    if ($targetBranch -cne 'master' -or $remoteName -cne 'origin') { Fail 'Only origin/master is supported.' }
    if ($headBranch -match '[:~^?*\[\\\s]' -or $headBranch.StartsWith('-') -or $headBranch.EndsWith('.') -or $headBranch.Contains('..') -or $headBranch.Contains('@{') -or $headBranch -notmatch '^(feature|fix|docs)/[^/]+(?:/[^/]+)*$') { Fail 'change.branch must be an unqualified short-lived branch.' }
    if ($headSha -cnotmatch '^(?:[0-9a-fA-F]{40}|[0-9a-fA-F]{64})$') { Fail 'change.headSha must be a 40- or 64-character Git OID.' }

    if ($null -ne $inputObject.existingPullRequest) {
        Assert-Properties $inputObject.existingPullRequest @('number','url','baseBranch','headBranch','headSha','state') @() 'existingPullRequest'
        [void](Assert-PositiveInteger $inputObject.existingPullRequest.number 'existingPullRequest.number')
        Assert-String $inputObject.existingPullRequest.url 'existingPullRequest.url' 2048 | Out-Null
        if ($inputObject.existingPullRequest.state -cne 'OPEN' -or $inputObject.existingPullRequest.baseBranch -cne $targetBranch -or $inputObject.existingPullRequest.headBranch -cne $headBranch -or $inputObject.existingPullRequest.headSha -cne $headSha) {
            Fail 'existingPullRequest identity does not match the finish request.'
        }
    }

    $repoRoot = FullPath (@(Invoke-GitCommand @('rev-parse','--show-toplevel'))[0].Trim())
    $requestedRoot = FullPath ((Resolve-Path -LiteralPath $inputObject.projectRoot -ErrorAction Stop).Path)
    if ($requestedRoot -cne $repoRoot) { Fail 'projectRoot does not identify the current repository root.' }
    Set-Location -LiteralPath $repoRoot
    $actualHead = @(Invoke-GitCommand @('rev-parse','HEAD'))[0].Trim()
    if ($actualHead -cne $headSha) { Fail 'change.headSha must equal the current Git HEAD.' }
    $actualBranch = @(Invoke-GitCommand @('branch','--show-current'))[0].Trim()
    if ($actualBranch -cne $headBranch) { Fail 'change.branch must equal the current checked-out branch.' }
    & git cat-file -e "$headSha^{commit}" 2>$null
    if ($LASTEXITCODE -ne 0) { Fail 'change.headSha is not a commit.' }

    $safeChange = ($changeName -replace '[^A-Za-z0-9._-]', '_')
    $artifactGitPath = @(Invoke-GitCommand @('rev-parse','--git-path',"comet/pr-authoring/$safeChange.json"))[0].Trim()
    $artifactPath = if ([IO.Path]::IsPathRooted($artifactGitPath)) { FullPath $artifactGitPath } else { FullPath (Join-Path $repoRoot $artifactGitPath) }
    $gitCommon = @(Invoke-GitCommand @('rev-parse','--git-common-dir'))[0].Trim()
    $gitCommonPath = if ([IO.Path]::IsPathRooted($gitCommon)) { FullPath $gitCommon } else { FullPath (Join-Path $repoRoot $gitCommon) }
    if (-not (Is-Within $artifactPath $gitCommonPath) -or -not (Test-Path -LiteralPath $artifactPath -PathType Leaf)) { Fail 'Authoring artifact is missing or outside the Git management path.' }
    Assert-NoReparseTraversal $artifactPath $gitCommonPath 'Authoring artifact'
    $artifactItem = Get-Item -LiteralPath $artifactPath -Force
    if ($artifactItem.Length -gt $MaxArtifactBytes) { Fail "Authoring artifact exceeds $MaxArtifactBytes bytes." }
    try { $artifact = Get-Content -LiteralPath $artifactPath -Raw -Encoding UTF8 | ConvertFrom-Json -Depth 64 } catch { Fail "Authoring artifact is invalid JSON: $($_.Exception.Message)" }
    Assert-Properties $artifact @('schema','change','baseBranch','headBranch','title','body','source','contentFingerprint') @() 'artifact'
    if ($artifact.schema -cne 'cap4k.native-pr-authoring.v1' -or $artifact.change -cne $changeName -or $artifact.baseBranch -cne 'master' -or $artifact.headBranch -cne $headBranch) { Fail 'Authoring artifact identity does not match this finish request.' }
    $title = Assert-String $artifact.title 'artifact.title' 256
    $body = Assert-String $artifact.body 'artifact.body' 262144
    Assert-Properties $artifact.source @('stateVersion','verificationResult','verification','brief','specs','template','preArchiveHeadSha','preArchiveTreeSha','facts') @() 'artifact.source'
    $sourceStateVersion = Assert-PositiveInteger $artifact.source.stateVersion 'artifact.source.stateVersion'
    if ($artifact.source.verificationResult -cne 'pass') { Fail 'Authoring artifact must come from an accepted verification.' }

    $archiveBase = FullPath (Join-Path $repoRoot 'docs/comet/archive')
    if (-not (Test-Path -LiteralPath $archiveBase -PathType Container)) { Fail 'Native archive root is missing.' }
    $archiveCandidates = [Collections.Generic.List[object]]::new()
    foreach ($directory in @(Get-ChildItem -LiteralPath $archiveBase -Directory -Force | Where-Object { $_.Name.EndsWith("-$changeName", [StringComparison]::Ordinal) })) {
        Assert-NoReparseTraversal $directory.FullName $archiveBase 'Native archive directory'
        $candidateState = Join-Path $directory.FullName 'comet-state.yaml'
        if (-not (Test-Path -LiteralPath $candidateState -PathType Leaf)) { continue }
        $candidateText = Get-Content -LiteralPath $candidateState -Raw -Encoding UTF8
        if ((Get-YamlScalar $candidateText 'name') -ceq $changeName) { $archiveCandidates.Add([pscustomobject]@{ Dir=$directory.FullName; State=$candidateState; Text=$candidateText }) }
    }
    if ($archiveCandidates.Count -ne 1) { Fail "Expected exactly one archived change source for '$changeName'; found $($archiveCandidates.Count)." }
    $archiveRoot = FullPath $archiveCandidates[0].Dir
    $stateText = [string]$archiveCandidates[0].Text
    $archivedStateVersionText = Get-YamlScalar $stateText 'state_version'
    [long]$archivedStateVersion = 0
    if (-not [long]::TryParse($archivedStateVersionText, [ref]$archivedStateVersion) -or $archivedStateVersion -ne ($sourceStateVersion + 1)) { Fail 'Archived Native state version is not the direct successor of the accepted authoring state.' }
    if ((Get-YamlScalar $stateText 'phase') -cne 'archive' -or (Get-YamlScalar $stateText 'status') -cne 'done' -or (Get-YamlScalar $stateText 'verification_result') -cne 'pass' -or (Get-YamlScalar $stateText 'archived') -cne 'true') { Fail 'Archived Native state is not a completed passing Archive.' }

    $briefRef = Get-YamlScalar $stateText 'brief'
    [void](Check-HashAt $artifact.source.brief $briefRef 'artifact.source.brief' $archiveRoot)
    $specSection = Get-YamlSection $stateText 'spec_changes'
    $specMatches = [regex]::Matches($specSection, '(?m)^\s+source:\s*(?<source>\S+)\s*$')
    $capabilityMatches = [regex]::Matches($specSection, '(?m)^\s+-\s+capability:\s*(?<capability>[a-z0-9][a-z0-9.-]*)\s*$')
    $expectedSpecs = @($specMatches | ForEach-Object { $_.Groups['source'].Value })
    $capabilities = @($capabilityMatches | ForEach-Object { $_.Groups['capability'].Value })
    if ($expectedSpecs.Count -eq 0 -or $expectedSpecs.Count -ne $capabilities.Count) { Fail 'Archived Native spec_changes are incomplete.' }
    if ($artifact.source.specs -isnot [array] -or $artifact.source.specs.Count -ne $expectedSpecs.Count) { Fail 'artifact.source.specs must exactly cover the archived change Specs.' }
    $providedSpecPaths = @($artifact.source.specs | ForEach-Object { [string]$_.path })
    if (@($providedSpecPaths | Sort-Object -Unique).Count -ne $providedSpecPaths.Count) { Fail 'artifact.source.specs contains duplicate paths.' }
    foreach ($expectedSpec in $expectedSpecs) {
        $source = @($artifact.source.specs | Where-Object { [string]$_.path -ceq $expectedSpec })
        if ($source.Count -ne 1) { Fail "artifact.source.specs must contain '$expectedSpec' exactly once." }
        [void](Check-HashAt $source[0] $expectedSpec "artifact.source.specs[$expectedSpec]" $archiveRoot)
    }

    $verificationSection = Get-YamlSection $stateText 'verification'
    $verificationRef = Get-YamlScalar $stateText 'verification_report'
    Assert-Properties $artifact.source.verification @('path','candidateId','verifierExecutionRef','iteration','attempt') @() 'artifact.source.verification'
    if ($artifact.source.verification.path -cne $verificationRef) { Fail 'artifact.source.verification.path does not match the archived verification report.' }
    $verificationPath = Resolve-SafeRelativeFile $verificationRef 'artifact.source.verification.path' $archiveRoot
    $verificationReport = Get-Content -LiteralPath $verificationPath -Raw -Encoding UTF8
    $generatedMatch = [regex]::Match($verificationReport, '^---\r?\ngenerated_from_state_version:\s*(?<version>[1-9][0-9]*)\r?\n---(?:\r?\n|$)')
    if (-not $generatedMatch.Success -or [long]$generatedMatch.Groups['version'].Value -ne $archivedStateVersion) { Fail 'Archived verification report is not aligned with the archived state.' }
    $expectedVerification = [ordered]@{
        candidateId = Get-SectionScalar $verificationSection 'candidate_id' 'state.verification'
        verifierExecutionRef = Get-SectionScalar $verificationSection 'verifier_execution_ref' 'state.verification'
        iteration = Get-SectionScalar $verificationSection 'iteration' 'state.verification'
        attempt = Get-SectionScalar $verificationSection 'attempt' 'state.verification'
    }
    if ((Get-SectionScalar $verificationSection 'verdict' 'state.verification') -cne 'pass') { Fail 'Archived verification identity is not passing.' }
    if ($artifact.source.verification.candidateId -cne $expectedVerification.candidateId -or $artifact.source.verification.verifierExecutionRef -cne $expectedVerification.verifierExecutionRef -or [string]$artifact.source.verification.iteration -cne $expectedVerification.iteration -or [string]$artifact.source.verification.attempt -cne $expectedVerification.attempt) { Fail 'Authoring artifact verification identity does not match the archived accepted verification.' }

    $templateSource = $artifact.source.template
    Assert-Properties $templateSource @('path','sha256') @() 'artifact.source.template'
    $templatePath = Resolve-SafeRelativeFile ([string]$templateSource.path) 'artifact.source.template.path' $repoRoot
    if ((TextSha256 $templatePath 'artifact.source.template') -cne ([string]$templateSource.sha256).ToLowerInvariant()) { Fail 'artifact.source.template hash does not match current content.' }
    $templateRel = ([IO.Path]::GetRelativePath($repoRoot, $templatePath) -replace '\\','/')
    $trackedTemplates = @(Invoke-GitCommand @('ls-files')) | Where-Object { $_ -match '(?i)(^|/)(pull_request_template\.md|pull_request_template/.*\.md)$' }
    if ($trackedTemplates -cnotcontains $templateRel) { Fail 'artifact.source.template must be a tracked PR template.' }

    Assert-String $artifact.source.preArchiveHeadSha 'artifact.source.preArchiveHeadSha' 64 | Out-Null
    if ($artifact.source.preArchiveHeadSha -cnotmatch '^(?:[0-9a-fA-F]{40}|[0-9a-fA-F]{64})$') { Fail 'preArchiveHeadSha is not a Git OID.' }
    & git cat-file -e "$($artifact.source.preArchiveHeadSha)^{commit}" 2>$null
    if ($LASTEXITCODE -ne 0) { Fail 'preArchiveHeadSha is not a commit.' }
    & git merge-base --is-ancestor $artifact.source.preArchiveHeadSha $headSha
    if ($LASTEXITCODE -ne 0) { Fail 'preArchiveHeadSha must be the requested head or one of its ancestors.' }

    Assert-String $artifact.source.preArchiveTreeSha 'artifact.source.preArchiveTreeSha' 64 | Out-Null
    if ($artifact.source.preArchiveTreeSha -cnotmatch '^(?:[0-9a-fA-F]{40}|[0-9a-fA-F]{64})$') { Fail 'preArchiveTreeSha is not a Git OID.' }
    $preTree = ([string]$artifact.source.preArchiveTreeSha).ToLowerInvariant()
    $preArchiveTreeType = @(& git cat-file -t $preTree 2>$null)
    if ($LASTEXITCODE -ne 0 -or $preArchiveTreeType.Count -ne 1 -or $preArchiveTreeType[0].Trim() -cne 'tree') { Fail 'preArchiveTreeSha must identify an existing Git tree.' }
    $finalTree = "$headSha^{tree}"
    $activeRel = "docs/comet/changes/$changeName"
    $archiveRel = ([IO.Path]::GetRelativePath($repoRoot, $archiveRoot) -replace '\\','/')
    $activePaths = @(Get-TreePaths $preTree $activeRel)
    $finalActivePaths = @(Get-TreePaths $finalTree $activeRel)
    $preArchivePaths = @(Get-TreePaths $preTree $archiveRel)
    $archivePathsInFinal = @(Get-TreePaths $finalTree $archiveRel)
    if ($activePaths.Count -eq 0) { Fail 'preArchiveTreeSha does not contain the accepted active change root.' }
    if ($finalActivePaths.Count -ne 0) { Fail 'Final Archive head still contains the active change root.' }
    if ($preArchivePaths.Count -ne 0) { Fail 'preArchiveTreeSha already contains the final archive root.' }
    if ($archivePathsInFinal.Count -eq 0) { Fail 'Final Archive head does not contain the archive root.' }
    $activeRelativeFiles = @($activePaths | ForEach-Object { $_.Substring($activeRel.Length + 1) })
    $archiveRelativeFiles = @($archivePathsInFinal | ForEach-Object { $_.Substring($archiveRel.Length + 1) })
    Assert-ExactPathSet $archiveRelativeFiles $activeRelativeFiles 'Active/archive relative file set'
    foreach ($relative in $activeRelativeFiles) {
        if (@('comet-state.yaml','verification.md') -ccontains $relative) { continue }
        $activeOid = Get-TreeBlobOid $preTree "$activeRel/$relative" $true
        $archiveOid = Get-TreeBlobOid $finalTree "$archiveRel/$relative" $true
        if ($activeOid -cne $archiveOid) { Fail "Archive changed immutable accepted artifact '$relative'." }
    }

    $activeStatePath = "$activeRel/comet-state.yaml"
    $activeStateText = Get-TreeUtf8 $preTree $activeStatePath $MaxArtifactBytes
    [long]$activeStateVersion = 0
    if ((Get-YamlScalar $activeStateText 'name' 'pre-Archive state') -cne $changeName -or
        -not [long]::TryParse((Get-YamlScalar $activeStateText 'state_version' 'pre-Archive state'), [ref]$activeStateVersion) -or
        $activeStateVersion -ne $sourceStateVersion -or
        (Get-YamlScalar $activeStateText 'archived' 'pre-Archive state') -cne 'false' -or
        (Get-YamlScalar $activeStateText 'verification_result' 'pre-Archive state') -cne 'pass') { Fail 'preArchiveTreeSha active state identity does not match the accepted authoring artifact.' }
    $activeVerificationSection = Get-YamlSection $activeStateText 'verification'
    $activeVerificationRef = Get-YamlScalar $activeStateText 'verification_report' 'pre-Archive state'
    if ($activeVerificationRef -cne [string]$artifact.source.verification.path) { Fail 'pre-Archive verification report path does not match the accepted authoring artifact.' }
    $activeVerificationReport = Get-TreeUtf8 $preTree "$activeRel/$activeVerificationRef" $MaxArtifactBytes
    $activeGeneratedMatch = [regex]::Match($activeVerificationReport, '^---\r?\ngenerated_from_state_version:\s*(?<version>[1-9][0-9]*)\r?\n---(?:\r?\n|$)')
    if (-not $activeGeneratedMatch.Success -or [long]$activeGeneratedMatch.Groups['version'].Value -ne $sourceStateVersion) { Fail 'pre-Archive verification report is not aligned with the accepted authoring state.' }
    if ((Get-SectionScalar $activeVerificationSection 'verdict' 'pre-Archive state.verification') -cne 'pass' -or
        (Get-SectionScalar $activeVerificationSection 'candidate_id' 'pre-Archive state.verification') -cne [string]$artifact.source.verification.candidateId -or
        (Get-SectionScalar $activeVerificationSection 'verifier_execution_ref' 'pre-Archive state.verification') -cne [string]$artifact.source.verification.verifierExecutionRef -or
        (Get-SectionScalar $activeVerificationSection 'iteration' 'pre-Archive state.verification') -cne [string]$artifact.source.verification.iteration -or
        (Get-SectionScalar $activeVerificationSection 'attempt' 'pre-Archive state.verification') -cne [string]$artifact.source.verification.attempt) { Fail 'preArchiveTreeSha verification identity does not match the accepted authoring artifact.' }
    $activeBriefRef = Get-YamlScalar $activeStateText 'brief' 'pre-Archive state'
    if ($activeBriefRef -cne [string]$artifact.source.brief.path) { Fail 'pre-Archive brief path does not match artifact.source.brief.' }
    $activeBriefBytes = Get-GitObjectBytes "$preTree`:$activeRel/$activeBriefRef" $MaxArtifactBytes
    if ((Get-CanonicalTextSha256 $activeBriefBytes 'pre-Archive brief') -cne ([string]$artifact.source.brief.sha256).ToLowerInvariant()) { Fail 'pre-Archive brief hash does not match artifact.source.brief.' }
    $activeSpecSection = Get-YamlSection $activeStateText 'spec_changes'
    $activeSpecPaths = @([regex]::Matches($activeSpecSection, '(?m)^\s+source:\s*(?<source>\S+)\s*$') | ForEach-Object { $_.Groups['source'].Value })
    Assert-ExactPathSet $activeSpecPaths $providedSpecPaths 'pre-Archive state Spec paths'
    foreach ($specSource in $artifact.source.specs) {
        $specBytes = Get-GitObjectBytes "$preTree`:$activeRel/$([string]$specSource.path)" $MaxArtifactBytes
        if ((Get-CanonicalTextSha256 $specBytes "pre-Archive Spec '$([string]$specSource.path)'") -cne ([string]$specSource.sha256).ToLowerInvariant()) { Fail "pre-Archive Spec hash does not match artifact source '$($specSource.path)'." }
    }

    $selectionPath = '.comet/current-change.json'
    $selectionText = Get-TreeUtf8 $preTree $selectionPath 65536
    try { $selection = $selectionText | ConvertFrom-Json -Depth 8 } catch { Fail 'pre-Archive current-change selection is invalid JSON.' }
    if ($null -eq $selection -or $selection.schema -cne 'comet.selection.v2' -or $selection.change -cne $changeName) { Fail 'pre-Archive current-change selection does not identify the accepted change.' }
    if ($null -ne (Get-TreeBlobOid $finalTree $selectionPath $false)) { Fail 'Final Archive head still contains .comet/current-change.json.' }

    $canonicalChanged = [Collections.Generic.List[string]]::new()
    foreach ($capability in $capabilities) {
        $canonicalPath = "docs/comet/specs/$capability/spec.md"
        $matchingArchivedSpec = @($expectedSpecs | Where-Object { $_ -ceq "specs/$capability/spec.md" })
        if ($matchingArchivedSpec.Count -ne 1) { Fail "Archived change does not uniquely map capability '$capability' to its Spec." }
        $archiveSpecOid = Get-TreeBlobOid $finalTree "$archiveRel/$($matchingArchivedSpec[0])" $true
        $canonicalOid = Get-TreeBlobOid $finalTree $canonicalPath $true
        if ($archiveSpecOid -cne $canonicalOid) { Fail "Final canonical Spec '$canonicalPath' differs from the archived accepted Spec." }
        $preCanonicalOid = Get-TreeBlobOid $preTree $canonicalPath $false
        if ($preCanonicalOid -cne $canonicalOid) { $canonicalChanged.Add($canonicalPath) }
    }
    $archiveDiff = @(Invoke-GitCommand @('diff','--name-status','--find-renames',$preTree,$finalTree))
    $actualProgressionPaths = Get-ChangedPaths $archiveDiff
    $expectedProgressionPaths = @($activePaths + $archivePathsInFinal + @($selectionPath) + @($canonicalChanged))
    Assert-ExactPathSet $actualProgressionPaths $expectedProgressionPaths 'Post-authoring Archive changed-path set'

    Assert-Properties $artifact.source.facts @('sha256') @() 'artifact.source.facts'
    if ($artifact.source.facts.sha256 -cnotmatch '^[0-9a-fA-F]{64}$') { Fail 'artifact.source.facts.sha256 is invalid.' }
    Assert-Properties $artifact.contentFingerprint @('algorithm','digest') @() 'artifact.contentFingerprint'
    if ($artifact.contentFingerprint.algorithm -cne 'sha256') { Fail 'Unsupported content fingerprint algorithm.' }
    [string[]]$fingerprintSpecPaths = @($artifact.source.specs | ForEach-Object { [string]$_.path })
    [Array]::Sort($fingerprintSpecPaths, [StringComparer]::Ordinal)
    $fingerprintSpecs = @($fingerprintSpecPaths | ForEach-Object {
        $specPath = $_
        $specSource = @($artifact.source.specs | Where-Object { [string]$_.path -ceq $specPath })[0]
        [ordered]@{ path=$specPath; sha256=([string]$specSource.sha256).ToLowerInvariant() }
    })
    $fingerprintPayload = [ordered]@{
        title = Normalize-Content $title
        body = Normalize-Content $body
        artifact = [ordered]@{ schema=[string]$artifact.schema; change=[string]$artifact.change; baseBranch=[string]$artifact.baseBranch; headBranch=[string]$artifact.headBranch }
        source = [ordered]@{ stateVersion=[long]$sourceStateVersion; verificationResult=[string]$artifact.source.verificationResult }
        verification = [ordered]@{ path=[string]$artifact.source.verification.path; candidateId=[string]$artifact.source.verification.candidateId; verifierExecutionRef=[string]$artifact.source.verification.verifierExecutionRef; iteration=[long]$artifact.source.verification.iteration; attempt=[long]$artifact.source.verification.attempt }
        brief = [ordered]@{ path=[string]$artifact.source.brief.path; sha256=([string]$artifact.source.brief.sha256).ToLowerInvariant() }
        specs = $fingerprintSpecs
        template = [ordered]@{ path=[string]$artifact.source.template.path; sha256=([string]$artifact.source.template.sha256).ToLowerInvariant() }
        preArchiveHeadSha = ([string]$artifact.source.preArchiveHeadSha).ToLowerInvariant()
        preArchiveTreeSha = $preTree
        factsSha256 = ([string]$artifact.source.facts.sha256).ToLowerInvariant()
    }
    $fingerprintJson = $fingerprintPayload | ConvertTo-Json -Compress -Depth 12
    $fingerprint = Get-BytesSha256 ([Text.UTF8Encoding]::new($false).GetBytes($fingerprintJson))
    if ($artifact.contentFingerprint.digest -cne $fingerprint) { Fail 'Authoring content fingerprint does not match accepted content and source identity.' }
    $factsFile = [IO.Path]::GetTempFileName(); $tempFiles.Add($factsFile)
    $diffFile = [IO.Path]::GetTempFileName(); $tempFiles.Add($diffFile)
    $bodyFile = [IO.Path]::GetTempFileName(); $tempFiles.Add($bodyFile)
    $exportOutput = & (Join-Path $repoRoot 'scripts/export-capability-contract-facts.ps1') -OutputFile $factsFile 2>&1
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $factsFile -PathType Leaf)) { Fail "Capability facts export failed: $(($exportOutput | Out-String).Trim())" }
    if ((Sha256 $factsFile) -cne $artifact.source.facts.sha256.ToLowerInvariant()) { Fail 'Current capability facts do not match the authoring artifact.' }
    $diffLines = @(Invoke-GitCommand @('diff','--name-status','--find-renames',"origin/master...$headSha"))
    [IO.File]::WriteAllLines($diffFile, $diffLines, [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText($bodyFile, $body, [Text.UTF8Encoding]::new($false))
    $createArgs = @{
        Base = 'master'; Head = $headBranch; Title = $title; BodyFile = $bodyFile; Template = $templatePath
        ExpectedHeadSha = $headSha; FactsFile = $factsFile; ChangedFilesFile = $diffFile; MachineReadable = $true
    }
    if ($null -ne $inputObject.existingPullRequest) {
        $createArgs.ExpectedPullRequestNumber = [long]$inputObject.existingPullRequest.number
        $createArgs.ExpectedPullRequestUrl = [string]$inputObject.existingPullRequest.url
        $createArgs.ExpectedPullRequestBase = [string]$inputObject.existingPullRequest.baseBranch
        $createArgs.ExpectedPullRequestHead = [string]$inputObject.existingPullRequest.headBranch
        $createArgs.ExpectedPullRequestHeadSha = [string]$inputObject.existingPullRequest.headSha
    }
    $prOutput = & (Join-Path $repoRoot 'scripts/create-pr.ps1') @createArgs 2>&1
    if ($LASTEXITCODE -ne 0) { Fail "Repository PR entry failed: $(($prOutput | Out-String).Trim())" }
    $lines = @($prOutput | ForEach-Object { [string]$_ } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($lines.Count -ne 1) { Fail 'Repository PR entry did not emit exactly one JSON object.' }
    try { $prResult = $lines[0] | ConvertFrom-Json -Depth 32 } catch { Fail 'Repository PR entry emitted invalid JSON.' }
    Assert-Properties $prResult @('schema','disposition','remoteVerified','pullRequest') @() 'repository result'
    if ($prResult.schema -cne 'cap4k.pull-request-result.v1' -or @('created','reused') -cnotcontains $prResult.disposition -or $prResult.remoteVerified -cne $true) { Fail 'Repository PR result is not verified.' }
    Assert-Properties $prResult.pullRequest @('number','url','baseBranch','headBranch','headSha') @() 'repository result.pullRequest'
    [void](Assert-PositiveInteger $prResult.pullRequest.number 'repository result.pullRequest.number')
    Assert-String $prResult.pullRequest.url 'repository result.pullRequest.url' 2048 | Out-Null
    if ($prResult.pullRequest.baseBranch -cne 'master' -or $prResult.pullRequest.headBranch -cne $headBranch -or $prResult.pullRequest.headSha -cne $headSha) { Fail 'Repository result PR identity does not match the finish request.' }
    if ($null -ne $inputObject.existingPullRequest) {
        if ($inputObject.existingPullRequest.number -ne $prResult.pullRequest.number -or $inputObject.existingPullRequest.url -cne $prResult.pullRequest.url) { Fail 'Provider result conflicts with existingPullRequest.' }
    }
    $result = [ordered]@{ schema='comet.native.pull-request-finish-result.v1'; disposition=$prResult.disposition; remoteVerified=$true; pullRequest=[ordered]@{ number=[long]$prResult.pullRequest.number; url=[string]$prResult.pullRequest.url; baseBranch='master'; headBranch=$headBranch; headSha=$headSha } }
    [Console]::Out.WriteLine(($result | ConvertTo-Json -Compress -Depth 8))
}
catch {
    [Console]::Error.WriteLine("ERROR: $($_.Exception.Message)")
    exit 1
}
finally {
    foreach ($file in $tempFiles) { Remove-Item -LiteralPath $file -Force -ErrorAction SilentlyContinue }
}
