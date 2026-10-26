package app.puritv;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class CarteAlertes {
    static final int JAUNE = 0xFFFFC60A, ORANGE = 0xFFFF9F0A, ROUGE = 0xFFFF453A;

    static View creer(Context ctx, List<Alertes.Alerte> alertes) {
        Alertes.Alerte pire = alertes.get(0);

        LinearLayout carte = new LinearLayout(ctx);
        carte.setOrientation(LinearLayout.HORIZONTAL);
        carte.setGravity(Gravity.TOP);
        GradientDrawable verre = new GradientDrawable();
        verre.setColor(0x47FFFFFF);
        verre.setCornerRadius(Ui.dp(ctx, 22));
        verre.setStroke(1, 0x33FFFFFF);
        carte.setBackground(verre);
        carte.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 14), Ui.dp(ctx, 24), Ui.dp(ctx, 16));

        TextView icone = texte(ctx, "!", 20, "sans-serif-black", Color.WHITE);
        icone.setGravity(Gravity.CENTER);
        GradientDrawable fondIcone = new GradientDrawable();
        fondIcone.setColor(couleur(pire.niveau));
        fondIcone.setCornerRadius(Ui.dp(ctx, 9));
        icone.setBackground(fondIcone);
        LinearLayout.LayoutParams pIcone = new LinearLayout.LayoutParams(Ui.dp(ctx, 38), Ui.dp(ctx, 38));
        pIcone.setMargins(0, Ui.dp(ctx, 2), Ui.dp(ctx, 14), 0);
        carte.addView(icone, pIcone);

        LinearLayout textes = new LinearLayout(ctx);
        textes.setOrientation(LinearLayout.VERTICAL);
        carte.addView(textes);

        TextView source = texte(ctx, "VIGILANCE MÉTÉO  ·  " + Config.departement(ctx).toUpperCase(Locale.FRANCE),
                11, "sans-serif-medium", 0xB3FFFFFF);
        source.setLetterSpacing(0.08f);
        textes.addView(source);

        TextView titre = texte(ctx, "Vigilance " + pire.couleurNom(), 19, "sans-serif-medium", Color.WHITE);
        titre.setPadding(0, Ui.dp(ctx, 5), 0, 0);
        textes.addView(titre);

        TextView corps = texte(ctx, phrase(alertes, pire.niveau), 16, "sans-serif", 0xE6FFFFFF);
        corps.setPadding(0, Ui.dp(ctx, 3), 0, 0);
        corps.setLineSpacing(Ui.dp(ctx, 3), 1f);
        corps.setMaxWidth(Ui.dp(ctx, 420));
        textes.addView(corps);

        carte.setElevation(Ui.dp(ctx, 8));
        return carte;
    }

    static String phrase(List<Alertes.Alerte> alertes, int niveauTitre) {
        Map<String, List<String>> parMoment = new LinkedHashMap<>();
        for (Alertes.Alerte a : alertes) {
            String nom = a.phenomene + (a.niveau == niveauTitre ? "" : " (" + a.couleurNom() + ")");
            List<String> l = parMoment.get(a.quand);
            if (l == null) parMoment.put(a.quand, l = new ArrayList<>());
            l.add(nom);
        }
        StringBuilder s = new StringBuilder();
        for (Map.Entry<String, List<String>> e : parMoment.entrySet()) {
            List<String> noms = e.getValue();
            StringBuilder groupe = new StringBuilder();
            for (int i = 0; i < noms.size(); i++) {
                String n = i == 0 ? noms.get(i) : minuscule(noms.get(i));
                if (i > 0) groupe.append(i == noms.size() - 1 ? " et " : ", ");
                groupe.append(n);
            }
            if (s.length() > 0) s.append(". ");
            s.append(groupe).append(' ').append(e.getKey());
        }
        return s.toString();
    }

    static int couleur(int niveau) {
        return niveau >= 4 ? ROUGE : niveau == 3 ? ORANGE : JAUNE;
    }

    private static String minuscule(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static TextView texte(Context ctx, String s, float sp, String police, int couleur) {
        TextView v = new TextView(ctx);
        v.setText(s);
        v.setTextColor(couleur);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTypeface(Typeface.create(police, Typeface.NORMAL));
        v.setIncludeFontPadding(false);
        v.setShadowLayer(6, 0, 1, 0x40000000);
        return v;
    }
}
