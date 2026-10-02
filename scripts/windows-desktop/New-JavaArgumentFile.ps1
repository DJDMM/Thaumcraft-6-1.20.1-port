[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$CapturePath,
    [Parameter(Mandatory = $true)][string]$ArgumentFile,
    [string]$EnvironmentFile = ($ArgumentFile + '.environment.json')
)
$ErrorActionPreference = 'Stop'
$capture = Get-Content -LiteralPath (Resolve-Path -LiteralPath $CapturePath).Path -Raw -Encoding UTF8 | ConvertFrom-Json
if ($capture.schemaVersion -ne 1 -or $capture.preparedWithoutLaunching -ne $true) {
    throw 'Expected a schemaVersion 1 capture prepared without launching.'
}
if ([string]::IsNullOrWhiteSpace($capture.mainClass) -or @($capture.arguments).Count -eq 0) {
    throw 'The prepared launch arguments/main class are missing.'
}
$resolvedArgumentFile = [IO.Path]::GetFullPath($ArgumentFile)
$resolvedEnvironmentFile = [IO.Path]::GetFullPath($EnvironmentFile)
if ($resolvedArgumentFile -eq $resolvedEnvironmentFile) { throw 'ArgumentFile and EnvironmentFile must differ.' }
foreach ($outputPath in @($resolvedArgumentFile, $resolvedEnvironmentFile)) {
    if (Test-Path -LiteralPath $outputPath) { throw "Use a fresh output file: $outputPath" }
    if (-not (Test-Path -LiteralPath ([IO.Path]::GetDirectoryName($outputPath)) -PathType Container)) {
        throw "Output directory does not exist: $outputPath"
    }
}

# Java argument-file syntax, not Windows shell/CreateProcess quoting. Quote every
# token, doubling backslashes and escaping quotes/control characters. No shell
# interpolation is involved. The current launch is ASCII, avoiding code-page
# ambiguity in Java 17's Windows argument-file reader.
function ConvertTo-JavaArgumentToken([string]$Value) {
    if ($null -eq $Value -or $Value.IndexOf([char]0) -ge 0) { throw 'A Java argument cannot contain NUL.' }
    foreach ($character in $Value.ToCharArray()) {
        if ([int]$character -gt 127) {
            throw 'Non-ASCII argument requires explicit local Java17 code-page verification before launch.'
        }
    }
    $escaped = $Value.Replace('\', '\\').Replace('"', '\"').Replace("`n", '\n').Replace("`r", '\r').Replace("`t", '\t').Replace([string][char]12, '\f')
    return '"' + $escaped + '"'
}
$argumentLines = foreach ($argument in @($capture.arguments)) {
    if ($null -eq $argument -or $argument -isnot [string]) { throw 'Every captured argument must be a string.' }
    ConvertTo-JavaArgumentToken $argument
}
# Within a fully quoted token, # is literal. The file contains one token per line.
$argumentText = ($argumentLines -join "`n") + "`n"
[IO.File]::WriteAllText($resolvedArgumentFile, $argumentText, [Text.Encoding]::ASCII)
[IO.File]::WriteAllText($resolvedEnvironmentFile, ([ordered]@{
    schemaVersion = 1
    overrides = $capture.environmentOverrides
    removedNames = @($capture.removedEnvironmentNames)
} | ConvertTo-Json -Depth 5),
    (New-Object Text.UTF8Encoding($false)))
[pscustomobject]@{
    JavaPath = $capture.executable
    WorkingDirectory = $capture.workingDirectory
    JavaArguments = @('@' + $resolvedArgumentFile)
    EnvironmentFile = $resolvedEnvironmentFile
    ArgumentCount = @($capture.arguments).Count
}
