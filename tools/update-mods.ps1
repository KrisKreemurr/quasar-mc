# Quasar MC: pulls the newest successful CI builds of all our mods straight into the MultiMC mods folder.
# Needs the GitHub CLI once:  winget install GitHub.cli   then   gh auth login
# Run it by double-clicking update-mods.bat (or: powershell -ExecutionPolicy Bypass -File update-mods.ps1)

param(
    [string]$Mods = "G:\MultiMC\instances\26.3\.minecraft\mods"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Host "GitHub CLI not found. Install it with:  winget install GitHub.cli   then run:  gh auth login" -ForegroundColor Red
    exit 1
}
if (-not (Test-Path $Mods)) {
    Write-Host "Mods folder not found: $Mods" -ForegroundColor Red
    exit 1
}

# repo, branch, artifact name, jars to install, old jars to remove first
$sources = @(
    @{ Repo = "KrisKreemurr/quasar-mc";         Branch = "main";        Artifact = "quasar-mc-mods";
       Take = @("quasarmc_core-*.jar", "quasarmc_lighting-*.jar", "quasarmc_quasium-*.jar");
       Remove = @("quasarmc_core-*.jar", "quasarmc_lighting-*.jar", "quasarmc_quasium-*.jar") },
    @{ Repo = "KrisKreemurr/quasar-figura";     Branch = "quasar/26.3"; Artifact = "figura-quasar-neoforge";
       Take = @("figura*.jar");
       Remove = @("figura*.jar") },
    @{ Repo = "KrisKreemurr/quasar-horizons";   Branch = "quasar/26.3"; Artifact = "quasar-horizons-26.3";
       Take = @("DistantHorizons-*-fabric-neoforge.jar");
       Remove = @("DistantHorizons*.jar") }
)

$temp = Join-Path $env:TEMP "quasar-mods-update"

foreach ($s in $sources) {
    Write-Host "== $($s.Repo) ($($s.Branch))" -ForegroundColor Cyan
    $runId = gh run list -R $s.Repo -b $s.Branch -w build.yml --status success -L 1 --json databaseId -q ".[0].databaseId"
    if (-not $runId) { Write-Host "   no successful build found, skipping" -ForegroundColor Yellow; continue }

    if (Test-Path $temp) { Remove-Item $temp -Recurse -Force }
    gh run download $runId -R $s.Repo -n $s.Artifact -D $temp
    if ($LASTEXITCODE -ne 0) { Write-Host "   download failed, skipping" -ForegroundColor Yellow; continue }

    $new = @()
    foreach ($pattern in $s.Take) {
        $new += Get-ChildItem $temp -Recurse -Filter $pattern | Where-Object { $_.Name -notmatch "-(sources|dev|all)\.jar$" }
    }
    if ($new.Count -eq 0) { Write-Host "   no jars in that build, skipping" -ForegroundColor Yellow; continue }

    foreach ($pattern in $s.Remove) {
        Get-ChildItem $Mods -Filter $pattern | ForEach-Object { Write-Host "   - $($_.Name)"; Remove-Item $_.FullName -Force }
    }
    foreach ($jar in $new) {
        Copy-Item $jar.FullName $Mods -Force
        Write-Host "   + $($jar.Name)  (build $runId)" -ForegroundColor Green
    }
}

if (Test-Path $temp) { Remove-Item $temp -Recurse -Force }
Write-Host "Done. Mods folder: $Mods" -ForegroundColor Cyan
