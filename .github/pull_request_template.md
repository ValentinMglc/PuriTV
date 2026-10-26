## Ce que change cette pull request

<!-- En quelques lignes : quoi, et pourquoi. Lien vers le ticket s'il existe (ex. « Corrige #12 »). -->

## Testé sur

- Télé (marque, modèle) :
- Version d'Android :

## Vérifications

- [ ] `scripts\installer.ps1 -Simulation` tourne sans erreur.
- [ ] Tout reste réversible : `restaurer-tv.bat` remet la télé comme avant.
- [ ] Rien n'est désinstallé, et l'utilisateur est toujours consulté avant un changement.
- [ ] Aucune donnée personnelle dans les fichiers (`sauvegarde-tv\`, `config-tv.json`, adresse IP, numéro de série).
- [ ] Si l'appli a changé : `app\build.ps1` compile sans erreur et PuriTV a été testé sur la télé.
- [ ] Si un profil a été ajouté ou modifié : tout ce qui fait marcher la télé est dans `jamais`.
