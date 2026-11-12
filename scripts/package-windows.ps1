# Builds Jeib for Windows: dist\Jeib\Jeib.exe (the app plus its own trimmed Java runtime, so users
# don't need Java) and dist\Jeib-<version>-windows.zip to send to testers.
# A real installer (--type exe or msi) also needs the WiX Toolset; that comes with the public v1.0.
#
# Usage (from the repo root):  powershell -ExecutionPolicy Bypass -File scripts\package-windows.ps1

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent

if (-not $env:JAVA_HOME) { throw 'JAVA_HOME must point to JDK 21.' }
$jlink = Join-Path $env:JAVA_HOME 'bin\jlink.exe'
$jpackage = Join-Path $env:JAVA_HOME 'bin\jpackage.exe'

# Version from pom.xml: 0.1.0-SNAPSHOT -> 0.1.0 (jpackage wants plain numbers).
[xml]$pom = Get-Content "$root\pom.xml"
$version = $pom.project.version -replace '-SNAPSHOT$', ''

& "$root\mvnw.cmd" -q -f "$root\pom.xml" clean package -DskipTests
if ($LASTEXITCODE -ne 0) { throw 'Maven build failed.' }

$jar = Get-ChildItem "$root\target" -Filter 'jeib-*.jar' | Select-Object -First 1
Copy-Item $jar.FullName "$root\target\libs\"

# A runtime with only the Java modules Jeib uses (about a third of the full JDK).
#   java.desktop      Desktop.browse (Report a problem) and JavaFX's AWT bits
#   java.sql          JDBC (SQLite)                java.logging, java.naming, java.management  Flyway
#   java.xml, java.scripting  FXML                 jdk.unsupported  JavaFX graphics
#   jdk.localedata    month and day names in Arabic and Russian; jdk.charsets, jdk.zipfs  just in case
$modules = 'java.base,java.desktop,java.sql,java.logging,java.naming,java.management,java.xml,java.scripting,' +
           'jdk.unsupported,jdk.localedata,jdk.charsets,jdk.zipfs'
$runtime = Join-Path $root 'target\runtime'
if (Test-Path $runtime) { Remove-Item $runtime -Recurse -Force }
& $jlink --add-modules $modules --include-locales=en,ar,ru,he --strip-debug --no-header-files --no-man-pages `
    --compress=zip-6 --output $runtime
if ($LASTEXITCODE -ne 0) { throw 'jlink failed.' }

$dest = Join-Path $root 'dist'
if (Test-Path "$dest\Jeib") { Remove-Item "$dest\Jeib" -Recurse -Force }

& $jpackage `
    --type app-image `
    --name Jeib `
    --app-version $version `
    --vendor Jeib `
    --description 'Plan, track, and review your spending.' `
    --icon "$root\packaging\jeib.ico" `
    --input "$root\target\libs" `
    --main-jar $jar.Name `
    --main-class com.jeib.Launcher `
    --runtime-image $runtime `
    --dest $dest
if ($LASTEXITCODE -ne 0) { throw 'jpackage failed.' }

# One zip for testers: unzip anywhere, open the Jeib folder, run Jeib.exe.
$zip = Join-Path $dest "Jeib-$version-windows.zip"
if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path "$dest\Jeib" -DestinationPath $zip

$sizeMb = [math]::Round((Get-ChildItem "$dest\Jeib" -Recurse | Measure-Object Length -Sum).Sum / 1MB)
$zipMb = [math]::Round((Get-Item $zip).Length / 1MB)
Write-Host "Built $dest\Jeib\Jeib.exe ($sizeMb MB) and $zip ($zipMb MB)"
