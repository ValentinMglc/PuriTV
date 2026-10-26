package app.puritv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;

import java.util.List;

final class Reglages {
    private static final int[] ECO_MIN = {5, 10, 15, 30, 60};
    private static final int[] VEILLE_MIN = {30, 60, 120, 180, 240};
    private static final int[] MEMOIRE_MIN = {0, 5, 10, 30};

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("puritv", Context.MODE_PRIVATE);
    }

    static int memoireMin(Context ctx) {
        return prefs(ctx).getInt("memoire_min", 10);
    }

    static boolean alertes(Context ctx) {
        return prefs(ctx).getBoolean("alertes", true);
    }

    static void ouvrir(final Activity a) {
        ContentResolver cr = a.getContentResolver();
        int eco = Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, -1);
        int veille = Settings.Secure.getInt(cr, "sleep_timeout", -1);
        int mem = memoireMin(a);
        String dep = Config.departement(a);
        String[] lignes = {
                "Économiseur d'écran : " + (eco < 0 ? "par défaut" : "après " + duree(eco / 60_000)),
                "Mise en veille de la télé : " + (veille < 0 ? "par défaut" : "après " + duree(Math.round(veille / 60_000f))),
                "Libérer la mémoire : " + (mem == 0 ? "jamais" : "après " + duree(mem) + " d'inactivité"),
                "Ville de la météo : " + (Config.villeChoisie(a)
                        ? Config.ville(a) + (dep.isEmpty() ? "" : " (" + dep + ")") : "à choisir"),
                "Alertes météo : " + (!Config.villeChoisie(a) ? "après le choix de la ville"
                        : dep.isEmpty() ? "indisponibles hors de France" : alertes(a) ? "affichées" : "masquées"),
                "Bloqueur de pubs (DNS) : " + (Dns.actif(a) ? "activé"
                        : Dns.coupeAuto(a) ? "coupé (réseau incompatible)" : "désactivé"),
        };
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Réglages PuriTV")
                .setItems(lignes, (d, quoi) -> {
                    switch (quoi) {
                        case 0:
                            choisir(a, "Économiseur d'écran après…", ECO_MIN, eco / 60_000,
                                    m -> ecrire(a, () -> Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, m * 60_000)));
                            break;
                        case 1:
                            choisir(a, "Mise en veille de la télé après…", VEILLE_MIN, Math.round(veille / 60_000f),
                                    m -> ecrire(a, () -> Settings.Secure.putInt(cr, "sleep_timeout", m * 60_000)));
                            break;
                        case 2:
                            choisir(a, "Libérer la mémoire après…", MEMOIRE_MIN, mem,
                                    m -> { prefs(a).edit().putInt("memoire_min", m).apply(); ouvrir(a); });
                            break;
                        case 3:
                            demanderVille(a, true);
                            break;
                        case 4:
                            if (!dep.isEmpty()) prefs(a).edit().putBoolean("alertes", !alertes(a)).apply();
                            ouvrir(a);
                            break;
                        default:
                            boolean oui = !Dns.actif(a);
                            ecrire(a, () -> Dns.activer(a, oui));
                            Toast.makeText(a, oui ? "Bloqueur de pubs activé" : "Bloqueur de pubs désactivé",
                                    Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    static void premiereVille(Activity a) {
        demanderVille(a, false);
    }

    private static void demanderVille(final Activity a, final boolean retour) {
        final EditText saisie = new EditText(a);
        saisie.setHint("Nom de la ville (ex. Annecy)");
        saisie.setSingleLine(true);
        saisie.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        saisie.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        int m = Ui.dp(a, 24);
        saisie.setPadding(m, m / 2, m, m / 2);

        final AlertDialog d = new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(retour ? "Ville de la météo" : "Bienvenue sur PuriTV ! Ta ville, pour la météo ?")
                .setView(saisie)
                .setPositiveButton("Chercher", (dlg, w) -> chercherVille(a, saisie.getText().toString(), retour))
                .setNegativeButton(retour ? "Annuler" : "Plus tard", (dlg, w) -> { if (retour) ouvrir(a); })
                .create();
        saisie.setOnEditorActionListener((v, action, ev) -> {
            d.dismiss();
            chercherVille(a, saisie.getText().toString(), retour);
            return true;
        });
        d.show();
        saisie.requestFocus();
    }

    private static void chercherVille(final Activity a, String nom, final boolean retour) {
        if (nom.trim().length() < 2) {
            if (retour) ouvrir(a);
            return;
        }
        Toast.makeText(a, "Recherche de « " + nom.trim() + " »…", Toast.LENGTH_SHORT).show();
        Config.chercher(nom, villes -> {
            if (villes == null || villes.isEmpty()) {
                Toast.makeText(a, villes == null ? "Recherche impossible : pas d'internet ?"
                        : "Aucune ville trouvée pour « " + nom.trim() + " »", Toast.LENGTH_LONG).show();
                demanderVille(a, retour);
                return;
            }
            final List<Config.Ville> l = villes;
            String[] noms = new String[l.size()];
            for (int i = 0; i < l.size(); i++) noms[i] = l.get(i).description();
            new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Quelle ville ?")
                    .setItems(noms, (dlg, i) -> {
                        Config.choisir(a, l.get(i));
                        Toast.makeText(a, "Météo : " + l.get(i).nom, Toast.LENGTH_SHORT).show();
                        if (a instanceof MainActivity) ((MainActivity) a).majMeteo();
                        if (retour) ouvrir(a);
                    })
                    .show();
        });
    }

    private interface Choix {
        void minutes(int m);
    }

    private static void choisir(final Activity a, String titre, final int[] valeurs, int actuel, final Choix choix) {
        String[] noms = new String[valeurs.length];
        int coche = -1;
        for (int i = 0; i < valeurs.length; i++) {
            noms[i] = valeurs[i] == 0 ? "Jamais" : duree(valeurs[i]);
            if (valeurs[i] == actuel) coche = i;
        }
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(titre)
                .setSingleChoiceItems(noms, coche, (d, i) -> {
                    d.dismiss();
                    choix.minutes(valeurs[i]);
                })
                .show();
    }

    private static void ecrire(Activity a, Runnable action) {
        try {
            action.run();
        } catch (SecurityException e) {
            Toast.makeText(a, "Autorisation manquante : lance verifier-tv.bat depuis le PC",
                    Toast.LENGTH_LONG).show();
        }
        ouvrir(a);
    }

    static String duree(int minutes) {
        if (minutes < 60) return minutes + " min";
        int h = minutes / 60, m = minutes % 60;
        return h + " h" + (m == 0 ? "" : String.format(" %02d", m));
    }
}
