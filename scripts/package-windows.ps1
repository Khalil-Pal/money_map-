# Builds dist\Jeib\Jeib.exe with jpackage: the app plus its own Java runtime, so users don't need Java.
# This makes an app image (a folder). A real installer (--type exe or msi) also needs the WiX Toolset;
# that's the Week 7 release task.
#
# Usage (from the repo root):  powershell -ExecutionPolicy Bypass -File scripts\package-windows.ps1

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent

if (-not $env:JAVA_HOME) { throw 'JAVA_HOME must point to JDK 21.' }
$jpackage = Join-Path $env:JAVA_HOME 'bin\jpackage.exe'

& "$root\mvnw.cmd" -q -f "$root\pom.xml" clean package -DskipTests
if ($LASTEXITCODE -ne 0) { throw 'Maven build failed.' }

$jar = Get-ChildItem "$root\target" -Filter 'jeib-*.jar' | Select-Object -First 1
Copy-Item $jar.FullName "$root\target\libs\"

$dest = Join-Path $root 'dist'
if (Test-Path "$dest\Jeib") { Remove-Item "$dest\Jeib" -Recurse -Force }

& $jpackage `
    --type app-image `
    --name Jeib `
    --app-version 0.1.0 `
    --vendor Jeib `
    --description 'Plan, track, and review your spending.' `
    --icon "$root\packaging\jeib.ico" `
    --input "$root\target\libs" `
    --main-jar $jar.Name `
    --main-class com.jeib.Launcher `
    --dest $dest
if ($LASTEXITCODE -ne 0) { throw 'jpackage failed.' }

Write-Host "Built $dest\Jeib\Jeib.exe"
