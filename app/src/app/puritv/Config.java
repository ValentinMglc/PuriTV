package app.puritv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

final class Config {
    static final class Ville {
        final String nom, departement, region, pays;
        final double lat, lon;

        Ville(String nom, String departement, String region, String pays, double lat, double lon) {
            this.nom = nom;
            this.departement = departement;
            this.region = region;
            this.pays = pays;
            this.lat = lat;
            this.lon = lon;
        }

        String description() {
            StringBuilder s = new StringBuilder(nom);
            String sep = " — ";
            for (String p : new String[]{departement, region, "FR".equals(pays) ? "" : pays}) {
                if (p == null || p.isEmpty()) continue;
                s.append(sep).append(p);
                sep = ", ";
            }
            return s.toString();
        }
    }

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("puritv", Context.MODE_PRIVATE);
    }

    static String ville(Context c) {
        return p(c).getString("ville", "");
    }

    static boolean villeChoisie(Context c) {
        return !ville(c).isEmpty();
    }

    static String departement(Context c) {
        return p(c).getString("departement", "");
    }

    static double lat(Context c) {
        return p(c).getFloat("lat", 0f);
    }

    static double lon(Context c) {
        return p(c).getFloat("lon", 0f);
    }

    static boolean villeDemandee(Context c) {
        return p(c).getBoolean("ville_demandee", false);
    }

    static void marquerVilleDemandee(Context c) {
        p(c).edit().putBoolean("ville_demandee", true).apply();
    }

    static void choisir(Context c, Ville v) {
        p(c).edit()
                .putString("ville", v.nom)
                .putString("departement", "FR".equals(v.pays) ? v.departement : "")
                .putFloat("lat", (float) v.lat)
                .putFloat("lon", (float) v.lon)
                .apply();
        Meteo.oublier();
        Alertes.oublier();
    }

    interface Resultats {
        void villes(List<Ville> villes);
    }

    static void chercher(final String nom, final Resultats r) {
        final Handler principal = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            List<Ville> l = null;
            try {
                URL url = new URL("https://geocoding-api.open-meteo.com/v1/search?count=8&language=fr&name="
                        + URLEncoder.encode(nom.trim(), "UTF-8"));
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                try (InputStream in = c.getInputStream()) {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                    JSONArray res = new JSONObject(b.toString("UTF-8")).optJSONArray("results");
                    l = new ArrayList<>();
                    for (int i = 0; res != null && i < res.length(); i++) {
                        JSONObject o = res.getJSONObject(i);
                        l.add(new Ville(o.optString("name"), o.optString("admin2"), o.optString("admin1"),
                                o.optString("country_code"), o.getDouble("latitude"), o.getDouble("longitude")));
                    }
                } finally {
                    c.disconnect();
                }
            } catch (Exception ignored) {
            }
            final List<Ville> resultat = l;
            principal.post(() -> r.villes(resultat));
        }).start();
    }
}
