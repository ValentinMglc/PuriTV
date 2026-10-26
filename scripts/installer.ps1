param(
    [string]$IP = "",
    [switch]$Simulation,
    [string]$Reponses = ""
)
$ici = Split-Path -Parent $MyInvocation.MyCommand.Path
. "$ici\tv-commun.ps1"

function Titre([string]$texte) { Write-Host "`n=== $texte ===" -ForegroundColor Cyan }
function Info([string]$texte) { Write-Host $texte -ForegroundColor DarkGray }

$script:fileReponses = New-Object System.Collections.Queue
if ($Reponses) { $Reponses -split ',' | ForEach-Object { $script:fileReponses.Enqueue($_.Trim()) } }

function Lire([string]$question) {
    if ($script:fileReponses.Count -gt 0) {
        $r = $script:fileReponses.Dequeue()
        Write-Host "$question $r"
    } elseif ($Reponses) {
        return $null
    } else {
        $r = Read-Host $question
    }
    if ($null -eq $r) { return $null }
    return $r.Trim().ToLower()
}

function Demander([string]$question, [bool]$defaut = $true) {
    $choix = if ($defaut) { "[O/n]" } else { "[o/N]" }
    while ($true) {
        $r = Lire "$question $choix"
        if ($null -eq $r -or $r -eq "") { return $defaut }
        if ($r -in "o", "oui", "y") { return $true }
        if ($r -in "n", "non") { return $false }
    }
}

function Agir([string]$quoi, [scriptblock]$action) {
    if ($Simulation) { Write-Host "  [simulation] $quoi" -ForegroundColor Yellow; return }
    & $action
    Write-Host "  [fait] $quoi" -ForegroundColor Green
}

Write-Host @"

  PuriTV — installation guidée
  ----------------------------
  1. sauvegarde complète de la télé (rien n'est modifié avant)
  2. désactivation des pubs, de la télémétrie et des applis inutiles, en te demandant à chaque fois
  3. écran d'accueil PuriTV (facultatif)
  4. quelques réglages (facultatifs)
  Rien n'est désinstallé : restaurer-tv.bat remet tout comme avant.
"@
if ($Simulation) { Write-Host "  MODE SIMULATION : rien ne sera modifié sur la télé.`n" -ForegroundColor Yellow }

Titre "Connexion à la télé"
try { $adb = Trouver-Adb } catch {
    Write-Host "Les outils Android (adb) ne sont pas installés sur ce PC."
    if (-not (Demander "Les installer maintenant depuis le site de Google (winget, environ 7 Mo) ?")) { exit 1 }
    winget install --id Google.PlatformTools -e --accept-source-agreements --accept-package-agreements
    try { $adb = Trouver-Adb } catch { Write-Error "adb toujours introuvable. Ferme cette fenêtre et relance installer.bat."; exit 1 }
}

$cible = $null
while (-not $cible) {
    $cible = Connecter-Tele $adb $IP
    if ($cible) { break }
    if ((& $adb devices) -match 'unauthorized') {
        Write-Host "`nLa télé demande ton autorisation : sur l'écran, coche « Toujours autoriser » puis OK." -ForegroundColor Yellow
    } else {
        Write-Host @"

Télé introuvable. Sur la télé, avec la télécommande :
  1. Paramètres > Système > À propos : appuie 7 fois sur « Build » (mode développeur activé)
  2. Paramètres > Système > Options pour les développeurs : active « Débogage USB »
  3. Vérifie que la télé et ce PC sont sur le même réseau (même box).
"@ -ForegroundColor Yellow
    }
    $r = Lire "Entrée pour réessayer, Q pour quitter"
    if ($null -eq $r -or $r -eq "q") { exit 2 }
}
$t = @("-s", $cible)
$modele = "$((& $adb @t shell getprop ro.product.manufacturer) -join '') $((& $adb @t shell getprop ro.product.model) -join '')"
$android = (& $adb @t shell getprop ro.build.version.release) -join ''
Write-Host "Télé trouvée : $modele, Android $android ($cible)" -ForegroundColor Green

$profil = Choisir-Profil $adb $t
Write-Host "Profil utilisé : $($profil.nom)  ($($profil.fichier))"
if ($profil.correspond.fabricant -eq "*") {
    Info "Pas de profil dédié à cette télé : seuls des éléments Google courants seront proposés."
    Info "Tu peux en créer un dans profils\ et le partager sur GitHub."
}

Titre "1. Sauvegarde"
if ($Simulation) { Write-Host "  [simulation] sauvegarde complète dans sauvegarde-tv\" -ForegroundColor Yellow }
elseif (Faire-Sauvegarde $adb $t) { Write-Host "  [fait] sauvegarde complète dans sauvegarde-tv\ (applis, réglages, capture d'écran)" -ForegroundColor Green }
else { Info "Sauvegarde d'origine déjà présente : on la garde telle quelle." }

Titre "2. Allègement"
$actives = (& $adb @t shell pm list packages -e) -replace '^package:', ''
$jamais = @($profil.jamais.PSObject.Properties.Name)
foreach ($cat in $profil.categories) {
    $presents = @($cat.paquets.PSObject.Properties | Where-Object { $actives -contains $_.Name -and $jamais -notcontains $_.Name })
    if (-not $presents) { continue }
    Write-Host "`n$($cat.titre)" -ForegroundColor White
    Info "  $($cat.explication)"
    foreach ($p in $presents) { Write-Host "   - $($p.Value)  ($($p.Name))" }
    $r = Lire "Désactiver $(if ($presents.Count -gt 1) { "ces $($presents.Count) applis" } else { 'cette appli' }) ? $(if ($cat.recommande) { '[O/n' } else { '[o/N' })/D = une par une]"
    if ($null -eq $r) { $r = "" }
    $choisis = @()
    if ($r -eq "d") {
        foreach ($p in $presents) { if (Demander "   Désactiver « $($p.Value) » ?" $cat.recommande) { $choisis += $p } }
    } elseif ($r -in "o", "oui", "y" -or ($r -eq "" -and $cat.recommande)) {
        $choisis = $presents
    }
    foreach ($p in $choisis) {
        $nom = $p.Name
        Agir "désactivé : $($p.Value)" { if (-not (Desactiver-Paquet $adb $t $nom)) { Write-Host "  [échec] $nom" -ForegroundColor Red } }
    }
}

Titre "3. Écran d'accueil PuriTV"
Info "Accueil minimaliste façon Apple TV : photos nature, heure, météo, dock de tes applis, économiseur d'écran."
if (Demander "Installer l'écran d'accueil PuriTV ?") {
    $apk = Join-Path $Racine "PuriTV.apk"
    if (-not (Test-Path $apk)) {
        Write-Host "  PuriTV.apk est absent." -ForegroundColor Yellow
        Write-Host "  Télécharge PuriTV.apk depuis la page « Releases » du projet sur GitHub et place-le à côté de installer.bat,"
        Write-Host "  ou compile-le avec app\build.ps1 (voir README)."
        Lire "Entrée quand c'est fait" | Out-Null
    }
    if (Test-Path $apk) {
        Agir "PuriTV installé, écran d'accueil et économiseur d'écran" { Installer-PuriTV $adb $t $apk }
        foreach ($p in $profil.ecrans_accueil_remplaces.PSObject.Properties | Where-Object { $actives -contains $_.Name }) {
            $nom = $p.Name
            Agir "désactivé (remplacé par PuriTV) : $($p.Value)" { Desactiver-Paquet $adb $t $nom | Out-Null }
        }
        Info "Au premier affichage, PuriTV te demandera ta ville pour la météo (modifiable ensuite dans ⚙)."
    } else {
        Write-Host "  PuriTV non installé. Tu pourras relancer installer.bat plus tard." -ForegroundColor Yellow
    }
}

Titre "4. Réglages facultatifs"
if (Demander "Animations 2 fois plus rapides (la télé paraît plus vive) ?") {
    Agir "animations ×0,5" {
        foreach ($k in "window_animation_scale", "transition_animation_scale", "animator_duration_scale") { Appliquer-Reglage $adb $t global $k "0.5" }
    }
}
Info "`nBloqueur de pubs par DNS (AdGuard) : bloque pubs et traqueurs dans beaucoup d'applis, pas sur YouTube ni Netflix."
Info "Si un réseau le refuse, PuriTV le coupe tout seul ; sans PuriTV, il se coupe dans les réglages réseau de la télé."
if (Demander "Activer le bloqueur de pubs DNS ?") {
    Agir "bloqueur de pubs DNS (AdGuard)" {
        Appliquer-Reglage $adb $t global private_dns_specifier "dns.adguard-dns.com"
        Appliquer-Reglage $adb $t global private_dns_mode "hostname"
        & $adb @t shell settings put global puritv_dns_bloqueur 1
    }
}
if (Demander "Activer HDMI-CEC (piloter console ou box avec la télécommande de la télé) ?" $false) {
    Agir "HDMI-CEC" {
        foreach ($k in "hdmi_control_enabled", "hdmi_control_auto_wakeup_enabled", "hdmi_control_auto_device_off_enabled") { Appliquer-Reglage $adb $t global $k "1" }
    }
}

Titre "Terminé"
if (-not $Simulation) {
    & $adb @t shell input keyevent KEYCODE_HOME
    Start-Sleep 4
    Capturer $adb $t "capture-apres-installation"
    Write-Host "Capture de la télé : sauvegarde-tv\capture-apres-installation.png"
}
Write-Host @"

  - Pour tout remettre comme avant : restaurer-tv.bat
  - Après une mise à jour de la télé : verifier-tv.bat (ou activer-verification-auto.bat,
    en tant qu'administrateur, pour une vérification automatique chaque semaine)
"@
