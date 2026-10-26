package app.puritv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.TypefaceSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class Ui {
    static int dp(Context ctx, float v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().widthPixels / 1280f);
    }

    static FrameLayout.LayoutParams plein() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static final class BlocMeteo {
        LinearLayout ligne;
        IconeMeteo icone;
        TextView principal, lieu, pluie;
    }

    static LinearLayout horloge(Context ctx, float echelle, boolean detaille) {
        LinearLayout bloc = new LinearLayout(ctx);
        bloc.setOrientation(LinearLayout.VERTICAL);
        bloc.setGravity(detaille ? Gravity.START : Gravity.END);

        TextView heure = new Horodatage(ctx, "HH:mm", false);
        texte(heure, 64 * echelle, "sans-serif-thin", Color.WHITE);
        heure.setLetterSpacing(-0.02f);
        bloc.addView(heure, ajuste());

        TextView date = new Horodatage(ctx, "EEEE d MMMM", true);
        texte(date, 20 * echelle, "sans-serif-light", 0xF2FFFFFF);
        date.setPadding(detaille ? dp(ctx, 4 * echelle) : 0, dp(ctx, 2 * echelle), detaille ? 0 : dp(ctx, 3), 0);
        bloc.addView(date, ajuste());

        BlocMeteo m = new BlocMeteo();
        m.ligne = new LinearLayout(ctx);
        m.ligne.setOrientation(LinearLayout.HORIZONTAL);
        m.ligne.setGravity(Gravity.TOP);
        m.ligne.setVisibility(View.GONE);
        LinearLayout.LayoutParams pLigne = ajuste();
        pLigne.topMargin = dp(ctx, 14 * echelle);
        if (detaille) pLigne.leftMargin = dp(ctx, 4 * echelle);
        bloc.addView(m.ligne, pLigne);

        m.icone = new IconeMeteo(ctx);
        int cote = dp(ctx, 30 * echelle);
        LinearLayout.LayoutParams pIcone = new LinearLayout.LayoutParams(cote, cote);
        pIcone.setMargins(0, 0, dp(ctx, 10 * echelle), 0);
        m.ligne.addView(m.icone, pIcone);

        LinearLayout colonne = new LinearLayout(ctx);
        colonne.setOrientation(LinearLayout.VERTICAL);
        m.ligne.addView(colonne);

        m.principal = new TextView(ctx);
        texte(m.principal, 19 * echelle, "sans-serif-light", Color.WHITE);
        colonne.addView(m.principal);

        if (detaille) {
            m.lieu = new TextView(ctx);
            texte(m.lieu, 14 * echelle, "sans-serif", 0xC7FFFFFF);
            LinearLayout.LayoutParams pLieu = ajuste();
            pLieu.setMargins(dp(ctx, 4 * echelle), dp(ctx, 8 * echelle), 0, 0);
            bloc.addView(m.lieu, pLieu);

            m.pluie = new TextView(ctx);
            texte(m.pluie, 14 * echelle, "sans-serif", 0xC7FFFFFF);
            LinearLayout.LayoutParams pPluie = ajuste();
            pPluie.setMargins(dp(ctx, 4 * echelle), dp(ctx, 3 * echelle), 0, 0);
            bloc.addView(m.pluie, pPluie);
        }
        bloc.setTag(m);
        return bloc;
    }

    static void majMeteo(final LinearLayout horloge) {
        final Context ctx = horloge.getContext();
        final BlocMeteo m = (BlocMeteo) horloge.getTag();
        if (!Config.villeChoisie(ctx)) {
            m.icone.setVisibility(View.GONE);
            SpannableStringBuilder s = new SpannableStringBuilder("Météo : choisis ta ville dans ⚙");
            s.setSpan(new RelativeSizeSpan(0.8f), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new ForegroundColorSpan(0xE0FFFFFF), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            m.principal.setText(s);
            if (m.lieu != null) m.lieu.setVisibility(View.GONE);
            if (m.pluie != null) m.pluie.setVisibility(View.GONE);
            m.ligne.setVisibility(View.VISIBLE);
            return;
        }
        Meteo.obtenir(ctx, e -> {
            if (e == null) {
                m.ligne.setVisibility(View.GONE);
                if (m.lieu != null) m.lieu.setVisibility(View.GONE);
                if (m.pluie != null) m.pluie.setVisibility(View.GONE);
                return;
            }
            m.icone.setVisibility(View.VISIBLE);
            m.icone.regler(e.code, e.jour);

            SpannableStringBuilder s = new SpannableStringBuilder();
            s.append(e.temperature + "°");
            s.setSpan(new TypefaceSpan("sans-serif"), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new RelativeSizeSpan(1.15f), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            int debut = s.length();
            s.append("   ").append(e.ciel);
            s.setSpan(new ForegroundColorSpan(0xE6FFFFFF), debut, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            m.principal.setText(s);

            if (m.lieu != null) {
                m.lieu.setText(Config.ville(ctx) + "  ·  Max. " + e.max + "°   Min. " + e.min + "°");
                m.lieu.setVisibility(View.VISIBLE);
            }
            if (m.pluie != null) {
                m.pluie.setText(e.pluie);
                m.pluie.setVisibility(e.pluie == null ? View.GONE : View.VISIBLE);
            }
            m.ligne.setVisibility(View.VISIBLE);
        });
    }

    private static LinearLayout.LayoutParams ajuste() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static void texte(TextView v, float sp, String police, int couleur) {
        v.setTextColor(couleur);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTypeface(Typeface.create(police, Typeface.NORMAL));
        v.setIncludeFontPadding(false);
        v.setShadowLayer(12, 0, 2, 0x66000000);
    }

    private static final class Horodatage extends TextView {
        private final SimpleDateFormat format;
        private final boolean majuscule;
        private final Runnable maj = new Runnable() {
            @Override public void run() {
                long maintenant = System.currentTimeMillis();
                format.setTimeZone(java.util.TimeZone.getDefault());
                String t = format.format(new Date(maintenant));
                setText(majuscule ? Character.toUpperCase(t.charAt(0)) + t.substring(1) : t);
                postDelayed(this, 60_000 - maintenant % 60_000 + 50);
            }
        };
        private final BroadcastReceiver reveil = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                removeCallbacks(maj);
                post(maj);
            }
        };

        Horodatage(Context ctx, String motif, boolean majuscule) {
            super(ctx);
            this.format = new SimpleDateFormat(motif, Locale.FRANCE);
            this.majuscule = majuscule;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
            f.addAction(Intent.ACTION_TIME_CHANGED);
            f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            f.addAction(Intent.ACTION_DATE_CHANGED);
            getContext().registerReceiver(reveil, f);
            removeCallbacks(maj);
            post(maj);
        }

        @Override
        protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            if (visibility == VISIBLE) {
                removeCallbacks(maj);
                post(maj);
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            removeCallbacks(maj);
            try {
                getContext().unregisterReceiver(reveil);
            } catch (IllegalArgumentException ignored) {
            }
            super.onDetachedFromWindow();
        }
    }
}
