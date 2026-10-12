$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$temporaryRoot = Join-Path $repoRoot 'tmp'
$testRoot = Join-Path $temporaryRoot ('updater-check-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot | Out-Null
$seed = Join-Path $testRoot 'seed'
& git init --quiet $seed
if ($LASTEXITCODE -ne 0) { throw 'Fixture initialization failed' }
Set-Content -LiteralPath (Join-Path $seed 'sample.txt') -Value 'original'
& git -C $seed add sample.txt
& git -C $seed -c user.name=AnchorTest -c user.email=anchor-test@example.invalid commit --quiet -m fixture
if ($LASTEXITCODE -ne 0) { throw 'Fixture commit failed' }

function Invoke-UpdaterCase([string]$Name, [string]$Setup, [int]$BuildExit, [int]$ExpectedExit, [string]$ExpectedText) {
    $fixture = Join-Path $testRoot $Name
    & git clone --local --quiet $seed $fixture
    if ($LASTEXITCODE -ne 0) { throw 'Fixture clone failed' }
    Copy-Item -LiteralPath (Join-Path $repoRoot 'Update-Anchor.bat') -Destination $fixture
    Set-Content -LiteralPath (Join-Path $fixture 'gradlew.bat') -Value "@echo off`r`necho FIXTURE_BUILD`r`nexit /b $BuildExit" -Encoding Ascii
    switch ($Setup) {
        'no-remote' { & git -C $fixture remote remove origin }
        'detached' { & git -C $fixture checkout --quiet --detach }
        'locked' { Set-Content -LiteralPath (Join-Path $fixture '.anchor_locked') -Value 'locked' }
        'dirty' {
            Set-Content -LiteralPath (Join-Path $fixture 'sample.txt') -Value 'old stashed change'
            & git -C $fixture stash push --quiet -m 'pre-existing stash'
            if ($LASTEXITCODE -ne 0) { throw 'Could not create the pre-existing stash fixture' }
            $existingStash = & git -C $fixture rev-parse 'stash@{0}'
            Set-Content -LiteralPath (Join-Path $fixture 'sample.txt') -Value 'local change'
            Set-Content -LiteralPath (Join-Path $seed 'remote.txt') -Value 'incoming update'
            & git -C $seed add remote.txt
            & git -C $seed -c user.name=AnchorTest -c user.email=anchor-test@example.invalid commit --quiet -m update
            if ($LASTEXITCODE -ne 0) { throw 'Could not create the incoming update fixture' }
        }
    }
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = 'cmd.exe'
    $info.Arguments = '/d /c Update-Anchor.bat'
    $info.WorkingDirectory = $fixture
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $process = [Diagnostics.Process]::Start($info)
    $process.StandardInput.WriteLine('n')
    $process.StandardInput.Close()
    $output = $process.StandardOutput.ReadToEnd() + $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne $ExpectedExit -or !$output.Contains($ExpectedText)) {
        throw "Case $Name failed (exit $($process.ExitCode)): $output"
    }
    if ($ExpectedExit -ne 0 -and $output.Contains('Source update and Windows build succeeded.')) {
        throw "Case $Name falsely reported success"
    }
    if ($Setup -eq 'dirty') {
        if ((Get-Content -LiteralPath (Join-Path $fixture 'sample.txt') -Raw).Trim() -ne 'local change') {
            throw 'Local workspace change was lost'
        }
        $preservedStash = & git -C $fixture rev-parse 'stash@{0}'
        if ($LASTEXITCODE -ne 0 -or $preservedStash -ne $existingStash) { throw 'Updater changed the pre-existing stash' }
        if (!(Test-Path -LiteralPath (Join-Path $fixture 'remote.txt'))) { throw 'Updater did not apply incoming commits' }
    }
    Write-Output "PASS: $Name"
}

try {
    Invoke-UpdaterCase 'pull-failure' 'no-remote' 0 1 'Source update failed.'
    Invoke-UpdaterCase 'build-failure' '' 7 1 'Windows build failed.'
    Invoke-UpdaterCase 'detached-head' 'detached' 0 1 'open a local Git branch'
    Invoke-UpdaterCase 'protected-folder' 'locked' 0 1 'deletion protection enabled'
    Invoke-UpdaterCase 'successful-dirty-workspace' 'dirty' 0 0 'Source update and Windows build succeeded.'
} finally {
    $allowedParent = [IO.Path]::GetFullPath($temporaryRoot) + [IO.Path]::DirectorySeparatorChar
    $resolvedTarget = [IO.Path]::GetFullPath($testRoot)
    if (!$resolvedTarget.StartsWith($allowedParent, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Refusing to remove a fixture outside the workspace temporary directory'
    }
    Remove-Item -LiteralPath $resolvedTarget -Recurse -Force
}
