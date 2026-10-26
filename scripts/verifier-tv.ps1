param(
    [string]$IP = "",
    [switch]$Simulation,
    [switch]$SansAccueil
)
$ici = Split-Path -Parent $MyInvocation.MyCommand.Path
. "$ici\tv-commun.ps1"

if (-not (Test-Path $ListePaquets) -and -not (Test-Path $ReglagesVoulus) -and -not (Test-Path $MarqueurPuriTV)) {
    Write-Host "Rien à vérifier : PuriTV n'a encore rien changé sur la télé (lance d'abord installer.bat)."
    exit 0
}

try { $adb = Trouver-Adb } catch { Write-Error $_; exit 1 }
$cible = Connecter-Tele $adb $IP
if (-not $cible) { Write-Error $MessageIntrouvable; exit 2 }
$t = @("-s", $cible)
$corrections = 0

function Corriger([string]$quoi, [scriptblock]$action) {
    $script:corrections++
    if ($Simulation) { Write-Host "  [à corriger] $quoi" -ForegroundColor Yellow }
    else { & $action | Out-Null; Write-Host "  [corrigé] $quoi" -ForegroundColor Green }
}

Write-Host "Télé : $cible  —  système : $(& $adb @t shell getprop ro.build.fingerprint)"

if (Test-Path $MarqueurPuriTV) {
    Write-Host "`nÉcran d'accueil PuriTV..."
    if (-not ((& $adb @t shell pm list packages $PaquetPuriTV) -match "package:$([regex]::Escape($PaquetPuriTV))$")) {
        $apk = Join-Path $Racine "PuriTV.apk"
        if (Test-Path $apk) { Corriger "PuriTV absent : réinstallation" { & $adb @t install -r $apk } }
        else { Write-Host "  [info] PuriTV absent et PuriTV.apk introuvable : réinstalle-le avec installer.bat" }
    }
    if (-not ((& $adb @t shell dumpsys package $PaquetPuriTV) -match "WRITE_SECURE_SETTINGS: granted=true")) {
        Corriger "droit WRITE_SECURE_SETTINGS de PuriTV" { & $adb @t shell pm grant $PaquetPuriTV android.permission.WRITE_SECURE_SETTINGS }
    }
    if (-not ((& $adb @t shell appops get $PaquetPuriTV WRITE_SETTINGS) -match "allow")) {
        Corriger "droit WRITE_SETTINGS de PuriTV" { & $adb @t shell appops set $PaquetPuriTV WRITE_SETTINGS allow }
    }
    $accueil = & $adb @t shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
    if (-not ($accueil -match [regex]::Escape($PaquetPuriTV))) {
        Corriger "écran d'accueil par défaut" { & $adb @t shell cmd package set-home-activity "$PaquetPuriTV/.MainActivity" }
    }
}

if (Test-Path $ListePaquets) {
    Write-Host "Applis désactivées..."
    $desactivees = (& $adb @t shell pm list packages -d) -replace '^package:', ''
    foreach ($p in Get-Content $ListePaquets | ForEach-Object { $_.Trim() } | Where-Object { $_ }) {
        if ($desactivees -notcontains $p) {
            $existe = (& $adb @t shell pm list packages $p) -match "package:$([regex]::Escape($p))$"
            if ($existe) { Corriger "$p réactivé par une mise à jour" { & $adb @t shell pm disable-user --user 0 $p } }
            else { Write-Host "  [info] $p n'existe plus sur la télé" }
        }
    }
}

if (Test-Path $ReglagesVoulus) {
    Write-Host "Réglages..."
    $bloqueurVoulu = (& $adb @t shell settings get global puritv_dns_bloqueur) -ne "0"
    if (-not $bloqueurVoulu) { Write-Host "  [info] bloqueur de pubs coupé depuis la télé : laissé tel quel" }
    foreach ($r in Lire-Reglages $ReglagesVoulus) {
        if (-not $bloqueurVoulu -and $r.cle -like "private_dns_*") { continue }
        if ((& $adb @t shell settings get $r.ns $r.cle) -ne $r.valeur) {
            $ns, $cle, $val = $r.ns, $r.cle, $r.valeur
            Corriger $cle { & $adb @t shell settings put $ns $cle $val }
        }
    }
}

if ($corrections -eq 0) { Write-Host "`nTout est en place, rien à corriger." -ForegroundColor Green }
elseif ($Simulation) { Write-Host "`n$corrections point(s) à corriger. Relance sans -Simulation pour corriger." }
else {
    if (-not $SansAccueil -and (Test-Path $MarqueurPuriTV)) { & $adb @t shell input keyevent KEYCODE_HOME }
    Write-Host "`n$corrections correction(s) appliquée(s)."
}
