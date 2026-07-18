$ErrorActionPreference = 'Stop'

$runtimeFiles = Get-ChildItem -LiteralPath 'skills' -Recurse -File |
  Where-Object { $_.Extension -in @('.md', '.yaml', '.yml') } |
  Sort-Object FullName

$dependencyPatterns = @(
  @{ Name = 'read docs/public'; Pattern = '(?i)\b(read|open|load|consult|use|require|requires|required|must read)\b.{0,60}\bdocs/public\b' },
  @{ Name = 'read docs/superpowers/analysis'; Pattern = '(?i)\b(read|open|load|consult|use|require|requires|required|must read)\b.{0,60}\bdocs/superpowers/analysis\b' },
  @{ Name = 'read GitHub issue'; Pattern = '(?i)\b(read|open|load|consult|use|require|requires|required|must read)\b.{0,60}\bGitHub issue\b' },
  @{ Name = 'read Context7'; Pattern = '(?i)\b(read|open|load|consult|use|require|requires|required|must read)\b.{0,60}\bContext7\b' },
  @{ Name = 'read historical spec'; Pattern = '(?i)\b(read|open|load|consult|use|require|requires|required|must read)\b.{0,60}\bhistorical specs?\b' },
  @{ Name = 'read cap4k source checkout'; Pattern = '(?i)\b(read|open|load|consult|use|require|requires|required|must read)\b.{0,60}\bcap4k source checkout\b' },
  @{ Name = 'read cap4k source tree'; Pattern = '(?i)\b(read|open|load|consult|use|require|requires|required|must read)\b.{0,60}\bcap4k source (tree|checkout)\b' },
  @{ Name = 'read source checkout'; Pattern = '(?i)\b(read|open|load|consult|use|require|requires|required|must read)\b.{0,60}\bsource checkout\b' },
  @{ Name = 'docs/public during normal operation'; Pattern = '(?i)\bdocs/public\b.{0,80}\b(normal operation|runtime instruction|runtime prerequisite|skill runtime)\b' }
)

$allowPattern = '(?i)\b(do not|don''t|must not|never|not require|does not require|without requiring|not a runtime|not runtime|source-extraction|source extraction|source-to-skill|maintenance|maintain)\b'

foreach ($file in $runtimeFiles) {
  $lines = Get-Content -LiteralPath $file.FullName
  for ($index = 0; $index -lt $lines.Count; $index++) {
    $line = $lines[$index]
    foreach ($dependencyPattern in $dependencyPatterns) {
      if ($line -match $dependencyPattern.Pattern -and $line -notmatch $allowPattern) {
        $lineNumber = $index + 1
        throw "Runtime skill file requires external dependency ($($dependencyPattern.Name)): $($file.FullName):$lineNumber"
      }
    }
  }
}