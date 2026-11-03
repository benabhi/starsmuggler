<#
.SYNOPSIS
    Writes CHANGELOG.md from the git history (SMUG-3).

.DESCRIPTION
    One section per version tag (vX.Y.Z), newest first, in the Keep a Changelog format. Every
    approved task is a merge into main, and its branch type sets the section: feature and proto
    are Added, fix is Fixed, chore, docs and test are Changed. A commit made straight on main
    counts as Changed. The version bump commits are left out.

    With -Next, the commits of the current branch since the last tag become that version, dated
    today: scripts\release.ps1 uses it to write the changelog before the merge.

.EXAMPLE
    .\scripts\changelog.ps1
    .\scripts\changelog.ps1 -Next 0.11.4
#>
param(
    [string]$Next
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$sectionOf = @{ feature = 'Added'; proto = 'Added'; fix = 'Fixed'; chore = 'Changed'; docs = 'Changed'; test = 'Changed' }
$order = 'Added', 'Changed', 'Fixed'
# Version bumps made by release.ps1, and the hand-written changelog of 0.0.1.
$skip = '^(Release v\d|Add changelog for )'

function Get-Section([string]$type) {
    if ($sectionOf.ContainsKey($type)) { $sectionOf[$type] } else { 'Changed' }
}

# The changes in a range of main, by section. Commits straight on the range go to $direct.
function Get-Changes([string]$range, [string]$direct) {
    $changes = @{}
    foreach ($name in $order) { $changes[$name] = New-Object System.Collections.Generic.List[string] }
    $log = git log --first-parent --reverse --format='%H%x09%P%x09%s' $range
    foreach ($entry in $log) {
        if (-not $entry) { continue }
        $hash, $parents, $subject = $entry -split "`t", 3
        $parents = @($parents -split ' ')
        if ($parents.Count -gt 1) {
            $type = if ($subject -match "^Merge (?:branch )?'?([a-z]+)/") { $Matches[1] } else { 'chore' }
            $section = Get-Section $type
            $subjects = git log --reverse --no-merges --format='%s' "$($parents[0])..$($parents[1])"
        } else {
            $section = $direct
            $subjects = @($subject)
        }
        foreach ($line in $subjects) {
            if ($line -and $line -notmatch $skip) { $changes[$section].Add($line) }
        }
    }
    $changes
}

function Format-Version([string]$version, [string]$date, $changes) {
    $text = "## [$version] - $date`n"
    foreach ($name in $order) {
        if ($changes[$name].Count -eq 0) { continue }
        $text += "`n### $name`n`n"
        foreach ($line in $changes[$name]) { $text += "- $line`n" }
    }
    $text
}

$tags = @(git tag -l 'v*' --sort=v:refname --merged HEAD)
$blocks = New-Object System.Collections.Generic.List[string]
$previous = $null
foreach ($tag in $tags) {
    $range = if ($previous) { "$previous..$tag" } else { $tag }
    $date = git log -1 --format=%as $tag
    $blocks.Insert(0, (Format-Version $tag.TrimStart('v') $date (Get-Changes $range 'Changed')))
    $previous = $tag
}
if ($Next) {
    $branch = git rev-parse --abbrev-ref HEAD
    $type = if ($branch -match '^([a-z]+)/') { $Matches[1] } else { 'chore' }
    $range = if ($previous) { "$previous..HEAD" } else { 'HEAD' }
    $blocks.Insert(0, (Format-Version $Next (Get-Date -Format 'yyyy-MM-dd') (Get-Changes $range (Get-Section $type))))
}

$header = @"
# Changelog

Every version of Star Smuggler for Android, newest first, following [semantic versioning](https://semver.org).
A minor version closes a plan (SMUG-PLAN-N) and a patch is any other task approved in between.
Each line names its Lific issue.

This file is generated from git by ``scripts\changelog.ps1``: do not edit it by hand.

"@
$content = ($header + "`n" + ($blocks -join "`n")) -replace "`r`n", "`n"
[System.IO.File]::WriteAllText((Join-Path $root 'CHANGELOG.md'), $content, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "==> CHANGELOG.md: $($blocks.Count) versions"
