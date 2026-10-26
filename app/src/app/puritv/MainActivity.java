package app.puritv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {
    static final String[] FAVORIS = {
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus",
            "com.canal.android.canal",
            "com.google.android.youtube.tv",
            "com.plexapp.android",
    };
    static final String PAQUET_TV = "com.tcl.tv";
    static final List<String> MASQUES = Arrays.asList("com.tcl.partnercustomizer");

    static final String APP = "app:", HDMI = "hdmi:";
    static final int LARGEUR_MAX = 1180;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Diaporama diaporama;
    private SharedPreferences prefs;
    private List<String> elementsDock;

    private LinearLayout dock;
    private LinearLayout horloge;
    private TextView etiquette;
    private FrameLayout grilleOverlay;
    private GridLayout grille;

    private final Runnable meteo = new Runnable() {
        @Override public void run() {
            Ui.majMeteo(horloge);
            handler.postDelayed(this, Meteo.CACHE_MS);
        }
    };
    private final Runnable liberer = () -> Memoire.liberer(this);

    private final android.content.BroadcastReceiver reveil = new android.content.BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            handler.removeCallbacks(meteo);
            handler.post(meteo);
        }
    };

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(reveil);
        } catch (IllegalArgumentException ignored) {
        }
        super.onDestroy();
    }

    private void armerLiberation() {
        handler.removeCallbacks(liberer);
        int m = Reglages.memoireMin(this);
        if (m > 0) handler.postDelayed(liberer, m * 60_000L);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        armerLiberation();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("puritv", Context.MODE_PRIVATE);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        FrameLayout racine = new FrameLayout(this);
        racine.setBackgroundColor(Color.BLACK);
        racine.setClipChildren(false);
        diaporama = new Diaporama(this, racine);

        View voileBas = new View(this);
        voileBas.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xB0000000, 0x00000000}));
        racine.addView(voileBas, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(320), Gravity.BOTTOM));

        View voileHaut = new View(this);
        voileHaut.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x80000000, 0x00000000}));
        racine.addView(voileHaut, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(220), Gravity.TOP));

        horloge = Ui.horloge(this, 1f, false);
        FrameLayout.LayoutParams pHorloge = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        pHorloge.setMargins(0, dp(40), dp(64), 0);
        racine.addView(horloge, pHorloge);

        LinearLayout bas = new LinearLayout(this);
        bas.setOrientation(LinearLayout.VERTICAL);
        bas.setGravity(Gravity.CENTER_HORIZONTAL);
        bas.setClipChildren(false);

        etiquette = new TextView(this);
        etiquette.setTextColor(Color.WHITE);
        etiquette.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        etiquette.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        etiquette.setShadowLayer(8, 0, 2, 0x99000000);
        etiquette.setPadding(0, 0, 0, dp(14));
        etiquette.setGravity(Gravity.CENTER_HORIZONTAL);
        bas.addView(etiquette, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setClipChildren(false);
        dock.setClipToPadding(false);
        GradientDrawable verre = new GradientDrawable();
        verre.setColor(0x33FFFFFF);
        verre.setCornerRadius(dp(20));
        verre.setStroke(1, 0x26FFFFFF);
        dock.setBackground(verre);
        dock.setPadding(dp(14), dp(12), dp(14), dp(12));
        bas.addView(dock);

        FrameLayout.LayoutParams pDock = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        pDock.bottomMargin = dp(44);
        racine.addView(bas, pDock);

        grilleOverlay = construireGrille();
        grilleOverlay.setVisibility(View.GONE);
        racine.addView(grilleOverlay, Ui.plein());

        setContentView(racine);
        registerReceiver(reveil, new android.content.IntentFilter(Intent.ACTION_SCREEN_ON));
        elementsDock = chargerDock();
    }

    @Override
    protected void onResume() {
        super.onResume();
        View f = dock.getFocusedChild();
        remplirDock(f == null ? null : (String) f.getTag());
        diaporama.demarrer();
        handler.removeCallbacks(meteo);
        handler.post(meteo);
        accueilAuPremierPlan = true;
        armerLiberation();
        if (!Config.villeChoisie(this) && !Config.villeDemandee(this)) {
            Config.marquerVilleDemandee(this);
            handler.postDelayed(() -> Reglages.premiereVille(this), 1500);
        }
        Dns.surveiller(this, () -> Toast.makeText(this,
                "Bloqueur de pubs coupé : ce réseau ne le permet pas.\nRéactivable dans ⚙ › Réglages PuriTV.",
                Toast.LENGTH_LONG).show());
    }

    void majMeteo() {
        Ui.majMeteo(horloge);
    }

    @Override
    protected void onPause() {
        super.onPause();
        diaporama.arreter();
        handler.removeCallbacks(meteo);
    }

    @Override
    protected void onStop() {
        super.onStop();
        accueilAuPremierPlan = false;
        handler.removeCallbacks(liberer);
    }

    static boolean accueilAuPremierPlan;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        fermerGrille();
    }

    private boolean appuiLongEnCours;

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
            View f = getCurrentFocus();
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                appuiLongEnCours = false;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() > 0) {
                if (!appuiLongEnCours && f != null && f.isLongClickable()) {
                    appuiLongEnCours = true;
                    f.setPressed(false);
                    f.performLongClick();
                }
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_UP && appuiLongEnCours) {
                appuiLongEnCours = false;
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            fermerGrille();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private List<String> chargerDock() {
        String s = prefs.getString("dock", null);
        if (s != null) return new ArrayList<>(Arrays.asList(TextUtils.split(s, "\n")));
        List<String> l = new ArrayList<>();
        for (String p : FAVORIS) l.add(APP + p);
        l.add(APP + PAQUET_TV);
        for (TvInputInfo e : entreesHdmi()) l.add(HDMI + e.getId());
        return l;
    }

    private void enregistrerDock() {
        prefs.edit().putString("dock", TextUtils.join("\n", elementsDock)).apply();
    }

    private void remplirDock(String focus) {
        dock.removeAllViews();

        List<String> visibles = new ArrayList<>();
        float largeur = 2 * 14 + 2 * (68 + 16) + 17;
        for (String id : elementsDock) {
            if (lancement(id) == null) continue;
            visibles.add(id);
            largeur += (estLarge(id) ? 120 : 68) + 16;
        }
        float k = Math.min(1f, LARGEUR_MAX / largeur);

        View aFocaliser = null;
        for (String id : visibles) {
            View t = tuile(id, estLarge(id) ? 120 * k : 68 * k, 68 * k);
            t.setOnLongClickListener(v -> { menuDock(id); return true; });
            ajouterAuDock(t, k);
            if (id.equals(focus)) aFocaliser = t;
        }

        View separateur = new View(this);
        separateur.setBackgroundColor(0x40FFFFFF);
        LinearLayout.LayoutParams pSep = new LinearLayout.LayoutParams(dp(1), dp(48 * k));
        pSep.setMargins(dp(8), 0, dp(8), 0);
        dock.addView(separateur, pSep);

        ajouterAuDock(tuileTexte("⋯", 26, "Toutes les applis", 68 * k, v -> ouvrirGrille()), k);
        ajouterAuDock(tuileTexte("⚙", 26, "Réglages", 68 * k, v -> menuReglages()), k);

        if (grilleVisible()) return;
        if (aFocaliser == null && dock.getChildCount() > 0) aFocaliser = dock.getChildAt(0);
        if (aFocaliser != null) aFocaliser.requestFocus();
    }

    private boolean estLarge(String id) {
        return id.startsWith(APP) && !id.equals(APP + PAQUET_TV) && banniere(id.substring(4)) != null;
    }

    private void ajouterAuDock(View tuile, float k) {
        ViewGroup.LayoutParams t = tuile.getLayoutParams();
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(t.width, t.height);
        p.setMargins(dp(8 * k), 0, dp(8 * k), 0);
        dock.addView(tuile, p);
    }

    private void menuReglages() {
        String[] choix = {"Paramètres de la télé", "Réglages PuriTV"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setItems(choix, (d, quoi) -> {
                    if (quoi == 0) lancer(new Intent(Settings.ACTION_SETTINGS));
                    else Reglages.ouvrir(this);
                })
                .show();
    }

    private void menuDock(final String id) {
        final int i = elementsDock.indexOf(id);
        String[] choix = {"◀  Déplacer à gauche", "Déplacer à droite  ▶", "Retirer du dock"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(nom(id))
                .setItems(choix, (d, quoi) -> {
                    if (quoi == 0 && i > 0) Collections.swap(elementsDock, i, i - 1);
                    else if (quoi == 1 && i < elementsDock.size() - 1) Collections.swap(elementsDock, i, i + 1);
                    else if (quoi == 2) elementsDock.remove(id);
                    enregistrerDock();
                    remplirDock(quoi == 2 ? null : id);
                })
                .show();
    }

    private FrameLayout construireGrille() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0xD9000000);

        ScrollView defilement = new ScrollView(this);
        defilement.setClipChildren(false);
        defilement.setClipToPadding(false);
        defilement.setPadding(dp(80), dp(60), dp(80), dp(60));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);

        TextView titre = new TextView(this);
        titre.setText("Applis");
        titre.setTextColor(Color.WHITE);
        titre.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        titre.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        titre.setPadding(dp(12), 0, 0, dp(4));
        col.addView(titre);

        TextView aide = new TextView(this);
        aide.setText("Appui long sur OK pour ajouter au dock ou en retirer");
        aide.setTextColor(0xAAFFFFFF);
        aide.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        aide.setPadding(dp(12), 0, 0, dp(20));
        col.addView(aide);

        grille = new GridLayout(this);
        grille.setColumnCount(5);
        grille.setClipChildren(false);
        col.addView(grille);

        defilement.addView(col);
        overlay.addView(defilement, Ui.plein());
        return overlay;
    }

    private void ouvrirGrille() {
        grille.removeAllViews();
        final PackageManager pm = getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> applis = pm.queryIntentActivities(i, 0);
        Collections.sort(applis, (a, b) ->
                a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));

        List<String> ids = new ArrayList<>();
        for (ResolveInfo ri : applis) {
            String paquet = ri.activityInfo.packageName;
            if (paquet.equals(getPackageName()) || MASQUES.contains(paquet)) continue;
            if (!ids.contains(APP + paquet)) ids.add(APP + paquet);
        }
        for (TvInputInfo e : entreesHdmi()) ids.add(HDMI + e.getId());

        for (final String id : ids) {
            GridLayout.LayoutParams p = new GridLayout.LayoutParams();
            p.width = dp(176);
            p.height = dp(99);
            p.setMargins(dp(12), dp(12), dp(12), dp(12));
            View t = tuile(id, 176, 99);
            t.setOnLongClickListener(v -> { basculerDock(id); return true; });
            grille.addView(t, p);
        }
        grilleOverlay.setVisibility(View.VISIBLE);
        grilleOverlay.setAlpha(0f);
        grilleOverlay.animate().alpha(1f).setDuration(200).start();
        if (grille.getChildCount() > 0) grille.getChildAt(0).requestFocus();
    }

    private void basculerDock(String id) {
        boolean retirer = elementsDock.contains(id);
        if (retirer) elementsDock.remove(id);
        else elementsDock.add(id);
        enregistrerDock();
        remplirDock(null);
        Toast.makeText(this, nom(id) + (retirer ? " retiré du dock" : " ajouté au dock"),
                Toast.LENGTH_SHORT).show();
    }

    private void fermerGrille() {
        if (!grilleVisible()) return;
        grilleOverlay.setVisibility(View.GONE);
        if (dock.getChildCount() > 0) dock.getChildAt(0).requestFocus();
    }

    private boolean grilleVisible() {
        return grilleOverlay != null && grilleOverlay.getVisibility() == View.VISIBLE;
    }

    private View tuile(final String id, float largeur, float hauteur) {
        if (id.startsWith(HDMI)) {
            String n = nom(id).replaceFirst(" ", "\n");
            View t = tuileTexte(n, 15 * hauteur / 68, nom(id), largeur, hauteur, v -> lancerId(id));
            t.setTag(id);
            return t;
        }
        String paquet = id.substring(APP.length());
        PackageManager pm = getPackageManager();
        Drawable visuel = banniere(paquet);
        boolean estBanniere = visuel != null;
        if (visuel == null) {
            try {
                visuel = pm.getApplicationIcon(paquet);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        FrameLayout t = cadre(dp(largeur), dp(hauteur));
        ImageView img = new ImageView(this);
        img.setImageDrawable(visuel);
        if (estBanniere) {
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            t.addView(img, Ui.plein());
        } else {
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int cote = dp(Math.min(largeur, hauteur) * 0.6f);
            t.addView(img, new FrameLayout.LayoutParams(cote, cote, Gravity.CENTER));
        }
        brancher(t, id, nom(id), v -> lancerId(id));
        return t;
    }

    private View tuileTexte(String texte, float sp, String nom, float cote, View.OnClickListener action) {
        return tuileTexte(texte, sp, nom, cote, cote, action);
    }

    private View tuileTexte(String texte, float sp, String nom, float largeur, float hauteur,
                            View.OnClickListener action) {
        FrameLayout t = cadre(dp(largeur), dp(hauteur));
        TextView v = new TextView(this);
        v.setText(texte);
        v.setTextColor(Color.WHITE);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        v.setGravity(Gravity.CENTER);
        t.addView(v, Ui.plein());
        brancher(t, null, nom, action);
        return t;
    }

    private FrameLayout cadre(int largeur, int hauteur) {
        FrameLayout tuile = new FrameLayout(this);
        GradientDrawable fond = new GradientDrawable();
        fond.setColor(0x40FFFFFF);
        fond.setCornerRadius(dp(12));
        tuile.setBackground(fond);
        tuile.setClipToOutline(true);
        tuile.setLayoutParams(new ViewGroup.LayoutParams(largeur, hauteur));
        tuile.setFocusable(true);
        tuile.setFocusableInTouchMode(true);
        return tuile;
    }

    private void brancher(View tuile, String id, final String nom, View.OnClickListener action) {
        tuile.setTag(id);
        tuile.setOnClickListener(action);
        tuile.setOnFocusChangeListener((v, focus) -> {
            float s = focus ? 1.12f : 1f;
            v.animate().scaleX(s).scaleY(s).translationZ(focus ? dp(10) : 0)
                    .setDuration(180).start();
            ((GradientDrawable) v.getBackground()).setStroke(focus ? dp(2) : 0, Color.WHITE);
            if (focus && !grilleVisible()) etiquette.setText(nom);
        });
    }

    private Drawable banniere(String paquet) {
        PackageManager pm = getPackageManager();
        try {
            Drawable d = pm.getApplicationBanner(paquet);
            if (d == null) {
                Intent li = pm.getLeanbackLaunchIntentForPackage(paquet);
                if (li != null) d = pm.getActivityBanner(li);
            }
            return d;
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return null;
        }
    }

    private List<TvInputInfo> entreesHdmi() {
        List<TvInputInfo> l = new ArrayList<>();
        TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return l;
        for (TvInputInfo e : tim.getTvInputList()) {
            if (e.getType() == TvInputInfo.TYPE_HDMI && e.getParentId() == null) l.add(e);
        }
        Collections.sort(l, (a, b) -> a.loadLabel(this).toString()
                .compareToIgnoreCase(b.loadLabel(this).toString()));
        return l;
    }

    private String nom(String id) {
        if (id.startsWith(HDMI)) {
            for (TvInputInfo e : entreesHdmi()) {
                if (e.getId().equals(id.substring(HDMI.length()))) {
                    CharSequence c = e.loadCustomLabel(this);
                    return (c != null ? c : e.loadLabel(this)).toString();
                }
            }
            return "HDMI";
        }
        PackageManager pm = getPackageManager();
        try {
            ApplicationInfo ai = pm.getApplicationInfo(id.substring(APP.length()), 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return id;
        }
    }

    private Intent lancement(String id) {
        if (id.startsWith(HDMI)) {
            String entree = id.substring(HDMI.length());
            for (TvInputInfo e : entreesHdmi()) {
                if (e.getId().equals(entree)) {
                    return new Intent(Intent.ACTION_VIEW,
                            TvContract.buildChannelUriForPassthroughInput(entree));
                }
            }
            return null;
        }
        String paquet = id.substring(APP.length());
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(paquet);
        if (i == null) i = pm.getLaunchIntentForPackage(paquet);
        return i;
    }

    private void lancerId(String id) {
        Intent i = lancement(id);
        if (i != null) lancer(i);
    }

    private void lancer(Intent i) {
        handler.removeCallbacks(liberer);
        accueilAuPremierPlan = false;
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException ignored) {
        }
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
