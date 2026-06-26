param(
    [string] $StarsectorRoot = $env:STARSECTOR_HOME,
    [string] $JavaHome = $env:JAVA_HOME,
    [string] $SourceLevel = "17",
    [string] $TargetLevel = "17",
    [switch] $KeepClasses
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($scriptDir)) {
    $scriptDir = (Get-Location).Path
}
$scriptDir = (Resolve-Path -LiteralPath $scriptDir).Path

if ([string]::IsNullOrWhiteSpace($StarsectorRoot)) {
    $StarsectorRoot = (Resolve-Path -LiteralPath (Join-Path $scriptDir "..\..")).Path
} else {
    $StarsectorRoot = (Resolve-Path -LiteralPath $StarsectorRoot).Path
}

$coreDir = Join-Path $StarsectorRoot "starsector-core"
$modsDir = Join-Path $StarsectorRoot "mods"
if (-not (Test-Path -LiteralPath $coreDir)) {
    throw "Starsector core not found at $coreDir. Pass -StarsectorRoot or set STARSECTOR_HOME."
}

function Find-Javac {
    param(
        [string] $Root,
        [string] $JavaHomePath
    )

    if (-not [string]::IsNullOrWhiteSpace($JavaHomePath)) {
        $candidate = Join-Path $JavaHomePath "bin\javac.exe"
        if (Test-Path -LiteralPath $candidate) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    $bundled = Get-ChildItem -LiteralPath $Root -Directory -Filter "jdk*" -ErrorAction SilentlyContinue |
        ForEach-Object { Join-Path $_.FullName "bin\javac.exe" } |
        Where-Object { Test-Path -LiteralPath $_ } |
        Select-Object -First 1
    if ($bundled) {
        return (Resolve-Path -LiteralPath $bundled).Path
    }

    $command = Get-Command javac -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    throw "Could not find javac. Pass -JavaHome, set JAVA_HOME, or install a JDK."
}

function Add-JarsFromDirectory {
    param(
        [System.Collections.Generic.List[string]] $Classpath,
        [string] $Path,
        [switch] $Recursive
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }

    $search = @{ LiteralPath = $Path; Filter = "*.jar"; File = $true }
    if ($Recursive) {
        $search.Recurse = $true
    }

    Get-ChildItem @search | ForEach-Object {
        $fullName = $_.FullName
        if (-not $Classpath.Contains($fullName)) {
            $Classpath.Add($fullName)
        }
    }
}

function Find-ModDirectory {
    param(
        [string[]] $Ids,
        [string[]] $NamePatterns
    )

    $dirs = Get-ChildItem -LiteralPath $modsDir -Directory
    foreach ($dir in $dirs) {
        $infoPath = Join-Path $dir.FullName "mod_info.json"
        if (-not (Test-Path -LiteralPath $infoPath)) {
            continue
        }

        $info = Get-Content -LiteralPath $infoPath -Raw
        $modId = $null
        if ($info -match '(?m)^\s*"id"\s*:\s*"([^"]+)"') {
            $modId = $Matches[1]
        }
        $modName = $null
        if ($info -match '(?m)^\s*"name"\s*:\s*"([^"]+)"') {
            $modName = $Matches[1]
        }

        foreach ($id in $Ids) {
            if ($modId -eq $id) {
                return $dir.FullName
            }
        }
        foreach ($pattern in $NamePatterns) {
            if ($modName -like $pattern) {
                return $dir.FullName
            }
        }
    }

    foreach ($pattern in $NamePatterns) {
        $match = $dirs | Where-Object { $_.Name -like $pattern } | Select-Object -First 1
        if ($match) {
            return $match.FullName
        }
    }

    return $null
}

function Add-ModJars {
    param(
        [System.Collections.Generic.List[string]] $Classpath,
        [string] $Label,
        [string[]] $Ids,
        [string[]] $NamePatterns,
        [switch] $Required
    )

    $dir = Find-ModDirectory -Ids $Ids -NamePatterns $NamePatterns
    if (-not $dir) {
        if ($Required) {
            throw "$Label not found in $modsDir."
        }
        Write-Warning "$Label not found; skipping optional compile dependency."
        return
    }

    Add-JarsFromDirectory -Classpath $Classpath -Path (Join-Path $dir "jars") -Recursive
    Write-Host "Using $Label from: $dir"
}

$javac = Find-Javac -Root $StarsectorRoot -JavaHomePath $JavaHome
$jarTool = Join-Path (Split-Path -Parent $javac) "jar.exe"
if (-not (Test-Path -LiteralPath $jarTool)) {
    throw "jar.exe not found next to javac at $jarTool."
}

Write-Host "Using Starsector root: $StarsectorRoot"
Write-Host "Using javac: $javac"

$classpath = [System.Collections.Generic.List[string]]::new()
Add-JarsFromDirectory -Classpath $classpath -Path $coreDir
Add-ModJars -Classpath $classpath -Label "LunaLib" -Ids @("lunalib") -NamePatterns @("LunaLib*") -Required

$buildDir = Join-Path $scriptDir "build\classes"
$modInfoPath = Join-Path $scriptDir "mod_info.json"
$jarRelPath = "jars/stat-derived-ship-costs.jar"
if (Test-Path -LiteralPath $modInfoPath) {
    $modInfo = Get-Content -LiteralPath $modInfoPath -Raw | ConvertFrom-Json
    if ($modInfo.jars -and $modInfo.jars.Count -gt 0) {
        $jarRelPath = [string] $modInfo.jars[0]
    }
}
$jarFile = Join-Path $scriptDir ($jarRelPath -replace "/", "\")

if (-not $KeepClasses -and (Test-Path -LiteralPath $buildDir)) {
    Remove-Item -LiteralPath $buildDir -Recurse -Force
}
New-Item -ItemType Directory -Path $buildDir -Force | Out-Null
New-Item -ItemType Directory -Path (Split-Path -Parent $jarFile) -Force | Out-Null

$sourceFiles = Get-ChildItem -LiteralPath (Join-Path $scriptDir "src") -Filter "*.java" -Recurse
if (-not $sourceFiles) {
    throw "No Java source files found under $scriptDir\src."
}

Write-Host "Compiling $($sourceFiles.Count) Java source files..."
& $javac `
    -encoding UTF-8 `
    -source $SourceLevel `
    -target $TargetLevel `
    -implicit:none `
    -cp ($classpath -join ";") `
    -d $buildDir `
    -Xlint:deprecation `
    @($sourceFiles.FullName)

if ($LASTEXITCODE -ne 0) {
    throw "Compilation failed."
}

Write-Host "Creating jar: $jarFile"
& $jarTool -cf $jarFile -C $buildDir .
if ($LASTEXITCODE -ne 0) {
    throw "Jar creation failed."
}

Write-Host "Build completed successfully." -ForegroundColor Green
