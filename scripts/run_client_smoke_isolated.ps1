<#
Prepare the pinned ForgeGradle client without launching it, export its final
Java17 arguments, and launch that exact JVM on a non-input Win32 desktop.
All launch evidence is written to ignored validation files. No UI input or
desktop switching is performed. Keep this runner alive until the client exits.
#>
[CmdletBinding()]
param(
    [ValidateSet('TheoryComplete', 'EssentiaProduction', 'ThaumonomiconComplete', 'Infusion', 'Auromancy', 'GolemPress')]
    [string]$SmokeTest = 'TheoryComplete',
    [string]$JavaHome = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot',
    [ValidatePattern('^[A-Za-z0-9_-]{1,100}$')]
    [string]$RunTag = ((Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)),
    [switch]$PrepareOnly
)
$ErrorActionPreference = 'Stop'
$profiles = @{
    GolemPress = @{ property = 'golemPressSmokeTest'; directory = 'golem-press-smoke'; prefix = 'golem-press-client'; marker = 'THAUMCRAFT_GOLEM_PRESS_CLIENT_SMOKE'; audit = 'THAUMCRAFT_GOLEM_PRESS_RENDER_AUDIT_OK' }
    Auromancy = @{ property = 'auromancySmokeTest'; directory = 'auromancy-smoke'; prefix = 'auromancy-client'; marker = 'THAUMCRAFT_AUROMANCY_CLIENT_SMOKE'; audit = 'THAUMCRAFT_AUROMANCY_RENDER_AUDIT_OK' }
    Infusion = @{ property = 'infusionSmokeTest'; directory = 'infusion-smoke'; prefix = 'infusion-client'; marker = 'THAUMCRAFT_INFUSION_CLIENT_SMOKE'; audit = 'THAUMCRAFT_INFUSION_RENDER_AUDIT_OK' }
    ThaumonomiconComplete = @{ property = 'thaumonomiconCompleteSmokeTest'; directory = 'thaumonomicon-complete-smoke'; prefix = 'thaumonomicon-client'; marker = 'THAUMCRAFT_THAUMONOMICON_COMPLETE_CLIENT_SMOKE'; audit = 'THAUMCRAFT_THAUMONOMICON_RENDER_AUDIT_OK' }
    TheoryComplete = @{ property = 'theoryCompleteSmokeTest'; directory = 'theory-complete-smoke'; prefix = 'theory-client'; marker = 'THAUMCRAFT_THEORY_COMPLETE_CLIENT_SMOKE'; audit = 'THAUMCRAFT_THEORY_TABLE_RENDER_AUDIT_OK' }
    EssentiaProduction = @{ property = 'essentiaProductionSmokeTest'; directory = 'essentia-production-smoke'; prefix = 'essentia-production-client'; marker = 'THAUMCRAFT_ESSENTIA_PRODUCTION_CLIENT_SMOKE'; audit = 'THAUMCRAFT_ESSENTIA_PRODUCTION_RENDER_AUDIT_OK' }
}
$profile = $profiles[$SmokeTest]
$expectedJvmProperty = '-Dthaumcraft.' + $profile.property + '=true'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$helperRoot = Join-Path $PSScriptRoot 'windows-desktop'
$initScript = Join-Path $helperRoot 'capture-javaexec.init.gradle'
$argumentGenerator = Join-Path $helperRoot 'New-JavaArgumentFile.ps1'
$isolatedLauncher = Join-Path $helperRoot 'Start-IsolatedJava.ps1'
$gradleWrapper = Join-Path $projectRoot 'gradlew.bat'
$wrapperProperties = Join-Path $projectRoot 'gradle\wrapper\gradle-wrapper.properties'
$buildScript = Join-Path $projectRoot 'build.gradle'
foreach ($requiredFile in @($initScript, $argumentGenerator, $isolatedLauncher,
    $gradleWrapper, $wrapperProperties, $buildScript)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) { throw "Required file missing: $requiredFile" }
}
if ((Get-Content -LiteralPath $wrapperProperties -Raw) -notmatch 'gradle-8\.8-bin\.zip') {
    throw 'The preparation helper is pinned to Gradle 8.8.'
}
if ((Get-Content -LiteralPath $buildScript -Raw) -notmatch "id\s+'net\.minecraftforge\.gradle'\s+version\s+'6\.0\.54'") {
    throw 'The preparation helper is pinned to ForgeGradle 6.0.54.'
}
$resolvedJavaHome = (Resolve-Path -LiteralPath $JavaHome).Path
$javaRelease = Join-Path $resolvedJavaHome 'release'
if (-not (Test-Path -LiteralPath (Join-Path $resolvedJavaHome 'bin\java.exe') -PathType Leaf) -or
    -not (Test-Path -LiteralPath $javaRelease -PathType Leaf) -or
    (Get-Content -LiteralPath $javaRelease -Raw) -notmatch '(?m)^JAVA_VERSION="17(?:\.|"|-)') {
    throw 'JavaHome must identify an existing Java17 installation.'
}

$validationRoot = Join-Path $projectRoot 'validation'
$runRoot = Join-Path $projectRoot 'run'
$smokeRoot = Join-Path $runRoot $profile.directory
foreach ($directory in @($validationRoot, $runRoot, $smokeRoot)) {
    if (Test-Path -LiteralPath $directory) {
        $directoryItem = Get-Item -LiteralPath $directory
        if (-not $directoryItem.PSIsContainer -or
            ($directoryItem.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw "QA directory must be an ordinary project directory: $directory"
        }
    } else {
        [void][IO.Directory]::CreateDirectory($directory)
    }
}
$prefix = Join-Path $validationRoot ($profile.prefix + '-' + $RunTag)
$prepareLog = $prefix + '-prepare.log'
$capturePath = $prefix + '-capture.json'
$argumentFile = $prefix + '.args'
$environmentFile = $prefix + '-environment.json'
$clientLog = $prefix + '-client.log'
$desktopStatus = $prefix + '-desktop.json'
$runSummary = $prefix + '-run.json'
foreach ($outputPath in @($prepareLog, $capturePath, $argumentFile, $environmentFile,
    $clientLog, $desktopStatus, $runSummary)) {
    if (Test-Path -LiteralPath $outputPath) { throw "RunTag already has QA evidence; choose a fresh RunTag: $outputPath" }
}

# Exactly these three options in the selected isolated smoke fixture. Other player/run
# directories are never read or modified. Keep all other fixture options intact.
$optionsFile = Join-Path $smokeRoot 'options.txt'
if (Test-Path -LiteralPath $optionsFile) {
    $optionsItem = Get-Item -LiteralPath $optionsFile
    if ($optionsItem.Attributes -band [IO.FileAttributes]::ReparsePoint) {
        throw 'The smoke options file must not be a link to another save/run.'
    }
    $optionLines = @([IO.File]::ReadAllLines($optionsFile))
} else { $optionLines = @() }
$optionLines = @($optionLines | Where-Object { $_ -notmatch '^(soundCategory_master|fullscreen|maxFps):' })
$optionLines += @('soundCategory_master:0.0', 'fullscreen:false', 'maxFps:30')
[IO.File]::WriteAllLines($optionsFile, [string[]]$optionLines, (New-Object Text.UTF8Encoding($false)))

$previousJavaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Process')
$previousLocation = Get-Location
try {
    # JAVA_HOME affects only this runner and its child processes.
    [Environment]::SetEnvironmentVariable('JAVA_HOME', $resolvedJavaHome, 'Process')
    Set-Location -LiteralPath $projectRoot
    Write-Output ('Preparing isolated smoke JVM; log: ' + $prepareLog)
    $gradleArguments = @('--no-daemon', '--console=plain', '--init-script', $initScript,
        'runClient', ('-P' + $profile.property), ('-PisolatedCaptureOutput=' + $capturePath))
    $savedErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $gradleWrapper @gradleArguments 2>&1 | Out-File -LiteralPath $prepareLog -Encoding UTF8
        $preparationExitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $savedErrorPreference }
    if ($preparationExitCode -ne 0 -or -not (Test-Path -LiteralPath $capturePath -PathType Leaf) -or
        -not (Select-String -LiteralPath $prepareLog -SimpleMatch 'ISOLATED_JVM_CAPTURE_OK' -Quiet) -or
        -not (Select-String -LiteralPath $prepareLog -SimpleMatch 'BUILD SUCCESSFUL' -Quiet)) {
        throw "Final JVM preparation failed; no client was launched. Inspect: $prepareLog"
    }
    $capture = Get-Content -LiteralPath $capturePath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($capture.schemaVersion -ne 1 -or $capture.preparedWithoutLaunching -ne $true -or
        $capture.gradleVersion -ne '8.8' -or $capture.taskPath -ne ':runClient' -or
        [IO.Path]::GetFullPath($capture.workingDirectory) -ne [IO.Path]::GetFullPath($smokeRoot) -or
        @($capture.allJvmArgs) -notcontains $expectedJvmProperty) {
        throw 'The exported JVM is not the expected isolated smoke client.'
    }
    $selectedJava = (Resolve-Path -LiteralPath $capture.executable).Path
    $selectedRelease = Join-Path ([IO.Directory]::GetParent([IO.Path]::GetDirectoryName($selectedJava)).FullName) 'release'
    if ((Get-Content -LiteralPath $selectedRelease -Raw) -notmatch '(?m)^JAVA_VERSION="17(?:\.|"|-)') {
        throw 'Gradle selected a JVM other than Java17.'
    }
    $launch = & $argumentGenerator -CapturePath $capturePath -ArgumentFile $argumentFile -EnvironmentFile $environmentFile
    $summary = [ordered]@{
        runTag = $RunTag; smokeTest = $SmokeTest; state = 'prepared'; javaPath = $selectedJava
        workingDirectory = $smokeRoot; prepareLog = $prepareLog; capturePath = $capturePath
        argumentFile = $argumentFile; environmentFile = $environmentFile
        clientLog = $clientLog; desktopStatus = $desktopStatus
        argumentCount = $launch.ArgumentCount
        environmentOverrideNames = @($capture.environmentOverrides.PSObject.Properties.Name)
    }
    [IO.File]::WriteAllText($runSummary, ($summary | ConvertTo-Json -Depth 5), (New-Object Text.UTF8Encoding($false)))
    if ($PrepareOnly) {
        [pscustomobject]$summary
        return
    }

    # Compile/validate the helper without a launch in this process. Suppress its
    # planned full command; logs/status/summary expose only necessary metadata.
    $null = & $isolatedLauncher -JavaPath $selectedJava -JavaArguments $launch.JavaArguments `
        -WorkingDirectory $smokeRoot -LogPath $clientLog -StatusPath $desktopStatus -EnvironmentFile $environmentFile
    $desktopName = 'ThaumcraftQA_' + [Guid]::NewGuid().ToString('N')
    $powerShellExecutable = Join-Path $PSHOME 'pwsh.exe'
    if (-not (Test-Path -LiteralPath $powerShellExecutable -PathType Leaf)) {
        $powerShellExecutable = Join-Path $PSHOME 'powershell.exe'
    }
    if (-not (Test-Path -LiteralPath $powerShellExecutable -PathType Leaf)) {
        $powerShellExecutable = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
    }
    $helperArguments = @('-NoProfile', '-File', $isolatedLauncher, '-JavaPath', $selectedJava,
        '-JavaArguments', ('@' + $argumentFile), '-WorkingDirectory', $smokeRoot,
        '-EnvironmentFile', $environmentFile, '-LogPath', $clientLog, '-StatusPath', $desktopStatus,
        '-DesktopName', $desktopName, '-Launch')
    $quotedHelperArguments = ($helperArguments | ForEach-Object {
        [ThaumcraftQa.IsolatedJava]::QuoteArgument([string]$_)
    }) -join ' '
    $summary.state = 'running'
    $summary.desktop = 'WinSta0\' + $desktopName
    [IO.File]::WriteAllText($runSummary, ($summary | ConvertTo-Json -Depth 5), (New-Object Text.UTF8Encoding($false)))
    Write-Output ('Launching isolated smoke JVM; client log: ' + $clientLog)
    $helperProcess = Start-Process -FilePath $powerShellExecutable -ArgumentList $quotedHelperArguments `
        -WindowStyle Hidden -PassThru -Wait
    if (-not (Test-Path -LiteralPath $desktopStatus -PathType Leaf)) {
        throw "The isolated launch did not produce status; inspect: $clientLog"
    }
    $status = Get-Content -LiteralPath $desktopStatus -Raw -Encoding UTF8 | ConvertFrom-Json
    $summary.state = $status.state
    $summary.jvmPid = $status.pid
    $summary.jvmExitCode = $status.exitCode
    $summary.maxOwnedWindowsOnTargetDesktop = $status.maxOwnedWindowsOnTargetDesktop
    $summary.inputDesktopBefore = $status.inputDesktopBefore
    $summary.inputDesktopAfter = $status.inputDesktopAfter
    [IO.File]::WriteAllText($runSummary, ($summary | ConvertTo-Json -Depth 5), (New-Object Text.UTF8Encoding($false)))
    if ($helperProcess.ExitCode -ne 0 -or $status.state -ne 'exited' -or $status.exitCode -ne 0 -or
        $status.desktop -ne ('WinSta0\' + $desktopName) -or $status.maxOwnedWindowsOnTargetDesktop -lt 1) {
        throw "The isolated client did not finish with verified desktop windows and exit0; inspect: $desktopStatus and $clientLog"
    }
    $smokeLog = [IO.File]::ReadAllText($clientLog)
    $smokeMatch = [regex]::Match($smokeLog, ([regex]::Escape($profile.marker) + '_OK: (\d+) scenes'))
    if (-not $smokeMatch.Success -or $smokeLog.Contains($profile.marker + '_FAILED') -or
        -not $smokeLog.Contains($profile.audit)) {
        throw "The client exited but the selected smoke checks did not pass; inspect: $clientLog"
    }
    $summary.smokeScenes = [int]$smokeMatch.Groups[1].Value
    [IO.File]::WriteAllText($runSummary, ($summary | ConvertTo-Json -Depth 5), (New-Object Text.UTF8Encoding($false)))
    [pscustomobject]$summary
} finally {
    Set-Location -LiteralPath $previousLocation.Path
    [Environment]::SetEnvironmentVariable('JAVA_HOME', $previousJavaHome, 'Process')
}
