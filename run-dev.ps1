param(
    [int]$Port = 8080,
    [string[]]$ExtraArgs = @()
)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not (Test-Path 'target\cp.txt')) {
    mvn -o dependency:build-classpath "-Dmdep.outputFile=target\cp.txt" "-Dmdep.includeScope=runtime"
}
mvn -o -q compile

$env:THYMELEAF_CACHE = 'false'
$cp = "target\classes;target\test-classes;" + (Get-Content 'target\cp.txt' -Raw).Trim()
$argList = @("--server.port=$Port") + $ExtraArgs
& java -cp $cp it.fn.redfish.catalog.ApiCatalogApplication @argList
