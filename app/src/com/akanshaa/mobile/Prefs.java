package com.akanshaa.mobile;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * App-private settings. The Gemini key lives in this app's own private prefs dir,
 * which another app cannot read without root. It is NOT encrypted at rest - see the
 * honest-limits note in the README.
 */
public final class Prefs {
    private static final String FILE = "akanshaa_prefs";
    public static final String DEFAULT_MODEL = "gemini-flash-latest";

    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public String apiKey() { return sp.getString("gemini_key", ""); }
    public void setApiKey(String k) { sp.edit().putString("gemini_key", trim(k)).apply(); }
    public boolean hasKey() { return apiKey().length() > 8; }

    public String model() {
        String m = sp.getString("model", "");
        return m.length() == 0 ? DEFAULT_MODEL : m;
    }
    public void setModel(String m) { sp.edit().putString("model", trim(m)).apply(); }

    public boolean speakReplies() { return sp.getBoolean("speak", true); }
    public void setSpeakReplies(boolean v) { sp.edit().putBoolean("speak", v).apply(); }

    public boolean readWhats() { return sp.getBoolean("read_whats", true); }
    public void setReadWhats(boolean v) { sp.edit().putBoolean("read_whats", v).apply(); }

    public boolean orbEnabled() { return sp.getBoolean("orb", true); }
    public void setOrbEnabled(boolean v) { sp.edit().putBoolean("orb", v).apply(); }

    /** Language tag for STT + TTS, e.g. en-IN or hi-IN. */
    public String language() {
        String l = sp.getString("lang", "");
        return l.length() == 0 ? "en-IN" : l;
    }
    public void setLanguage(String l) { sp.edit().putString("lang", trim(l)).apply(); }

    public float lat() { return sp.getFloat("lat", 0f); }
    public float lon() { return sp.getFloat("lon", 0f); }
    public void setLatLon(float lat, float lon) {
        sp.edit().putFloat("lat", lat).putFloat("lon", lon).apply();
    }

    public String city() { return sp.getString("city", ""); }
    public void setCity(String v) { sp.edit().putString("city", trim(v)).apply(); }

    /** Conversation memory, newest last, capped so it cannot grow forever. */
    public void rememberTurn(String role, String text) {
        String prev = sp.getString("history", "");
        String line = role + "\u001F" + text.replace("\n", " ") + "\u001E";
        String next = prev + line;
        int over = next.length() - 6000;
        if (over > 0) {
            int cut = next.indexOf('\u001E', over);
            next = cut >= 0 ? next.substring(cut + 1) : next.substring(over);
        }
        sp.edit().putString("history", next).apply();
    }

    public String historyForPrompt(int maxChars) {
        String h = sp.getString("history", "");
        if (h.length() > maxChars) h = h.substring(h.length() - maxChars);
        StringBuilder sb = new StringBuilder();
        for (String chunk : h.split("\u001E")) {
            int i = chunk.indexOf('\u001F');
            if (i <= 0) continue;
            sb.append(chunk, 0, i).append(": ").append(chunk.substring(i + 1)).append('\n');
        }
        return sb.toString();
    }

    public void clearHistory() { sp.edit().putString("history", "").apply(); }

    private static String trim(String s) { return s == null ? "" : s.trim(); }
}
