$ErrorActionPreference = "Stop"
$ici = Split-Path -Parent $MyInvocation.MyCommand.Path
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
$bt  = "$sdk\build-tools\34.0.0"
$jar = "$sdk\platforms\android-28\android.jar"
$jdk = if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\javac.exe")) { $env:JAVA_HOME } else {
    Get-ChildItem "C:\Program Files\Microsoft" -Directory -Filter "jdk-17*" -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $jdk) { throw "Java 17 introuvable. Installe-le avec : winget install Microsoft.OpenJDK.17" }
if (-not (Test-Path "$bt\aapt2.exe") -or -not (Test-Path $jar)) {
    throw "SDK Android incomplet dans $sdk. Installe : sdkmanager `"platforms;android-28`" `"build-tools;34.0.0`""
}
$env:JAVA_HOME = $jdk; $env:Path = "$jdk\bin;$env:Path"

$app = $ici; $out = "$ici\build"; $racine = Split-Path -Parent $ici
if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Force "$out\res", "$out\gen", "$out\classes", "$out\dex" | Out-Null

& "$bt\aapt2.exe" compile --dir "$app\res" -o "$out\res.zip"
if ($LASTEXITCODE) { throw "aapt2 compile" }
& "$bt\aapt2.exe" link -o "$out\base.apk" -I $jar --manifest "$app\AndroidManifest.xml" `
    --java "$out\gen" --min-sdk-version 21 --target-sdk-version 28 "$out\res.zip"
if ($LASTEXITCODE) { throw "aapt2 link" }

$sources = Get-ChildItem "$app\src", "$out\gen" -Recurse -Filter *.java | ForEach-Object FullName
$ErrorActionPreference = "Continue"
& javac -encoding UTF-8 --release 8 -classpath $jar -d "$out\classes" @sources 2>&1 | ForEach-Object { "$_" }
$ErrorActionPreference = "Stop"
if ($LASTEXITCODE) { throw "javac" }

$classes = Get-ChildItem "$out\classes" -Recurse -Filter *.class | ForEach-Object FullName
& "$bt\d8.bat" --lib $jar --min-api 21 --release --output "$out\dex" @classes
if ($LASTEXITCODE) { throw "d8" }

Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open("$out\base.apk", "Update")
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, "$out\dex\classes.dex", "classes.dex") | Out-Null
Get-ChildItem "$app\assets" -Recurse -File | ForEach-Object {
    $nom = $_.FullName.Substring($app.Length + 1).Replace('\', '/')
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $_.FullName, $nom,
        [System.IO.Compression.CompressionLevel]::NoCompression) | Out-Null
}
$zip.Dispose()

& "$bt\zipalign.exe" -f -p 4 "$out\base.apk" "$out\aligned.apk"
if ($LASTEXITCODE) { throw "zipalign" }

$cle = "$ici\puritv.keystore"
$fichierMdp = "$cle.motdepasse"
if ((Test-Path $cle) -and -not (Test-Path $fichierMdp)) {
    "puritv-local" | Out-File -Encoding ascii -NoNewline $fichierMdp
}
if (-not (Test-Path $cle)) {
    $mdp = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 24 | ForEach-Object { [char]$_ })
    $mdp | Out-File -Encoding ascii -NoNewline $fichierMdp
    $ErrorActionPreference = "Continue"
    & keytool -genkeypair -keystore $cle -storepass $mdp -keypass $mdp -alias puritv `
        -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=PuriTV" 2>&1 | Out-Null
    $ErrorActionPreference = "Stop"
    if (-not (Test-Path $cle)) { throw "keytool" }
}
$mdp = (Get-Content $fichierMdp -Raw).Trim()
& "$bt\apksigner.bat" sign --ks $cle --ks-pass "pass:$mdp" --key-pass "pass:$mdp" `
    --out "$racine\PuriTV.apk" "$out\aligned.apk"
if ($LASTEXITCODE) { throw "apksigner" }
"OK -> $racine\PuriTV.apk"
