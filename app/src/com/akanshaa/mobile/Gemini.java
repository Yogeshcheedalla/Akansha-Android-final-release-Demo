package com.akanshaa.mobile;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal Gemini REST client over HttpURLConnection. No third-party SDK, so the
 * whole app builds without Gradle. Runs on a background thread and calls back on main.
 */
public final class Gemini {

    public interface Reply {
        void onResult(String text, String error);
    }

    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";

    private static final String SYSTEM_PROMPT =
            "You are Akanshaa, a voice assistant on an Android phone. "
          + "Answer in one or two short spoken sentences unless asked for more. "
          + "The user is a non-native English speaker; use simple words and short sentences. "
          + "Never mention which underlying model you are running on. "
          + "If you cannot do something on the phone, say so plainly instead of inventing it.";

    private final Prefs prefs;

    public Gemini(Prefs prefs) { this.prefs = prefs; }

    /** Ask a question. history = prior turns already formatted as "user: ...\n". */
    public void ask(String userText, final Reply reply) {
        final String body;
        try {
            JSONObject root = new JSONObject();
            JSONArray contents = new JSONArray();

            JSONObject ctx = new JSONObject();
            ctx.put("role", "user");
            ctx.put("parts", new JSONArray().put(new JSONObject().put("text",
                    "Earlier in this conversation:\n" + prefs.historyForPrompt(3000))));
            contents.put(ctx);

            JSONObject turn = new JSONObject();
            turn.put("role", "user");
            turn.put("parts", new JSONArray().put(new JSONObject().put("text", userText)));
            contents.put(turn);

            root.put("contents", contents);
            root.put("systemInstruction", new JSONObject()
                    .put("parts", new JSONArray().put(new JSONObject().put("text", SYSTEM_PROMPT))));
            root.put("generationConfig", new JSONObject()
                    .put("temperature", 0.6)
                    .put("maxOutputTokens", 512));
            body = root.toString();
        } catch (Exception e) {
            postError(reply, "Could not build the request: " + e.getMessage());
            return;
        }

        new Thread(new Runnable() {
            @Override public void run() {
                String url = BASE + prefs.model() + ":generateContent?key=" + prefs.apiKey();
                HttpURLConnection c = null;
                try {
                    c = (HttpURLConnection) new URL(url).openConnection();
                    c.setRequestProperty("Content-Type", "application/json");
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(45000);
                    c.setDoOutput(true);
                    c.setRequestMethod("POST");
                    OutputStream os = c.getOutputStream();
                    os.write(body.getBytes("UTF-8"));
                    os.flush();
                    os.close();

                    int code = c.getResponseCode();
                    String raw = read(c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream());
                    if (code != 200) { postError(reply, friendly(code, raw)); return; }

                    String text = extract(raw);
                    if (text == null) { postError(reply, "The reply came back empty."); return; }
                    deliver(reply, text.trim(), null);
                } catch (Exception e) {
                    postError(reply, e.getClass().getSimpleName() + ": " + e.getMessage());
                } finally {
                    if (c != null) c.disconnect();
                }
            }
        }, "akanshaa-gemini").start();
    }

    /** Lists model ids so the user can pick one that actually exists for their key. */
    public void listModels(final Reply reply) {
        new Thread(new Runnable() {
            @Override public void run() {
                HttpURLConnection c = null;
                try {
                    c = (HttpURLConnection) new URL(BASE + "?key=" + prefs.apiKey()).openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(30000);
                    int code = c.getResponseCode();
                    String raw = read(code >= 400 ? c.getErrorStream() : c.getInputStream());
                    if (code != 200) { postError(reply, friendly(code, raw)); return; }
                    JSONObject o = new JSONObject(raw);
                    JSONArray ms = o.optJSONArray("models");
                    List<String> out = new ArrayList<String>();
                    for (int i = 0; ms != null && i < ms.length(); i++) {
                        String name = ms.getJSONObject(i).optString("name", "");
                        if (name.startsWith("models/")) name = name.substring(7);
                        if (name.length() > 0) out.add(name);
                    }
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < out.size(); i++) {
                        if (i > 0) sb.append('\n');
                        sb.append(out.get(i));
                    }
                    deliver(reply, sb.toString(), null);
                } catch (Exception e) {
                    postError(reply, e.getClass().getSimpleName() + ": " + e.getMessage());
                } finally {
                    if (c != null) c.disconnect();
                }
            }
        }, "akanshaa-models").start();
    }

    private static String extract(String raw) throws Exception {
        JSONObject o = new JSONObject(raw);
        JSONArray cand = o.optJSONArray("candidates");
        if (cand == null || cand.length() == 0) return null;
        JSONArray parts = cand.getJSONObject(0).optJSONObject("content") == null
                ? null : cand.getJSONObject(0).getJSONObject("content").optJSONArray("parts");
        if (parts == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            String t = parts.getJSONObject(i).optString("text", "");
            if (t.length() > 0) { if (sb.length() > 0) sb.append(' '); sb.append(t); }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static String friendly(int code, String raw) {
        String msg = raw == null ? "" : raw;
        try { msg = new JSONObject(raw).getJSONObject("error").optString("message", msg); }
        catch (Exception ignored) { }
        if (code == 400 && msg.toLowerCase().contains("api key")) return "The API key was rejected. Check it in Settings.";
        if (code == 403) return "Access denied (403). This key may not allow that model.";
        if (code == 404) return "Model not found (404). Pick another model in Settings - use List models.";
        if (code == 429) return "Quota reached (429). The free tier is rate limited; wait and try again.";
        return "Gemini error " + code + ": " + (msg.length() > 300 ? msg.substring(0, 300) : msg);
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toString("UTF-8");
    }

    private static void postError(final Reply r, final String err) { deliver(r, null, err); }

    private static void deliver(final Reply r, final String text, final String err) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() { r.onResult(text, err); }
        });
    }
}
