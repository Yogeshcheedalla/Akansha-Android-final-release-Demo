package com.akanshaa.mobile;

import android.app.Notification;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Reads incoming chat and missed-call notifications out loud and keeps them in
 * MessageStore so you can ask about them later. The user has to switch this on in
 * Settings > Notification access; it cannot be granted by the app itself.
 */
public class WhatsListener extends NotificationListenerService {

    private static final Set<String> WATCHED = new HashSet<String>(Arrays.asList(
            "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger",
            "com.google.android.apps.messaging", "com.android.messaging",
            "com.ss.android.ugc.aweme", "com.instagram.direct", "com.facebook.orca",
            "com.android.server.telecom", "com.google.android.dialer", "com.android.dialer"));

    /** Verification codes and OTPs are never read aloud, even though they arrive here. */
    private static final String[] SECRET_HINTS = {
            "code is", "otp", "one time password", "verification code", "do not share" };

    private Prefs prefs;
    private Voice voice;
    private String lastKey = "";
    private long lastAt = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        voice = new Voice(this, prefs);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (sbn == null || sbn.getPackageName() == null) return;
            if (sbn.getPackageName().equals(getPackageName())) return;
            if (!prefs.readWhats()) return;
            if (!WATCHED.contains(sbn.getPackageName())) return;

            Notification n = sbn.getNotification();
            if (n == null) return;
            Bundle ex = n.extras;
            if (ex == null) return;

            String title = str(ex.getCharSequence(Notification.EXTRA_TITLE));
            String text = str(ex.getCharSequence(Notification.EXTRA_BIG_TEXT));
            if (text.length() == 0) text = str(ex.getCharSequence(Notification.EXTRA_TEXT));
            if (title.length() == 0 && text.length() == 0) return;

            String lower = (title + " " + text).toLowerCase(Locale.US);
            for (String hint : SECRET_HINTS) {
                if (lower.contains(hint)) {
                    MessageStore.add(label(sbn.getPackageName()), title, "[content kept private]");
                    return;
                }
            }

            String dedupe = sbn.getPackageName() + "|" + title + "|" + text;
            long now = System.currentTimeMillis();
            if (dedupe.equals(lastKey) && now - lastAt < 20000) return;
            lastKey = dedupe;
            lastAt = now;

            String who = label(sbn.getPackageName());
            MessageStore.add(who, title, text);
            if (!prefs.speakReplies()) return;

            StringBuilder spoken = new StringBuilder();
            spoken.append(isCallApp(sbn.getPackageName()) ? "Missed call" : "Message");
            spoken.append(" from ").append(title.length() > 0 ? title : who).append(". ");
            if (text.length() > 0) spoken.append(text.length() > 240 ? text.substring(0, 240) : text);
            voice.speak(spoken.toString());
        } catch (Exception ignored) {
            // a listener that throws gets force-closed by the system; stay quiet instead
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) { }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (voice != null) voice.release();
    }

    private static boolean isCallApp(String pkg) {
        return pkg.contains("telecom") || pkg.contains("dialer");
    }

    private String label(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            String l = String.valueOf(pm.getApplicationLabel(ai));
            return TextUtils.isEmpty(l) ? pkg : l;
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private static String str(CharSequence cs) { return cs == null ? "" : cs.toString().trim(); }
}
