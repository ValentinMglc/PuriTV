param(
    [string]$IP = "",
    [switch]$GarderPuriTV
)
$ici = Split-Path -Parent $MyInvocation.MyCommand.Path
. "$ici\tv-commun.ps1"

try { $adb = Trouver-Adb } catch { Write-Error $_; exit 1 }
Write-Host "Recherche de la télé..."
$cible = Connecter-Tele $adb $IP
if (-not $cible) { Write-Error $MessageIntrouvable; exit 2 }
Write-Host "Télé : $cible"
$t = @("-s", $cible)

$liste = @()
if (Test-Path $ListePaquets) { $liste = @(Get-Content $ListePaquets | ForEach-Object { $_.Trim() } | Where-Object { $_ }) }
Write-Host "`nRéactivation de $($liste.Count) applis..."
foreach ($p in $liste) {
    $r = & $adb @t shell pm enable --user 0 $p 2>&1
    Write-Host "  $p -> $r"
}

Write-Host "`nRéglages d'origine..."
foreach ($r in Lire-Reglages $ReglagesAvant) {
    if ($r.valeur -eq "null" -or $r.valeur -eq "") { & $adb @t shell settings delete $r.ns $r.cle | Out-Null }
    else { & $adb @t shell settings put $r.ns $r.cle $r.valeur }
    Write-Host "  $($r.ns) $($r.cle) -> $($r.valeur)"
}
& $adb @t shell settings delete global puritv_dns_bloqueur | Out-Null

$origine = Lire-AccueilOrigine
if ($origine) {
    Write-Host "`nÉcran d'accueil d'origine : $origine"
    & $adb @t shell cmd package set-home-activity $origine | Out-Null
}
if (-not $GarderPuriTV) {
    Write-Host "Désinstallation de PuriTV..."
    & $adb @t uninstall $PaquetPuriTV
}

$suffixe = Get-Date -Format 'yyyy-MM-dd-HHmm'
foreach ($f in $ListePaquets, $ReglagesVoulus, $MarqueurPuriTV) {
    if (Test-Path $f) { Move-Item $f ($f -replace '\.txt$', "-restaure-$suffixe.txt") }
}
if (Test-Path $ReglagesAvant) { Move-Item $ReglagesAvant ($ReglagesAvant -replace '\.txt$', "-restaure-$suffixe.txt") }

& $adb @t shell input keyevent KEYCODE_HOME
Start-Sleep 4
Capturer $adb $t "capture-apres-restauration"
Write-Host "`nTerminé. Capture : sauvegarde-tv\capture-apres-restauration.png"
Write-Host "Pour comparer : sauvegarde-tv\capture-avant.png"
