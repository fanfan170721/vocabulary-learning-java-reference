$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    mvn -q compile exec:java
    if ($LASTEXITCODE -ne 0) { throw "The Java reference application could not start." }
} finally {
    Pop-Location
}
