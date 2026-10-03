<# Compatibility entry point for the isolated full-theory-table client. #>
[CmdletBinding()]
param(
    [string]$JavaHome = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot',
    [ValidatePattern('^[A-Za-z0-9_-]{1,100}$')]
    [string]$RunTag = ((Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)),
    [switch]$PrepareOnly
)
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'run_client_smoke_isolated.ps1') -SmokeTest TheoryComplete -JavaHome $JavaHome -RunTag $RunTag -PrepareOnly:$PrepareOnly
