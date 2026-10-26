package app.puritv;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;

import java.util.Arrays;
import java.util.List;

final class Memoire {
    static final List<String> GARDER = Arrays.asList(
            "com.tcl.tv", "com.google.android.apps.mediashell");

    static void liberer(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return;
        long avant = disponible(am);
        PackageManager pm = ctx.getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        int n = 0;
        for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
            String p = ri.activityInfo.packageName;
            if (p.equals(ctx.getPackageName()) || GARDER.contains(p)) continue;
            am.killBackgroundProcesses(p);
            n++;
        }
        am.killBackgroundProcesses("com.android.vending");
        Log.i("PuriTV", "Mémoire libérée : " + n + " applis, "
                + (disponible(am) - avant) / (1024 * 1024) + " Mo récupérés, "
                + disponible(am) / (1024 * 1024) + " Mo libres");
    }

    private static long disponible(ActivityManager am) {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.availMem;
    }
}
