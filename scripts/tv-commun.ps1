$script:Racine = Split-Path -Parent $PSScriptRoot
$script:ConfigTv = Join-Path $script:Racine "config-tv.json"
$script:Sauvegarde = Join-Path $script:Racine "sauvegarde-tv"
$script:ListePaquets = Join-Path $script:Sauvegarde "paquets-desactives-par-puritv.txt"
$script:ReglagesAvant = Join-Path $script:Sauvegarde "reglages-modifies-avant.txt"
$script:ReglagesVoulus = Join-Path $script:Sauvegarde "reglages-appliques.txt"
$script:MarqueurPuriTV = Join-Path $script:Sauvegarde "puritv-installe.txt"
$script:PaquetPuriTV = "app.puritv"

function Trouver-Adb {
    $adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
    if (-not $adb) {
        $adb = Get-ChildItem "$env:LOCALAPPDATA\Microsoft\WinGet\Packages" -Recurse -Filter adb.exe -ErrorAction SilentlyContinue |
            Select-Object -First 1 -ExpandProperty FullName
    }
    if (-not $adb) { throw "adb introuvable. Installe-le avec : winget install Google.PlatformTools" }
    return $adb
}

function Lire-ConfigTv {
    if (Test-Path $script:ConfigTv) {
        try { return Get-Content $script:ConfigTv -Raw | ConvertFrom-Json } catch { }
    }
    return [pscustomobject]@{ ip = ""; serie = "" }
}

function Ecrire-ConfigTv([string]$ip, [string]$serie) {
    [pscustomobject]@{ ip = $ip; serie = $serie; vue_le = (Get-Date -Format 'yyyy-MM-dd HH:mm') } |
        ConvertTo-Json | Out-File -Encoding utf8 $script:ConfigTv
}

function Scanner-Reseau {
    $prefixes = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object { $_.IPAddress -notmatch '^(127\.|169\.254\.)' -and $_.PrefixOrigin -ne 'WellKnown' } |
        ForEach-Object { ($_.IPAddress -split '\.')[0..2] -join '.' } | Select-Object -Unique
    $essais = foreach ($p in $prefixes) {
        foreach ($n in 1..254) {
            $c = New-Object System.Net.Sockets.TcpClient
            [pscustomobject]@{ ip = "$p.$n"; client = $c; attente = $c.BeginConnect("$p.$n", 5555, $null, $null) }
        }
    }
    Start-Sleep -Milliseconds 1500
    $ouverts = foreach ($e in $essais) {
        if ($e.attente.IsCompleted -and $e.client.Connected) { $e.ip }
        $e.client.Close()
    }
    return @($ouverts)
}

function Lire-Serie([string]$adb, [string]$ip) {
    & $adb connect "${ip}:5555" 2>$null | Out-Null
    if ((& $adb -s "${ip}:5555" get-state 2>$null) -ne "device") { return $null }
    $s = (& $adb -s "${ip}:5555" shell getprop ro.serialno 2>$null) -join ''
    if ($s) { return $s.Trim() } else { return $null }
}

function Connecter-Tele([string]$adb, [string]$IP = "", [switch]$Silencieux) {
    $cfg = Lire-ConfigTv
    function Dire($m) { if (-not $Silencieux) { Write-Host $m } }

    foreach ($ip in @($IP, $cfg.ip) | Where-Object { $_ }) {
        $s = Lire-Serie $adb $ip
        if ($s -and (-not $cfg.serie -or $s -eq $cfg.serie)) {
            Ecrire-ConfigTv $ip $s
            return "${ip}:5555"
        }
    }

    if ($cfg.ip) { Dire "Télé absente de sa dernière adresse ($($cfg.ip)) : recherche sur le réseau..." }
    else { Dire "Recherche de la télé sur le réseau..." }
    foreach ($ip in Scanner-Reseau) {
        $s = Lire-Serie $adb $ip
        if (-not $s) { continue }
        if (-not $cfg.serie -or $s -eq $cfg.serie) {
            Dire "Télé trouvée à la nouvelle adresse $ip."
            Ecrire-ConfigTv $ip $s
            return "${ip}:5555"
        }
    }
    return $null
}

$script:MessageIntrouvable = "Télé introuvable. Vérifie qu'elle est allumée, sur le même réseau que le PC, " +
    "que le débogage USB est actif, et accepte le message d'autorisation s'il s'affiche à l'écran."

function Faire-Sauvegarde([string]$adb, [string[]]$t) {
    if (Test-Path (Join-Path $script:Sauvegarde "paquets-tous.txt")) { return $false }
    New-Item -ItemType Directory -Force $script:Sauvegarde | Out-Null
    $d = $script:Sauvegarde
    & $adb @t shell pm list packages -f -u | Out-File -Encoding utf8 "$d\paquets-tous.txt"
    & $adb @t shell pm list packages -e | Out-File -Encoding utf8 "$d\paquets-actifs.txt"
    & $adb @t shell pm list packages -d | Out-File -Encoding utf8 "$d\paquets-desactives.txt"
    & $adb @t shell pm list packages -3 | Out-File -Encoding utf8 "$d\paquets-tiers.txt"
    foreach ($ns in "global", "secure", "system") { & $adb @t shell settings list $ns | Out-File -Encoding utf8 "$d\settings-$ns.txt" }
    & $adb @t shell getprop | Out-File -Encoding utf8 "$d\getprop.txt"
    & $adb @t shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME |
        Out-File -Encoding utf8 "$d\launcher-actuel.txt"
    Capturer $adb $t "capture-avant"
    return $true
}

function Capturer([string]$adb, [string[]]$t, [string]$nom) {
    & $adb @t shell screencap -p /sdcard/puritv-capture.png
    & $adb @t pull /sdcard/puritv-capture.png (Join-Path $script:Sauvegarde "$nom.png") 2>$null | Out-Null
    & $adb @t shell rm /sdcard/puritv-capture.png
}

function Lire-AccueilOrigine {
    $f = Join-Path $script:Sauvegarde "launcher-actuel.txt"
    if (-not (Test-Path $f)) { return $null }
    return Get-Content $f | Where-Object { $_ -match '^[\w.]+/[\w.$]+$' } | Select-Object -Last 1
}

function Ajouter-Ligne([string]$fichier, [string]$ligne) {
    if ((Test-Path $fichier) -and ((Get-Content $fichier) -contains $ligne)) { return }
    $ligne | Out-File -Append -Encoding utf8 $fichier
}

function Desactiver-Paquet([string]$adb, [string[]]$t, [string]$paquet) {
    $r = (& $adb @t shell pm disable-user --user 0 $paquet 2>&1) -join ' '
    if ($r -match 'disabled') { Ajouter-Ligne $script:ListePaquets $paquet; return $true }
    return $false
}

function Appliquer-Reglage([string]$adb, [string[]]$t, [string]$ns, [string]$cle, [string]$valeur) {
    $deja = (Test-Path $script:ReglagesAvant) -and
        ((Get-Content $script:ReglagesAvant) -match "^$ns\s+$([regex]::Escape($cle))=")
    if (-not $deja) {
        $avant = (& $adb @t shell settings get $ns $cle) -join ''
        "$ns $cle=$($avant.Trim())" | Out-File -Append -Encoding utf8 $script:ReglagesAvant
    }
    & $adb @t shell settings put $ns $cle $valeur
    $lignes = @()
    if (Test-Path $script:ReglagesVoulus) {
        $lignes = @(Get-Content $script:ReglagesVoulus | Where-Object { $_ -notmatch "^$ns\s+$([regex]::Escape($cle))=" })
    }
    ($lignes + "$ns $cle=$valeur") | Out-File -Encoding utf8 $script:ReglagesVoulus
}

function Lire-Reglages([string]$fichier) {
    if (-not (Test-Path $fichier)) { return @() }
    foreach ($l in Get-Content $fichier) {
        if ($l -match '^(\w+)\s+([\w.]+)=(.*)$') {
            [pscustomobject]@{ ns = $Matches[1]; cle = $Matches[2]; valeur = $Matches[3].Trim() }
        }
    }
}

function Choisir-Profil([string]$adb, [string[]]$t) {
    $fabricant = ((& $adb @t shell getprop ro.product.manufacturer) -join '').Trim()
    $android = ((& $adb @t shell getprop ro.build.version.release) -join '').Trim()
    $profils = Get-ChildItem (Join-Path $script:Racine "profils") -Filter *.json |
        ForEach-Object { $p = Get-Content $_.FullName -Raw -Encoding UTF8 | ConvertFrom-Json; $p | Add-Member fichier $_.Name -PassThru }
    $exact = $profils | Where-Object { $_.correspond.fabricant -eq $fabricant -and $_.correspond.android -eq $android } | Select-Object -First 1
    if ($exact) { return $exact }
    return $profils | Where-Object { $_.correspond.fabricant -eq "*" } | Select-Object -First 1
}

function Installer-PuriTV([string]$adb, [string[]]$t, [string]$apk) {
    $r = (& $adb @t install -r $apk 2>&1) -join ' '
    if ($r -notmatch 'Success') { throw "Installation de PuriTV impossible : $r" }
    & $adb @t shell pm grant $script:PaquetPuriTV android.permission.WRITE_SECURE_SETTINGS
    & $adb @t shell appops set $script:PaquetPuriTV WRITE_SETTINGS allow
    & $adb @t shell cmd package set-home-activity "$($script:PaquetPuriTV)/.MainActivity" | Out-Null
    Appliquer-Reglage $adb $t secure screensaver_components "$($script:PaquetPuriTV)/.Veille"
    Get-Date -Format 'yyyy-MM-dd HH:mm' | Out-File -Encoding ascii $script:MarqueurPuriTV
}
