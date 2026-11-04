<#
.SYNOPSIS
    Makes a new version of the app at each approved task (SMUG-3).

.DESCRIPTION
    Two steps around the merge into main:

    1. On the approved task branch, before the merge: -Bump patch (any task) or -Bump minor (the
       last task of a plan) raises versionName from the last tag and versionCode by 1 in
       app\build.gradle.kts, writes CHANGELOG.md with scripts\changelog.ps1 and commits both as
       "Release vX.Y.Z (SMUG-N)".
    2. On main, right after the merge: -Tag puts the annotated tag vX.Y.Z on the merge, with
       -Message as its text ("SMUG-N: <issue title>" or "SMUG-PLAN-N: <plan title>").

    Pushing the tag stays with the caller: git push origin vX.Y.Z.

.EXAMPLE
    .\scripts\release.ps1 -Bump patch
    .\scripts\release.ps1 -Tag -Message "SMUG-3: Scripts run.ps1, release.ps1 y changelog.ps1"
#>
[CmdletBinding(DefaultParameterSetName = 'Bump')]
param(
    [Parameter(ParameterSetName = 'Bump', Mandatory = $true)]
    [ValidateSet('patch', 'minor', 'major')]
    [string]$Bump,

    [Parameter(ParameterSetName = 'Tag', Mandatory = $true)]
    [switch]$Tag,

    [Parameter(ParameterSetName = 'Tag', Mandatory = $true)]
    [string]$Message
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$gradle = Join-Path $root 'app\build.gradle.kts'
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Fail([string]$why) {
    Write-Host "!! $why" -ForegroundColor Red
    exit 1
}

# Runs git so that a warning on stderr (such as a line-ending notice) is shown but does not stop
# the script, as it would under 'Stop' in Windows PowerShell; a failing exit code still does.
function Invoke-Git {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & git @args 2>&1 | ForEach-Object { Write-Host "$_" }
    } finally {
        $ErrorActionPreference = $previous
    }
    if ($LASTEXITCODE -ne 0) { Fail "git $args failed" }
}

$branch = git rev-parse --abbrev-ref HEAD
$build = [System.IO.File]::ReadAllText($gradle)
if ($build -notmatch 'versionName = "(\d+)\.(\d+)\.(\d+)"') { Fail 'No versionName in app\build.gradle.kts' }
$current = "$($Matches[1]).$($Matches[2]).$($Matches[3])"

if ($Tag) {
    if ($branch -ne 'main') { Fail "Tag the merge on main, not on $branch" }
    if (@(git rev-list --parents -n 1 HEAD).Split(' ').Count -lt 3) { Fail 'HEAD is not a merge: merge the task first' }
    if (git tag -l "v$current") { Fail "v$current already exists" }
    Invoke-Git tag -a "v$current" -m $Message
    Write-Host "==> Tagged v$current on $(git rev-parse --short HEAD). Push it with: git push origin v$current"
    exit 0
}

if ($branch -eq 'main') { Fail 'Run -Bump on the task branch, before the merge' }
if (git status --porcelain) { Fail 'Commit or discard the changes first' }
if ($branch -notmatch '(SMUG-\d+)') { Fail "The branch name has no SMUG-N: $branch" }
$issue = $Matches[1]

# The last version released on main, whatever the gradle file says.
$last = @(git tag -l 'v*' --sort=-v:refname --merged main)[0]
if (-not $last) { Fail 'No version tag on main' }
$major, $minor, $patch = $last.TrimStart('v').Split('.') | ForEach-Object { [int]$_ }
switch ($Bump) {
    'major' { $major++; $minor = 0; $patch = 0 }
    'minor' { $minor++; $patch = 0 }
    'patch' { $patch++ }
}
$next = "$major.$minor.$patch"

if ($build -notmatch 'versionCode = (\d+)') { Fail 'No versionCode in app\build.gradle.kts' }
$code = [int]$Matches[1] + 1
$build = $build -replace 'versionName = "[^"]*"', "versionName = `"$next`"" -replace 'versionCode = \d+', "versionCode = $code"
[System.IO.File]::WriteAllText($gradle, $build, $utf8)

& (Join-Path $PSScriptRoot 'changelog.ps1') -Next $next
Invoke-Git add app/build.gradle.kts CHANGELOG.md
Invoke-Git commit -q -m "Release v$next ($issue)"
Write-Host "==> v$next (versionCode $code) committed on $branch. After the merge into main: .\scripts\release.ps1 -Tag -Message `"${issue}: <title>`""
