package app.puritv;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.dreams.DreamService;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

public class Veille extends DreamService {
    private Diaporama diaporama;
    private LinearLayout horloge;
    private FrameLayout alertes;
    private String alertesAffichees = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable meteo = new Runnable() {
        @Override public void run() {
            Ui.majMeteo(horloge);
            majAlertes();
            handler.postDelayed(this, Meteo.CACHE_MS);
        }
    };
    private final Runnable liberer = () -> Memoire.liberer(this);

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);

        FrameLayout racine = new FrameLayout(this);
        racine.setBackgroundColor(Color.BLACK);
        diaporama = new Diaporama(this, racine);

        View voile = new View(this);
        voile.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0x99000000, 0x00000000}));
        racine.addView(voile, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 300), Gravity.BOTTOM));

        horloge = Ui.horloge(this, 1.3f, true);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        p.setMargins(Ui.dp(this, 72), 0, 0, Ui.dp(this, 56));
        racine.addView(horloge, p);

        alertes = new FrameLayout(this);
        alertes.setClipToPadding(false);
        alertes.setPadding(Ui.dp(this, 16), 0, 0, Ui.dp(this, 16));
        FrameLayout.LayoutParams pa = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        pa.setMargins(0, Ui.dp(this, 44), Ui.dp(this, 72), 0);
        racine.addView(alertes, pa);

        lieu = new TextView(this);
        lieu.setGravity(Gravity.END);
        lieu.setTextColor(0xD9FFFFFF);
        lieu.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        lieu.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        lieu.setLineSpacing(Ui.dp(this, 3), 1f);
        lieu.setShadowLayer(10, 0, 2, 0x66000000);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        pl.setMargins(0, 0, Ui.dp(this, 72), Ui.dp(this, 56));
        racine.addView(lieu, pl);
        lieux = chargerLieux();
        diaporama.surChangement(this::afficherLieu);

        setContentView(racine);
    }

    private TextView lieu;
    private Map<String, String[]> lieux;

    private void afficherLieu(String chemin) {
        final String[] l = lieux.get(chemin);
        lieu.animate().alpha(0f).setStartDelay(0).setDuration(600).withEndAction(() -> {
            if (l == null) {
                lieu.setText("");
                return;
            }
            SpannableStringBuilder s = new SpannableStringBuilder(l[0]);
            if (l.length > 1) {
                int debut = s.length();
                s.append("\nPhoto : ").append(l[1]);
                s.setSpan(new RelativeSizeSpan(0.72f), debut, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                s.setSpan(new ForegroundColorSpan(0x99FFFFFF), debut, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            lieu.setText(s);
            lieu.animate().alpha(1f).setStartDelay(Diaporama.FONDU_MS / 2).setDuration(1200).start();
        }).start();
    }

    private Map<String, String[]> chargerLieux() {
        Map<String, String[]> m = new HashMap<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(getAssets().open("lieux.txt"), "UTF-8"))) {
            String ligne;
            while ((ligne = r.readLine()) != null) {
                int egal = ligne.indexOf('=');
                if (ligne.startsWith("#") || egal < 0) continue;
                m.put(ligne.substring(0, egal).trim(), ligne.substring(egal + 1).trim().split("\\|"));
            }
        } catch (Exception ignored) {
        }
        return m;
    }

    private void majAlertes() {
        if (!Reglages.alertes(this)) {
            afficherAlertes(null);
            return;
        }
        Alertes.obtenir(this, this::afficherAlertes);
    }

    private void afficherAlertes(java.util.List<Alertes.Alerte> liste) {
        StringBuilder cle = new StringBuilder();
        if (liste != null) for (Alertes.Alerte a : liste) cle.append(a.phenomene).append(a.niveau).append(a.quand);
        if (cle.toString().equals(alertesAffichees)) return;
        alertesAffichees = cle.toString();

        alertes.removeAllViews();
        if (liste == null || liste.isEmpty()) return;
        View carte = CarteAlertes.creer(this, liste);
        alertes.addView(carte);
        carte.setAlpha(0f);
        carte.setTranslationY(-Ui.dp(this, 24));
        carte.setScaleX(0.96f);
        carte.setScaleY(0.96f);
        carte.animate().alpha(1f).translationY(0).scaleX(1f).scaleY(1f)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(2f))
                .setStartDelay(600).setDuration(600).start();
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        diaporama.demarrer();
        handler.post(meteo);
        int m = Reglages.memoireMin(this);
        if (m > 0 && MainActivity.accueilAuPremierPlan) {
            int eco = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, 0) / 60_000;
            handler.postDelayed(liberer, Math.max(0, m - eco) * 60_000L);
        }
    }

    @Override
    public void onDreamingStopped() {
        super.onDreamingStopped();
        diaporama.arreter();
        handler.removeCallbacks(meteo);
        handler.removeCallbacks(liberer);
    }
}
