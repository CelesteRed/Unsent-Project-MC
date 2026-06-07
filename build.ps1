# Builds UnsentPlugin -> target\UnsentPlugin-<version>.jar
# Requires JDK 25 (Paper API 26.1.2 is Java-25 bytecode; JDK 21 will NOT compile it).
# Run from this folder in PowerShell:   .\build.ps1

$env:JAVA_HOME = "C:\Users\celes\.jdks\openjdk-25.0.2"
$mvn = "C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.2\plugins\maven\lib\maven3\bin\mvn.cmd"

& $mvn -f "$PSScriptRoot\pom.xml" clean package

if ($LASTEXITCODE -eq 0) {
    Write-Host "`nDone. Jar is in:" -ForegroundColor Green
    Get-ChildItem "$PSScriptRoot\target\*.jar" | Select-Object Name, Length
} else {
    Write-Host "`nBuild failed (exit $LASTEXITCODE). If it says 'wrong version 69.0', your JAVA_HOME isn't JDK 25." -ForegroundColor Red
}
