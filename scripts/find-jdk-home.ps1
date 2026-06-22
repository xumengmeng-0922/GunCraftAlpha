# Find JDK root that contains bin\jpackage.exe using same java.exe as PATH
$ErrorActionPreference = 'SilentlyContinue'
try {
    $out = & java -XshowSettings:properties -version 2>&1 | ForEach-Object { $_.ToString() } | Out-String
    foreach ($line in $out -split "`n") {
        if ($line -match '^\s*java\.home\s*=\s*(.+)\s*$') {
            $home = $matches[1].Trim()
            if (Test-Path -LiteralPath (Join-Path $home 'bin\jpackage.exe')) {
                Write-Output $home
                exit 0
            }
        }
    }
} catch { }
exit 1
