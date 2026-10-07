package com.akanshaa.mobile;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Weather from Open-Meteo. No API key, no account, plain HTTPS. */
public final class Weather {

    public interface Result { void onResult(String spoken, String error); }

    public static void current(final Context ctx, final Prefs prefs, final Result cb) {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    float[] ll = resolve(ctx, prefs);
                    if (ll == null) {
                        say(cb, null, "I do not know where you are. Set a city in Settings, "
                                + "or grant location and open a map once so the phone has a fix.");
                        return;
                    }
                    String url = "https://api.open-meteo.com/v1/forecast?latitude=" + ll[0]
                            + "&longitude=" + ll[1]
                            + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m"
                            + "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                            + "&forecast_days=1&timezone=auto";
                    JSONObject o = new JSONObject(get(url));
                    JSONObject cur = o.getJSONObject("current");
                    JSONObject daily = o.optJSONObject("daily");

                    int code = cur.optInt("weather_code", -1);
                    double t = cur.optDouble("temperature_2m", Double.NaN);
                    double feels = cur.optDouble("apparent_temperature", t);
                    int hum = (int) cur.optDouble("relative_humidity_2m", 0);
                    double wind = cur.optDouble("wind_speed_10m", 0);

                    StringBuilder sb = new StringBuilder();
                    sb.append(prefs.city().length() > 0 ? prefs.city() + " is " : "It is ");
                    sb.append(Math.round(t)).append(" degrees, feels like ").append(Math.round(feels));
                    sb.append(", ").append(desc(code));
                    sb.append(". Humidity ").append(hum).append("%, wind ").append(Math.round(wind)).append(" km/h.");
                    if (daily != null) {
                        JSONArray mx = daily.optJSONArray("temperature_2m_max");
                        JSONArray mn = daily.optJSONArray("temperature_2m_min");
                        if (mx != null && mx.length() > 0) sb.append(" High ").append(Math.round(mx.getDouble(0)));
                        if (mn != null && mn.length() > 0) sb.append(", low ").append(Math.round(mn.getDouble(0)));
                        JSONArray pp = daily.optJSONArray("precipitation_probability_max");
                        if (pp != null && pp.length() > 0) sb.append(". Rain chance ").append(Math.round(pp.getDouble(0))).append("%");
                    }
                    say(cb, sb.toString(), null);
                } catch (Exception e) {
                    say(cb, null, "Weather lookup failed: " + e.getMessage());
                }
            }
        }, "akanshaa-weather").start();
    }

    private static float[] resolve(Context ctx, Prefs prefs) {
        if (prefs.lat() != 0f || prefs.lon() != 0f) return new float[] { prefs.lat(), prefs.lon() };

        if (prefs.city().length() > 0) {
            try {
                String g = get("https://geocoding-api.open-meteo.com/v1/search?count=1&name="
                        + java.net.URLEncoder.encode(prefs.city(), "UTF-8"));
                JSONArray r = new JSONObject(g).optJSONArray("results");
                if (r != null && r.length() > 0) {
                    JSONObject j = r.getJSONObject(0);
                    float lat = (float) j.getDouble("latitude");
                    float lon = (float) j.getDouble("longitude");
                    prefs.setLatLon(lat, lon);
                    return new float[] { lat, lon };
                }
            } catch (Exception ignored) { }
        }

        try {
            LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            String[] providers = { LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER };
            for (String p : providers) {
                if (!lm.isProviderEnabled(p)) continue;
                Location l = lm.getLastKnownLocation(p);
                if (l != null) return new float[] { (float) l.getLatitude(), (float) l.getLongitude() };
            }
        } catch (SecurityException e) {
            // location permission not granted yet - fall through
        } catch (Exception e) {
            // provider unavailable - fall through
        }
        return null;
    }

    private static String desc(int code) {
        switch (code) {
            case 0: return "clear";
            case 1: case 2: return "partly cloudy";
            case 3: return "overcast";
            case 45: case 48: return "foggy";
            case 51: case 53: case 55: case 56: case 57: return "drizzly";
            case 61: case 63: case 65: case 66: case 67: return "rainy";
            case 71: case 73: case 75: case 77: case 85: case 86: return "snowy";
            case 80: case 81: case 82: return "showery";
            case 95: case 96: case 99: return "thundery";
            default: return "unsettled";
        }
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", "Akanshaa/1.0");
        c.setConnectTimeout(12000);
        c.setReadTimeout(15000);
        try {
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String body = read(in);
            if (code != 200) throw new Exception("HTTP " + code + " " + trim(body));
            return body;
        } finally {
            c.disconnect();
        }
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

    private static String trim(String s) { return s.length() > 160 ? s.substring(0, 160) : s; }

    private static void say(final Result cb, final String text, final String err) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() { cb.onResult(text, err); }
        });
    }
}
