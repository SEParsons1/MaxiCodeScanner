# Rebuild app/src/main/assets/postal_codes.db from GeoNames CA_full.csv.zip.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$tools = $PSScriptRoot
$assets = Join-Path $root "app\src\main\assets"
$zip = Join-Path $tools "CA_full.csv.zip"
$txt = Join-Path $tools "CA_full.txt"
$tsv = Join-Path $tools "postal.tsv"
$db = Join-Path $assets "postal_codes.db"
$tsvUnix = $tsv.Replace('\', '/')

New-Item -ItemType Directory -Force -Path $tools, $assets | Out-Null
Invoke-WebRequest -Uri "https://download.geonames.org/export/zip/CA_full.csv.zip" -OutFile $zip -UseBasicParsing
if (Test-Path $txt) { Remove-Item $txt }
Expand-Archive -Path $zip -DestinationPath $tools -Force

$reader = [System.IO.StreamReader]::new($txt)
$writer = [System.IO.StreamWriter]::new($tsv, $false, [System.Text.UTF8Encoding]::new($false))
try {
    while (($line = $reader.ReadLine()) -ne $null) {
        $parts = $line.Split([char]9)
        if ($parts.Length -lt 3) { continue }
        $code = ($parts[1] -replace '\s', '').ToUpperInvariant()
        $name = $parts[2].Trim()
        if ($code.Length -lt 6 -or [string]::IsNullOrWhiteSpace($name)) { continue }
        $writer.Write($code)
        $writer.Write("`t")
        $writer.WriteLine($name)
    }
} finally {
    $writer.Close()
    $reader.Close()
}

if (Test-Path $db) { Remove-Item $db }
sqlite3 $db "CREATE TABLE staging (postal_code TEXT NOT NULL, place_name TEXT NOT NULL);"
sqlite3 -separator "`t" $db ".import `"$tsvUnix`" staging"
sqlite3 $db @"
CREATE TABLE postal_codes (
  postal_code TEXT PRIMARY KEY NOT NULL,
  place_name TEXT NOT NULL
);
INSERT OR IGNORE INTO postal_codes SELECT postal_code, place_name FROM staging;
DROP TABLE staging;
VACUUM;
"@

$v8h = Join-Path $tools "v8h_postal.tsv"
if (Test-Path $v8h) {
    $v8hUnix = $v8h.Replace('\', '/')
    sqlite3 $db "CREATE TABLE v8h (postal_code TEXT NOT NULL, place_name TEXT NOT NULL);"
    sqlite3 -separator "`t" $db ".import `"$v8hUnix`" v8h"
    sqlite3 $db @"
INSERT OR REPLACE INTO postal_codes SELECT postal_code, place_name FROM v8h;
DROP TABLE v8h;
VACUUM;
"@
}

Remove-Item $zip, $txt, $tsv -ErrorAction SilentlyContinue
Write-Host "Wrote $db ($((Get-Item $db).Length) bytes, $(sqlite3 $db 'SELECT COUNT(*) FROM postal_codes;') codes)"
