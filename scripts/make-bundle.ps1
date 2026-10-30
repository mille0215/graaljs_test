# Build an offline bundle: JDK + jars + compiled classes, so no install, Maven or network is needed to run.
#   .\scripts\make-bundle.ps1                                # download Temurin 21 and write dist\graaljs-bundle.zip
#   .\scripts\make-bundle.ps1 -JdkZip C:\temp\OpenJDK21.zip  # use a Temurin zip you already have
# Run this on a machine that can reach Maven Central and api.adoptium.net.
# (Keep this file ASCII-only: Windows PowerShell 5.1 misreads BOM-less UTF-8.)
param(
    [string]$JdkZip
)
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
# Windows PowerShell 5.1 writes backslashes into zip entry names by default; the zip spec requires "/".
[AppContext]::SetSwitch("Switch.System.IO.Compression.ZipFile.UseBackslash", $false)
Add-Type -AssemblyName System.IO.Compression.FileSystem

$root = Split-Path -Parent $PSScriptRoot
$dist = Join-Path $root "dist"
$stage = Join-Path $dist "graaljs-bundle"
$zip = Join-Path $dist "graaljs-bundle.zip"
$cache = Join-Path $dist "cache"

# Download the latest Temurin 21 zip (checksum verified); reuse it if it is already cached.
function Get-TemurinZip {
    $api = "https://api.adoptium.net/v3/assets/latest/21/hotspot?os=windows&architecture=x64&image_type=jdk&vendor=eclipse"
    $package = @(Invoke-RestMethod $api)[0].binary.package
    New-Item -ItemType Directory -Force $cache | Out-Null
    $path = Join-Path $cache $package.name
    if (-not (Test-Path $path)) {
        Write-Host "[make-bundle] downloading $($package.name)..."
        Invoke-WebRequest $package.link -OutFile "$path.tmp" -UseBasicParsing
        Move-Item -Force "$path.tmp" $path
    }
    if ((Get-FileHash $path -Algorithm SHA256).Hash -ne $package.checksum) {
        Remove-Item $path
        throw "SHA-256 mismatch for $($package.name)"
    }
    return $path
}

Write-Host "[make-bundle] building..."
& mvn -q -f (Join-Path $root "pom.xml") compile
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if (-not $JdkZip) { $JdkZip = Get-TemurinZip }

if (Test-Path $stage) { Remove-Item -Recurse -Force $stage }
if (Test-Path $zip) { Remove-Item -Force $zip }
New-Item -ItemType Directory -Force $stage, "$stage\lib", "$stage\runtime", "$stage\scripts" | Out-Null

Write-Host "[make-bundle] staging JDK..."
$unpacked = Join-Path $stage "runtime\unpacked"
[IO.Compression.ZipFile]::ExtractToDirectory($JdkZip, $unpacked)
$jdkDirs = @(Get-ChildItem $unpacked -Directory)
if ($jdkDirs.Count -ne 1 -or -not (Test-Path (Join-Path $jdkDirs[0].FullName "bin\javac.exe"))) {
    throw "Not a JDK zip: $JdkZip"
}
Move-Item $jdkDirs[0].FullName "$stage\runtime\jdk"
Remove-Item -Recurse -Force $unpacked

Write-Host "[make-bundle] staging project..."
# classpath.txt also lists .pom entries, which are not needed at run time.
(Get-Content (Join-Path $root "target\classpath.txt") -Raw).Trim() -split ";" |
    Where-Object { $_ -like "*.jar" } |
    ForEach-Object { Copy-Item $_ "$stage\lib" }
Copy-Item -Recurse (Join-Path $root "target\classes") "$stage\classes"
Copy-Item (Join-Path $root "scripts\run.ps1") "$stage\scripts"
foreach ($item in ".vscode", "diet_templates", "src", "types", "jsconfig.json", "pom.xml", "README.md", "DEVELOPMENT.md") {
    Copy-Item -Recurse (Join-Path $root $item) $stage
}

Write-Host "[make-bundle] compressing..."
[IO.Compression.ZipFile]::CreateFromDirectory($stage, $zip, [IO.Compression.CompressionLevel]::Optimal, $true)
Remove-Item -Recurse -Force $stage

Write-Host ("[make-bundle] wrote {0} ({1:N0} MB)" -f $zip, ((Get-Item $zip).Length / 1MB))
