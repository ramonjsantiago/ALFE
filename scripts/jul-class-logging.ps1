Param(
  [Parameter(Mandatory=$true, Position=0)]
  [ValidateSet('list','status','on','off','all-on','all-off')]
  [string]$Command,

  [Parameter(Mandatory=$false, Position=1)]
  [string]$Logger,

  [Parameter(Mandatory=$false, Position=2)]
  [string]$Level = 'FINE'
)

$RootDir = Resolve-Path (Join-Path $PSScriptRoot '..')
$ConfFile = Join-Path $RootDir 'config\logging.properties'
$ListFile = Join-Path $RootDir 'config\jul-loggers.txt'

function Ensure-Files {
  if (-not (Test-Path $ConfFile)) {
    New-Item -ItemType Directory -Force -Path (Split-Path $ConfFile) | Out-Null
    @(
      '.level=INFO',
      'handlers=java.util.logging.ConsoleHandler',
      'java.util.logging.ConsoleHandler.level=ALL',
      'java.util.logging.ConsoleHandler.formatter=java.util.logging.SimpleFormatter',
      'java.util.logging.SimpleFormatter.format=%1$tF %1$tT.%1$tL %4$s %3$s - %5$s%6$s%n'
    ) | Set-Content -Encoding UTF8 $ConfFile
  }
}

function Set-LoggerLevel([string]$Name, [string]$Lvl) {
  Ensure-Files
  $Key = "$Name.level"
  $Lines = Get-Content -Encoding UTF8 $ConfFile
  $Found = $false
  for ($i=0; $i -lt $Lines.Count; $i++) {
    if ($Lines[$i] -match ('^' + [regex]::Escape($Key) + '=')) {
      $Lines[$i] = "$Key=$Lvl"
      $Found = $true
      break
    }
  }
  if (-not $Found) {
    $Lines += "$Key=$Lvl"
  }
  $Lines | Set-Content -Encoding UTF8 $ConfFile
}

switch ($Command) {
  'list' {
    if (-not (Test-Path $ListFile)) {
      Write-Error "No logger list file found at: $ListFile"
      exit 2
    }
    Get-Content -Encoding UTF8 $ListFile
  }
  'status' {
    if ([string]::IsNullOrWhiteSpace($Logger)) {
      Write-Error "status requires a logger name (fully-qualified class name)."
      exit 2
    }
    Ensure-Files
    $Key = "$Logger.level"
    $Hit = Select-String -Path $ConfFile -Pattern ('^' + [regex]::Escape($Key) + '=') | Select-Object -First 1
    if ($null -eq $Hit) {
      Write-Output "$Key is not set (inherits; root controlled by .level)"
    } else {
      Write-Output $Hit.Line
    }
  }
  'on' {
    if ([string]::IsNullOrWhiteSpace($Logger)) {
      Write-Error "on requires a logger name (fully-qualified class name)."
      exit 2
    }
    Set-LoggerLevel $Logger $Level
    Write-Output "Enabled $Logger at level=$Level"
  }
  'off' {
    if ([string]::IsNullOrWhiteSpace($Logger)) {
      Write-Error "off requires a logger name (fully-qualified class name)."
      exit 2
    }
    Set-LoggerLevel $Logger 'OFF'
    Write-Output "Disabled $Logger (level=OFF)"
  }
  'all-on' {
    if (-not (Test-Path $ListFile)) {
      Write-Error "No logger list file found at: $ListFile"
      exit 2
    }
    Get-Content -Encoding UTF8 $ListFile | Where-Object { $_ -and $_.Trim().Length -gt 0 } | ForEach-Object {
      Set-LoggerLevel $_ $Level
    }
    Write-Output "Enabled all listed loggers at level=$Level"
  }
  'all-off' {
    if (-not (Test-Path $ListFile)) {
      Write-Error "No logger list file found at: $ListFile"
      exit 2
    }
    Get-Content -Encoding UTF8 $ListFile | Where-Object { $_ -and $_.Trim().Length -gt 0 } | ForEach-Object {
      Set-LoggerLevel $_ 'OFF'
    }
    Write-Output "Disabled all listed loggers (level=OFF)"
  }
}

# Tip:
#   mvn -Djava.util.logging.config.file=config/logging.properties javafx:run
