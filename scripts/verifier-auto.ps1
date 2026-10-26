$ici = Split-Path -Parent $MyInvocation.MyCommand.Path
$racine = Split-Path -Parent $ici
$journal = "$racine\verifier-auto.log"
$repere = "$racine\sauvegarde-tv\derniere-verification.txt"

function Noter([string]$texte) {
    "$(Get-Date -Format 'yyyy-MM-dd HH:mm')  $texte" | Out-File -Append -Encoding utf8 $journal
}

if (Test-Path $repere) {
    $derniere = [datetime]::ParseExact((Get-Content $repere -Raw).Trim(), 'yyyy-MM-dd HH:mm', $null)
    if ((Get-Date) - $derniere -lt [timespan]::FromDays(7)) { exit 0 }
}

$sortie = & powershell -NoProfile -ExecutionPolicy Bypass -File "$ici\verifier-tv.ps1" -SansAccueil 2>&1 | ForEach-Object { "$_" }
$code = $LASTEXITCODE
if ($code -eq 2) { Noter "Télé introuvable (éteinte ?), nouvel essai demain."; exit 0 }
$sortie | Where-Object { $_ -match '\[corrigé\]|\[info\]|Tout est en place|correction|nouvelle adresse' } | ForEach-Object { Noter $_.Trim() }
if ($code -eq 0) {
    Get-Date -Format 'yyyy-MM-dd HH:mm' | Out-File -Encoding ascii $repere
} else {
    Noter "Échec de la vérification (code $code) : $($sortie | Select-Object -Last 1)"
}
