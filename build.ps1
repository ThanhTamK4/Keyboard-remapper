$ErrorActionPreference = "Stop"

$libDir = "lib"
$outDir = "build\classes"
$jarFile = "build\key-remapper.jar"
$srcDir = "src\main\java"

# --- Parse dependency versions from pom.xml (single source of truth) ---
[xml]$pom = Get-Content "pom.xml"
$ns = @{ m = "http://maven.apache.org/POM/4.0.0" }

function Get-PomVersion($groupId, $artifactId) {
    $dep = $pom.SelectNodes("//m:dependency", (New-Object Xml.XmlNamespaceManager($pom.NameTable)).Tap({
        param($nsMgr); $nsMgr.AddNamespace("m", "http://maven.apache.org/POM/4.0.0")
    })) | Where-Object {
        $_.groupId -eq $groupId -and $_.artifactId -eq $artifactId
    }
    return $dep.version
}

# Simpler: just regex-extract from pom.xml text
$pomText = Get-Content "pom.xml" -Raw

function Extract-Version($artifact) {
    if ($pomText -match "<artifactId>$artifact</artifactId>\s*<version>([^<]+)</version>") {
        return $Matches[1]
    }
    throw "Could not find version for $artifact in pom.xml"
}

$jnaVersion    = Extract-Version "jna"
$flatlafVersion = Extract-Version "flatlaf"
$gsonVersion   = Extract-Version "gson"

Write-Host "Versions from pom.xml: jna=$jnaVersion, flatlaf=$flatlafVersion, gson=$gsonVersion"

$deps = @(
    @{ Name = "jna-$jnaVersion.jar";          Url = "https://repo1.maven.org/maven2/net/java/dev/jna/jna/$jnaVersion/jna-$jnaVersion.jar" },
    @{ Name = "jna-platform-$jnaVersion.jar"; Url = "https://repo1.maven.org/maven2/net/java/dev/jna/jna-platform/$jnaVersion/jna-platform-$jnaVersion.jar" },
    @{ Name = "flatlaf-$flatlafVersion.jar";  Url = "https://repo1.maven.org/maven2/com/formdev/flatlaf/$flatlafVersion/flatlaf-$flatlafVersion.jar" },
    @{ Name = "gson-$gsonVersion.jar";        Url = "https://repo1.maven.org/maven2/com/google/code/gson/gson/$gsonVersion/gson-$gsonVersion.jar" }
)

# --- Download dependencies ---
if (-not (Test-Path $libDir)) { New-Item -ItemType Directory -Path $libDir | Out-Null }

foreach ($dep in $deps) {
    $target = Join-Path $libDir $dep.Name
    if (-not (Test-Path $target)) {
        Write-Host "Downloading $($dep.Name)..."
        Invoke-WebRequest -Uri $dep.Url -OutFile $target -UseBasicParsing
    }
}

Write-Host "Dependencies OK."

# --- Compile ---
if (Test-Path $outDir) { Remove-Item -Recurse -Force $outDir }
New-Item -ItemType Directory -Path $outDir | Out-Null

$cp = (Get-ChildItem "$libDir\*.jar" | ForEach-Object { $_.FullName }) -join ";"
$sources = Get-ChildItem -Recurse "$srcDir\*.java" | ForEach-Object { $_.FullName }

Write-Host "Compiling $($sources.Count) source files..."
& javac -d $outDir -cp $cp --release 11 $sources

if ($LASTEXITCODE -ne 0) {
    Write-Host "Compilation failed." -ForegroundColor Red
    exit 1
}

Write-Host "Compilation OK."

# --- Package JAR ---
$manifest = Join-Path $outDir "MANIFEST.MF"
$cpJars = ($deps | ForEach-Object { "../lib/$($_.Name)" }) -join " "
@"
Main-Class: com.keyremapper.App
Class-Path: $cpJars
"@ | Set-Content -Path $manifest -Encoding ASCII

$buildDir = Split-Path $jarFile
if (-not (Test-Path $buildDir)) { New-Item -ItemType Directory -Path $buildDir | Out-Null }

Push-Location $outDir
& jar cfm "..\key-remapper.jar" MANIFEST.MF com
Pop-Location

if ($LASTEXITCODE -ne 0) {
    Write-Host "JAR packaging failed." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Build successful: $jarFile" -ForegroundColor Green
Write-Host "Run with:  java -jar $jarFile"
