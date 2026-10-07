package com.akanshaa.mobile;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Finding and launching the phone's other apps by what the user calls them. */
public final class Apps {

    private static final Map<String, String> ALIAS = new HashMap<String, String>();
    static {
        ALIAS.put("whatsapp", "com.whatsapp");
        ALIAS.put("wa", "com.whatsapp");
        ALIAS.put("whatsapp business", "com.whatsapp.w4b");
        ALIAS.put("instagram", "com.instagram.android");
        ALIAS.put("insta", "com.instagram.android");
        ALIAS.put("youtube", "com.google.android.youtube");
        ALIAS.put("chrome", "com.android.chrome");
        ALIAS.put("maps", "com.google.android.apps.maps");
        ALIAS.put("gmail", "com.google.android.gm");
        ALIAS.put("camera", "android.media.action.STILL_IMAGE_CAMERA");
        ALIAS.put("calculator", "com.google.android.calculator");
        ALIAS.put("spotify", "com.spotify.music");
        ALIAS.put("telegram", "org.telegram.messenger");
        ALIAS.put("phone", "com.android.dialer");
        ALIAS.put("dialer", "com.android.dialer");
        ALIAS.put("settings", "com.android.settings");
        ALIAS.put("files", "com.google.android.apps.docs");
    }

    private final Context ctx;
    private final PackageManager pm;

    public Apps(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.pm = this.ctx.getPackageManager();
    }

    /** @return null on success, otherwise a human-readable reason. */
    public String open(String spokenName) {
        String want = clean(spokenName);
        if (want.length() == 0) return "Which app?";

        String exact = ALIAS.get(want);
        if (exact != null) {
            String err = launch(exact);
            if (err == null) return null;
        }

        List<ApplicationInfo> installed = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        ApplicationInfo best = null;
        int bestScore = 0;
        for (ApplicationInfo ai : installed) {
            if (pm.getLaunchIntentForPackage(ai.packageName) == null) continue;
            String label = clean(String.valueOf(pm.getApplicationLabel(ai)));
            int score = score(want, label, ai.packageName);
            if (score > bestScore) { bestScore = score; best = ai; }
        }
        if (best != null && bestScore >= 60) {
            String err = launch(best.packageName);
            if (err == null) return null;
            return err;
        }
        return "I could not find an app called " + spokenName + " on this phone.";
    }

    private String launch(String pkg) {
        Intent i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) return "No launcher icon for " + pkg + ".";
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(i);
            return null;
        } catch (Exception e) {
            return "Could not open " + pkg + ": " + e.getMessage();
        }
    }

    /** Names the user can say, for the settings list and for debugging. */
    public List<String> launchableNames() {
        List<String> out = new ArrayList<String>();
        Intent probe = new Intent(Intent.ACTION_MAIN, null);
        probe.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = pm.queryIntentActivities(probe, 0);
        for (ResolveInfo ri : ris) {
            if (ri.activityInfo == null || ri.activityInfo.packageName == null) continue;
            if (ri.activityInfo.packageName.equals(ctx.getPackageName())) continue;
            String label = String.valueOf(ri.loadLabel(pm));
            if (label.length() > 0) out.add(label);
        }
        return out;
    }

    private static int score(String want, String label, String pkg) {
        if (label.equalsIgnoreCase(want)) return 100;
        if (label.toLowerCase(Locale.US).startsWith(want)) return 85;
        if (label.toLowerCase(Locale.US).contains(want)) return 70;
        if (pkg.toLowerCase(Locale.US).contains(want)) return 60;
        return 0;
    }

    private static String clean(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
