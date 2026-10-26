$ici = Split-Path -Parent $MyInvocation.MyCommand.Path
$nom = "PuriTV - vérification TV"

$action = New-ScheduledTaskAction -Execute "powershell.exe" `
    -Argument "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$ici\verifier-auto.ps1`""
$declencheur = New-ScheduledTaskTrigger -Daily -At 19:00
$declencheur.StartBoundary = (Get-Date "19:00").ToString("yyyy-MM-ddTHH:mm:ss")
$reglages = New-ScheduledTaskSettingsSet -StartWhenAvailable -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries -ExecutionTimeLimit (New-TimeSpan -Minutes 10)

try {
    Register-ScheduledTask -TaskName $nom -Action $action -Trigger $declencheur -Settings $reglages -Force `
        -Description "Vérifie chaque semaine que l'allègement de la télé TCL est toujours en place (journal : verifier-auto.log)." `
        -ErrorAction Stop | Out-Null
} catch {
    Write-Host "Méthode 1 refusée ($($_.Exception.Message.Trim())), essai avec schtasks..." -ForegroundColor Yellow
    $commande = "powershell.exe -NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$ici\verifier-auto.ps1`""
    schtasks /Create /F /SC DAILY /ST 19:00 /TN $nom /TR $commande
    if ($LASTEXITCODE) {
        Write-Host "`nÉchec : la tâche n'a pas pu être créée. Fais un clic droit sur activer-verification-auto.bat > Exécuter en tant qu'administrateur." -ForegroundColor Red
        exit 1
    }
}

$info = Get-ScheduledTaskInfo -TaskName $nom -ErrorAction SilentlyContinue
if (-not $info) {
    Write-Host "`nÉchec : la tâche est introuvable après création." -ForegroundColor Red
    exit 1
}
Write-Host "Tâche « $nom » créée. Prochaine exécution : $($info.NextRunTime)" -ForegroundColor Green
Write-Host "Journal des vérifications : $(Split-Path -Parent $ici)\verifier-auto.log"
