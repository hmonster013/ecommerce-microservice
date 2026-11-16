param(
    [Parameter(Mandatory = $true)][ValidateRange(1, 4)][int]$ExpectedVersion,
    [Parameter(Mandatory = $true)][string]$ActualService,
    [Parameter(Mandatory = $true)][string]$ReferenceService
)

$ErrorActionPreference = 'Stop'
if ($ActualService -eq $ReferenceService) { throw 'Actual and reference services must differ.' }
$query = Join-Path $PSScriptRoot 'schema-fingerprint.sql'
function Get-Identity([string]$service) {
    $result = @(& psql "service=$service" -X -q -t -A -v ON_ERROR_STOP=1 -c "SELECT system_identifier::text || ':' || current_database() FROM pg_control_system()")
    if ($LASTEXITCODE -ne 0 -or $result.Count -ne 1 -or $result[0] -notmatch '^\d+:.+$') { throw 'Database identity query failed; stop without baselining.' }
    return $result[0]
}
if ((Get-Identity $ActualService) -ceq (Get-Identity $ReferenceService)) {
    throw 'Actual and reference must be distinct databases; stop without baselining.'
}
function Get-Fingerprint([string]$service) {
    $result = @(& psql "service=$service" -X -q -t -A -v ON_ERROR_STOP=1 -f $query)
    if ($LASTEXITCODE -ne 0 -or $result.Count -ne 1) { throw 'Fingerprint query failed.' }
    return $result[0]
}
$referenceVersion = @(& psql "service=$ReferenceService" -X -q -t -A -v ON_ERROR_STOP=1 -c "SELECT CASE WHEN count(*) = $ExpectedVersion AND bool_and(success AND type = 'SQL' AND checksum IS NOT NULL AND version ~ '^[1-4]$') AND count(DISTINCT version) = $ExpectedVersion AND max(version::integer) = $ExpectedVersion THEN 'ok' ELSE 'invalid' END FROM public.flyway_schema_history WHERE installed_rank > 0")
if ($LASTEXITCODE -ne 0 -or $referenceVersion.Count -ne 1 -or $referenceVersion[0] -ne 'ok') {
    throw 'Reference history is not a complete SQL migration history at the requested version.'
}
$actual = Get-Fingerprint $ActualService
$reference = Get-Fingerprint $ReferenceService
if ($actual -cne $reference) { throw "Schema does not match reference version $ExpectedVersion; stop without baselining." }
Write-Output "Schema matches the supplied version $ExpectedVersion reference. Verify reference provenance and run native Flyway validate separately before any operator action."
