param([switch]$Google, [string]$EnvFile = (Join-Path $PSScriptRoot '.env'))

$ErrorActionPreference = 'Stop'
$previous = @{}
function Set-LaunchValue([string]$Name, [string]$Value) {
    if (-not $previous.ContainsKey($Name)) {
        $previous[$Name] = [Environment]::GetEnvironmentVariable($Name)
    }
    [Environment]::SetEnvironmentVariable($Name, $Value, 'Process')
}
function Read-Setting([string]$Name, [switch]$Secret) {
    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        if ($Secret) {
            $inputSecret = Read-Host $Name -AsSecureString
            try { $value = [Net.NetworkCredential]::new('', $inputSecret).Password }
            finally { $inputSecret.Dispose() }
        } else {
            $value = Read-Host $Name
        }
        if ([string]::IsNullOrWhiteSpace($value)) { throw "Missing $Name" }
        Set-LaunchValue $Name $value
    }
    if ($Name -eq 'JWT_SECRET' -and [Text.Encoding]::UTF8.GetByteCount($value) -lt 32) {
        throw 'JWT_SECRET requires at least 32 UTF-8 bytes.'
    }
}

Push-Location $PSScriptRoot
try {
    if (Test-Path -LiteralPath $EnvFile -PathType Leaf) {
        $allowed = @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'JWT_SECRET', 'MAIL_HOST', 'MAIL_PORT',
            'MAIL_USER', 'MAIL_PASSWORD', 'PORT', 'GOOGLE_CLIENT_ID', 'GOOGLE_CLIENT_SECRET',
            'SPRING_PROFILES_ACTIVE', 'SPRING_PROFILES_INCLUDE')
        $lineNumber = 0
        foreach ($line in Get-Content -LiteralPath $EnvFile -Encoding UTF8) {
            $lineNumber++
            if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) { continue }
            if ($line -notmatch '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') { throw "Invalid setting on line $lineNumber" }
            $key = $Matches[1]
            $entry = $Matches[2].Trim()
            if ($key -notin $allowed) { throw "Unknown server setting on line $lineNumber ($key)" }
            if ($entry.Length -ge 2 -and $entry[0] -eq $entry[-1] -and $entry[0] -in '"', "'") {
                $entry = $entry.Substring(1, $entry.Length - 2)
            }
            if ($entry -and -not [Environment]::GetEnvironmentVariable($key)) {
                Set-LaunchValue $key $entry
            }
        }
    } elseif ($PSBoundParameters.ContainsKey('EnvFile')) { throw "Cannot find env file: $EnvFile" }
    if (-not $env:DB_URL) {
        $jdbc = Read-Host 'DB_URL (JDBC URL, Enter for the configured TiDB endpoint)'
        if ($jdbc) { Set-LaunchValue 'DB_URL' $jdbc }
    }
    Read-Setting DB_USERNAME
    Read-Setting DB_PASSWORD -Secret
    Read-Setting JWT_SECRET -Secret
    Read-Setting MAIL_USER
    Read-Setting MAIL_PASSWORD -Secret
    $included = @($env:SPRING_PROFILES_INCLUDE -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    if ($Google -and $included -notcontains 'google') {
        Set-LaunchValue 'SPRING_PROFILES_INCLUDE' (($included + 'google') -join ',')
    }
    if ($Google -or (($env:SPRING_PROFILES_INCLUDE + ',' + $env:SPRING_PROFILES_ACTIVE) -split '\s*,\s*') -contains 'google') {
        Read-Setting GOOGLE_CLIENT_ID
        Read-Setting GOOGLE_CLIENT_SECRET -Secret
    }
    $mavenArgs = @('-pl', 'server', '-am', '-DskipTests', 'package')
    if (Test-Path '../.m2/repository') {
        $repo = (Resolve-Path '../.m2/repository').Path
        $mavenArgs += "-Dmaven.repo.local=$repo"
    }
    & mvn @mavenArgs
    if ($LASTEXITCODE -ne 0) { throw "Maven failed ($LASTEXITCODE)" }
    $jar = Get-ChildItem server/target/server-*.jar | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    & java -jar $jar.FullName
    if ($LASTEXITCODE -ne 0) { throw "Server exited ($LASTEXITCODE)" }
} finally {
    foreach ($key in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process')
    }
    Pop-Location
}
