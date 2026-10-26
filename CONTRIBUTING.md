# Contribuer à PuriTV

Merci de vouloir aider ! Toutes les contributions sont les bienvenues, en particulier les profils pour de nouvelles télés.

## Les règles du projet

PuriTV touche à la télé des gens. Quelques principes ne se discutent pas :

1. **Tout est réversible.** Chaque changement est noté dans `sauvegarde-tv\`, et `restaurer-tv.bat` remet la télé exactement comme avant.
2. **On ne désinstalle jamais rien.** Les applis sont désactivées (`pm disable-user`), pas supprimées.
3. **On demande avant d'agir.** L'installateur explique chaque catégorie et attend la réponse de l'utilisateur.
4. **On ne touche jamais à ce qui fait marcher la télé :** image, son, chaînes, entrées HDMI, télécommande, mises à jour, Chromecast, Play Store.
5. **Aucune donnée personnelle dans le dépôt.** `sauvegarde-tv\` et `config-tv.json` restent sur le PC de chacun.

## Ajouter une télé (le plus utile)

Un profil est un fichier JSON dans [`profils/`](profils) qui dit à l'installateur quoi proposer sur un modèle de télé.

1. Connecte ta télé au PC (voir le README), puis récupère ses informations :
   ```
   adb shell getprop ro.product.manufacturer
   adb shell getprop ro.build.version.release
   adb shell pm list packages -s
   ```
2. Copie [`profils/tcl-android9.json`](profils/tcl-android9.json) sous un nouveau nom, par exemple `profils/sony-android10.json`.
3. Remplis-le :
   - `correspond` : fabricant et version d'Android, exactement comme les donnent les commandes ci-dessus ;
   - `categories` : les applis à proposer, regroupées par thème, chacune avec une courte description. `recommande: true` seulement pour ce qui est sans risque (pubs, télémétrie) ;
   - `ecrans_accueil_remplaces` : l'écran d'accueil et l'économiseur d'écran d'origine, désactivés quand PuriTV les remplace ;
   - `masques_dans_puritv` : les applis système à cacher de la grille de PuriTV sans les désactiver ;
   - `jamais` : tout ce qui fait marcher la télé. En cas de doute, une appli va ici.
4. Teste sans rien changer, puis pour de vrai :
   ```
   powershell -ExecutionPolicy Bypass -File scripts\installer.ps1 -Simulation
   ```
5. Vérifie que la télé marche normalement (image, son, chaînes, HDMI, télécommande), puis que `restaurer-tv.bat` remet tout comme avant.
6. Propose ton profil en pull request, ou ouvre un ticket **📺 Ajouter ma télé** si tu préfères qu'on le fasse ensemble.

## Modifier l'appli PuriTV

L'appli se trouve dans [`app/`](app) et se compile sans Android Studio ni Gradle :

```
powershell -ExecutionPolicy Bypass -File app\build.ps1
```

Prérequis : Java 17 (`winget install Microsoft.OpenJDK.17`) et le SDK Android en ligne de commande avec `platforms;android-28` et `build-tools;34.0.0`. Le script crée `PuriTV.apk` à la racine. Installe-le sur ta télé avec `adb install -r PuriTV.apk` pour tester.

Quelques repères :
- l'appli vise Android TV 5.0 et plus (API 21), sans bibliothèque externe ;
- l'interface est construite en Java, sans fichiers de mise en page XML ;
- les tailles passent par `Ui.dp()`, pensé pour un écran de 1280 de large ;
- tout doit se piloter à la télécommande (flèches, OK, retour, appui long sur OK).

## Modifier les scripts

Les scripts PowerShell sont dans [`scripts/`](scripts) et doivent tourner sur Windows PowerShell 5.1, celui livré avec Windows 10 et 11. Les fonctions communes (connexion à la télé, sauvegarde, réglages, profils) sont dans [`scripts/tv-commun.ps1`](scripts/tv-commun.ps1).

- Tout réglage modifié passe par `Appliquer-Reglage`, qui note la valeur d'origine.
- Toute appli désactivée passe par `Desactiver-Paquet`, qui la note pour la restauration.
- Les fichiers `.ps1` sont enregistrés en UTF-8 avec BOM, pour que les accents s'affichent bien.

## Style

- Le projet est en français : noms de variables, messages et documentation.
- Le code reste simple et lisible, sans commentaire superflu.
- Une pull request = un sujet.

## Signaler un problème ou poser une question

- Un bug : ticket **🐞 Signaler un problème**, avec le modèle de télé et la version d'Android.
- Une idée : ticket **💡 Proposer une idée**.
- Une question : onglet **Discussions**.
