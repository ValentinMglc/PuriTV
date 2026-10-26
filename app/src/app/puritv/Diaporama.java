package app.puritv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

final class Diaporama {
    static final long ROTATION_MS = 40_000;
    static final long FONDU_MS = 2_500;

    private final Context ctx;
    private final FrameLayout parent;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ImageView fondA, fondB;
    private final View voileNuit;
    private boolean aVisible = true;

    private final List<String> jour, nuit;
    private int iJour, iNuit;

    interface Ecouteur {
        void photo(String chemin);
    }

    private Ecouteur ecouteur;

    void surChangement(Ecouteur e) {
        ecouteur = e;
    }

    private final Runnable rotation = new Runnable() {
        @Override public void run() {
            suivant();
            handler.postDelayed(this, ROTATION_MS);
        }
    };

    Diaporama(Context ctx, FrameLayout parent) {
        this.ctx = ctx;
        this.parent = parent;
        fondA = image();
        fondB = image();
        fondB.setAlpha(0f);
        parent.addView(fondA, 0, Ui.plein());
        parent.addView(fondB, 1, Ui.plein());

        voileNuit = new View(ctx);
        voileNuit.setBackgroundColor(0x66000000);
        voileNuit.setAlpha(estNuit() ? 1f : 0f);
        parent.addView(voileNuit, 2, Ui.plein());

        jour = liste("wallpapers");
        nuit = liste("nuit");
        charger(fondA);
    }

    void demarrer() {
        handler.removeCallbacks(rotation);
        handler.postDelayed(rotation, ROTATION_MS);
    }

    void arreter() {
        handler.removeCallbacks(rotation);
    }

    static boolean estNuit() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h >= 21 || h < 7;
    }

    private void suivant() {
        final ImageView entrant = aVisible ? fondB : fondA;
        final ImageView sortant = aVisible ? fondA : fondB;
        aVisible = !aVisible;
        parent.removeView(sortant);
        parent.removeView(entrant);
        parent.addView(sortant, 0, Ui.plein());
        parent.addView(entrant, 1, Ui.plein());
        entrant.setAlpha(0f);
        charger(entrant);
        handler.postDelayed(() -> sortant.setImageDrawable(new ColorDrawable(Color.TRANSPARENT)),
                FONDU_MS * 3);
    }

    private String prochain() {
        boolean n = estNuit() && !nuit.isEmpty();
        List<String> l = n ? nuit : jour;
        if (l.isEmpty()) return null;
        int i = n ? iNuit++ : iJour++;
        return l.get(i % l.size());
    }

    private void charger(final ImageView cible) {
        final String chemin = prochain();
        voileNuit.animate().alpha(estNuit() ? 1f : 0f).setDuration(FONDU_MS).start();
        if (chemin == null) return;
        new Thread(() -> {
            Bitmap bmp = null;
            try (InputStream in = ctx.getAssets().open(chemin)) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inPreferredConfig = Bitmap.Config.RGB_565;
                bmp = BitmapFactory.decodeStream(in, null, o);
            } catch (Exception ignored) {
            }
            final Bitmap resultat = bmp;
            handler.post(() -> {
                if (resultat == null) return;
                cible.setImageBitmap(resultat);
                if (ecouteur != null) ecouteur.photo(chemin);
                cible.setScaleX(1f);
                cible.setScaleY(1f);
                cible.animate().alpha(1f).scaleX(1.08f).scaleY(1.08f)
                        .setInterpolator(new LinearInterpolator())
                        .setDuration(ROTATION_MS + FONDU_MS).start();
            });
        }).start();
    }

    private List<String> liste(String dossier) {
        List<String> l = new ArrayList<>();
        try {
            String[] noms = ctx.getAssets().list(dossier);
            if (noms != null) for (String n : noms) l.add(dossier + "/" + n);
        } catch (Exception ignored) {
        }
        Collections.shuffle(l);
        return l;
    }

    private ImageView image() {
        ImageView v = new ImageView(ctx);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return v;
    }
}
