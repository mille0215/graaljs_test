# Run a JavaScript file with GraalJS (Nashorn compatible mode).
#   .\scripts\run.ps1 diet_templates\main.js a b c
#   .\scripts\run.ps1 -Inspect diet_templates\main.js        # wait for debugger on 127.0.0.1:9229
#   .\scripts\run.ps1 -InspectBrk diet_templates\main.js     # wait and stop at the first line
# (Keep this file ASCII-only: Windows PowerShell 5.1 misreads BOM-less UTF-8.)
param(
    [switch]$Inspect,
    [switch]$InspectBrk,
    [switch]$Scripting,
    [int]$Port = 9229,
    [Parameter(Mandatory = $true, Position = 0)][string]$Script,
    [Parameter(ValueFromRemainingArguments = $true)][string[]]$ScriptArgs
)
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$bundledJdk = Join-Path $root "runtime\jdk"

if (Test-Path $bundledJdk) {
    # Offline bundle (see make-bundle.ps1): use the bundled JDK and jars, never Maven or the network.
    $java = Join-Path $bundledJdk "bin\java.exe"
    $classes = Join-Path $root "classes"
    $cp = "$classes;$root\lib\*"

    # Recompile with the bundled javac when Java sources are newer than the classes.
    $sources = @(Get-ChildItem -Recurse (Join-Path $root "src\main\java") -Filter *.java)
    $built = (Get-ChildItem -Recurse $classes -Filter *.class | Measure-Object LastWriteTime -Maximum).Maximum
    if ($sources | Where-Object { $_.LastWriteTime -gt $built }) {
        Write-Host "[run.ps1] compiling..."
        & (Join-Path $bundledJdk "bin\javac.exe") -encoding UTF-8 --release 21 -proc:none -cp "$root\lib\*" -d $classes ($sources | ForEach-Object { $_.FullName })
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
}
else {
    $cpFile = Join-Path $root "target\classpath.txt"
    $classes = Join-Path $root "target\classes"

    # Build when classes are missing or pom.xml / Java sources are newer than the last build.
    $stale = -not (Test-Path $cpFile)
    if (-not $stale) {
        $built = (Get-Item $cpFile).LastWriteTime
        $inputs = @(Get-Item (Join-Path $root "pom.xml")) + @(Get-ChildItem -Recurse (Join-Path $root "src\main") -File)
        $stale = [bool]($inputs | Where-Object { $_.LastWriteTime -gt $built })
    }
    if ($stale) {
        Write-Host "[run.ps1] building..."
        & mvn -q -f (Join-Path $root "pom.xml") compile
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        (Get-Item $cpFile).LastWriteTime = Get-Date
    }

    $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin\java.exe" } else { "java" }
    $cp = "$classes;" + (Get-Content $cpFile -Raw).Trim()
}

$opts = @()
if ($InspectBrk) { $opts += "--inspect-brk=$Port" }
elseif ($Inspect) { $opts += "--inspect=$Port" }
if ($Scripting) { $opts += "--scripting" }

& $java "-Dfile.encoding=UTF-8" "-Dstdout.encoding=UTF-8" "-Dstderr.encoding=UTF-8" -cp $cp com.example.graaljs.ScriptRunner @opts $Script @ScriptArgs
exit $LASTEXITCODE
