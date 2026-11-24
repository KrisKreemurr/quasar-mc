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
gh auth status *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "GitHub CLI is installed but not signed in." -ForegroundColor Red
    Write-Host "Open a NEW Command Prompt (not as administrator) and run:  gh auth login" -ForegroundColor Red
    Write-Host "Choose GitHub.com, HTTPS, Login with a web browser, then enter the code at github.com/login/device." -ForegroundColor Red
    exit 1
}
# Keep this script itself up to date: fetch the newest copy from GitHub and rerun it if it changed.
if (-not $env:QUASAR_UPDATER_FRESH) {
    $self = $MyInvocation.MyCommand.Path
    $latest = gh api repos/KrisKreemurr/quasar-mc/contents/tools/update-mods.ps1 -H "Accept: application/vnd.github.raw" 2>$null
    if ($LASTEXITCODE -eq 0 -and $latest) {
        $text = ($latest -join "`r`n")
        $current = Get-Content $self -Raw
        if ($text.Trim() -ne $current.Trim()) {
            Write-Host "Updating the updater itself..." -ForegroundColor Cyan
            Set-Content -Path $self -Value $text -Encoding UTF8
            $env:QUASAR_UPDATER_FRESH = "1"
            & powershell -NoProfile -ExecutionPolicy Bypass -File $self -Mods $Mods
            exit $LASTEXITCODE
        }
    }
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
       Take = @("QuasarHorizons-*-fabric-neoforge.jar", "DistantHorizons-*-fabric-neoforge.jar");
       Remove = @("QuasarHorizons*.jar", "DistantHorizons*.jar") },
    @{ Repo = "KrisKreemurr/quasar-creativecore"; Branch = "quasar/26.3"; Artifact = "quasar-creativecore-26.3";
       Take = @("CreativeCore_NEOFORGE_v*.jar");
       Remove = @("CreativeCore*.jar", "creativecore*.jar") },
    # LittleTiles 26.3 port: first-boot build (tiles load/save/work but are not drawn yet - render rewrite pending)
    @{ Repo = "KrisKreemurr/quasar-littletiles"; Branch = "quasar/26.3"; Artifact = "quasar-littletiles-26.3";
       Take = @("LittleTiles_BETA_v*.jar");
       Remove = @("LittleTiles*.jar", "littletiles*.jar") },
    # Meshelium fork (LGPL-3.0): mesh-shader terrain + Quasar lighting changes
    @{ Repo = "KrisKreemurr/quasar-meshelium"; Branch = "quasar/26.3"; Artifact = "quasar-meshelium-26.3";
       Take = @("QuasarMeshelium-neoforge-*.jar");
       Remove = @("QuasarMeshelium*.jar", "meshelium*.jar", "Meshelium*.jar") },
    # Immersive Portals fork (Apache-2.0): seamless portals, 26.3 port (renderer replaced by Quasar MC's)
    @{ Repo = "KrisKreemurr/quasar-immersiveportals"; Branch = "quasar/26.3"; Artifact = "quasar-immersiveportals-26.3";
       Take = @("QuasarImmersivePortals-*.jar");
       Remove = @("QuasarImmersivePortals*.jar", "immersive_portals*.jar", "immersive-portals*.jar", "imm_ptl*.jar") }
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
