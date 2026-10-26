package app.puritv;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

final class Dns {
    static final String SERVEUR = "dns.adguard-dns.com";
    private static final long INTERVALLE_MS = 10 * 60_000;
    private static long dernierControle;

    static boolean actif(Context c) {
        return "hostname".equals(Settings.Global.getString(c.getContentResolver(), "private_dns_mode"));
    }

    static boolean coupeAuto(Context c) {
        return prefs(c).getBoolean("dns_coupe_auto", false);
    }

    static void activer(Context c, boolean oui) {
        if (oui) Settings.Global.putString(c.getContentResolver(), "private_dns_specifier", SERVEUR);
        Settings.Global.putString(c.getContentResolver(), "private_dns_mode", oui ? "hostname" : "opportunistic");
        try {
            Settings.Global.putString(c.getContentResolver(), "puritv_dns_bloqueur", oui ? "1" : "0");
        } catch (Exception ignored) {
        }
        prefs(c).edit().putBoolean("dns_coupe_auto", false).apply();
    }

    static void surveiller(final Context c, final Runnable siCoupe) {
        long maintenant = System.currentTimeMillis();
        if (maintenant - dernierControle < INTERVALLE_MS || !actif(c) || !connecte(c)) return;
        dernierControle = maintenant;
        final Context app = c.getApplicationContext();
        final Handler principal = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            if (valide(app)) return;
            dormir(20_000);
            if (!actif(app) || !connecte(app) || valide(app)) return;
            try {
                activer(app, false);
            } catch (SecurityException e) {
                return;
            }
            boolean revenu = false;
            for (int i = 0; i < 10 && !revenu; i++) {
                dormir(3_000);
                revenu = valide(app);
            }
            if (revenu) {
                prefs(app).edit().putBoolean("dns_coupe_auto", true).apply();
                Log.i("PuriTV", "Bloqueur de pubs coupé : DNS privé injoignable sur ce réseau");
                principal.post(siCoupe);
            } else {
                activer(app, true);
            }
        }).start();
    }

    private static boolean valide(Context c) {
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return true;
        Network n = cm.getActiveNetwork();
        NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
        return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private static boolean connecte(Context c) {
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("puritv", Context.MODE_PRIVATE);
    }
}
