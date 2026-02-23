Param(
  [string]$Version = "",
  [string]$Sha = ""
)

$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $root

if ([string]::IsNullOrWhiteSpace($Version)) {
  # Try to read project version from pom.xml (simple parse)
  $pom = Get-Content -Raw -Path "pom.xml"
  $m = [regex]::Match($pom, "<version>([^<]+)</version>")
  if ($m.Success) { $Version = $m.Groups[1].Value } else { $Version = "0.1.0" }
}

if ([string]::IsNullOrWhiteSpace($Sha)) {
  if ($env:GITHUB_SHA) { $Sha = $env:GITHUB_SHA.Substring(0,7) }
  else {
    try { $Sha = (git rev-parse --short HEAD).Trim() } catch { $Sha = "local" }
  }
}

$dist = Join-Path $root "dist"
if (Test-Path $dist) { Remove-Item $dist -Recurse -Force }
New-Item -ItemType Directory -Path $dist | Out-Null

$jar = Join-Path $root ("target\\FileExplorer-$Version.jar")
if (-not (Test-Path $jar)) {
  $jar = Get-ChildItem -Path (Join-Path $root "target") -Filter "*.jar" | Select-Object -First 1
  if (-not $jar) { throw "No jar found in target/. Run mvn package first." }
  $jar = $jar.FullName
}

$bundleRoot = Join-Path $dist ("FileExplorer-$Version")
New-Item -ItemType Directory -Path $bundleRoot | Out-Null

Copy-Item $jar $bundleRoot
foreach ($f in @("README.md","README.txt","CHANGELOG.md")) {
  if (Test-Path (Join-Path $root $f)) { Copy-Item (Join-Path $root $f) $bundleRoot }
}
if (Test-Path (Join-Path $root "scripts")) { Copy-Item (Join-Path $root "scripts") (Join-Path $bundleRoot "scripts") -Recurse }

$runSh = Join-Path $bundleRoot "run.ps1"
Set-Content -Path $runSh -Value @'
Param(
  [Parameter(ValueFromRemainingArguments=$true)]
  [string[]]$Args
)
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
$jar = Get-ChildItem -Path $dir -Filter "*.jar" | Select-Object -First 1
if (-not $jar) { throw "No jar found in bundle folder." }
java -jar $jar.FullName @Args
'@

$zip = Join-Path $dist ("FileExplorer-$Version-$Sha.zip")
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::CreateFromDirectory($bundleRoot, $zip)
Write-Host "Created: $zip"
