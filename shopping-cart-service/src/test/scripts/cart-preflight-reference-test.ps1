$ErrorActionPreference = 'Stop'
$script = Join-Path $PSScriptRoot '../../main/resources/db/cart-preflight.ps1'
function psql {
    $global:LASTEXITCODE = 0
    $arguments = $args -join ' '
    if ($arguments -match 'pg_control_system') {
        if ($arguments -match 'reference') { return 'cluster:reference' }
        return 'cluster:target'
    }
    if ($arguments -match 'flyway_schema_history') {
        # Model a real V3 reference: V1-only history check must fail.
        if ($arguments -match "ARRAY\['1'\]::text\[\]") { return 'INVALID' }
        return 'OK'
    }
    return 'identical fingerprint'
}
$rejected = $false
try { & $script -ReferenceDatabase reference -TargetDatabase target -Version 1 }
catch { $rejected = $_.Exception.Message -match 'Reference history' }
if (-not $rejected) { throw 'V3 reference was not rejected for V1.' }
$result = & $script -ReferenceDatabase reference -TargetDatabase target -Version 3
if ($result -notmatch 'MATCH V3') { throw 'V3 reference did not match.' }
Write-Output 'Reference version binding check passed.'
