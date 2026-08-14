# Rebuild app/src/main/assets/postal_codes.db from the curated overlay TSV.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$tools = $PSScriptRoot
$assets = Join-Path $root "app\src\main\assets"
$overlay = Join-Path $tools "v8h_postal.tsv"
$db = Join-Path $assets "postal_codes.db"

if (-not (Test-Path $overlay)) {
    throw "Missing $overlay"
}

New-Item -ItemType Directory -Force -Path $assets | Out-Null
if (Test-Path $db) { Remove-Item $db }

$overlayUnix = $overlay.Replace('\', '/')
sqlite3 $db "CREATE TABLE postal_codes (postal_code TEXT PRIMARY KEY NOT NULL, place_name TEXT NOT NULL);"
sqlite3 -separator "`t" $db ".import `"$overlayUnix`" postal_codes"
sqlite3 $db "VACUUM;"
Write-Host "Wrote $db ($((Get-Item $db).Length) bytes, $(sqlite3 $db 'SELECT COUNT(*) FROM postal_codes;') codes)"
