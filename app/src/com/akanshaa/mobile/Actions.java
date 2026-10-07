package com.akanshaa.mobile;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.telephony.SmsManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Turns a spoken sentence into a phone action without spending a token. Anything it
 * does not recognise falls through to Gemini. Every path answers through the callback,
 * so the caller never has to guess whether something happened.
 */
public final class Actions {

    public interface Reply {
        /** speak=true means the caller should read this aloud. */
        void say(String text, boolean speak);
        void openSettings();
    }

    private final Context ctx;
    private final Prefs prefs;
    private final Apps apps;

    public Actions(Context ctx, Prefs prefs) {
        this.ctx = ctx.getApplicationContext();
        this.prefs = prefs;
        this.apps = new Apps(ctx);
    }

    /** @return true if a local command handled it. */
    public boolean run(String heard, Reply out) {
        if (heard == null) return false;
        String s = heard.toLowerCase(Locale.US).trim().replaceAll("[.!?]+$", "").trim();
        if (s.length() == 0) return false;

        // ---- calls ---------------------------------------------------------
        if (s.startsWith("call ") || s.startsWith("phone ") || s.startsWith("dial ")) {
            String name = s.replaceFirst("^(call|phone|dial) ", "").trim();
            if (name.length() == 0) { out.say("Who should I call?", true); return true; }
            String number = numberFor(name);
            boolean mayDialDirectly =
                    ctx.checkSelfPermission(android.Manifest.permission.CALL_PHONE)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
            Intent i = new Intent(mayDialDirectly ? Intent.ACTION_CALL : Intent.ACTION_DIAL);
            i.setData(Uri.parse("tel:" + Uri.encode(number != null ? number : name)));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                ctx.startActivity(i);
                out.say("Calling " + name + ".", true);
            } catch (Exception e) {
                out.say("I could not start that call. " + e.getMessage(), true);
            }
            return true;
        }

        // ---- sms -----------------------------------------------------------
        if (s.startsWith("text ") || s.startsWith("message ") || s.startsWith("send ")) {
            String body = s.replaceFirst("^(text|message|send) ", "");
            int at = body.indexOf(" to ");
            if (at < 0) { out.say("Say it like: text Rahul to hello", true); return true; }
            String message = body.substring(0, at).trim();
            String who = body.substring(at + 4).trim();
            String number = numberFor(who);
            if (number == null) number = who.matches("[0-9+\\- ]{6,}") ? who : null;
            if (number == null) { out.say("I could not find " + who + " in your contacts.", true); return true; }
            try {
                SmsManager sm = Build.VERSION.SDK_INT >= 31
                        ? ctx.getSystemService(SmsManager.class) : SmsManager.getDefault();
                sm.sendTextMessage(number, null, message, null, null);
                out.say("Sent to " + who + ".", true);
            } catch (SecurityException se) {
                out.say("SMS permission is not granted. Turn it on in Settings.", true);
            } catch (Exception e) {
                out.say("The message did not send. " + e.getMessage(), true);
            }
            return true;
        }

        // ---- open apps -----------------------------------------------------
        if (s.startsWith("open ") || s.startsWith("launch ") || s.startsWith("start ")) {
            String name = s.replaceFirst("^(open|launch|start) ", "").trim()
                           .replaceFirst(" app$", "").trim();
            if (name.equals("akanshaa") || name.equals("akanisha") || name.equals("settings")) {
                out.openSettings();
                out.say("Here I am.", true);
                return true;
            }
            String err = apps.open(name);
            out.say(err == null ? "Opening " + name + "." : err, true);
            return true;
        }

        // ---- weather -------------------------------------------------------
        if (s.contains("weather") || s.contains("temperature") || s.contains("forecast")
                || s.contains("how hot") || s.contains("will it rain")) {
            out.say("Checking the weather.", true);
            Weather.current(ctx, prefs, new Weather.Result() {
                @Override public void onResult(String text, String error) {
                    out.say(text != null ? text : error, true);
                }
            });
            return true;
        }

        // ---- time and date -------------------------------------------------
        if (s.equals("time") || s.startsWith("what time") || s.equals("date")
                || s.startsWith("what day") || s.startsWith("what's the date")
                || s.startsWith("whats the date")) {
            out.say(new SimpleDateFormat("EEEE d MMMM, h:mm a", Locale.getDefault())
                    .format(new Date()), true);
            return true;
        }

        // ---- notification memory ------------------------------------------
        if (s.contains("any message") || s.contains("new message") || s.contains("unread")
                || s.startsWith("read my") || s.contains("who messaged") || s.contains("what did")) {
            String needle = s.replaceAll("(any|new|message|unread|read|my|who|messaged|what|did|the|tell|me|of|from)", " ")
                             .replaceAll("\\s+", " ").trim();
            List<MessageStore.Item> items = MessageStore.recent(needle);
            if (items.isEmpty()) {
                out.say(needle.length() > 2
                        ? "Nothing from " + needle + " that I have seen since the phone unlocked."
                        : "I have not seen any messages yet. Turn on notification access in Settings.", true);
            } else {
                StringBuilder sb = new StringBuilder();
                int n = Math.min(3, items.size());
                for (int i = 0; i < n; i++) {
                    if (i > 0) sb.append(". Then ");
                    sb.append(items.get(i).describe());
                }
                out.say(sb.toString(), true);
            }
            return true;
        }
        if (s.contains("clear message") || s.contains("forget message")) {
            MessageStore.clear();
            out.say("Cleared.", true);
            return true;
        }

        // ---- speech control ------------------------------------------------
        if (s.equals("stop") || s.equals("quiet") || s.equals("shut up") || s.equals("enough")
                || s.equals("silence")) {
            out.say("", false);
            return true;
        }

        // ---- settings ------------------------------------------------------
        if (s.contains("api key") || s.contains("change key") || s.contains("add key")
                || s.contains("set my gemini") || s.contains("gemini key")) {
            out.openSettings();
            out.say("Paste your key in the box at the top.", true);
            return true;
        }

        return false;
    }

    /** Looks up a contact's first number. Returns null when nothing matches. */
    private String numberFor(String name) {
        if (name == null) return null;
        String digits = name.replaceAll("[^0-9+]", "");
        if (digits.length() >= 6) return digits;

        try {
            Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
            String[] proj = { ContactsContract.CommonDataKinds.Phone.NUMBER };
            String sel = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?";
            String[] args = { "%" + name.trim() + "%" };
            Cursor c = ctx.getContentResolver().query(uri, proj, sel, args, null);
            try {
                if (c != null && c.moveToFirst()) return c.getString(0);
            } finally {
                if (c != null) c.close();
            }
        } catch (SecurityException e) {
            return null;   // contacts permission not granted yet
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}
