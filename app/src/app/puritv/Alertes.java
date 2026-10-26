package app.puritv;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.JsonReader;
import android.util.JsonToken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class Alertes {
    static final String FLUX = "https://feeds.meteoalarm.org/api/v1/warnings/feeds-france";
    static final long CACHE_MS = 30 * 60_000;

    static final class Alerte {
        final String phenomene;
        final int niveau;
        final String quand;
        Alerte(String phenomene, int niveau, String quand) {
            this.phenomene = phenomene;
            this.niveau = niveau;
            this.quand = quand;
        }

        String couleurNom() {
            return niveau >= 4 ? "rouge" : niveau == 3 ? "orange" : "jaune";
        }
    }

    interface Ecouteur {
        void alertes(List<Alerte> alertes);
    }

    private static List<Alerte> cache = Collections.emptyList();
    private static long quand;

    static void oublier() {
        cache = Collections.emptyList();
        quand = 0;
    }

    static void obtenir(Context ctx, final Ecouteur ecouteur) {
        final String departement = Config.departement(ctx);
        if (departement.isEmpty()) {
            ecouteur.alertes(Collections.emptyList());
            return;
        }
        if (quand != 0 && System.currentTimeMillis() - quand < CACHE_MS) {
            ecouteur.alertes(cache);
            return;
        }
        final Handler principal = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            List<Alerte> l = null;
            try {
                l = charger(departement);
            } catch (Exception ignored) {
            }
            final List<Alerte> resultat = l;
            principal.post(() -> {
                if (resultat != null) {
                    cache = resultat;
                    quand = System.currentTimeMillis();
                }
                ecouteur.alertes(cache);
            });
        }).start();
    }

    private static List<Alerte> charger(String departement) throws Exception {
        Map<String, JSONObject> derniers = new HashMap<>();
        Map<String, String> envois = new HashMap<>();

        HttpURLConnection c = (HttpURLConnection) new URL(FLUX).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        try (JsonReader r = new JsonReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
            r.beginObject();
            while (r.hasNext()) {
                if (!r.nextName().equals("warnings")) {
                    r.skipValue();
                    continue;
                }
                r.beginArray();
                while (r.hasNext()) {
                    JSONObject alerte = ((JSONObject) lire(r)).optJSONObject("alert");
                    if (alerte == null) continue;
                    String envoi = alerte.optString("sent");
                    JSONArray infos = alerte.optJSONArray("info");
                    for (int i = 0; infos != null && i < infos.length(); i++) {
                        JSONObject info = infos.getJSONObject(i);
                        if (!info.optString("language").startsWith("fr") || !concerne(info, departement)) continue;
                        String cle = parametre(info, "awareness_type") + "|" + info.optString("onset");
                        String precedent = envois.get(cle);
                        if (precedent == null || envoi.compareTo(precedent) > 0) {
                            envois.put(cle, envoi);
                            derniers.put(cle, info);
                        }
                    }
                }
                r.endArray();
            }
        } finally {
            c.disconnect();
        }

        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
        long maintenant = System.currentTimeMillis();
        Map<String, Alerte> parTexte = new HashMap<>();
        for (JSONObject info : derniers.values()) {
            int niveau = niveau(parametre(info, "awareness_level"));
            if (niveau < 2) continue;
            Date debut = iso.parse(info.optString("onset"));
            Date fin = iso.parse(info.optString("expires"));
            if (fin.getTime() <= maintenant || debut.getTime() > maintenant + 24 * 3600_000L) continue;
            String phenomene = phenomene(info.optString("event"));
            String quand = debut.getTime() > maintenant ? quandCommence(debut) : "en cours";
            Alerte deja = parTexte.get(phenomene);
            if (deja == null || deja.niveau < niveau) parTexte.put(phenomene, new Alerte(phenomene, niveau, quand));
        }
        List<Alerte> l = new ArrayList<>(parTexte.values());
        Collections.sort(l, (a, b) -> a.niveau != b.niveau ? b.niveau - a.niveau
                : Boolean.compare(!a.quand.equals("en cours"), !b.quand.equals("en cours")));
        return l;
    }

    private static String phenomene(String event) {
        String p = event.replaceFirst("(?i)^vigilance\\s+(jaune|orange|rouge)\\s+", "").trim();
        if (p.isEmpty()) return event;
        p = p.replaceFirst("(?i)^(vent|grand)-", "$1 ");
        return Character.toUpperCase(p.charAt(0)) + p.substring(1);
    }

    private static boolean concerne(JSONObject info, String departement) {
        JSONArray zones = info.optJSONArray("area");
        for (int i = 0; zones != null && i < zones.length(); i++) {
            if (departement.equalsIgnoreCase(zones.optJSONObject(i).optString("areaDesc"))) return true;
        }
        return false;
    }

    private static String parametre(JSONObject info, String nom) {
        JSONArray ps = info.optJSONArray("parameter");
        for (int i = 0; ps != null && i < ps.length(); i++) {
            JSONObject p = ps.optJSONObject(i);
            if (nom.equals(p.optString("valueName"))) return p.optString("value");
        }
        return "";
    }

    private static int niveau(String valeur) {
        try {
            return Integer.parseInt(valeur.split(";")[0].trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static String quandCommence(Date debut) {
        Calendar d = Calendar.getInstance();
        d.setTime(debut);
        Calendar auj = Calendar.getInstance();
        int h = d.get(Calendar.HOUR_OF_DAY);
        boolean demain = d.get(Calendar.DAY_OF_YEAR) != auj.get(Calendar.DAY_OF_YEAR);
        if (h == 0) return "dès minuit";
        return (demain ? "demain " : "") + "dès " + h + " h";
    }

    private static Object lire(JsonReader r) throws Exception {
        JsonToken t = r.peek();
        switch (t) {
            case BEGIN_OBJECT: {
                JSONObject o = new JSONObject();
                r.beginObject();
                while (r.hasNext()) o.put(r.nextName(), lire(r));
                r.endObject();
                return o;
            }
            case BEGIN_ARRAY: {
                JSONArray a = new JSONArray();
                r.beginArray();
                while (r.hasNext()) a.put(lire(r));
                r.endArray();
                return a;
            }
            case BOOLEAN:
                return r.nextBoolean();
            case NULL:
                r.nextNull();
                return JSONObject.NULL;
            default:
                return r.nextString();
        }
    }
}
