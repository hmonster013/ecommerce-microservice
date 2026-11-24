param(
    [Parameter(Mandatory=$true)][string]$ReferenceDatabase,
    [Parameter(Mandatory=$true)][string]$TargetDatabase,
    [Parameter(Mandatory=$true)][ValidateSet('1','3')][string]$Version
)
$ErrorActionPreference = 'Stop'
# Both databases must be reached using ordinary libpq environment settings. Never pass credentials here.
$sql = Join-Path $PSScriptRoot 'cart-preflight.sql'
function Query([string]$database, [string]$statement) {
    $result = & psql -X -q -A -t -v ON_ERROR_STOP=1 -d $database -c $statement
    if ($LASTEXITCODE -ne 0) { throw 'Cart preflight query failed; do not baseline or migrate.' }
    return ($result -join "`n")
}
# The system identifier distinguishes clusters even when reached through different host aliases.
$identityQuery = "SELECT system_identifier::text || ':' || current_database() FROM pg_control_system()"
$referenceIdentity = Query $ReferenceDatabase $identityQuery
$targetIdentity = Query $TargetDatabase $identityQuery
if (-not $referenceIdentity -or -not $targetIdentity -or $referenceIdentity -eq $targetIdentity) {
    throw 'Reference and target must be distinct database identities; do not baseline or migrate.'
}
$expected = if ($Version -eq '1') { "ARRAY['1']::text[]" } else { "ARRAY['1','2','3']::text[]" }
$historyQuery = "SELECT CASE WHEN count(*) FILTER (WHERE NOT success OR type NOT IN ('SQL','SCHEMA') OR (type = 'SQL' AND (version IS NULL OR checksum IS NULL)) OR (type = 'SCHEMA' AND version IS NOT NULL)) = 0 AND array_agg(version::text ORDER BY installed_rank) FILTER (WHERE type = 'SQL') = $expected THEN 'OK' ELSE 'INVALID' END FROM flyway_schema_history"
if ((Query $ReferenceDatabase $historyQuery) -ne 'OK') {
    throw "Reference history does not contain exactly the successful SQL migrations through V$Version; do not baseline or migrate."
}
function Fingerprint([string]$database) {
    $result = & psql -X -q -A -t -v ON_ERROR_STOP=1 -d $database -f $sql
    if ($LASTEXITCODE -ne 0) { throw 'Cart preflight failed; do not baseline or migrate.' }
    return ($result -join "`n")
}
$reference = Fingerprint $ReferenceDatabase
$target = Fingerprint $TargetDatabase
if ($reference -ne $target) { throw 'Cart schema fingerprint mismatch; do not baseline or migrate.' }
Write-Output "MATCH V$Version; native Flyway validate the reference against immutable migrations before any separately decided explicit baseline."
