package app.puritv;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

final class Meteo {
    static final long CACHE_MS = 30 * 60_000;

    static final class Etat {
        int temperature, min, max;
        int code;
        boolean jour;
        String ciel;
        String pluie;
    }

    interface Ecouteur {
        void meteo(Etat etat);
    }

    private static Etat cache;
    private static long quand;

    static void oublier() {
        cache = null;
        quand = 0;
    }

    static void obtenir(Context ctx, final Ecouteur ecouteur) {
        if (!Config.villeChoisie(ctx)) {
            ecouteur.meteo(null);
            return;
        }
        if (cache != null && System.currentTimeMillis() - quand < CACHE_MS) {
            ecouteur.meteo(cache);
            return;
        }
        final double lat = Config.lat(ctx), lon = Config.lon(ctx);
        final Handler principal = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            Etat e = null;
            try {
                e = charger(lat, lon);
            } catch (Exception ignored) {
            }
            final Etat resultat = e;
            principal.post(() -> {
                if (resultat != null) {
                    cache = resultat;
                    quand = System.currentTimeMillis();
                }
                ecouteur.meteo(cache);
            });
        }).start();
    }

    private static Etat charger(double lat, double lon) throws Exception {
        URL url = new URL(String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&current=temperature_2m,weather_code,is_day"
                        + "&daily=temperature_2m_max,temperature_2m_min"
                        + "&hourly=precipitation_probability&forecast_days=2&timezone=auto",
                lat, lon));
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            JSONObject j = new JSONObject(b.toString("UTF-8"));
            JSONObject cur = j.getJSONObject("current");
            JSONObject d = j.getJSONObject("daily");

            Etat e = new Etat();
            e.temperature = (int) Math.round(cur.getDouble("temperature_2m"));
            e.code = cur.getInt("weather_code");
            e.jour = cur.optInt("is_day", 1) == 1;
            e.ciel = ciel(e.code, e.jour);
            e.min = (int) Math.round(d.getJSONArray("temperature_2m_min").getDouble(0));
            e.max = (int) Math.round(d.getJSONArray("temperature_2m_max").getDouble(0));
            e.pluie = pluie(j.getJSONObject("hourly"), cur.getString("time"));
            return e;
        } finally {
            c.disconnect();
        }
    }

    private static String pluie(JSONObject h, String maintenant) throws Exception {
        JSONArray heures = h.getJSONArray("time");
        JSONArray proba = h.getJSONArray("precipitation_probability");
        String heureActuelle = maintenant.substring(0, 13);
        int debut = 0;
        while (debut < heures.length() && heures.getString(debut).substring(0, 13).compareTo(heureActuelle) < 0) {
            debut++;
        }
        for (int i = debut; i < Math.min(debut + 12, heures.length()); i++) {
            if (!proba.isNull(i) && proba.getInt(i) >= 50) {
                return i == debut ? "Pluie probable maintenant"
                        : "Pluie probable vers " + Integer.parseInt(heures.getString(i).substring(11, 13)) + " h";
            }
        }
        return null;
    }

    static String ciel(int code, boolean jour) {
        if (code == 0) return jour ? "Ensoleillé" : "Dégagé";
        if (code == 1) return jour ? "Plutôt ensoleillé" : "Plutôt dégagé";
        if (code == 2) return "Partiellement nuageux";
        if (code == 3) return "Nuageux";
        if (code == 45 || code == 48) return "Brouillard";
        if (code >= 51 && code <= 57) return "Bruine";
        if (code == 66 || code == 67) return "Pluie verglaçante";
        if (code == 65) return "Forte pluie";
        if (code >= 61 && code <= 64) return "Pluie";
        if (code >= 71 && code <= 77) return "Neige";
        if (code >= 80 && code <= 82) return "Averses";
        if (code == 85 || code == 86) return "Averses de neige";
        if (code >= 95) return "Orage";
        return "";
    }
}
